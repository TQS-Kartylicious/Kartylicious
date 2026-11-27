import { apiFetch } from './apiClient.js'

async function request(path, token, method = 'GET', data) {
  const options = { method }
  if (data !== undefined) {
    options.headers = { 'Content-Type': 'application/json' }
    options.body = JSON.stringify(data)
  }
  const response = await apiFetch(`/api/operator${path}`, options, token)
  const body = await response.json().catch(() => ({}))
  if (!response.ok) throw new Error(body.message || 'The fleet operation failed.')
  return body
}

export const getManagedHubs = (token) => request('/hubs', token)

export const saveHub = (hubId, details, token) => hubId
  ? request(`/hubs/${encodeURIComponent(hubId)}`, token, 'PUT', details)
  : request('/hubs', token, 'POST', details)

export const registerKart = (details, token) => request('/karts', token, 'POST', details)

export const setKartActive = (kartId, active, token) =>
  request(`/karts/${encodeURIComponent(kartId)}`, token, 'PATCH', { active })
