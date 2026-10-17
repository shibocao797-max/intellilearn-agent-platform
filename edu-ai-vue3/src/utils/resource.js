import { sanitizeDisplayText } from '@/utils/format'

export function normalizeResourceFilterType(type) {
  const normalized = String(type || '').trim().toLowerCase()
  if (normalized === 'document' || normalized === 'doc') {
    return 'article'
  }
  return normalized
}

export function parseResourceRef(value) {
  const raw = value === undefined || value === null ? '' : String(value).trim()
  if (!raw) {
    return {
      raw: '',
      prefix: '',
      payload: '',
      ids: [],
      primaryId: '',
      numericId: null,
      resourceType: '',
      kind: 'resource'
    }
  }

  const separatorIndex = raw.indexOf(':')
  if (separatorIndex < 0) {
    const numericId = toNumericId(raw)
    return {
      raw,
      prefix: '',
      payload: raw,
      ids: [raw],
      primaryId: raw,
      numericId,
      resourceType: '',
      kind: 'resource'
    }
  }

  const prefix = raw.slice(0, separatorIndex).trim().toLowerCase()
  const payload = raw.slice(separatorIndex + 1)
  const ids = payload.split(',').map(item => item.trim()).filter(Boolean)
  const primaryId = ids[0] || ''
  const normalizedType = normalizeResourceType(prefix)

  return {
    raw,
    prefix,
    payload,
    ids,
    primaryId,
    numericId: toNumericId(primaryId),
    resourceType: normalizedType,
    kind: normalizedType === 'quiz' ? 'quiz' : 'resource'
  }
}

export function getResourceRef(resource) {
  const persistedId = resource?.persistedId
  if (persistedId !== undefined && persistedId !== null && String(persistedId).trim()) {
    return String(persistedId).trim()
  }

  const id = resource?.id
  if (id !== undefined && id !== null && String(id).trim()) {
    return String(id).trim()
  }

  return ''
}

export function resolveResourceImage(resource) {
  if (!resource) return ''

  const type = normalizeResourceType(resource.type || resource.resourceType)
  const sourceUrl = String(resource.sourceUrl || resource.coverUrl || '').trim()
  const content = String(resource.content || '').trim()
  const isImageResource = type === 'mindmap' || type === 'image'

  if (isImageResource && sourceUrl) {
    return sourceUrl
  }

  if (isImageResource && content.startsWith('data:image/')) {
    return content
  }

  return ''
}

export function resolveResourceVideo(resource) {
  if (!resource) return ''

  const type = normalizeResourceType(resource.type || resource.resourceType)
  const sourceUrl = String(resource.sourceUrl || resource.videoUrl || '').trim()
  if (type === 'video' && sourceUrl) {
    return sourceUrl
  }
  return ''
}

export function extractResourceNumericId(resource) {
  if (!resource) return null

  const ref = getResourceRef(resource)
  const parsed = parseResourceReference(ref)
  if (parsed.numericId !== null) {
    return parsed.numericId
  }

  const rawId = resource.id
  const number = Number(rawId)
  return Number.isFinite(number) ? number : null
}

export function parseResourceReference(value) {
  return parseResourceRef(value)
}

export function normalizeResourcePayload(payload) {
  if (!payload || typeof payload !== 'object') return null

  const displayPayload = { ...payload }
  for (const key of ['title', 'displayName', 'description', 'content', 'tag']) {
    if (typeof displayPayload[key] === 'string') displayPayload[key] = sanitizeDisplayText(displayPayload[key])
  }

  const ref = getResourceRef(displayPayload)
  const parsed = parseResourceReference(ref)
  const type = normalizeResourceType(displayPayload.type || displayPayload.resourceType || parsed.resourceType)
  const numericId = displayPayload.id ?? parsed.numericId

  return {
    ...displayPayload,
    id: numericId ?? displayPayload.id ?? '',
    type,
    resourceType: type,
    title: displayPayload.title || displayPayload.displayName || '',
    displayName: displayPayload.displayName || displayPayload.title || '',
    persistedId: ref,
    sourceUrl: displayPayload.sourceUrl || displayPayload.url || '',
    coverUrl: displayPayload.coverUrl || displayPayload.sourceUrl || displayPayload.url || ''
  }
}

export function uniqueResourceKey(resource) {
  if (!resource) return ''
  return [
    getResourceRef(resource),
    resource?.revisionOf,
    resource?.videoId,
    resource?.sourceUrl,
    resource?.displayName,
    resource?.title
  ].find(value => value !== undefined && value !== null && String(value).trim()) || ''
}

export function resourceMatchesType(resource, filterType) {
  const normalizedFilter = normalizeResourceFilterType(filterType)
  if (!normalizedFilter) return true
  const resourceType = normalizeResourceFilterType(resource?.type || resource?.resourceType)
  return resourceType === normalizedFilter
}

export function buildResourceLocation(resource) {
  const normalized = normalizeResourcePayload(resource)
  if (!normalized) return null

  const ref = getResourceRef(normalized)
  const parsed = parseResourceReference(ref)
  const type = normalizeResourceType(normalized.type || parsed.resourceType)

  if (type === 'quiz' || parsed.kind === 'quiz') {
    const ids = parsed.ids.length ? parsed.ids.join(',') : ''
    return ids ? { name: 'quiz', query: { ids } } : { name: 'quiz' }
  }

  const resourceId = ref || normalized.id
  if (!resourceId) return { name: 'resources' }
  return { name: 'resources', query: { resourceId: String(resourceId) } }
}

function normalizeResourceType(type) {
  const normalized = String(type || '').trim().toLowerCase()
  if (normalized === 'doc' || normalized === 'document') {
    return 'article'
  }
  return normalized
}

function toNumericId(value) {
  const raw = String(value || '').trim()
  if (!raw) return null
  const number = Number(raw)
  return Number.isFinite(number) ? number : null
}
