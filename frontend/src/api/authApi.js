export async function login(email, password) {
  const response = await fetch('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ user: email, password }),
  })

  let body = null
  try {
    body = await response.json()
  } catch {
    body = null
  }

  return { response, body }
}
