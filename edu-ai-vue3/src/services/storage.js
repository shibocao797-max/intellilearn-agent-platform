const TOKEN_KEY = 'Admin-Token'
const PROFILE_KEY = 'eduai-auth-profile-v1'

export function getToken() {
  return localStorage.getItem(TOKEN_KEY) || ''
}

export function setToken(token) {
  localStorage.setItem(TOKEN_KEY, token)
}

export function getAuthProfile() {
  try {
    const raw = localStorage.getItem(PROFILE_KEY)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

export function setAuthProfile(profile) {
  if (!profile || typeof profile !== 'object') {
    localStorage.removeItem(PROFILE_KEY)
    return
  }

  localStorage.setItem(PROFILE_KEY, JSON.stringify(profile))
}

export function removeToken() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(PROFILE_KEY)
}
