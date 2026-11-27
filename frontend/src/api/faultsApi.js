import { apiFetch } from './apiClient.js'

export async function submitFault(report, token) {
  const response = await apiFetch('/api/fault-reports', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(report),
  }, token)
  const body = await response.json().catch(() => ({}))
  if (!response.ok) throw new Error(body.message || 'We could not submit this report.')
  return body
}

export async function getFaults(path, token) {
  const response = await apiFetch(path, {}, token)
  if (!response.ok) throw new Error('We could not load the maintenance records.')
  return response.json()
}
