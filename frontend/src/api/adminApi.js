import { apiFetch } from './apiClient.js'

export async function listUsers(token) {
  return apiFetch('/api/admin/users', {}, token)
}

export async function createStaffAccount(token, account) {
  return apiFetch('/api/admin/users', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(account),
  }, token)
}

export async function changeUserRole(token, id, role) {
  return apiFetch(`/api/admin/users/${id}/role`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ role }),
  }, token)
}

export async function deactivateUser(token, id) {
  return apiFetch(`/api/admin/users/${id}/status`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ active: false }),
  }, token)
}
