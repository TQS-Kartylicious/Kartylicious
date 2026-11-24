import { apiFetch } from './apiClient.js'

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
