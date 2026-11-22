import { useEffect } from 'react'
import { useAuth } from '../auth/AuthContext.jsx'

function ProtectedRoute({ children }) {
  const { token, user, isSessionLoading } = useAuth()

  useEffect(() => {
    if (!isSessionLoading && (!token || !user)) {
      window.history.replaceState({}, '', '/login')
      window.dispatchEvent(new PopStateEvent('popstate'))
    }
  }, [isSessionLoading, token, user])

  if (!token) return null
  if (isSessionLoading) return null
  if (!user) return null
  return children
}

export default ProtectedRoute
