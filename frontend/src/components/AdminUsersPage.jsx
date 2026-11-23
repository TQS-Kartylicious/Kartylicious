import { useEffect, useState } from 'react'
import Brand from './Brand.jsx'
import { useAuth } from '../auth/AuthContext.jsx'
import {
  createStaffAccount,
  deactivateUser,
  changeUserRole,
  listUsers,
} from '../api/adminApi.js'

const ROLE_OPTIONS = [
  ['OPERATOR', 'Service Operator'],
  ['MUNICIPAL_WORKER', 'Municipal Worker'],
  ['ADMIN', 'Admin'],
]

function readBody(response) {
  return response.json().catch(() => null)
}

function AdminUsersPage() {
  const { token } = useAuth()
  const [users, setUsers] = useState([])
  const [values, setValues] = useState({
    username: '',
    email: '',
    password: '',
    role: 'OPERATOR',
  })
  const [fieldErrors, setFieldErrors] = useState({})
  const [statusMessage, setStatusMessage] = useState('')
  const [errorMessage, setErrorMessage] = useState('')
  const [rowRoles, setRowRoles] = useState({})

  async function reloadUsers() {
    const response = await listUsers(token)
    const body = await readBody(response)
    if (!response.ok) {
      setErrorMessage(body?.message || 'Could not load users.')
      return
    }
    setUsers(body || [])
  }

  useEffect(() => {
    reloadUsers()
  }, [])

  function updateValue(event) {
    const { name, value } = event.target
    setValues((current) => ({ ...current, [name]: value }))
    setFieldErrors((current) => ({ ...current, [name]: undefined }))
  }

  async function createAccount(event) {
    event.preventDefault()
    setStatusMessage('')
    setErrorMessage('')
    setFieldErrors({})

    const response = await createStaffAccount(token, values)
    const body = await readBody(response)
    if (!response.ok) {
      if (body && typeof body === 'object' && !body.message) {
        setFieldErrors(body)
      } else {
        setErrorMessage(body?.message || 'Could not create the account.')
      }
      return
    }
    setStatusMessage(body.message)
    setValues({ username: '', email: '', password: '', role: 'OPERATOR' })
    await reloadUsers()
  }

  async function saveRole(id) {
    setStatusMessage('')
    setErrorMessage('')
    const response = await changeUserRole(token, id, rowRoles[id])
    const body = await readBody(response)
    if (!response.ok) {
      setErrorMessage(body?.message || 'Could not update the role.')
      return
    }
    setStatusMessage(body.message)
    await reloadUsers()
  }

  async function deactivate(id) {
    setStatusMessage('')
    setErrorMessage('')
    const response = await deactivateUser(token, id)
    const body = await readBody(response)
    if (!response.ok) {
      setErrorMessage(body?.message || 'Could not deactivate the account.')
      return
    }
    setStatusMessage(body.message)
    await reloadUsers()
  }

  return (
    <div className="page-shell">
      <header className="site-header">
        <Brand />
        <span className="header-note">User management</span>
      </header>
      <main className="registration-layout">
        <section className="registration-card" aria-labelledby="create-title">
          <div className="card-heading">
            <p className="eyebrow">ADMIN / STAFF</p>
            <h2 id="create-title">Create staff account</h2>
          </div>
          {statusMessage && <p role="status" className="status-message">{statusMessage}</p>}
          {errorMessage && <p role="alert" className="form-error">{errorMessage}</p>}
          <form className="registration-form" noValidate onSubmit={createAccount}>
            {['username', 'email', 'password'].map((name) => (
              <div className="form-field" key={name}>
                <label htmlFor={name}>{name[0].toUpperCase() + name.slice(1)}</label>
                <input
                  id={name}
                  name={name}
                  type={name === 'password' ? 'password' : name === 'email' ? 'email' : 'text'}
                  value={values[name]}
                  onChange={updateValue}
                  aria-invalid={Boolean(fieldErrors[name])}
                  aria-describedby={fieldErrors[name] ? `${name}-error` : undefined}
                />
                {fieldErrors[name] && (
                  <p className="field-error" id={`${name}-error`} role="alert">
                    {fieldErrors[name]}
                  </p>
                )}
              </div>
            ))}
            <div className="form-field">
              <label htmlFor="role">Role</label>
              <select id="role" name="role" value={values.role} onChange={updateValue}>
                {ROLE_OPTIONS.map(([value, label]) => (
                  <option key={value} value={value}>{label}</option>
                ))}
              </select>
            </div>
            <button className="submit-button" type="submit">Create account</button>
          </form>
        </section>
        <section className="editorial-panel" aria-labelledby="users-title">
          <h2 id="users-title">Users</h2>
          {users.map((user) => (
            <div className="form-field" key={user.id}>
              <strong>{user.username}</strong>
              <span>{user.email}</span>
              <span>{user.active ? 'Active' : 'Inactive'}</span>
              <select
                value={rowRoles[user.id] || user.role}
                onChange={(event) =>
                  setRowRoles((current) => ({ ...current, [user.id]: event.target.value }))
                }
              >
                <option value="END_USER">End User</option>
                {ROLE_OPTIONS.map(([value, label]) => (
                  <option key={value} value={value}>{label}</option>
                ))}
              </select>
              <button type="button" onClick={() => saveRole(user.id)}>Save role</button>
              {user.active && (
                <button type="button" onClick={() => deactivate(user.id)}>Deactivate</button>
              )}
            </div>
          ))}
        </section>
      </main>
    </div>
  )
}

export default AdminUsersPage
