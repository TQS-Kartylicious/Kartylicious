import http from 'k6/http'
import { Counter, Rate, Trend } from 'k6/metrics'

const BASE = 'http://backend:8080'

export const summaryTrendStats = ['avg', 'min', 'med', 'p(95)', 'p(99)', 'max', 'count']

export function request(method, path, token, body) {
  const headers = { 'Content-Type': 'application/json' }
  if (token) headers.Authorization = `Bearer ${token}`
  return http.request(method, BASE + path, body === undefined ? null : JSON.stringify(body), {
    headers,
    redirects: 0,
    timeout: '5s',
  })
}

export function json(response) {
  try {
    return response.json()
  } catch {
    return null
  }
}

export function requestMetrics(prefix) {
  const latency = new Trend(`${prefix}_latency_ms`, true)
  const requests = new Counter(`${prefix}_requests`)
  const successes = new Counter(`${prefix}_successes`)
  const conflicts = new Counter(`${prefix}_conflicts`)
  const unexpected = new Counter(`${prefix}_unexpected`)
  const serverErrors = new Counter(`${prefix}_http_5xx`)
  const serverErrorRate = new Rate(`${prefix}_http_5xx_rate`)
  const network = new Counter(`${prefix}_network_failures`)
  const auth = new Counter(`${prefix}_auth_failures`)
  const other = new Counter(`${prefix}_other_unexpected`)

  return (response, success, conflict = false) => {
    const status = response.status
    const serverError = status >= 500 && status <= 599
    const authError = status === 401 || status === 403
    requests.add(1)
    latency.add(response.timings.duration)
    successes.add(Number(success))
    conflicts.add(Number(conflict))
    unexpected.add(Number(!success && !conflict))
    serverErrors.add(Number(serverError))
    network.add(Number(status === 0))
    auth.add(Number(authError))
    other.add(Number(!success && !conflict && !serverError && !authError && status !== 0))
    if (!conflict && status !== 0) serverErrorRate.add(serverError)
  }
}

export function writeSummary(suite, data) {
  const metrics = Object.fromEntries(
    Object.entries(data.metrics)
      .filter(([name]) => !name.startsWith('http_') && name !== 'checks')
      .map(([name, metric]) => [name, { ...metric.values, thresholds: metric.thresholds }]),
  )
  return {
    [`/evidence/${suite}.json`]: JSON.stringify({
      suite,
      duration_ms: data.state.testRunDurationMs,
      metrics,
    }, null, 2) + '\n',
  }
}
