import { useEffect, useState } from 'react'
import { getManagedHubs, registerKart, saveHub, setKartActive } from '../api/fleetApi.js'
import { useAuth } from '../auth/AuthContext.jsx'

const emptyHub = { name: '', address: '', capacity: '1' }

export default function OperatorFleetManagement() {
  const { token } = useAuth()
  const [hubs, setHubs] = useState(null)
  const [editingHubId, setEditingHubId] = useState(null)
  const [hubForm, setHubForm] = useState(emptyHub)
  const [kartId, setKartId] = useState('')
  const [kartHubId, setKartHubId] = useState('')
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')

  useEffect(() => {
    let current = true
    getManagedHubs(token).then((result) => {
      if (current) setHubs(result)
    }).catch((failure) => {
      if (current) setError(failure.message)
    })
    return () => { current = false }
  }, [token])

  async function applyChange(operation, onSaved) {
    setBusy(true)
    setMessage('')
    setError('')
    try {
      const result = await operation()
      onSaved?.()
      setMessage(result.message)
      setHubs(await getManagedHubs(token))
    } catch (failure) {
      setError(failure.message)
    } finally {
      setBusy(false)
    }
  }

  function resetHubForm() {
    setEditingHubId(null)
    setHubForm(emptyHub)
  }

  function changeHubField(event) {
    const { name, value } = event.target
    setHubForm((current) => ({ ...current, [name]: value }))
  }

  function submitHub(event) {
    event.preventDefault()
    applyChange(
      () => saveHub(editingHubId, { ...hubForm, capacity: Number(hubForm.capacity) }, token),
      resetHubForm,
    )
  }

  function submitKart(event) {
    event.preventDefault()
    applyChange(() => registerKart({ id: kartId, hubId: kartHubId }, token), () => setKartId(''))
  }

  return (
    <section className="active-trips fleet-management" aria-labelledby="fleet-management-title">
      <h3 id="fleet-management-title">Fleet management</h3>
      {message && <p className="hub-message" role="status">{message}</p>}
      {error && <p className="hub-message hub-message--error" role="alert">{error}</p>}
      {hubs === null && !error && <p role="status">Loading fleet…</p>}

      <form className="booking-panel" aria-labelledby="hub-form-title" onSubmit={submitHub}>
        <h4 className="booking-panel__heading" id="hub-form-title">
          {editingHubId ? 'Edit hub' : 'Register hub'}
        </h4>
        <label>
          Hub name
          <input name="name" value={hubForm.name} onChange={changeHubField} maxLength={255} required />
        </label>
        <label>
          Location
          <input name="address" value={hubForm.address} onChange={changeHubField} maxLength={255} required />
        </label>
        <label>
          Capacity
          <input name="capacity" type="number" min="1" step="1" value={hubForm.capacity} onChange={changeHubField} required />
        </label>
        <button className="booking-confirm" type="submit" disabled={busy}>
          {editingHubId ? 'Save hub' : 'Register hub'}
        </button>
        {editingHubId && (
          <button className="kart-select" type="button" onClick={resetHubForm} disabled={busy}>Cancel edit</button>
        )}
      </form>

      <form className="booking-panel" aria-labelledby="kart-form-title" onSubmit={submitKart}>
        <h4 className="booking-panel__heading" id="kart-form-title">Register mini-kart</h4>
        <label>
          Mini-kart identifier
          <input value={kartId} onChange={(event) => setKartId(event.target.value)} maxLength={64} required />
        </label>
        <label>
          Parking hub
          <select value={kartHubId} onChange={(event) => setKartHubId(event.target.value)} required>
            <option value="">Choose a hub</option>
            {hubs?.map((hub) => <option key={hub.id} value={hub.id}>{hub.name} — {hub.address}</option>)}
          </select>
        </label>
        <button className="booking-confirm" type="submit" disabled={busy || !hubs?.length}>Register mini-kart</button>
      </form>

      {hubs?.map((hub) => (
        <article key={hub.id} className="fleet-management__hub" aria-label={hub.name}>
          <h4>{hub.name}</h4>
          <p>{hub.address} · {hub.occupancy} / {hub.capacity} spaces occupied</p>
          <button className="kart-select" type="button" disabled={busy} onClick={() => {
            setEditingHubId(hub.id)
            setHubForm({ name: hub.name, address: hub.address, capacity: String(hub.capacity) })
          }}>Edit hub</button>
          {hub.karts.length > 0 && (
            <ul>
              {hub.karts.map((kart) => (
                <li key={kart.id} aria-label={kart.id}>
                  <strong>{kart.id}</strong>
                  <span>{kart.batteryLevel}% · {kart.status.replaceAll('_', ' ')} · {kart.active ? 'Active' : 'Inactive'}</span>
                  {kart.status !== 'RETIRED' && (
                    <button className="kart-select" type="button" disabled={busy}
                      onClick={() => applyChange(() => setKartActive(kart.id, !kart.active, token))}>
                      {kart.active ? 'Deactivate' : 'Activate'}
                    </button>
                  )}
                </li>
              ))}
            </ul>
          )}
        </article>
      ))}
    </section>
  )
}
