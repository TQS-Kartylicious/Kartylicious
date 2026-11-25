import { useState } from 'react'
import { useAuth } from '../auth/AuthContext.jsx'
import Brand from './Brand.jsx'

function LoginPage() {
  const { login } = useAuth()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [fieldErrors, setFieldErrors] = useState({})
  const [formError, setFormError] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)

  function validate() {
    const errors = {}
    if (!email.trim()) errors.email = 'The email is required'
    if (!password) errors.password = 'The password is required'
    return errors
  }

  async function submit(event) {
    event.preventDefault()
    setFormError('')
    const errors = validate()
    setFieldErrors(errors)
    if (Object.keys(errors).length > 0) return

    setIsSubmitting(true)
    try {
      const result = await login(email.trim(), password)
      if (!result.success) {
        setFormError(result.body?.message || 'Incorrect email or password.')
        return
      }
      const destination = result.user?.role === 'ADMIN' ? '/admin/users' : '/dashboard'
      window.history.pushState({}, '', destination)
      window.dispatchEvent(new PopStateEvent('popstate'))
    } catch {
      setFormError('We could not connect to Kartylicious. Please try again.')
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <div className="page-shell">
      <header className="site-header">
        <Brand />
        <span className="header-note">
          <span className="header-note__dot" aria-hidden="true" />
          Log in
        </span>
      </header>
      <main className="registration-layout">
        <section className="editorial-panel" aria-labelledby="editorial-title">
          <div className="editorial-copy">
            <h1 id="editorial-title">Kartylicious</h1>
            <p className="editorial-description">A <em>delicious</em> ride</p>
          </div>
        </section>
        <section className="registration-card" aria-labelledby="login-title">
          <div className="card-heading">
            <p className="eyebrow">WELCOME BACK</p>
            <h2 id="login-title">Log in</h2>
          </div>
          <form className="registration-form" noValidate onSubmit={submit}>
            <div className="form-field">
              <label htmlFor="email">Email</label>
              <input
                id="email"
                name="email"
                type="email"
                autoComplete="email"
                value={email}
                onChange={(event) => {
                  setEmail(event.target.value)
                  setFieldErrors((current) => ({ ...current, email: undefined }))
                  setFormError('')
                }}
                aria-invalid={Boolean(fieldErrors.email)}
                aria-describedby={fieldErrors.email ? 'email-error' : undefined}
              />
              {fieldErrors.email && (
                <p className="field-error" id="email-error" role="alert">
                  {fieldErrors.email}
                </p>
              )}
            </div>
            <div className="form-field">
              <label htmlFor="password">Password</label>
              <input
                id="password"
                name="password"
                type="password"
                autoComplete="current-password"
                value={password}
                onChange={(event) => {
                  setPassword(event.target.value)
                  setFieldErrors((current) => ({ ...current, password: undefined }))
                  setFormError('')
                }}
                aria-invalid={Boolean(fieldErrors.password)}
                aria-describedby={fieldErrors.password ? 'password-error' : undefined}
              />
              {fieldErrors.password && (
                <p className="field-error" id="password-error" role="alert">
                  {fieldErrors.password}
                </p>
              )}
            </div>
            {formError && <p className="form-error" role="alert">{formError}</p>}
            <button className="submit-button" disabled={isSubmitting} type="submit">
              {isSubmitting ? 'Logging in…' : 'Log in'}
            </button>
            <p className="role-note">
              New here? <a href="/register">Create an account</a>
            </p>
          </form>
        </section>
      </main>
    </div>
  )
}

export default LoginPage
