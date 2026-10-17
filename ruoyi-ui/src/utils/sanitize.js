const BLOCKED_TAGS = new Set([
  'script',
  'style',
  'iframe',
  'object',
  'embed',
  'link',
  'meta',
  'base',
  'form',
  'input',
  'button',
  'textarea',
  'select',
  'option',
  'svg',
  'math'
])

const URL_ATTRS = new Set(['href', 'src', 'xlink:href', 'formaction'])

function isSafeUrl(value, attrName) {
  if (!value) return true
  const normalized = String(value).trim().replace(/[\u0000-\u001F\u007F\s]+/g, '').toLowerCase()
  if (!normalized) return true

  if (normalized.startsWith('#') || normalized.startsWith('/') || normalized.startsWith('./') || normalized.startsWith('../')) {
    return true
  }

  if (normalized.startsWith('http://') || normalized.startsWith('https://') || normalized.startsWith('mailto:') || normalized.startsWith('tel:')) {
    return true
  }

  if (attrName === 'src' && normalized.startsWith('data:image/')) {
    return true
  }

  return false
}

export function sanitizeHtml(html) {
  if (!html || typeof document === 'undefined') {
    return html || ''
  }

  const template = document.createElement('template')
  template.innerHTML = html

  const walk = node => {
    Array.from(node.children || []).forEach(child => {
      const tag = child.tagName.toLowerCase()
      if (BLOCKED_TAGS.has(tag)) {
        child.remove()
        return
      }

      Array.from(child.attributes || []).forEach(attr => {
        const name = attr.name.toLowerCase()
        if (name.startsWith('on') || name === 'style' || name === 'srcdoc') {
          child.removeAttribute(attr.name)
          return
        }

        if (URL_ATTRS.has(name) && !isSafeUrl(attr.value, name)) {
          child.removeAttribute(attr.name)
        }
      })

      if (tag === 'a' && child.getAttribute('target') === '_blank') {
        child.setAttribute('rel', 'noopener noreferrer')
      }

      walk(child)
    })
  }

  walk(template.content)
  return template.innerHTML
}
