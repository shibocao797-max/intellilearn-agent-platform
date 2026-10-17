import { computed, reactive, readonly } from 'vue'

const RIGHT_PANEL_MODES = ['trace', 'monitor', 'split']

const state = reactive({
  rightPanelMode: 'trace'
})

export function useUiStore() {
  return {
    state: readonly(state),
    rightPanelMode: computed(() => state.rightPanelMode),
    setRightPanelMode,
    cycleRightPanelMode
  }
}

export function normalizeRightPanelMode(value, fallback = 'trace') {
  return RIGHT_PANEL_MODES.includes(value) ? value : fallback
}

function setRightPanelMode(mode) {
  state.rightPanelMode = normalizeRightPanelMode(mode)
}

function cycleRightPanelMode() {
  const currentIndex = RIGHT_PANEL_MODES.indexOf(state.rightPanelMode)
  const nextIndex = currentIndex < 0 ? 0 : (currentIndex + 1) % RIGHT_PANEL_MODES.length
  state.rightPanelMode = RIGHT_PANEL_MODES[nextIndex]
  return state.rightPanelMode
}
