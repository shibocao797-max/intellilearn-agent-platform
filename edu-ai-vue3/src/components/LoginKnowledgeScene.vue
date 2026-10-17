<template>
  <canvas ref="canvasRef" class="login-knowledge-scene" aria-hidden="true"></canvas>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'

const canvasRef = ref(null)
const props = defineProps({
  mode: {
    type: String,
    default: 'login'
  }
})
let context = null
let frameId = 0
let lastFrameAt = 0
let resizeObserver = null
let motionQuery = null
let canvasWidth = 0
let canvasHeight = 0

const desktopNodes = [
  { x: 0.14, y: 0.2, size: 10, tone: 'primary' },
  { x: 0.32, y: 0.12, size: 8, tone: 'secondary' },
  { x: 0.72, y: 0.16, size: 11, tone: 'primary' },
  { x: 0.88, y: 0.37, size: 9, tone: 'secondary' },
  { x: 0.82, y: 0.74, size: 10, tone: 'primary' },
  { x: 0.38, y: 0.84, size: 9, tone: 'secondary' },
  { x: 0.1, y: 0.66, size: 8, tone: 'primary' }
]

const mobileNodes = [
  { x: 0.12, y: 0.17, size: 8, tone: 'primary' },
  { x: 0.83, y: 0.18, size: 8, tone: 'secondary' },
  { x: 0.9, y: 0.75, size: 8, tone: 'primary' },
  { x: 0.14, y: 0.8, size: 8, tone: 'secondary' }
]

function resolveColor(name, fallback) {
  const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  return value || fallback
}

function resizeCanvas() {
  const canvas = canvasRef.value
  if (!canvas) return

  const rect = canvas.getBoundingClientRect()
  const dpr = Math.min(window.devicePixelRatio || 1, 2)
  canvasWidth = Math.max(1, rect.width)
  canvasHeight = Math.max(1, rect.height)
  canvas.width = Math.round(canvasWidth * dpr)
  canvas.height = Math.round(canvasHeight * dpr)
  context = canvas.getContext('2d')
  context.setTransform(dpr, 0, 0, dpr, 0, 0)
  drawScene(0, true)
}

function drawRoundedRect(ctx, x, y, width, height, radius) {
  const corner = Math.min(radius, width / 2, height / 2)
  ctx.beginPath()
  ctx.moveTo(x + corner, y)
  ctx.arcTo(x + width, y, x + width, y + height, corner)
  ctx.arcTo(x + width, y + height, x, y + height, corner)
  ctx.arcTo(x, y + height, x, y, corner)
  ctx.arcTo(x, y, x + width, y, corner)
  ctx.closePath()
}

function pointFor(node) {
  return { x: node.x * canvasWidth, y: node.y * canvasHeight }
}

function drawConnection(ctx, from, to, progress, color, muted) {
  ctx.save()
  ctx.beginPath()
  ctx.moveTo(from.x, from.y)
  ctx.lineTo(to.x, to.y)
  ctx.strokeStyle = muted ? 'rgba(82, 96, 90, 0.16)' : color
  ctx.lineWidth = 1
  ctx.setLineDash(muted ? [] : [3, 13])
  ctx.lineDashOffset = -progress * 30
  ctx.stroke()
  ctx.restore()
}

function drawPacket(ctx, from, to, progress, color, muted) {
  if (muted) return
  const ratio = (progress % 1 + 1) % 1
  const x = from.x + (to.x - from.x) * ratio
  const y = from.y + (to.y - from.y) * ratio
  ctx.save()
  ctx.translate(x, y)
  ctx.rotate(Math.PI / 4)
  ctx.globalAlpha = 0.78
  ctx.fillStyle = color
  ctx.fillRect(-2.5, -2.5, 5, 5)
  ctx.restore()
}

function drawNode(ctx, point, node, color, surface, muted, timestamp, index) {
  const drift = muted ? 0 : Math.sin(timestamp * 0.0014 + index * 1.7) * 2.5
  ctx.save()
  ctx.translate(point.x, point.y + drift)
  ctx.rotate(Math.PI / 4)
  drawRoundedRect(ctx, -node.size / 2, -node.size / 2, node.size, node.size, 2)
  ctx.fillStyle = muted ? surface : color
  ctx.globalAlpha = muted ? 0.6 : 0.76
  ctx.fill()
  ctx.lineWidth = 1
  ctx.strokeStyle = muted ? 'rgba(82, 96, 90, 0.32)' : surface
  ctx.stroke()
  ctx.restore()
}

function drawCoordinator(ctx, center, primary, secondary, surface, reduced, timestamp) {
  const size = Math.min(54, Math.max(40, canvasWidth * 0.045))
  const pulse = reduced ? 1 : 0.9 + Math.sin(timestamp * 0.0012) * 0.08
  ctx.save()
  ctx.globalAlpha = reduced ? 0.34 : 0.62
  ctx.strokeStyle = primary
  ctx.lineWidth = 1
  ctx.beginPath()
  ctx.arc(center.x, center.y, size * 0.88 * pulse, 0, Math.PI * 2)
  ctx.stroke()
  if (!reduced) {
    ctx.globalAlpha = 0.24
    ctx.beginPath()
    ctx.arc(center.x, center.y, size * 1.22 * pulse, 0, Math.PI * 2)
    ctx.stroke()
  }
  ctx.globalAlpha = 0.9
  drawRoundedRect(ctx, center.x - size / 2, center.y - size / 2, size, size, 12)
  ctx.fillStyle = surface
  ctx.fill()
  ctx.strokeStyle = secondary
  ctx.stroke()
  ctx.fillStyle = primary
  ctx.fillRect(center.x - 4, center.y - 4, 8, 8)
  ctx.restore()
}

function drawScene(timestamp, forceStatic = false) {
  if (!context || !canvasWidth || !canvasHeight) return

  const reduced = motionQuery?.matches || forceStatic
  if (!reduced && timestamp - lastFrameAt < 1000 / 24) {
    frameId = window.requestAnimationFrame(drawScene)
    return
  }
  lastFrameAt = timestamp

  const primary = resolveColor('--primary', '#1f4b7a')
  const secondary = resolveColor('--secondary', '#0f766e')
  const surface = resolveColor('--surface-container-lowest', '#ffffff')
  const background = resolveColor('--background', '#f6f8f5')
  const nodes = canvasWidth < 680 ? mobileNodes : desktopNodes
  const center = { x: canvasWidth * 0.54, y: canvasHeight * 0.5 }
  const progress = reduced ? 0 : (timestamp % 6400) / 6400
  const isRegister = props.mode === 'register'
  const packetOffset = isRegister ? 0.2 : 0

  context.clearRect(0, 0, canvasWidth, canvasHeight)
  context.fillStyle = background
  context.globalAlpha = 0.18
  context.fillRect(0, 0, canvasWidth, canvasHeight)
  context.globalAlpha = 1

  nodes.forEach((node, index) => {
    const point = pointFor(node)
    const source = isRegister ? center : point
    const target = isRegister ? point : center
    drawConnection(context, source, target, progress + index * 0.14, node.tone === 'primary' ? primary : secondary, reduced)
    drawPacket(context, source, target, progress + packetOffset + index * 0.14, node.tone === 'primary' ? primary : secondary, reduced)
  })
  drawCoordinator(context, center, primary, secondary, surface, reduced, timestamp)
  nodes.forEach((node, index) => drawNode(context, pointFor(node), node, node.tone === 'primary' ? primary : secondary, surface, reduced, timestamp, index))

  if (!reduced) frameId = window.requestAnimationFrame(drawScene)
}

function handleMotionPreference() {
  if (frameId) window.cancelAnimationFrame(frameId)
  frameId = 0
  lastFrameAt = 0
  drawScene(0, motionQuery?.matches)
}

onMounted(() => {
  motionQuery = window.matchMedia('(prefers-reduced-motion: reduce)')
  motionQuery.addEventListener('change', handleMotionPreference)
  resizeObserver = new ResizeObserver(resizeCanvas)
  if (canvasRef.value) resizeObserver.observe(canvasRef.value)
  resizeCanvas()
  if (!motionQuery.matches) frameId = window.requestAnimationFrame(drawScene)
})

onBeforeUnmount(() => {
  if (frameId) window.cancelAnimationFrame(frameId)
  resizeObserver?.disconnect()
  motionQuery?.removeEventListener('change', handleMotionPreference)
})
</script>

<style scoped>
.login-knowledge-scene {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
}
</style>
