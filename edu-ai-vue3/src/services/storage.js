const TOKEN_KEY = 'Admin-Token'
const PROFILE_KEY = 'eduai-auth-profile-v1'

function readCookie(name) {
  const parts = document.cookie ? document.cookie.split('; ') : []
  const found = parts.find(item => item.startsWith(`${name}=`))
  return found ? decodeURIComponent(found.slice(name.length + 1)) : ''
}

export function getToken() {
  return localStorage.getItem(TOKEN_KEY) || readCookie(TOKEN_KEY)
}

export function setToken(token) {
  localStorage.setItem(TOKEN_KEY, token)
  document.cookie = `${TOKEN_KEY}=${encodeURIComponent(token)}; path=/; SameSite=Lax`
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
  document.cookie = `${TOKEN_KEY}=; path=/; Max-Age=0; SameSite=Lax`
}
