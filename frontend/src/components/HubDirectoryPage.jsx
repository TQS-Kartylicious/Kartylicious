import { useEffect, useState } from 'react'
import { getHub, getHubs } from '../api/hubsApi.js'
import { createBooking, getActiveBookings } from '../api/bookingsApi.js'
import { useAuth } from '../auth/AuthContext.jsx'

function currentDateTimeLocal() {
  const now = new Date()
  const pad = (value) => String(value).padStart(2, '0')
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}T${pad(now.getHours())}:${pad(now.getMinutes())}`
}

function HubDirectoryPage() {
  const { token } = useAuth()
  const [hubs, setHubs] = useState([])
  const [selectedHubId, setSelectedHubId] = useState('')
  const [hub, setHub] = useState(null)
  const [selectedKartId, setSelectedKartId] = useState('')
  const [isLoadingHubs, setIsLoadingHubs] = useState(true)
  const [isLoadingHub, setIsLoadingHub] = useState(false)
  const [error, setError] = useState('')
  const [bookingMessage, setBookingMessage] = useState('')
  const [bookingError, setBookingError] = useState('')
  const [startsAt, setStartsAt] = useState('')
  const [duration, setDuration] = useState('60')
  const [isBooking, setIsBooking] = useState(false)
  const [activeBookings, setActiveBookings] = useState([])

  async function refreshActiveBookings() {
    const result = await getActiveBookings(token)
    setActiveBookings(result)
  }

  async function bookSelectedKart() {
    const kart = hub?.karts.find((item) => item.id === selectedKartId)
    if (!kart || !startsAt) return
    setIsBooking(true)
    setBookingMessage('')
    setBookingError('')
    const start = new Date(startsAt)
    const end = new Date(start.getTime() + Number(duration) * 60 * 1000)
    try {
      const result = await createBooking({
        hubId: selectedHubId,
        kartId: selectedKartId,
        startsAt: start.toISOString(),
        endsAt: end.toISOString(),
      }, token)
      setBookingMessage(result.message)
      setSelectedKartId('')
      const refreshedHub = await getHub(selectedHubId, token)
      setHub(refreshedHub)
      await refreshActiveBookings()
    } catch (bookingLoadError) {
      setBookingError(bookingLoadError.message)
    } finally {
      setIsBooking(false)
    }
  }

  useEffect(() => {
    let isCancelled = false
    getHubs(token)
      .then((result) => {
        if (!isCancelled) {
          setHubs(result)
          setSelectedHubId(result[0]?.id || '')
        }
      })
      .catch((loadError) => {
        if (!isCancelled) setError(loadError.message)
      })
      .finally(() => {
        if (!isCancelled) setIsLoadingHubs(false)
      })

    return () => {
      isCancelled = true
    }
  }, [token])

  useEffect(() => {
    refreshActiveBookings().catch(() => {})
  }, [token])

  useEffect(() => {
    if (!selectedHubId) {
      setHub(null)
      return undefined
    }

    let isCancelled = false
    setIsLoadingHub(true)
    setError('')
    setSelectedKartId('')
    getHub(selectedHubId, token)
      .then((result) => {
        if (!isCancelled) setHub(result)
      })
      .catch((loadError) => {
        if (!isCancelled) {
          setHub(null)
          setError(loadError.message)
        }
      })
      .finally(() => {
        if (!isCancelled) setIsLoadingHub(false)
      })

    return () => {
      isCancelled = true
    }
  }, [selectedHubId, token])

  return (
    <section className="hub-directory" aria-labelledby="hub-directory-title">
      <div className="hub-directory__heading">
        <div>
          <p className="eyebrow">YOUR CITY, READY TO RIDE</p>
          <h2 id="hub-directory-title">Find your mini-kart</h2>
          <p>Choose a municipal hub to see its fleet and battery levels.</p>
        </div>
        <span className="battery-rule">Minimum charge for booking: 20%</span>
      </div>

      {error && <p className="hub-message hub-message--error" role="alert">{error}</p>}
      {bookingMessage && <p className="hub-message" role="status">{bookingMessage}</p>}
      {bookingError && <p className="hub-message hub-message--error" role="alert">{bookingError}</p>}

      <section aria-labelledby="active-trips-title" className="active-trips">
        <h3 id="active-trips-title">Active trips</h3>
        {activeBookings.length === 0 ? (
          <p className="hub-message">No active trips.</p>
        ) : (
          <ul>
            {activeBookings.map((booking) => (
              <li key={booking.id}>
                <span>{booking.kartId}</span>
                <span>{booking.status === 'ACTIVE' ? 'Reserved' : booking.status}</span>
              </li>
            ))}
          </ul>
        )}
      </section>

      {isLoadingHubs ? (
        <p className="hub-message" role="status">Loading municipal hubs…</p>
      ) : (
        <div className="hub-browser">
          <nav className="hub-list" aria-label="Municipal hubs">
            {hubs.map((item) => (
              <button
                aria-pressed={selectedHubId === item.id}
                className={`hub-option${selectedHubId === item.id ? ' hub-option--selected' : ''}`}
                key={item.id}
                onClick={() => setSelectedHubId(item.id)}
                type="button"
              >
                <span className="hub-option__name">{item.name}</span>
                <span className="hub-option__address">{item.address}</span>
                <span className="hub-option__count">
                  {item.availableKartCount} {item.availableKartCount === 1 ? 'kart' : 'karts'} available
                </span>
              </button>
            ))}
          </nav>

          <div className="hub-details" aria-live="polite">
            {isLoadingHub ? (
              <p className="hub-message" role="status">Loading karts…</p>
            ) : hub ? (
              <>
                <div className="hub-details__heading">
                  <div>
                    <p className="eyebrow">MUNICIPAL HUB</p>
                    <h3>{hub.name}</h3>
                    <p>{hub.address}</p>
                  </div>
                  <span className="fleet-count">{hub.karts.length} karts</span>
                </div>

                {hub.karts.every((kart) => !kart.selectable) && (
                  <p className="empty-fleet" role="status">
                    No karts are currently available at this location.
                  </p>
                )}

                <div className="kart-list">
                  {hub.karts.map((kart) => (
                    <article
                      aria-label={kart.id}
                      className={`kart-card${kart.selectable ? '' : ' kart-card--unavailable'}${selectedKartId === kart.id ? ' kart-card--selected' : ''}`}
                      key={kart.id}
                    >
                      <div className="kart-card__topline">
                        <div>
                          <p className="kart-card__id">{kart.id}</p>
                          <h4>{kart.name}</h4>
                        </div>
                        <span className={`availability availability--${kart.status.toLowerCase().replaceAll(' ', '-')}`}>
                          {kart.status}
                        </span>
                      </div>
                      <div className="battery-level" aria-label={`Battery ${kart.batteryLevel}%`}>
                        <span>Battery</span>
                        <strong>{kart.batteryLevel}%</strong>
                        <span className="battery-track" aria-hidden="true">
                          <span style={{ width: `${kart.batteryLevel}%` }} />
                        </span>
                      </div>
                      <button
                        aria-pressed={selectedKartId === kart.id}
                        className="kart-select"
                        disabled={!kart.selectable}
                        onClick={() => setSelectedKartId(kart.id)}
                        type="button"
                      >
                        {selectedKartId === kart.id ? 'Selected' : 'Select kart'}
                      </button>
                      {!kart.selectable && kart.status === 'Available' && (
                        <p className="kart-card__note">Not eligible for booking</p>
                      )}
                    </article>
                  ))}
                </div>
                {selectedKartId && (
                  <div className="booking-panel">
                    <div className="booking-panel__heading">
                      <p className="eyebrow">READY WHEN YOU ARE</p>
                      <h4>Book your selected mini-kart</h4>
                      <p className="booking-panel__hint">Choose a window of up to 60 minutes.</p>
                    </div>
                    <label>
                      Start time
                      <input min={currentDateTimeLocal()} type="datetime-local" value={startsAt} onChange={(event) => setStartsAt(event.target.value)} />
                    </label>
                    <label>
                      Duration (minutes)
                      <input min="1" max="60" type="number" value={duration} onChange={(event) => setDuration(event.target.value)} />
                    </label>
                    <button className="booking-confirm" disabled={isBooking || !startsAt} onClick={bookSelectedKart} type="button">
                      {isBooking ? 'Confirming…' : 'Confirm booking'}
                    </button>
                  </div>
                )}
              </>
            ) : null}
          </div>
        </div>
      )}
    </section>
  )
}

export default HubDirectoryPage
