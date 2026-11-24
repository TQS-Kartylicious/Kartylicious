import { apiFetch } from './apiClient.js'

export async function createBooking(request, token) {
  const response = await apiFetch('/api/bookings', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  }, token)
  const body = await response.json().catch(() => ({}))
  if (!response.ok) throw new Error(body.message || 'We could not confirm this booking.')
  return body
}

export async function getActiveBookings(token) {
  const response = await apiFetch('/api/bookings/active', {}, token)
  if (!response.ok) throw new Error('We could not load your active trips.')
  return response.json()
}
