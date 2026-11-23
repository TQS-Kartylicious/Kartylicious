import Brand from './Brand.jsx'
import { useAuth } from '../auth/AuthContext.jsx'
import HubDirectoryPage from './HubDirectoryPage.jsx'

function DashboardPage({ username }) {
  const { logout } = useAuth()

  function handleLogout() {
    logout()
    window.history.replaceState({}, '', '/login')
    window.dispatchEvent(new PopStateEvent('popstate'))
  }

  return (
    <div className="page-shell dashboard-shell">
      {username && (
        <div className="kart-sweep" aria-hidden="true">
          <svg viewBox="0 0 240 110" role="presentation">
            <path
              className="kart-sweep__body"
              d="M25 66h30l18-25h59l18 13h34l22 13v12h-25a17 17 0 0 0-33 0H82a17 17 0 0 0-33 0H25z"
            />
            <path className="kart-sweep__window" d="M82 43h43l15 11H73z" />
            <circle className="kart-sweep__wheel" cx="66" cy="80" r="15" />
            <circle className="kart-sweep__wheel" cx="164" cy="80" r="15" />
            <circle className="kart-sweep__hub" cx="66" cy="80" r="5" />
            <circle className="kart-sweep__hub" cx="164" cy="80" r="5" />
          </svg>
        </div>
      )}
      <header className="site-header">
        <Brand />
        <span className="header-note">
          <span className="header-note__dot" aria-hidden="true" />
          Your dashboard
        </span>
      </header>

      <main className="dashboard-main dashboard-main--directory">
        <div className="dashboard-welcome">
          <p className="eyebrow">ACCOUNT READY</p>
          <h1>{username ? `Welcome, ${username}.` : 'Welcome to Kartylicious.'}</h1>
          <p className="dashboard-copy">Your next ride is waiting at a municipal hub.</p>
        </div>
        <HubDirectoryPage />
        <button className="dashboard-link" type="button" onClick={handleLogout}>
          Logout
        </button>
      </main>

      <footer className="site-footer">
        <span>KARTYLICIOUS</span>
        <span>MADE TO MOVE YOU</span>
      </footer>
    </div>
  )
}

export default DashboardPage
