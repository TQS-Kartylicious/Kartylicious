import { apiFetch } from './apiClient.js'

export async function getActiveRide(token) {
  const response = await apiFetch('/api/rides/active', {}, token)
  if (response.status === 204) return null
  if (!response.ok) throw new Error('We could not load your active ride.')
  return response.json()
}

export async function startRide(request, token) {
  const response = await apiFetch('/api/rides', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  }, token)
  const body = await response.json().catch(() => ({}))
  if (!response.ok) throw new Error(body.message || 'We could not start this ride.')
  return body
}

export async function endRide(rideId, request, token) {
  const response = await apiFetch(`/api/rides/${rideId}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  }, token)
  const body = await response.json().catch(() => ({}))
  if (!response.ok) throw new Error(body.message || 'We could not end this ride.')
  return body
}
