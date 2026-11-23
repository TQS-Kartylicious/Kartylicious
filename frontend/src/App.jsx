import { useEffect, useState } from 'react'
import { AuthProvider, useAuth } from './auth/AuthContext.jsx'
import DashboardPage from './components/DashboardPage.jsx'
import LoginPage from './components/LoginPage.jsx'
import ProtectedRoute from './components/ProtectedRoute.jsx'
import RegistrationPage from './components/RegistrationPage.jsx'
import './App.css'

function App() {
  return (
    <AuthProvider>
      <AppRoutes />
    </AuthProvider>
  )
}

function AppRoutes() {
  const [pathname, setPathname] = useState(window.location.pathname)
  const [registeredUsername, setRegisteredUsername] = useState('')
  const { token } = useAuth()

  useEffect(() => {
    const handlePopState = () => setPathname(window.location.pathname)
    window.addEventListener('popstate', handlePopState)

    return () => window.removeEventListener('popstate', handlePopState)
  }, [])

  function showDashboard(username) {
    setRegisteredUsername(username)
    window.history.pushState({}, '', '/dashboard')
    setPathname('/dashboard')
  }

  if (pathname === '/dashboard') {
    return (
      <ProtectedRoute>
        <DashboardPage username={registeredUsername} />
      </ProtectedRoute>
    )
  }

  if (pathname === '/login') {
    return <LoginPage />
  }

  if (pathname === '/register') {
    return <RegistrationPage onRegistrationSuccess={showDashboard} />
  }

  if (pathname === '/' && token) {
    return (
      <ProtectedRoute>
        <DashboardPage username={registeredUsername} />
      </ProtectedRoute>
    )
  }

  return <RegistrationPage onRegistrationSuccess={showDashboard} />
}

export default App
