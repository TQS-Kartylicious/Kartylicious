import { useRef, useState } from 'react'
import { useAuth } from '../auth/AuthContext.jsx'
import Brand from './Brand.jsx'

const EMAIL_IN_USE = 'The email is already in use'
const USERNAME_IN_USE = 'The username is already in use'

function RegistrationPage({ onRegistrationSuccess }) {
  const { login } = useAuth()
  const [values, setValues] = useState({ username: '', email: '', password: '' })
  const [fieldErrors, setFieldErrors] = useState({})
  const [formError, setFormError] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const emailInput = useRef(null)

  function updateField(event) {
    const { name, value } = event.target
    setValues((current) => ({ ...current, [name]: value }))
    setFieldErrors((current) => {
      if (!current[name]) return current
      const next = { ...current }
      delete next[name]
      return next
    })
    setFormError('')
  }

  function validate() {
    const errors = {}
    const username = values.username.trim()
    const email = values.email.trim()

    if (!username) {
      errors.username = 'The username is required'
    } else if (username.length > 64) {
      errors.username = 'The username must be at most 64 characters'
    }

    if (!email) {
      errors.email = 'The email is required'
    } else if (emailInput.current?.validity.typeMismatch) {
      errors.email = 'The email format is incorrect'
    } else if (email.length > 254) {
      errors.email = 'The email must be at most 254 characters'
    }

    if (!values.password) {
      errors.password = 'The password is required'
    } else if (!values.password.trim()) {
      errors.password = 'The password is required'
    } else if (values.password.length < 8) {
      errors.password = 'The password must be equal or longer than 8 characters'
    }

    return errors
  }

  async function handleSubmit(event) {
    event.preventDefault()
    setFormError('')

    const errors = validate()
    setFieldErrors(errors)
    if (Object.keys(errors).length > 0) return

    setIsSubmitting(true)

    try {
      const response = await fetch('/api/registration', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          username: values.username.trim(),
          email: values.email.trim(),
          password: values.password,
        }),
      })

      let responseBody = null
      try {
        responseBody = await response.json()
      } catch {
        responseBody = null
      }

      if (!response.ok) {
        if (response.status === 409 && responseBody?.message === EMAIL_IN_USE) {
          setFieldErrors({ email: EMAIL_IN_USE })
        } else if (response.status === 409 && responseBody?.message === USERNAME_IN_USE) {
          setFieldErrors({ username: USERNAME_IN_USE })
        } else if (response.status === 400 && responseBody && typeof responseBody === 'object') {
          const errors = Object.fromEntries(
            Object.entries(responseBody).filter(
              ([field, message]) =>
                ['username', 'email', 'password'].includes(field) && typeof message === 'string',
            ),
          )
          if (Object.keys(errors).length > 0) {
            setFieldErrors(errors)
          } else {
            setFormError('Please check your details and try again.')
          }
        } else {
          setFormError(responseBody?.message || 'We could not create your account. Please try again.')
        }
        return
      }

      const loginResult = await login(values.email.trim(), values.password)
      if (!loginResult.success) {
        setFormError(loginResult.body?.message || 'We could not log you in. Please try again.')
        return
      }
      onRegistrationSuccess(loginResult.user?.username || values.username.trim())
    } catch {
      setFormError("We couldn't connect to Kartylicious. Please try again.")
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
          End User registration
        </span>
      </header>

      <main className="registration-layout">
        <section className="editorial-panel" aria-labelledby="editorial-title">
          <div className="editorial-copy">
            <h1 id="editorial-title">Kartylicious</h1>
            <p className="editorial-description">
              A <em>delicious</em> ride
            </p>
          </div>
        </section>

        <section className="registration-card" aria-labelledby="registration-title">
          <div className="card-heading">
            <p className="eyebrow">01 / YOUR DETAILS</p>
            <h2 id="registration-title">Create your account</h2>
            <p className="card-intro">Just the essentials. Then you’re ready to roll.</p>
          </div>

          <form
            aria-busy={isSubmitting}
            className="registration-form"
            noValidate
            onSubmit={handleSubmit}
          >
            <div className="form-field">
              <label htmlFor="username">Username</label>
              <input
                autoComplete="username"
                id="username"
                maxLength={64}
                name="username"
                onChange={updateField}
                required
                aria-describedby={fieldErrors.username ? 'username-error' : undefined}
                aria-invalid={Boolean(fieldErrors.username)}
                value={values.username}
              />
              {fieldErrors.username && (
                <p className="field-error" id="username-error" role="alert">
                  {fieldErrors.username}
                </p>
              )}
            </div>

            <div className="form-field">
              <label htmlFor="email">Email</label>
              <input
                autoComplete="email"
                id="email"
                maxLength={254}
                name="email"
                onChange={updateField}
                required
                ref={emailInput}
                type="email"
                aria-describedby={fieldErrors.email ? 'email-error' : undefined}
                aria-invalid={Boolean(fieldErrors.email)}
                value={values.email}
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
                autoComplete="new-password"
                id="password"
                name="password"
                onChange={updateField}
                required
                type="password"
                aria-describedby={
                  fieldErrors.password ? 'password-hint password-error' : 'password-hint'
                }
                aria-invalid={Boolean(fieldErrors.password)}
                value={values.password}
              />
              {fieldErrors.password && (
                <p className="field-error" id="password-error" role="alert">
                  {fieldErrors.password}
                </p>
              )}
              <p className="field-hint" id="password-hint">
                At least 8 characters
              </p>
            </div>

            {formError && (
              <p className="form-error" role="alert">
                {formError}
              </p>
            )}

            <button className="submit-button" disabled={isSubmitting} type="submit">
              {isSubmitting ? (
                <>
                  <span className="button-spinner" aria-hidden="true" />
                  Creating your account…
                </>
              ) : (
                <>
                  Create account
                  <span aria-hidden="true">↗</span>
                </>
              )}
            </button>
            <p className="role-note">
              Your account will be created with the <strong>End User</strong> role.
            </p>
            <p className="role-note">
              Already have an account? <a href="/login">Log in</a>
            </p>
          </form>
        </section>
      </main>

      <footer className="site-footer">
        <span>KARTYLICIOUS</span>
        <span>MADE TO MOVE YOU</span>
      </footer>
    </div>
  )
}

export default RegistrationPage
