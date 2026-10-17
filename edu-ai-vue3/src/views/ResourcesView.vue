<template>
  <section class="page resources-page">
    <header class="resource-header">
      <div>
        <span class="resource-eyebrow">学习空间</span>
        <h2>学习资源</h2>
        <p>集中查看并继续使用智能体为你生成的学习资料。</p>
      </div>
      <div class="resource-toolbar">
        <label class="resource-search">
          <span class="material-symbols-outlined">search</span>
          <input ref="resourceSearchInput" v-model.trim="searchKeyword" type="search" placeholder="搜索资源名称或内容" />
        </label>
        <label class="sort-select">
          <span class="material-symbols-outlined">sort</span>
          <select v-model="sortBy">
            <option value="newest">最新生成</option>
            <option value="oldest">最早生成</option>
            <option value="type">按资源类型</option>
          </select>
        </label>
        <button class="refresh-button" type="button" :disabled="loading" title="刷新资源" @click="loadResources">
          <span class="material-symbols-outlined" :class="{ spinning: loading }">refresh</span>
        </button>
      </div>
    </header>

    <div class="resource-workspace">
      <aside class="resource-filter-panel" aria-label="资源筛选">
        <section class="filter-section">
          <div class="filter-title-row">
            <h3>资源类型</h3>
            <button v-if="selectedTypes.length" class="clear-filter" type="button" @click="selectedTypes = []">清除</button>
          </div>
          <label v-for="type in resourceTypes" :key="type.value" class="filter-check">
            <input v-model="selectedTypes" type="checkbox" :value="type.value" />
            <span class="check-mark"></span>
            <span class="material-symbols-outlined type-symbol">{{ type.icon }}</span>
            <span>{{ type.label }}</span>
            <small>{{ typeCounts[type.value] || 0 }}</small>
          </label>
        </section>

        <section class="filter-section">
          <h3>资源状态</h3>
          <label v-for="status in resourceStatuses" :key="status.value" class="filter-check">
            <input v-model="selectedStatuses" type="checkbox" :value="status.value" />
            <span class="check-mark"></span>
            <span class="status-dot" :class="status.value"></span>
            <span>{{ status.label }}</span>
            <small>{{ statusCounts[status.value] || 0 }}</small>
          </label>
        </section>

        <section class="filter-section">
          <h3>生成时间</h3>
          <label class="time-select">
            <span class="material-symbols-outlined">calendar_month</span>
            <select v-model="timeRange">
              <option value="all">所有时间</option>
              <option value="today">今天</option>
              <option value="week">近 7 天</option>
              <option value="month">近 30 天</option>
            </select>
          </label>
        </section>
      </aside>

      <div class="resource-results">
        <div class="result-summary">
          <p>显示 <strong>{{ displayedResources.length }}</strong> 条，共 <strong>{{ filteredResources.length }}</strong> 条资源</p>
          <button v-if="hasActiveFilters" class="text-action" type="button" @click="resetFilters">
            <span class="material-symbols-outlined">restart_alt</span>
            重置筛选
          </button>
        </div>

        <div v-if="loading" class="resource-grid">
      <SkeletonBlock v-for="n in 6" :key="n" height="320px" />
        </div>

        <template v-else-if="loadError">
          <div class="resource-empty-state">
            <UiState
              icon="wifi_off"
              title="资源列表加载失败"
              :description="loadError"
              action-label="重新加载"
              action-icon="refresh"
              @action="loadResources"
            />
          </div>
        </template>

        <template v-else-if="!resourceSource.length">
          <div class="resource-empty-state">
            <UiState
              icon="inventory_2"
              title="暂无学习资源"
              description="开始新的 AI 对话后，系统会在这里沉淀讲义、题库、导图、动画和视频资源。"
              action-label="前往 AI 对话"
              action-icon="chat"
              @action="$router.push('/chat')"
            />
          </div>
        </template>

        <UiState
          v-else-if="!filteredResources.length"
          icon="search_off"
          title="没有匹配的资源"
          description="可以清除筛选条件，或换一个关键词继续查找。"
          action-label="清除筛选"
          action-icon="restart_alt"
          @action="resetFilters"
        />

        <div v-else ref="resourceGrid" class="resource-grid">
      <article
        v-for="resource in displayedResources"
        :key="resource.id"
        class="resource-card"
        :class="{ processing: isProcessing(resource) }"
      >
        <div
          class="resource-card-main"
          role="button"
          tabindex="0"
          :aria-label="`打开资源：${resource.title || resource.displayName || '未命名资源'}`"
          @click="openDetail(resource)"
          @keydown.enter.prevent="openDetail(resource)"
          @keydown.space.prevent="openDetail(resource)"
        >
          <div class="resource-cover" :class="`cover-${resource.type}`">
          <div class="cover-marker-row">
            <span class="cover-type-marker">
              <span class="material-symbols-outlined filled" aria-hidden="true">{{ resourceIcon(resource.type) }}</span>
              {{ resourceTypeLabel(resource.type) }}
            </span>
            <span v-if="resourceStateLabel(resource)" class="resource-state" :class="resourceStatus(resource)">{{ resourceStateLabel(resource) }}</span>
          </div>

          <div class="resource-cover-content">
            <video v-if="resource.type === 'video' && cardVideoUrl(resource)" class="resource-card-video" :src="cardVideoUrl(resource)" muted playsinline preload="metadata" aria-hidden="true"></video>
            <img v-else-if="cardImageUrl(resource)" class="resource-card-image" :src="cardImageUrl(resource)" alt="" loading="lazy" />
            <iframe
              v-else-if="resource.type === 'animation' && cardAnimationHtml(resource)"
              class="resource-card-animation"
              :srcdoc="cardAnimationHtml(resource)"
              sandbox="allow-scripts"
              referrerpolicy="no-referrer"
              loading="lazy"
              tabindex="-1"
              aria-hidden="true"
              title="教学动画预览"
            ></iframe>
            <img v-else-if="resource.type === 'animation' && cardAnimationPosterUrl(resource)" class="resource-card-image" :src="cardAnimationPosterUrl(resource)" alt="" loading="lazy" />
            <pre v-else-if="resource.type === 'code'" class="code-preview">{{ codePreview(resource) }}</pre>
            <div v-else-if="resource.type === 'quiz'" class="quiz-preview">
              <strong>{{ quizTypeLabel(resource) }}</strong>
              <p>{{ resourceExcerpt(resource) }}</p>
            </div>
            <div v-else-if="resource.type === 'article' || resource.type === 'document'" class="text-preview">
              <span>正文摘要</span>
              <p>{{ resourceExcerpt(resource) }}</p>
            </div>
            <div v-else class="preview-unavailable">
              <span class="material-symbols-outlined" aria-hidden="true">{{ unavailablePreviewIcon(resource.type) }}</span>
              <strong>{{ unavailablePreviewTitle(resource.type) }}</strong>
              <small>{{ unavailablePreviewMessage(resource) }}</small>
            </div>
          </div>

          <div v-if="isProcessing(resource)" class="progress-line">
            <span :style="{ width: resource.mediaStatus === 'processing' ? '82%' : '36%' }"></span>
          </div>
        </div>

          <div class="resource-body">
            <h3>{{ resource.title || resource.displayName || '未命名资源' }}</h3>

            <p>{{ resourceSummary(resource) }}</p>

            <footer>
              <span>{{ resourceTypeLabel(resource.type) }}</span>
              <span>{{ formatDate(resource.createTime) }}</span>
            </footer>
          </div>
        </div>
        <button
          class="resource-delete-button"
          type="button"
          :disabled="deletingResourceId === resource.id"
          :aria-label="`删除资源：${resource.title || resource.displayName || '未命名资源'}`"
          :title="deletingResourceId === resource.id ? '正在删除' : '删除资源'"
          @click="requestDelete(resource, $event.currentTarget)"
        >
          <span class="material-symbols-outlined" :class="{ spinning: deletingResourceId === resource.id }">{{ deletingResourceId === resource.id ? 'progress_activity' : 'delete_outline' }}</span>
        </button>
      </article>
        </div>

        <div v-if="filteredResources.length > filters.pageSize" class="pager">
          <button class="page-button" type="button" :disabled="filters.pageNum <= 1" aria-label="上一页" @click="turnPage(-1)">
            <span class="material-symbols-outlined">chevron_left</span>
          </button>
          <button
            v-for="page in visiblePages"
            :key="page"
            class="page-button"
            :class="{ active: page === filters.pageNum }"
            type="button"
            @click="filters.pageNum = page"
          >
            {{ page }}
          </button>
          <button class="page-button" type="button" :disabled="filters.pageNum >= pageCount" aria-label="下一页" @click="turnPage(1)">
            <span class="material-symbols-outlined">chevron_right</span>
          </button>
        </div>
      </div>
    </div>

    <aside v-if="selected" ref="detailDialog" class="detail-panel" role="dialog" aria-modal="true" aria-labelledby="resource-detail-title" tabindex="-1" @keydown="handleDetailDialogKeydown">
      <div class="detail-backdrop" @click="closeDetail"></div>
      <section class="detail-card">
        <header>
          <div>
            <div class="detail-pill-row">
              <span class="status-pill primary">{{ resourceTypeLabel(selected.type) }}</span>
              <span v-if="resourceStateLabel(selected)" class="status-pill resource-detail-status" :class="resourceStatus(selected)">
                {{ resourceStateLabel(selected) }}
              </span>
            </div>
            <h3 id="resource-detail-title">{{ selected.title || '资源详情' }}</h3>
          </div>
          <button ref="detailCloseButton" class="icon-button" type="button" aria-label="关闭资源详情" title="关闭资源详情" @click="closeDetail">
            <span class="material-symbols-outlined" aria-hidden="true">close</span>
          </button>
        </header>

        <div v-if="detailLoading" class="detail-body">
          <SkeletonBlock height="360px" />
        </div>

        <UiState
          v-else-if="detailError"
          icon="error_outline"
          title="资源详情加载失败"
          :description="detailError"
          action-label="重新加载"
          action-icon="refresh"
          @action="retryDetail"
        />

        <div v-else class="detail-body">
          <iframe v-if="animationUrl" :src="animationUrl" class="animation-frame" sandbox="allow-scripts" referrerpolicy="no-referrer" title="教学动画"></iframe>
          <img v-else-if="previewImageUrl" :src="previewImageUrl" class="detail-image" alt="" />
          <video v-else-if="previewVideoUrl" class="detail-video" :src="previewVideoUrl" controls playsinline></video>

          <div v-else-if="selected.sourceUrl" class="external-resource">
            <span class="material-symbols-outlined">{{ resourceIcon(selected.type) }}</span>
            <p>该资源包含外部链接，可以在新窗口中打开。</p>
            <a class="primary-button" :href="selected.sourceUrl" target="_blank" rel="noopener noreferrer">打开资源</a>
          </div>

          <pre v-else class="resource-content">{{ detailDisplayText(selected) || '暂无正文内容' }}</pre>

          <div v-if="selected.videoId" class="video-status">
            <button class="ghost-button" type="button" @click="refreshVideoStatus">查询视频状态</button>
            <span>{{ mediaStatus || readableVideoStatus(selected.mediaStatus) }}</span>
          </div>
        </div>
      </section>
    </aside>

    <div v-if="deleteCandidate" class="resource-delete-scrim" role="presentation" @click.self="cancelDelete">
      <section ref="deleteDialog" class="resource-delete-dialog" role="dialog" aria-modal="true" aria-labelledby="resource-delete-title" aria-describedby="resource-delete-description" tabindex="-1" @keydown="handleDeleteDialogKeydown">
        <span class="material-symbols-outlined resource-delete-dialog-icon" aria-hidden="true">delete_outline</span>
        <h3 id="resource-delete-title">删除这条资源？</h3>
        <p id="resource-delete-description">“{{ deleteCandidate.title || deleteCandidate.displayName || '未命名资源' }}”将从你的学习资源中移除。</p>
        <p v-if="deleteError" class="resource-delete-error" role="alert">{{ deleteError }}</p>
        <div class="resource-delete-actions">
          <button class="ghost-button" type="button" :disabled="Boolean(deletingResourceId)" @click="cancelDelete">取消</button>
          <button ref="deleteConfirmButton" class="danger-button" type="button" :disabled="Boolean(deletingResourceId)" @click="confirmDelete">
            {{ deletingResourceId ? '正在删除' : '删除资源' }}
          </button>
        </div>
      </section>
    </div>
  </section>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { authApi, eduApi } from '@/services/api'
import { getToken } from '@/services/storage'
import { cleanAiDisplayText, formatDate, resourceIcon, resourceTypeLabel, rowsOf } from '@/utils/format'
import {
  extractResourceNumericId,
  normalizeResourcePayload,
  parseResourceReference,
  resolveResourceImage,
  resolveResourceVideo
} from '@/utils/resource'
import SkeletonBlock from '@/components/SkeletonBlock.vue'
import UiState from '@/components/UiState.vue'

const VIDEO_POLL_INTERVAL_MS = 4000
const VIDEO_POLL_MAX_ATTEMPTS = 30
const RESOURCE_FILTER_STORAGE_PREFIX = 'eduai-resource-filters-v2'
const MAX_FILTER_KEY_LENGTH = 128
const RESOURCE_STATUS_VALUES = ['published', 'reviewing', 'replaced', 'processing']
const RESOURCE_STATUS_ALIASES = {
  '0': 'published',
  active: 'published',
  published: 'published',
  ready: 'published',
  '1': 'reviewing',
  reviewing: 'reviewing',
  '2': 'replaced',
  replaced: 'replaced',
  '3': 'rejected',
  rejected: 'rejected',
  processing: 'processing',
  pending: 'processing',
  submitted: 'processing',
  failed: 'failed',
  error: 'failed',
  cancelled: 'failed'
}

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const detailLoading = ref(false)
const loadError = ref('')
const detailError = ref('')
const resourceSource = ref([])
const selected = ref(null)
const animationUrl = ref('')
const mediaStatus = ref('')
const detailDialog = ref(null)
const detailCloseButton = ref(null)
const deleteCandidate = ref(null)
const deletingResourceId = ref(null)
const deleteError = ref('')
const deleteConfirmButton = ref(null)
const deleteDialog = ref(null)
const resourceGrid = ref(null)
const resourceSearchInput = ref(null)
const prefersReducedMotion = ref(false)
let reducedMotionMediaQuery = null
let lastFocusedElement = null
let lastDeleteTrigger = null
const searchKeyword = ref('')
const selectedTypes = ref([])
const selectedStatuses = ref([...RESOURCE_STATUS_VALUES])
const timeRange = ref('all')
const sortBy = ref('newest')
const filtersReady = ref(false)
let resourceFilterStorageKey = ''
let resourceFilterToken = ''
const filters = reactive({
  pageNum: 1,
  pageSize: 12
})

const resourceTypes = [
  { value: 'article', label: '学习文档', icon: 'article' },
  { value: 'quiz', label: '练习题', icon: 'quiz' },
  { value: 'mindmap', label: '思维导图', icon: 'account_tree' },
  { value: 'code', label: '代码案例', icon: 'code_blocks' },
  { value: 'image', label: '教学配图', icon: 'image' },
  { value: 'video', label: '教学视频', icon: 'smart_display' },
  { value: 'animation', label: '教学动画', icon: 'animation' }
]

const resourceStatuses = [
  { value: 'published', label: '已发布' },
  { value: 'reviewing', label: '审核中' },
  { value: 'processing', label: '处理中' }
]

function normalizeFilterUserId(value) {
  const userId = String(value || '').trim()
  return userId && userId.length <= MAX_FILTER_KEY_LENGTH ? userId : ''
}

function resourceFilterKey(userId) {
  return `${RESOURCE_FILTER_STORAGE_PREFIX}:${encodeURIComponent(userId)}`
}

function normalizeStoredFilters(value) {
  if (!value || typeof value !== 'object') return null

  const allowedTypes = new Set(resourceTypes.map(item => item.value))
  const allowedStatuses = new Set(RESOURCE_STATUS_VALUES)
  const storedTypes = Array.isArray(value.selectedTypes)
    ? [...new Set(value.selectedTypes.map(item => String(item)).filter(item => allowedTypes.has(item)))]
    : []
  const normalizeStoredStatus = item => {
    const normalized = String(item || '').trim().toLowerCase()
    return RESOURCE_STATUS_ALIASES[normalized] || normalized
  }
  const storedStatuses = Array.isArray(value.selectedStatuses)
    ? [...new Set(value.selectedStatuses.map(normalizeStoredStatus).filter(item => allowedStatuses.has(item)))]
    : [...RESOURCE_STATUS_VALUES]
  const nextTimeRange = ['all', 'today', 'week', 'month'].includes(value.timeRange)
    ? value.timeRange
    : 'all'
  const nextSortBy = ['newest', 'oldest', 'type'].includes(value.sortBy)
    ? value.sortBy
    : 'newest'

  return {
    searchKeyword: typeof value.searchKeyword === 'string' ? value.searchKeyword.trim().slice(0, 200) : '',
    selectedTypes: storedTypes,
    selectedStatuses: storedStatuses,
    timeRange: nextTimeRange,
    sortBy: nextSortBy
  }
}

function readStoredFilters(key) {
  try {
    const raw = localStorage.getItem(key)
    return raw ? normalizeStoredFilters(JSON.parse(raw)) : null
  } catch {
    return null
  }
}

function writeStoredFilters() {
  if (!filtersReady.value || !resourceFilterStorageKey || getToken() !== resourceFilterToken) return

  const payload = {
    version: 1,
    searchKeyword: searchKeyword.value.slice(0, 200),
    selectedTypes: [...selectedTypes.value],
    selectedStatuses: [...selectedStatuses.value],
    timeRange: timeRange.value,
    sortBy: sortBy.value
  }

  try {
    localStorage.setItem(resourceFilterStorageKey, JSON.stringify(payload))
  } catch {
    // Private browsing or a full storage quota should not block resource browsing.
  }
}

async function resolveResourceFilterUserId() {
  const tokenAtStart = getToken()
  if (!tokenAtStart) return ''

  const info = await authApi.getInfo().catch(() => null)
  if (tokenAtStart !== getToken()) return ''
  return normalizeFilterUserId(info?.user?.userId)
}

async function restoreStoredFilters() {
  const tokenAtStart = getToken()
  const userId = await resolveResourceFilterUserId()
  if (userId && tokenAtStart && tokenAtStart === getToken()) {
    resourceFilterStorageKey = resourceFilterKey(userId)
    resourceFilterToken = tokenAtStart
    const stored = readStoredFilters(resourceFilterStorageKey)
    if (stored) {
      searchKeyword.value = stored.searchKeyword
      selectedTypes.value = stored.selectedTypes
      selectedStatuses.value = stored.selectedStatuses
      timeRange.value = stored.timeRange
      sortBy.value = stored.sortBy
    }
  }
  filtersReady.value = true
}

let videoDetailPollTimer = null
let videoDetailPollAttempts = 0

const typeCounts = computed(() => resourceTypes.reduce((counts, type) => {
  counts[type.value] = resourceSource.value.filter(resource => resourceMatchesFilterType(resource, type.value)).length
  return counts
}, {}))
const statusCounts = computed(() => resourceStatuses.reduce((counts, status) => {
  counts[status.value] = resourceSource.value.filter(resource => resourceStatus(resource) === status.value).length
  return counts
}, {}))
const filteredResources = computed(() => {
  const keyword = searchKeyword.value.trim().toLowerCase()
  const minTime = timeRangeStart(timeRange.value)

  return resourceSource.value
    .filter(resource => {
      if (selectedTypes.value.length && !selectedTypes.value.some(type => resourceMatchesFilterType(resource, type))) return false
      if (selectedStatuses.value.length && !selectedStatuses.value.includes(resourceStatus(resource))) return false
      if (minTime && resourceTimestamp(resource) < minTime) return false
      if (!keyword) return true
      const searchable = [resource.title, resource.displayName, resource.description, resource.content, resourceTypeLabel(resource.type)]
        .filter(Boolean)
        .join(' ')
        .toLowerCase()
      return searchable.includes(keyword)
    })
    .sort(compareResources)
})
const pageCount = computed(() => Math.max(1, Math.ceil(filteredResources.value.length / filters.pageSize)))
const displayedResources = computed(() => {
  const start = (filters.pageNum - 1) * filters.pageSize
  return filteredResources.value.slice(start, start + filters.pageSize)
})
const visiblePages = computed(() => {
  const count = pageCount.value
  const current = filters.pageNum
  const start = Math.max(1, Math.min(current - 2, count - 4))
  const end = Math.min(count, start + 4)
  return Array.from({ length: end - start + 1 }, (_, index) => start + index)
})
const hasActiveFilters = computed(() => Boolean(
  searchKeyword.value
  || selectedTypes.value.length
  || selectedStatuses.value.length !== RESOURCE_STATUS_VALUES.length
  || timeRange.value !== 'all'
  || sortBy.value !== 'newest'
))
const previewImageUrl = computed(() => resolveResourceImage(selected.value))
const previewVideoUrl = computed(() => resolveResourceVideo(selected.value))

function isProcessing(resource) {
  return ['processing', 'pending', 'reviewing', 'submitted'].includes(String(resource.mediaStatus || resource.statusText || '').toLowerCase())
}

function resourceStatus(resource) {
  const backendStatus = String(resource?.status ?? '').trim().toLowerCase()
  const mediaStatus = String(resource?.mediaStatus || resource?.statusText || '').trim().toLowerCase()
  const reviewStatus = backendStatus && RESOURCE_STATUS_ALIASES[backendStatus]
  if (resourceGenerationFailed(resource)) return 'failed'
  // A review result does not change whether the resource was generated. Keep
  // historical status 2/3 resources in the normal published collection and
  // avoid exposing review-only labels in the resource browser.
  if (reviewStatus === 'replaced' || reviewStatus === 'rejected') return 'published'
  if (['failed', 'error', 'cancelled'].includes(mediaStatus)) return 'failed'
  if (['processing', 'pending', 'submitted'].includes(mediaStatus)) return 'processing'
  if (reviewStatus) return reviewStatus
  return 'published'
}

function resourceGenerationFailed(resource) {
  const mediaStatus = String(resource?.mediaStatus || resource?.statusText || '').trim().toLowerCase()
  const persistedStatus = String(resource?.status || '').trim().toLowerCase()
  return resource?.success === false
    || String(resource?.success || '').trim().toLowerCase() === 'false'
    || String(resource?.generationStatus || '').trim().toLowerCase() === 'failed'
    || ['failed', 'error', 'cancelled'].includes(mediaStatus)
    || ['failed', 'error', 'cancelled'].includes(persistedStatus)
}

function resourceMatchesFilterType(resource, filterType) {
  const type = String(resource?.type || resource?.resourceType || '').toLowerCase()
  if (filterType === 'article') return type === 'article' || type === 'document'
  return type === filterType
}

function resourceTimestamp(resource) {
  const timestamp = new Date(resource?.createTime || resource?.updateTime || 0).getTime()
  return Number.isFinite(timestamp) ? timestamp : 0
}

function timeRangeStart(range) {
  if (range === 'all') return 0
  const now = new Date()
  if (range === 'today') return new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()
  if (range === 'week') return now.getTime() - 7 * 24 * 60 * 60 * 1000
  if (range === 'month') return now.getTime() - 30 * 24 * 60 * 60 * 1000
  return 0
}

function compareResources(left, right) {
  if (sortBy.value === 'type') {
    const typeOrder = resourceTypes.map(item => item.value)
    const typeDiff = typeOrder.indexOf(left.type) - typeOrder.indexOf(right.type)
    if (typeDiff) return typeDiff
  }
  const difference = resourceTimestamp(right) - resourceTimestamp(left)
  return sortBy.value === 'oldest' ? -difference : difference
}

function resetFilters() {
  searchKeyword.value = ''
  selectedTypes.value = []
  selectedStatuses.value = [...RESOURCE_STATUS_VALUES]
  timeRange.value = 'all'
  sortBy.value = 'newest'
  filters.pageNum = 1
}

function readableVideoStatus(status) {
  const normalized = String(status || '').toLowerCase()
  if (normalized === 'completed') return '已完成'
  if (normalized === 'failed') return '暂不可用'
  if (normalized === 'processing') return '生成中'
  if (normalized === 'pending') return '排队中'
  if (normalized === 'submitted') return '已提交'
  return normalized || '等待生成'
}

function cardImageUrl(resource) {
  return resolveResourceImage(resource)
}

function cardVideoUrl(resource) {
  return resolveResourceVideo(resource)
}

function cardAnimationPosterUrl(resource) {
  const candidate = String(
    resource.thumbnailUrl
    || resource.posterUrl
    || resource.previewImageUrl
    || resource.coverImageUrl
    || resource.coverUrl
    || ''
  ).trim()
  return /^(data:image\/|https?:\/\/.*\.(?:avif|gif|jpe?g|png|webp)(?:[?#].*)?$)/i.test(candidate) ? candidate : ''
}

function cardAnimationHtml(resource) {
  if (resource?.type !== 'animation' || prefersReducedMotion.value) return ''
  const content = String(resource.content || '').trim()
  return /^(?:<!doctype\s+html|<html[\s>])/i.test(content) ? content : ''
}

function hasAnimationHtml(resource) {
  if (resource?.type !== 'animation') return false
  return /^(?:<!doctype\s+html|<html[\s>])/i.test(String(resource.content || '').trim())
}

function resourceExcerpt(resource) {
  if (resource?.type === 'animation') {
    if (hasAnimationHtml(resource) && prefersReducedMotion.value) {
      return '动画内容已就绪，点击卡片进入播放。'
    }
    return cardAnimationHtml(resource)
      ? '自包含 HTML 动画，点击卡片即可播放。'
      : '动画内容暂不可用，可进入详情查看资源状态。'
  }
  const excerpt = cleanAiDisplayText(resource.description || resource.content || '', {
    singleLine: true,
    maxLength: 128,
    keepCodeBlocks: false
  })
  return excerpt || '暂无可用摘要'
}

function quizTypeLabel(resource) {
  const value = resource.questionType || resource.quizType || resource.questionKind || resource.metadata?.questionType
  const labels = {
    single_choice: '单选题',
    multi_choice: '多选题',
    fill_blank: '填空题',
    short_answer: '简答题',
    code: '代码题'
  }
  const normalized = String(value || '').trim().toLowerCase()
  return labels[normalized] || (value ? String(value) : '打开查看练习题')
}

function unavailablePreviewIcon(type) {
  return ({ video: 'videocam_off', animation: 'animation', mindmap: 'account_tree', image: 'image_not_supported' })[type] || 'visibility_off'
}

function unavailablePreviewTitle(type) {
  return ({ video: '视频预览暂不可用', animation: '动画预览暂不可用', mindmap: '导图预览暂不可用', image: '图片预览暂不可用' })[type] || '资源预览暂不可用'
}

function unavailablePreviewMessage(resource) {
  if (isProcessing(resource)) return '资源仍在生成，完成后会在这里显示预览。'
  if (resource?.type === 'animation' && hasAnimationHtml(resource) && prefersReducedMotion.value) {
    return '已根据系统动效偏好关闭自动播放，点击卡片进入动画。'
  }
  const status = resourceStatus(resource)
  if (status === 'reviewing') return '资源正在审核，审核通过后即可使用。'
  if (status === 'failed') return '该资源当前不可用，已从资源列表隐藏。'
  return '当前资源没有可用于卡片预览的内容或地址。'
}

function resourceSummary(resource) {
  if (resource?.type === 'animation') {
    if (hasAnimationHtml(resource) && prefersReducedMotion.value) {
      return '动画内容已就绪，点击卡片播放完整动画。'
    }
    return cardAnimationHtml(resource) || cardAnimationPosterUrl(resource)
      ? '动态预览已就绪，可点击播放完整动画。'
      : '动画内容暂不可用，可进入详情查看资源状态。'
  }
  const raw = cleanAiDisplayText(resource.description || resource.content || '', {
    singleLine: true,
    maxLength: 72,
    keepCodeBlocks: false
  })
  if (raw) return raw

  const fallback = {
    mindmap: cardImageUrl(resource) ? '导图预览已就绪，可点击查看完整内容。' : '导图预览地址暂不可用，可进入详情查看资源状态。',
    code: '代码正文暂不可用，可进入详情查看资源状态。',
    quiz: '练习题摘要暂不可用，可进入详情查看。',
    video: isProcessing(resource) ? '视频仍在生成，完成后会显示预览。' : (cardVideoUrl(resource) ? '视频预览已就绪，可点击查看。' : '视频预览地址暂不可用，可在详情中查询状态。'),
    animation: cardAnimationHtml(resource) || cardAnimationPosterUrl(resource)
      ? '动态预览已就绪，可点击播放完整动画。'
      : '动画内容暂不可用，可进入详情查看资源状态。',
    article: '文档摘要暂不可用，可进入详情查看完整内容。',
    document: '文档摘要暂不可用，可进入详情查看完整内容。',
    image: cardImageUrl(resource) ? '图片预览已就绪，可点击放大查看。' : '图片预览地址暂不可用，可进入详情查看资源状态。'
  }

  return fallback[resource.type] || '该资源可点击进入查看详情。'
}

function resourceStateLabel(resource) {
  const status = resourceStatus(resource)
  if (status === 'replaced') return ''
  const label = resourceStatuses.find(item => item.value === status)?.label
  return label || '已发布'
}

function codePreview(resource) {
  const content = String(resource.content || resource.description || '').trim()
  if (!content) {
    return '代码正文未生成'
  }

  const cleaned = content
    .replace(/```[a-zA-Z]*/g, '')
    .replace(/```/g, '')
    .split('\n')
    .map(line => line.trimEnd())
    .filter(Boolean)
    .slice(0, 4)
    .join('\n')

  return cleaned || '代码正文未生成'
}

function detailDisplayText(resource) {
  if (!resource) return ''
  if (resource.type === 'code') {
    return String(resource.content || resource.description || '').trim()
  }
  return cleanAiDisplayText(resource.content || resource.description || '', { keepCodeBlocks: false })
}

function normalizeRows(rows) {
  return rows
    .map(item => normalizeResourcePayload(item))
    .filter(Boolean)
    .filter(resource => !isHiddenResource(resource))
}

function isHiddenResource(resource) {
  return resourceGenerationFailed(resource)
}

async function loadResources() {
  loading.value = true
  loadError.value = ''
  try {
    const pageSize = 100
    const firstPage = await eduApi.resources({ pageNum: 1, pageSize })
    const pageTotal = Math.max(1, Math.ceil(Number(firstPage.total || rowsOf(firstPage).length) / pageSize))
    const remainingPages = await Promise.all(
      Array.from({ length: Math.max(0, pageTotal - 1) }, (_, index) => eduApi.resources({ pageNum: index + 2, pageSize }))
    )
    resourceSource.value = [firstPage, ...remainingPages]
      .flatMap(result => normalizeRows(rowsOf(result)))
      .filter((item, index, list) => list.findIndex(entry => String(entry.id) === String(item.id)) === index)
  } catch (error) {
    console.warn('资源列表加载失败', error)
    resourceSource.value = []
    loadError.value = '资源列表暂时无法加载，请检查网络或重新刷新。'
  } finally {
    loading.value = false
  }
}

function turnPage(step) {
  filters.pageNum = Math.min(pageCount.value, Math.max(1, filters.pageNum + step))
}

async function requestDelete(resource, trigger) {
  if (!resource?.id || deletingResourceId.value) return
  deleteError.value = ''
  lastDeleteTrigger = trigger instanceof HTMLElement ? trigger : null
  deleteCandidate.value = resource
  await nextTick()
  deleteConfirmButton.value?.focus()
}

async function cancelDelete() {
  if (deletingResourceId.value) return
  deleteError.value = ''
  deleteCandidate.value = null
  await nextTick()
  restoreDeleteTriggerFocus()
}

function handleDeleteDialogKeydown(event) {
  if (event.key === 'Escape') {
    event.preventDefault()
    cancelDelete()
    return
  }
  if (event.key !== 'Tab') return

  const focusable = [...(deleteDialog.value?.querySelectorAll('button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])') || [])]
    .filter(element => element instanceof HTMLElement && !element.hasAttribute('hidden'))
  if (!focusable.length) {
    event.preventDefault()
    deleteDialog.value?.focus()
    return
  }

  const first = focusable[0]
  const last = focusable[focusable.length - 1]
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault()
    first.focus()
  }
}

function restoreDeleteTriggerFocus() {
  if (lastDeleteTrigger && document.contains(lastDeleteTrigger)) {
    lastDeleteTrigger.focus()
  }
  lastDeleteTrigger = null
}

async function confirmDelete() {
  const resource = deleteCandidate.value
  if (!resource?.id || deletingResourceId.value) return
  const resourceIndex = displayedResources.value.findIndex(item => String(item.id) === String(resource.id))
  deletingResourceId.value = resource.id
  deleteError.value = ''
  try {
    await eduApi.deleteResource(resource.id)
    if (selected.value && String(selected.value.id) === String(resource.id)) {
      await closeDetail()
    }
    resourceSource.value = resourceSource.value.filter(item => String(item.id) !== String(resource.id))
    deleteCandidate.value = null
    await nextTick()
    const cards = resourceGrid.value?.querySelectorAll('.resource-card-main') || []
    const nextCard = cards[Math.min(Math.max(resourceIndex, 0), cards.length - 1)]
    if (nextCard) nextCard.focus()
    else resourceSearchInput.value?.focus()
    lastDeleteTrigger = null
  } catch (error) {
    deleteError.value = error?.message || '删除失败，请稍后重试。'
  } finally {
    deletingResourceId.value = null
  }
}

async function openDetail(resource, preserveTriggerFocus = false) {
  if (!resource?.id || isHiddenResource(resource)) return
  stopVideoPolling()
  if (!preserveTriggerFocus) {
    lastFocusedElement = document.activeElement instanceof HTMLElement ? document.activeElement : null
  }
  selected.value = resource
  detailLoading.value = true
  detailError.value = ''
  animationUrl.value = ''
  mediaStatus.value = ''
  await nextTick()
  detailCloseButton.value?.focus()

  try {
    const res = await eduApi.resourceDetail(resource.id)
    const normalized = normalizeResourcePayload(res.data || resource) || resource
    if (isHiddenResource(normalized)) {
      await closeDetail()
      return
    }
    selected.value = normalized
    if (selected.value.type === 'animation') {
      const html = await eduApi.animationHtml(selected.value.id)
      const blob = new Blob([html], { type: 'text/html' })
      animationUrl.value = URL.createObjectURL(blob)
    } else if (selected.value.type === 'video' && !previewVideoUrl.value && selected.value.videoId) {
      startVideoPolling()
    }
  } catch (error) {
    console.warn('资源详情加载失败', error)
    detailError.value = '暂时无法获取这条资源的完整内容，请稍后重试。'
  } finally {
    detailLoading.value = false
  }
}

async function retryDetail() {
  if (!selected.value?.id) return
  await openDetail(selected.value, true)
}

function handleDetailDialogKeydown(event) {
  if (event.key === 'Escape') {
    event.preventDefault()
    closeDetail()
    return
  }
  if (event.key !== 'Tab') return

  const focusable = [...(detailDialog.value?.querySelectorAll('button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])') || [])]
    .filter(element => element instanceof HTMLElement && !element.hasAttribute('hidden'))
  if (!focusable.length) {
    event.preventDefault()
    detailDialog.value?.focus()
    return
  }

  const first = focusable[0]
  const last = focusable[focusable.length - 1]
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault()
    first.focus()
  }
}

async function closeDetail() {
  stopVideoPolling()
  if (animationUrl.value) URL.revokeObjectURL(animationUrl.value)
  animationUrl.value = ''
  detailError.value = ''
  selected.value = null
  await nextTick()
  if (lastFocusedElement && document.contains(lastFocusedElement)) {
    lastFocusedElement.focus()
  }
  lastFocusedElement = null
}

async function refreshVideoStatus() {
  if (!selected.value?.videoId) return
  try {
    const res = await eduApi.videoStatus(selected.value.videoId, {
      resourceId: extractResourceNumericId(selected.value),
      attempts: 1,
      intervalMs: 0
    })

    const statusData = res.data || {}
    mediaStatus.value = readableVideoStatus(statusData.status || res.msg)

    if (statusData.status) {
      selected.value = { ...selected.value, mediaStatus: statusData.status }
      patchResource(selected.value.id, { mediaStatus: statusData.status })
    }

    if (statusData.videoUrl) {
      const nextCoverUrl = selected.value.coverUrl || statusData.videoUrl
      selected.value = {
        ...selected.value,
        sourceUrl: statusData.videoUrl,
        coverUrl: nextCoverUrl
      }
      patchResource(selected.value.id, {
        sourceUrl: statusData.videoUrl,
        coverUrl: nextCoverUrl
      })
      stopVideoPolling()
    }
  } catch (error) {
    mediaStatus.value = error.message || '查询失败'
  }
}

function startVideoPolling() {
  if (!selected.value?.videoId || previewVideoUrl.value) return
  stopVideoPolling()
  videoDetailPollAttempts = 0

  const poll = async () => {
    videoDetailPollAttempts += 1
    await refreshVideoStatus()
    if (!selected.value?.videoId) {
      stopVideoPolling()
      return
    }
    const status = String(selected.value.mediaStatus || '').toLowerCase()
    if (
      previewVideoUrl.value
      || ['completed', 'failed'].includes(status)
      || videoDetailPollAttempts >= VIDEO_POLL_MAX_ATTEMPTS
    ) {
      stopVideoPolling()
      return
    }
    videoDetailPollTimer = window.setTimeout(poll, VIDEO_POLL_INTERVAL_MS)
  }

  videoDetailPollTimer = window.setTimeout(poll, 600)
}

function stopVideoPolling() {
  if (videoDetailPollTimer) window.clearTimeout(videoDetailPollTimer)
  videoDetailPollTimer = null
  videoDetailPollAttempts = 0
}

function patchResource(resourceId, patch) {
  if (!resourceId) return
  const index = resourceSource.value.findIndex(item => String(item.id) === String(resourceId))
  if (index < 0) return
  const nextResource = { ...resourceSource.value[index], ...patch }
  if (isHiddenResource(nextResource)) {
    resourceSource.value.splice(index, 1)
    return
  }
  resourceSource.value[index] = nextResource
}

async function openByReference(reference) {
  const parsed = parseResourceReference(reference)
  if (parsed.kind === 'quiz' && parsed.ids.length) {
    await router.replace({ name: 'quiz', query: { ids: parsed.ids.join(',') } })
    return
  }

  const targetId = parsed.numericId ?? (parsed.ids.length ? Number(parsed.ids[0]) : null)
  if (!targetId) return
  const resource = resourceSource.value.find(item => String(item.id) === String(targetId)) || { id: targetId, type: parsed.resourceType }
  await openDetail(resource)
}

watch([searchKeyword, selectedTypes, selectedStatuses, timeRange, sortBy], () => {
  filters.pageNum = 1
  writeStoredFilters()
}, { deep: true })

watch(pageCount, count => {
  if (filters.pageNum > count) filters.pageNum = count
})

onMounted(async () => {
  reducedMotionMediaQuery = window.matchMedia?.('(prefers-reduced-motion: reduce)') || null
  if (reducedMotionMediaQuery) {
    prefersReducedMotion.value = reducedMotionMediaQuery.matches
    reducedMotionMediaQuery.addEventListener?.('change', handleReducedMotionChange)
  }
  await restoreStoredFilters()
  await loadResources()
  if (route.query.resourceId) {
    await openByReference(route.query.resourceId)
  }
})

onBeforeUnmount(() => {
  reducedMotionMediaQuery?.removeEventListener?.('change', handleReducedMotionChange)
  closeDetail()
})

function handleReducedMotionChange(event) {
  prefersReducedMotion.value = Boolean(event.matches)
}
</script>

<style scoped>
.filter-row {
  display: flex;
  gap: var(--space-sm);
}

.filter-row .control {
  min-width: 180px;
}

.resource-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-lg);
}

.resource-card {
  min-height: 336px;
  display: flex;
  flex-direction: column;
  position: relative;
  overflow: hidden;
  border-radius: var(--radius-lg);
  border: 1px solid var(--outline-variant);
  background: var(--surface-container-lowest);
  box-shadow: var(--shadow-soft);
  transition: transform 180ms ease, box-shadow 180ms ease, border-color 180ms ease;
}

.resource-card:hover {
  transform: translateY(-3px);
  border-color: rgba(0, 40, 142, 0.26);
  box-shadow: 0 12px 28px rgba(30, 64, 175, 0.1);
}

.resource-card.processing {
  border-color: rgba(113, 42, 226, 0.2);
}

.resource-cover {
  position: relative;
  height: 192px;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px 16px;
  overflow: hidden;
  background: linear-gradient(180deg, rgba(240, 244, 255, 0.9), rgba(230, 236, 252, 0.9));
}

.cover-marker-row {
  min-height: 26px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-right: 38px;
}

.cover-type-marker,
.resource-state {
  min-width: 0;
  min-height: 26px;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 0 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 700;
  line-height: 18px;
  white-space: nowrap;
}

.cover-type-marker {
  overflow: hidden;
  color: var(--primary);
  background: color-mix(in srgb, var(--primary-fixed) 72%, var(--surface-container-lowest));
  text-overflow: ellipsis;
}

.cover-type-marker .material-symbols-outlined {
  flex: 0 0 auto;
  font-size: 17px;
}

.resource-cover-content {
  min-width: 0;
  min-height: 0;
  flex: 1;
  overflow: hidden;
}

.resource-cover-content > img,
.resource-cover-content > video,
.resource-cover-content > iframe {
  width: 100%;
  height: 100%;
  display: block;
  border: 0;
  object-fit: cover;
  background: var(--surface-container);
}

.resource-card-image,
.resource-card-video,
.resource-card-animation {
  transition: transform 500ms ease;
}

.resource-card-animation {
  pointer-events: none;
  overflow: hidden;
}

.resource-card:hover .resource-card-image,
.resource-card:hover .resource-card-video,
.resource-card:hover .resource-card-animation {
  transform: scale(1.03);
}

.resource-card-main {
  min-height: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
  cursor: pointer;
}

.resource-card-main:focus-visible {
  outline: 3px solid color-mix(in srgb, var(--primary) 54%, transparent);
  outline-offset: -3px;
}

.text-preview,
.quiz-preview,
.preview-unavailable {
  width: 100%;
  height: 100%;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 6px;
  min-width: 0;
  color: var(--on-surface);
}

.text-preview span,
.quiz-preview strong,
.preview-unavailable strong {
  font-size: 15px;
  line-height: 22px;
  font-weight: 700;
}

.text-preview span {
  color: var(--on-surface-variant);
  font-size: 12px;
  line-height: 18px;
}

.text-preview p,
.quiz-preview p,
.preview-unavailable small {
  display: -webkit-box;
  margin: 0;
  overflow: hidden;
  color: var(--on-surface-variant);
  font-size: 13px;
  line-height: 20px;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
}

.quiz-preview {
  justify-content: flex-end;
}

.code-preview {
  width: 100%;
  height: 100%;
  box-sizing: border-box;
  margin: 0;
  overflow: auto;
  color: #1f2937;
  background: rgba(17, 24, 39, 0.04);
  font-family: "JetBrains Mono", monospace;
  font-size: 12px;
  line-height: 20px;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

.preview-unavailable {
  align-items: center;
  justify-content: center;
  text-align: center;
}

.preview-unavailable .material-symbols-outlined {
  color: var(--outline);
  font-size: 34px;
}

.progress-line {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 4px;
  background: rgba(255, 255, 255, 0.6);
}

.progress-line span {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, var(--secondary), #8b5cf6);
}

.resource-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: var(--space-lg);
}

.resource-title-row {
  display: flex;
  justify-content: space-between;
  gap: var(--space-sm);
  align-items: flex-start;
}

.resource-body h3 {
  margin: 0 0 var(--space-xs);
  font-size: 20px;
  line-height: 28px;
}

.resource-body p {
  flex: 1;
  margin: 0 0 var(--space-lg);
  color: var(--on-surface-variant);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.resource-delete-button {
  position: absolute;
  top: 11px;
  right: 10px;
  z-index: 2;
  width: 32px;
  height: 32px;
  display: inline-grid;
  place-items: center;
  margin: 0;
  padding: 0;
  border: 0;
  border-radius: 8px;
  color: var(--outline);
  background: transparent;
  cursor: pointer;
  transition: color 160ms ease, background-color 160ms ease, transform 160ms ease;
}

.resource-delete-button:hover:not(:disabled),
.resource-delete-button:focus-visible {
  color: var(--error);
  background: var(--error-container);
  outline: none;
}

.resource-delete-button:active:not(:disabled) {
  transform: scale(0.96);
}

.resource-delete-button:disabled {
  cursor: wait;
  opacity: 0.7;
}

.resource-delete-button .material-symbols-outlined {
  font-size: 19px;
}

.resource-body footer {
  display: flex;
  justify-content: space-between;
  gap: var(--space-sm);
  padding-top: var(--space-md);
  border-top: 1px solid var(--surface-container);
  color: var(--outline);
  font-size: 13px;
}

.pager {
  margin-top: var(--space-xl);
  display: flex;
  justify-content: center;
  align-items: center;
  gap: var(--space-md);
}

.detail-panel {
  position: fixed;
  inset: 0;
  z-index: 80;
  display: flex;
  justify-content: flex-end;
}

.resource-delete-scrim {
  position: fixed;
  inset: 0;
  z-index: 100;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(13, 28, 46, 0.32);
}

.resource-delete-dialog {
  width: min(390px, 100%);
  display: grid;
  justify-items: center;
  gap: 12px;
  padding: 24px;
  border: 1px solid var(--outline-variant);
  border-radius: var(--radius-lg);
  background: var(--surface-container-lowest);
  box-shadow: 0 24px 58px rgba(13, 28, 46, 0.24);
  text-align: center;
}

.resource-delete-dialog-icon {
  width: 44px;
  height: 44px;
  display: grid;
  place-items: center;
  border-radius: 12px;
  color: var(--error);
  background: var(--error-container);
  font-size: 24px;
}

.resource-delete-dialog h3,
.resource-delete-dialog p {
  margin: 0;
}

.resource-delete-dialog h3 {
  color: var(--on-surface);
  font-size: 20px;
  line-height: 28px;
}

.resource-delete-dialog > p {
  color: var(--on-surface-variant);
  font-size: 14px;
  line-height: 22px;
}

.resource-delete-error {
  width: 100%;
  box-sizing: border-box;
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  color: var(--on-error-container) !important;
  background: var(--error-container);
  text-align: left;
}

.resource-delete-actions {
  width: 100%;
  display: flex;
  justify-content: center;
  gap: 10px;
  margin-top: 4px;
}

.resource-delete-actions .ghost-button,
.resource-delete-actions .danger-button {
  min-width: 96px;
}

.detail-backdrop {
  position: absolute;
  inset: 0;
  background: rgba(13, 28, 46, 0.18);
}

.detail-card {
  position: relative;
  width: min(720px, 100%);
  height: 100%;
  background: var(--surface-container-lowest);
  border-left: 1px solid var(--outline-variant);
  box-shadow: -16px 0 48px rgba(13, 28, 46, 0.12);
  display: flex;
  flex-direction: column;
}

.detail-card header {
  padding: var(--space-lg);
  display: flex;
  justify-content: space-between;
  gap: var(--space-md);
  border-bottom: 1px solid var(--outline-variant);
  background: var(--surface);
}

.detail-pill-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.resource-detail-status.published { border-color: #34d399; background: var(--success-container); color: #166534; }
.resource-detail-status.reviewing,
.resource-detail-status.processing { border-color: #f59e0b; background: var(--warning-container); color: #854d0e; }
.resource-detail-status.replaced { border-color: rgba(0, 40, 142, 0.18); background: var(--primary-fixed); color: var(--primary); }
.resource-detail-status.rejected,
.resource-detail-status.failed { border-color: var(--error); background: var(--error-container); color: var(--on-error-container); }

.detail-card h3 {
  margin: var(--space-xs) 0 0;
  font-size: 24px;
  line-height: 32px;
}

.detail-body {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: var(--space-lg);
}

.animation-frame {
  width: 100%;
  height: 70vh;
  border: 1px solid var(--outline-variant);
  border-radius: var(--radius-md);
  background: white;
}

.detail-image {
  width: 100%;
  border-radius: var(--radius-md);
  background: var(--surface-container);
}

.detail-video {
  width: 100%;
  max-height: 70vh;
  border-radius: var(--radius-md);
  background: #06070a;
}

.resource-content {
  white-space: pre-wrap;
  margin: 0;
  color: var(--on-surface);
  font-family: Inter, "Microsoft YaHei", sans-serif;
}

.external-resource {
  min-height: 360px;
  display: grid;
  place-items: center;
  align-content: center;
  gap: var(--space-md);
  text-align: center;
}

.external-resource .material-symbols-outlined {
  font-size: 80px;
  color: var(--primary);
}

.video-status {
  margin-top: var(--space-lg);
  display: flex;
  align-items: center;
  gap: var(--space-md);
}

/* Resource library layout: the app navigation stays untouched; these controls belong to this page only. */
.resources-page { min-width: 0; }
.resource-header { display: flex; align-items: end; justify-content: space-between; gap: var(--space-xl); padding-bottom: var(--space-xl); border-bottom: 1px solid var(--outline-variant); }
.resource-eyebrow { display: block; margin-bottom: 6px; color: var(--primary); font-size: 13px; font-weight: 700; }
.resource-header h2 { margin: 0; color: var(--on-surface); font-size: 32px; line-height: 40px; }
.resource-header p { margin: 8px 0 0; color: var(--on-surface-variant); }
.resource-toolbar { display: flex; align-items: center; gap: 10px; flex-shrink: 0; }
.resource-search, .sort-select, .time-select { min-height: 42px; display: flex; align-items: center; gap: 8px; padding: 0 12px; border: 1px solid var(--outline-variant); border-radius: var(--radius-md); background: var(--surface-container-lowest); color: var(--on-surface-variant); }
.resource-search:focus-within, .sort-select:focus-within, .time-select:focus-within { border-color: var(--primary); box-shadow: 0 0 0 3px rgba(0, 40, 142, 0.09); }
.resource-search .material-symbols-outlined, .sort-select .material-symbols-outlined, .time-select .material-symbols-outlined { font-size: 19px; color: var(--primary); }
.resource-search input, .sort-select select, .time-select select { min-width: 0; border: 0; outline: 0; color: var(--on-surface); font: inherit; background: transparent; }
.resource-search input { width: 218px; }
.sort-select select { min-width: 108px; }
.refresh-button, .page-button { width: 42px; height: 42px; display: inline-grid; place-items: center; padding: 0; border: 1px solid var(--outline-variant); border-radius: var(--radius-md); background: var(--surface-container-lowest); color: var(--primary); cursor: pointer; transition: background-color 160ms ease, border-color 160ms ease; }
.refresh-button:hover:not(:disabled), .page-button:hover:not(:disabled) { border-color: rgba(0, 40, 142, 0.34); background: var(--primary-fixed); }
.refresh-button:disabled, .page-button:disabled { opacity: 0.46; cursor: not-allowed; }
.spinning { animation: resource-spin 700ms linear infinite; }
.resource-workspace { display: grid; grid-template-columns: 236px minmax(0, 1fr); gap: var(--space-xl); min-height: 560px; padding-top: var(--space-xl); }
.resource-filter-panel { align-self: start; padding: 2px var(--space-lg) var(--space-lg) 0; border-right: 1px solid var(--outline-variant); }
.filter-section + .filter-section { margin-top: 28px; }
.filter-title-row { display: flex; justify-content: space-between; align-items: center; }
.filter-section h3 { margin: 0 0 13px; color: var(--on-surface-variant); font-size: 14px; line-height: 20px; font-weight: 700; }
.clear-filter, .text-action { display: inline-flex; align-items: center; gap: 4px; padding: 0; border: 0; background: transparent; color: var(--primary); font: inherit; font-size: 13px; cursor: pointer; }
.text-action .material-symbols-outlined { font-size: 17px; }
.filter-check { min-height: 34px; display: flex; align-items: center; gap: 9px; color: var(--on-surface-variant); font-size: 14px; cursor: pointer; }
.filter-check input { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); }
.check-mark { width: 18px; height: 18px; box-sizing: border-box; display: inline-grid; place-items: center; flex: 0 0 auto; border: 1.5px solid var(--outline); border-radius: 4px; background: var(--surface-container-lowest); }
.filter-check input:checked + .check-mark { border-color: var(--primary); background: var(--primary); }
.filter-check input:checked + .check-mark::after { content: ''; width: 8px; height: 4px; margin-top: -2px; border: solid white; border-width: 0 0 2px 2px; transform: rotate(-45deg); }
.filter-check input:focus-visible + .check-mark { outline: 3px solid rgba(0, 40, 142, 0.2); }
.type-symbol { font-size: 18px; color: var(--primary); }
.status-dot { width: 8px; height: 8px; margin: 0 5px; border-radius: 50%; background: var(--success); }
.status-dot.published { background: var(--success); }
.status-dot.reviewing,
.status-dot.processing { background: var(--warning); }
.status-dot.replaced { background: var(--primary); }
.status-dot.rejected,
.status-dot.failed { background: var(--error); }
.filter-check small { margin-left: auto; color: var(--outline); font-size: 12px; }
.time-select { width: 100%; box-sizing: border-box; }.time-select select { width: 100%; }
.resource-results { min-width: 0; }
.resource-empty-state { min-height: 360px; display: grid; place-items: center; align-content: center; gap: 2px; }
.resource-empty-state :deep(.ui-state) { width: min(560px, 100%); }
.result-summary { min-height: 24px; display: flex; justify-content: space-between; align-items: center; gap: var(--space-md); margin-bottom: var(--space-md); }
.result-summary p { margin: 0; color: var(--on-surface-variant); font-size: 13px; }.result-summary strong { color: var(--primary); }
.resource-workspace .resource-grid { gap: 20px; }
.resource-workspace .resource-card { min-height: 336px; border-radius: 8px; box-shadow: none; }
.resource-workspace .resource-card:hover { border-color: rgba(0, 40, 142, 0.38); box-shadow: 0 12px 24px rgba(13, 28, 46, 0.08); }
.resource-workspace .resource-cover { height: 176px; background: var(--surface-container-low); }
.resource-state { position: static; flex: 0 0 auto; background: var(--success-container); color: #047857; }
.resource-state.published { background: var(--success-container); color: #047857; }
.resource-state.reviewing,
.resource-state.processing { background: var(--warning-container); color: #a65c00; }
.resource-state.replaced { background: var(--primary-fixed); color: var(--primary); }
.resource-state.rejected,
.resource-state.failed { background: var(--error-container); color: var(--error); }
.resource-workspace .progress-line span { background: var(--primary); }
.resource-workspace .resource-body { padding: 18px 20px; }.resource-workspace .resource-body h3 { margin: 0 0 9px; color: var(--on-surface); font-size: 18px; line-height: 26px; font-weight: 700; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.resource-workspace .resource-body p { margin: 0 0 18px; }.resource-workspace .resource-body footer { padding-top: 12px; }
.resource-workspace .pager { margin-top: 28px; gap: 8px; }.page-button.active { border-color: var(--primary); background: var(--primary); color: var(--on-primary); }
@keyframes resource-spin { to { transform: rotate(360deg); } }

@media (max-width: 1180px) {
  .resource-header { align-items: flex-start; flex-direction: column; }
  .resource-toolbar { width: 100%; }.resource-search { flex: 1; }.resource-search input { width: 100%; }
  .resource-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .resource-toolbar { flex-wrap: wrap; }.resource-search { min-width: 100%; }
  .resource-workspace { grid-template-columns: 1fr; gap: var(--space-lg); }.resource-filter-panel { padding: 0 0 var(--space-lg); border-right: 0; border-bottom: 1px solid var(--outline-variant); }
  .filter-section { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); column-gap: var(--space-sm); }.filter-section h3, .filter-title-row { grid-column: 1 / -1; }.filter-section + .filter-section { margin-top: var(--space-lg); }
  .resource-grid {
    grid-template-columns: 1fr;
  }
  .resource-workspace .resource-cover { height: 168px; padding: 12px; }
  .cover-marker-row { gap: 8px; }
  .cover-type-marker, .resource-state { max-width: calc(50% - 4px); overflow: hidden; text-overflow: ellipsis; }

  .filter-row {
    flex-direction: column;
  }

  .resource-body footer {
    flex-direction: column;
  }
}
</style>
