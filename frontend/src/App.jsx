import { useEffect, useState } from 'react'
import DashboardPage from './components/DashboardPage.jsx'
import RegistrationPage from './components/RegistrationPage.jsx'
import './App.css'

function App() {
  const [pathname, setPathname] = useState(window.location.pathname)
  const [registeredUsername, setRegisteredUsername] = useState('')

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
    return <DashboardPage username={registeredUsername} />
  }

  return <RegistrationPage onRegistrationSuccess={showDashboard} />
}

export default App
