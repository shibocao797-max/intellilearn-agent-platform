import { mkdir, writeFile } from 'node:fs/promises'
import { spawn } from 'node:child_process'
import os from 'node:os'
import path from 'node:path'

const EDGE = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'
const ORIGIN = 'http://localhost:5173'
const DEBUG_PORT = 9666
const OUTPUT_DIR = path.resolve('output/qa-auth')
const sleep = milliseconds => new Promise(resolve => setTimeout(resolve, milliseconds))

async function debuggerUrl() {
  for (let attempt = 0; attempt < 40; attempt += 1) {
    try {
      const response = await fetch(`http://127.0.0.1:${DEBUG_PORT}/json/list`)
      const targets = await response.json()
      const page = targets.find(target => target.type === 'page')
      if (page?.webSocketDebuggerUrl) return page.webSocketDebuggerUrl
    } catch {
      // Edge is still starting.
    }
    await sleep(250)
  }
  throw new Error('Timed out waiting for Edge DevTools')
}

function connect(url) {
  const socket = new WebSocket(url)
  const pending = new Map()
  const events = []
  let id = 0
  socket.addEventListener('message', event => {
    const message = JSON.parse(event.data)
    if (!message.id) {
      events.push(message)
      return
    }
    const request = pending.get(message.id)
    if (!request) return
    pending.delete(message.id)
    if (message.error) request.reject(new Error(message.error.message))
    else request.resolve(message.result)
  })
  const ready = new Promise((resolve, reject) => {
    socket.addEventListener('open', resolve, { once: true })
    socket.addEventListener('error', reject, { once: true })
  })
  return {
    ready,
    events,
    close: () => socket.close(),
    send(method, params = {}) {
      id += 1
      return new Promise((resolve, reject) => {
        pending.set(id, { resolve, reject })
        socket.send(JSON.stringify({ id, method, params }))
      })
    }
  }
}

async function waitForDocument(cdp) {
  for (let attempt = 0; attempt < 35; attempt += 1) {
    const state = await cdp.send('Runtime.evaluate', { expression: 'document.readyState', returnByValue: true })
    if (state.result?.value === 'complete') break
    await sleep(100)
    if (attempt === 34) throw new Error('Timed out waiting for document readiness')
  }
  await sleep(550)
}

async function waitForPath(cdp, expectedPath) {
  for (let attempt = 0; attempt < 25; attempt += 1) {
    const state = await cdp.send('Runtime.evaluate', { expression: 'location.pathname', returnByValue: true })
    if (state.result?.value === expectedPath) return
    await sleep(100)
  }
  const state = await cdp.send('Runtime.evaluate', { expression: 'location.href', returnByValue: true })
  throw new Error(`Timed out waiting for ${expectedPath}; current URL: ${state.result?.value || 'unknown'}`)
}

async function waitForSelector(cdp, selector) {
  for (let attempt = 0; attempt < 35; attempt += 1) {
    const state = await cdp.send('Runtime.evaluate', {
      expression: `Boolean(document.querySelector(${JSON.stringify(selector)}))`,
      returnByValue: true
    })
    if (state.result?.value) return
    await sleep(100)
  }
  throw new Error(`Timed out waiting for selector ${selector}`)
}

async function inspect(cdp, page, viewport) {
  let navigation
  if (page === '/register') {
    await cdp.send('Page.navigate', { url: `${ORIGIN}/register` })
    await waitForDocument(cdp)
    await waitForPath(cdp, '/register')
    await waitForSelector(cdp, '.auth-shell')

    navigation = await cdp.send('Page.navigate', { url: `${ORIGIN}/login` })
    await waitForDocument(cdp)
    await waitForPath(cdp, '/login')
    await waitForSelector(cdp, '.auth-switch a')
    const click = await cdp.send('Runtime.evaluate', {
      expression: 'Boolean(document.querySelector(".auth-switch a")?.click())',
      returnByValue: true
    })
    if (click.exceptionDetails) throw new Error('Could not click the register link')
  } else {
    navigation = await cdp.send('Page.navigate', { url: `${ORIGIN}${page}` })
  }
  await waitForDocument(cdp)
  await waitForPath(cdp, page)
  await waitForSelector(cdp, '.auth-shell')
  const state = await cdp.send('Runtime.evaluate', {
    expression: `JSON.stringify((() => {
      const shell = document.querySelector('.auth-shell')
      const form = document.querySelector('form')
      const passwordInputs = [...document.querySelectorAll('input[type=password]')]
      const title = document.querySelector('h1')
      const requiredFields = form ? form.querySelectorAll('[required]').length : 0
      const canvas = document.querySelector('.login-knowledge-scene')
      const overflow = [...document.querySelectorAll('body *')]
        .map(node => ({ node, rect: node.getBoundingClientRect() }))
        .filter(item => item.rect.right > document.documentElement.clientWidth + 1 || item.rect.left < -1)
        .slice(0, 8)
        .map(item => ({ tag: item.node.tagName, cls: String(item.node.className || '').slice(0, 80) }))
      return {
        page: location.pathname,
        viewport: ${viewport.width},
        shell: shell ? { width: Math.round(shell.getBoundingClientRect().width), height: Math.round(shell.getBoundingClientRect().height) } : null,
        form: Boolean(form),
        title: title?.textContent?.trim() || null,
        requiredFields,
        passwordType: passwordInputs[0]?.type || null,
        canvas: canvas ? {
          width: canvas.width,
          height: canvas.height,
          hasPixels: canvas.toDataURL('image/png').length > 500
        } : null,
        scrollWidth: document.documentElement.scrollWidth,
        clientWidth: document.documentElement.clientWidth,
        overflow
      }
    })())`,
    returnByValue: true
  })
  const metrics = JSON.parse(state.result?.value || '{}')
  const minimumFields = page === '/register' ? 3 : 2
  if (metrics.page !== page) throw new Error(`Expected ${page}, inspected ${metrics.page || 'unknown'}`)
  if (!metrics.shell || !metrics.form || !metrics.title) throw new Error(`${page} is missing its shell, form, or title`)
  if (metrics.requiredFields < minimumFields) throw new Error(`${page} exposes only ${metrics.requiredFields} required fields`)
  if (metrics.scrollWidth > metrics.clientWidth + 1) throw new Error(`${page} overflows horizontally at ${viewport.width}px`)
  const passwordSelector = page === '/register' ? 'input[autocomplete="new-password"]' : 'input[autocomplete="current-password"]'
  const toggle = await cdp.send('Runtime.evaluate', {
    expression: `document.querySelector(${JSON.stringify(passwordSelector)}) && document.querySelector('.password-toggle')?.click(); "clicked"`,
    returnByValue: true
  })
  await sleep(80)
  const toggleState = await cdp.send('Runtime.evaluate', {
    expression: `document.querySelector(${JSON.stringify(passwordSelector)})?.type || null`,
    returnByValue: true
  })
  metrics.passwordToggleResult = toggleState.result?.value || toggle.result?.value || null
  if (metrics.passwordToggleResult !== 'text') throw new Error(`${page} password visibility toggle did not reveal the password input`)
  metrics.navigation = { requested: `${ORIGIN}${page}`, loaderId: navigation.loaderId || null }
  const shot = await cdp.send('Page.captureScreenshot', { format: 'png', fromSurface: true, captureBeyondViewport: false })
  await writeFile(path.join(OUTPUT_DIR, `${page.replace('/', '')}-${viewport.width}.png`), Buffer.from(shot.data, 'base64'))

  if (!metrics.canvas?.width || !metrics.canvas?.height || !metrics.canvas.hasPixels) {
    throw new Error(`${page} canvas did not render a visible scene`)
  }

  if (page === '/register') {
    await cdp.send('Runtime.evaluate', {
      expression: `(() => {
        const inputs = [...document.querySelectorAll('input[autocomplete="new-password"]')]
        const setValue = (input, value) => {
          const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')?.set
          setter?.call(input, value)
          input?.dispatchEvent(new Event('input', { bubbles: true }))
        }
        setValue(inputs[0], 'one-password')
        setValue(inputs[1], 'different-password')
        document.querySelector('form')?.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))
      })()`
    })
    await waitForSelector(cdp, '.register-alert')
    metrics.passwordMismatchAlert = true
  }
  return metrics
}

async function main() {
  await mkdir(OUTPUT_DIR, { recursive: true })
  const edge = spawn(EDGE, [
    '--headless=new',
    '--disable-gpu',
    '--hide-scrollbars',
    '--no-first-run',
    '--disable-extensions',
    `--remote-debugging-port=${DEBUG_PORT}`,
    `--user-data-dir=${path.join(os.tmpdir(), 'chengxi-auth-qa-edge')}`,
    'about:blank'
  ], { stdio: 'ignore' })

  try {
    const cdp = connect(await debuggerUrl())
    await cdp.ready
    await cdp.send('Page.enable')
    await cdp.send('Runtime.enable')
    await cdp.send('Log.enable')

    for (const viewport of [{ width: 1440, height: 900 }, { width: 1280, height: 720 }, { width: 390, height: 844 }]) {
      await cdp.send('Emulation.setDeviceMetricsOverride', {
        ...viewport,
        deviceScaleFactor: 1,
        mobile: viewport.width < 700,
        screenWidth: viewport.width,
        screenHeight: viewport.height
      })
      for (const page of ['/login', '/register']) {
        const metrics = await inspect(cdp, page, viewport)
        process.stdout.write(`${page} ${viewport.width}: ${JSON.stringify(metrics)}\n`)
      }
    }

    await cdp.send('Emulation.setEmulatedMedia', {
      features: [{ name: 'prefers-reduced-motion', value: 'reduce' }]
    })
    await cdp.send('Page.navigate', { url: `${ORIGIN}/login` })
    await waitForDocument(cdp)
    await waitForPath(cdp, '/login')
    await waitForSelector(cdp, '.auth-shell')
    const reducedState = await cdp.send('Runtime.evaluate', {
      expression: `JSON.stringify((() => {
        const canvas = document.querySelector('.login-knowledge-scene')
        const orbit = document.querySelector('.auth-stage-orbit')
        return {
          canvas: Boolean(canvas && canvas.width && canvas.height && canvas.toDataURL('image/png').length > 500),
          orbitAnimation: orbit ? getComputedStyle(orbit).animationName : null
        }
      })())`,
      returnByValue: true
    })
    const reducedMotion = JSON.parse(reducedState.result?.value || '{}')
    if (!reducedMotion.canvas || reducedMotion.orbitAnimation !== 'none') throw new Error('Reduced-motion mode did not keep the scene visible and static')
    process.stdout.write(`reduced motion: ${JSON.stringify(reducedMotion)}\n`)
    await cdp.send('Emulation.setEmulatedMedia', { features: [] })

    const errors = cdp.events
      .filter(event => event.method === 'Runtime.exceptionThrown' || (event.method === 'Log.entryAdded' && event.params?.entry?.level === 'error'))
      .map(event => event.params?.exceptionDetails?.exception?.description || event.params?.entry?.text || event.method)
    if (errors.length) throw new Error(`browser errors: ${JSON.stringify(errors)}`)
    process.stdout.write(`browser errors: ${JSON.stringify(errors)}\n`)
    cdp.close()
  } finally {
    edge.kill()
  }
}

main().catch(error => {
  console.error(error.message)
  process.exitCode = 1
})
