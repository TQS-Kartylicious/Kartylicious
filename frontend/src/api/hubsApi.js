import { apiFetch } from './apiClient.js'

export async function getHubs(token) {
  const response = await apiFetch('/api/hubs', {}, token)
  if (!response.ok) throw new Error('We could not load the hub directory.')
  return response.json()
}

export async function getHub(hubId, token) {
  const response = await apiFetch(`/api/hubs/${encodeURIComponent(hubId)}`, {}, token)
  if (!response.ok) throw new Error('We could not load this location.')
  return response.json()
}
