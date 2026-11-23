import { createContext, useContext, useEffect, useMemo, useState } from 'react'
import { apiFetch } from '../api/apiClient.js'
import { login as loginRequest } from '../api/authApi.js'

const AuthContext = createContext(null)
const TOKEN_KEY = 'kartylicious.auth.token'

export function AuthProvider({ children }) {
  const [token, setToken] = useState(() => sessionStorage.getItem(TOKEN_KEY))
  const [user, setUser] = useState(null)
  const [isSessionLoading, setIsSessionLoading] = useState(Boolean(token))

  useEffect(() => {
    if (!token) {
      setUser(null)
      setIsSessionLoading(false)
      return
    }

    let isCancelled = false
    apiFetch('/api/auth/me', {}, token)
      .then(async (response) => {
        if (!response.ok) {
          throw new Error('Session validation failed')
        }
        return response.json()
      })
      .then((currentUser) => {
        if (!isCancelled) setUser(currentUser)
      })
      .catch(() => {
        if (!isCancelled) {
          sessionStorage.removeItem(TOKEN_KEY)
          setToken(null)
          setUser(null)
        }
      })
      .finally(() => {
        if (!isCancelled) setIsSessionLoading(false)
      })

    return () => {
      isCancelled = true
    }
  }, [])

  async function login(email, password) {
    const { response, body } = await loginRequest(email, password)
    if (!response.ok) {
      return { success: false, body }
    }

    sessionStorage.setItem(TOKEN_KEY, body.token)
    setToken(body.token)
    setUser(body.user)
    setIsSessionLoading(false)
    return { success: true, user: body.user }
  }

  function logout() {
    // Stateless logout: the browser discards the token; no server-side denylist is used.
    sessionStorage.removeItem(TOKEN_KEY)
    setToken(null)
    setUser(null)
    setIsSessionLoading(false)
  }

  const value = useMemo(
    () => ({ token, user, isSessionLoading, login, logout }),
    [token, user, isSessionLoading],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used inside AuthProvider')
  }
  return context
}
