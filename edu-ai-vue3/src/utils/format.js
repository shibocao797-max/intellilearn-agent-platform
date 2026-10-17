export function dataOf(result, fallback = null) {
  if (!result || typeof result !== 'object') return fallback
  return result.data ?? fallback
}

export function rowsOf(result) {
  return Array.isArray(result?.rows) ? result.rows : Array.isArray(result?.data) ? result.data : []
}

export function safeJson(value, fallback = null) {
  if (!value) return fallback
  if (typeof value === 'object') return value
  try {
    return JSON.parse(value)
  } catch {
    return fallback
  }
}

export function formatDate(value) {
  if (!value) return '暂无'

  const text = String(value).trim()
  if (!text) return '暂无'

  const parsed = new Date(text)
  if (Number.isNaN(parsed.getTime())) {
    return text.replace('T', ' ').slice(0, 19) || text
  }

  const year = parsed.getFullYear()
  const month = String(parsed.getMonth() + 1).padStart(2, '0')
  const day = String(parsed.getDate()).padStart(2, '0')
  const hours = String(parsed.getHours()).padStart(2, '0')
  const minutes = String(parsed.getMinutes()).padStart(2, '0')
  const seconds = String(parsed.getSeconds()).padStart(2, '0')
  return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`
}

export function percent(value, fallback = 0) {
  if (value === undefined || value === null || value === '') return fallback
  if (typeof value === 'string' && value.includes('%')) return Number.parseFloat(value) || fallback
  const number = Number(value)
  if (Number.isNaN(number)) return fallback
  return Math.max(0, Math.min(100, number))
}

export function resourceTypeLabel(type) {
  const map = {
    document: '学习文档',
    article: '文章',
    quiz: '题库',
    mindmap: '思维导图',
    code: '代码案例',
    image: '图片',
    video: '视频',
    animation: '教学动画'
  }
  return map[type] || type || '资源'
}

export function resourceIcon(type) {
  const map = {
    document: 'description',
    article: 'article',
    quiz: 'quiz',
    mindmap: 'account_tree',
    code: 'code_blocks',
    image: 'image',
    video: 'smart_display',
    animation: 'animation'
  }
  return map[type] || 'library_books'
}

export function agentLabel(name) {
  const map = {
    textReply: '文本答疑',
    docGen: '文档生成',
    quizGen: '题库生成',
    mindmapGen: '思维导图',
    codeGen: '代码案例',
    imageGen: '图片生成',
    videoGen: '视频生成',
    animationGen: '教学动画',
    readingExpand: '阅读拓展',
    profileUpdate: '画像更新',
    qualityReview: '质量审核',
    learningPath: '学习路径',
    resourcePlanner: '资源规划'
  }
  return sanitizeDisplayText(map[name] || name || '智能体')
}

function structuredText(value) {
  if (value === undefined || value === null) return ''
  if (Array.isArray(value)) return value.map(structuredText).filter(Boolean).join('、')
  if (typeof value === 'object') return ''
  return String(value).trim()
}

function structuredMap(value) {
  return value && typeof value === 'object' && !Array.isArray(value) ? value : null
}

function addStructuredField(lines, label, value) {
  const text = structuredText(value)
  if (text) lines.push(`${label}：${text}`)
}

/** Turns persisted learning-plan JSON into readable text for existing resources. */
export function formatStructuredResourceText(value) {
  const parsed = typeof value === 'string' ? safeJson(value, null) : value
  const data = structuredMap(parsed)
  if (!data) return ''

  const knownKeys = [
    'topic', 'goal', 'recommendStrategy', 'nextBestAction', 'totalEstimatedTime',
    'profileBasis', 'planningRationale', 'stages', 'orderedResources',
    'masteryRubric', 'packageSummary', 'reviewDependency'
  ]
  if (!knownKeys.some(key => data[key] !== undefined && data[key] !== null)) return ''

  const lines = []
  addStructuredField(lines, '主题', data.topic)
  addStructuredField(lines, '学习目标', data.goal)
  addStructuredField(lines, '推荐策略', data.recommendStrategy)
  addStructuredField(lines, '画像依据', data.profileBasis)
  addStructuredField(lines, '预计时长', data.totalEstimatedTime)
  addStructuredField(lines, '路径说明', data.planningRationale)

  const packageSummary = structuredMap(data.packageSummary)
  if (packageSummary) {
    const count = structuredText(packageSummary.resourceCount)
    const types = structuredText(packageSummary.resourceTypes)
    if (count || types) {
      lines.push(`资源包：${count ? `${count} 项` : ''}${types ? `，包含 ${types}` : ''}`)
    }
  }

  const orderedResources = Array.isArray(data.orderedResources) ? data.orderedResources : []
  if (orderedResources.length) {
    lines.push('推荐顺序：')
    orderedResources.forEach((item, index) => {
      const row = structuredMap(item) || {}
      const name = structuredText(row.displayName || row.resourceType) || `学习资源 ${index + 1}`
      const reason = structuredText(row.reason)
      lines.push(`${row.rank || index + 1}. ${name}${reason ? `：${reason}` : ''}`)
      const useWhen = structuredText(row.useWhen)
      if (useWhen) lines.push(`使用时机：${useWhen}`)
    })
  }

  const stages = Array.isArray(data.stages) ? data.stages : []
  if (stages.length) {
    lines.push('学习阶段：')
    stages.forEach((item, index) => {
      const row = structuredMap(item) || {}
      const name = structuredText(row.stageName) || `阶段 ${index + 1}`
      lines.push(`${row.order || index + 1}. ${name}`)
      addStructuredField(lines, '目标', row.objective)
      addStructuredField(lines, '练习', row.practice)
      addStructuredField(lines, '掌握标准', row.masteryCriteria)
      addStructuredField(lines, '推荐理由', row.recommendReason)
    })
  }

  if (Array.isArray(data.masteryRubric) && data.masteryRubric.length) {
    lines.push(`掌握标准：${data.masteryRubric.map(structuredText).filter(Boolean).join('；')}`)
  }
  const reviewDependency = structuredMap(data.reviewDependency)
  if (reviewDependency) {
    const reviewed = structuredText(reviewDependency.reviewedCount)
    const passed = structuredText(reviewDependency.passedCount)
    if (reviewed || passed) lines.push(`质量检查：已检查 ${reviewed || 0} 项，通过 ${passed || 0} 项`)
  }
  addStructuredField(lines, '下一步', data.nextBestAction)

  return sanitizeDisplayText(lines.filter(Boolean).join('\n'))
}

// Provider/model identifiers are implementation details and should never leak
// into student-facing copy, resource summaries, or error messages.
const PROVIDER_NAME_PATTERN = /\bAgnes(?:[-_\s]*2(?:\.0)?[-_\s]*Flash)?\b/gi

export function sanitizeDisplayText(value) {
  if (value === undefined || value === null) return ''
  return String(value).replace(PROVIDER_NAME_PATTERN, '学习智能体')
}

const EMOJI_REGEX = /[\p{Extended_Pictographic}\uFE0F]/gu

function cleanAiLine(line) {
  let text = String(line || '').trimEnd()
  if (!text) return ''

  if (/^\s*([-*_])\1{2,}\s*$/.test(text)) return ''

  text = text.replace(/^\s{0,3}>+\s?/g, '')
  text = text.replace(/^\s{0,3}#{1,6}\s*/g, '')
  text = text.replace(/^\s*[-+*]\s+/g, '· ')
  text = text.replace(/`([^`\n]+)`/g, '$1')
  text = text.replace(/(^|[\s([{（【])\*{1,2}([^*\n]+?)\*{1,2}(?=([\s)\]}）】,，。！？；：:]|$))/g, '$1$2')
  text = text.replace(/(^|[\s([{（【])_{1,2}([^_\n]+?)_{1,2}(?=([\s)\]}）】,，。！？；：:]|$))/g, '$1$2')
  text = text.replace(EMOJI_REGEX, '')
  return text.trim()
}

export function cleanAiDisplayText(value, options = {}) {
  const {
    singleLine = false,
    maxLength = 0,
    keepCodeBlocks = true
  } = options

  if (value === undefined || value === null) return ''

  const rawText = String(value)
  const trimmedRaw = rawText.trim()
  if ((trimmedRaw.startsWith('{') || trimmedRaw.startsWith('['))) {
    const parsed = safeJson(trimmedRaw, null)
    if (parsed) {
      const readable = formatStructuredResourceText(parsed)
      return readable ? cleanAiDisplayText(readable, options) : ''
    }
  }

  let text = rawText
    .replace(/\r\n?/g, '\n')
    .replace(EMOJI_REGEX, '')

  const codeBlocks = []
  text = text.replace(/```[\w-]*\n?([\s\S]*?)```/g, (_, code) => {
    const token = `__CODE_BLOCK_${codeBlocks.length}__`
    codeBlocks.push(String(code || '').trimEnd())
    return `\n${token}\n`
  })

  text = text
    .split('\n')
    .map(cleanAiLine)
    .join('\n')
    .replace(/\n{3,}/g, '\n\n')
    .trim()

  text = text.replace(/__CODE_BLOCK_(\d+)__/g, (_, index) => {
    if (!keepCodeBlocks) return ''
    return codeBlocks[Number(index)] || ''
  })

  if (singleLine) {
    text = text.replace(/\s*\n+\s*/g, ' ').replace(/\s{2,}/g, ' ').trim()
  }

  if (maxLength > 0 && text.length > maxLength) {
    text = `${text.slice(0, maxLength).trimEnd()}…`
  }

  return sanitizeDisplayText(text)
}
