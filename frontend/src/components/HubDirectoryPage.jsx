import { useEffect, useState } from 'react'
import { getHub, getHubs } from '../api/hubsApi.js'
import { createBooking, getActiveBookings } from '../api/bookingsApi.js'
import { startRide } from '../api/ridesApi.js'
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
  const [ride, setRide] = useState(null)
  const [rideError, setRideError] = useState('')
  const [isStartingRide, setIsStartingRide] = useState(false)

  async function startSelectedRide(hubId, kartId) {
    setIsStartingRide(true)
    setRideError('')
    try {
      const result = await startRide({ hubId, kartId }, token)
      setRide(result)
      const refreshedHub = await getHub(hubId, token)
      if (hubId === selectedHubId) setHub(refreshedHub)
      await refreshActiveBookings()
    } catch (startError) {
      setRideError(startError.message)
    } finally {
      setIsStartingRide(false)
    }
  }

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
    refreshActiveBookings().catch((loadError) => setBookingError(loadError.message))
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
      {rideError && <p className="hub-message hub-message--error" role="alert">{rideError}</p>}

      {ride && (
        <section aria-labelledby="active-ride-title" className="active-ride" role="status">
          <div>
            <p className="eyebrow">RIDE IN PROGRESS</p>
            <h3 id="active-ride-title">{ride.kartId} is ready to go</h3>
          </div>
          <RideTimer startedAt={ride.startedAt} />
        </section>
      )}

      <section aria-labelledby="active-trips-title" className="active-trips">
        <h3 id="active-trips-title">Active trips</h3>
        {activeBookings.length === 0 ? (
          <p className="hub-message">No active trips.</p>
        ) : (
          <ul>
            {activeBookings.map((booking) => (
              <li key={booking.id}>
                <span>{booking.kartId}</span>
                <span className="active-trip__actions">
                  <span>{booking.status === 'ACTIVE' ? 'Reserved' : booking.status}</span>
                  {booking.status === 'ACTIVE' && !ride && (
                    <button
                      className="trip-start"
                      disabled={isStartingRide}
                      onClick={() => startSelectedRide(booking.hubId, booking.kartId)}
                      type="button"
                    >
                      {isStartingRide ? 'Starting…' : 'Start Ride'}
                    </button>
                  )}
                </span>
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
                      {kart.selectable && !ride && (
                        <button
                          className="kart-start"
                          disabled={isStartingRide}
                          onClick={() => startSelectedRide(selectedHubId, kart.id)}
                          type="button"
                        >
                          {isStartingRide ? 'Starting…' : 'Start Ride'}
                        </button>
                      )}
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

function RideTimer({ startedAt }) {
  const [elapsedSeconds, setElapsedSeconds] = useState(() => elapsedTime(startedAt))

  useEffect(() => {
    const timer = window.setInterval(() => setElapsedSeconds(elapsedTime(startedAt)), 1000)
    return () => window.clearInterval(timer)
  }, [startedAt])

  const hours = Math.floor(elapsedSeconds / 3600)
  const minutes = Math.floor((elapsedSeconds % 3600) / 60)
  const seconds = elapsedSeconds % 60
  return (
    <time className="ride-timer" dateTime={startedAt}>
      {hours > 0 ? `${String(hours).padStart(2, '0')}:` : ''}
      {String(minutes).padStart(2, '0')}:{String(seconds).padStart(2, '0')}
    </time>
  )
}

function elapsedTime(startedAt) {
  return Math.max(0, Math.floor((Date.now() - new Date(startedAt).getTime()) / 1000))
}

export default HubDirectoryPage
