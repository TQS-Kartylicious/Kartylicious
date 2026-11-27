import { sleep } from 'k6'
import exec from 'k6/execution'
import { Rate, Trend } from 'k6/metrics'
import { json, request, requestMetrics, summaryTrendStats, writeSummary } from './common.js'

const suite = __ENV.SUITE
const input = JSON.parse(open('/performance/input.json'))
const warmupMs = 30_000
const recordBooking = requestMetrics('booking')
const recordCleanup = requestMetrics('cleanup')
const recordHubList = requestMetrics('hub_list')
const recordHubDetail = requestMetrics('hub_detail')
const recordBrowse = requestMetrics('health')
const bookingSuccessLatency = new Trend('booking_success_latency_ms', true)
const bookingSuccessRate = new Rate('booking_success_rate')
const bookingPeakErrors = new Rate('booking_peak_http_5xx_rate')
const discoveryPeakErrors = new Rate('discovery_peak_http_5xx_rate')
const healthAvailability = new Rate('health_availability')

const load = { executor: 'constant-vus', vus: 50, duration: '90s', gracefulStop: '15s' }
const probe = {
  executor: 'constant-arrival-rate', rate: 1, timeUnit: '1s', duration: '60s',
  preAllocatedVUs: 2, maxVUs: 10, gracefulStop: '10s',
}

const thresholds = {
  booking: {
    booking_latency_ms: ['p(95)<500'],
    booking_success_latency_ms: ['p(95)<500'],
    booking_success_rate: ['rate>0.90'],
    booking_conflicts: ['count>0'],
    booking_unexpected: ['count==0'],
    cleanup_unexpected: ['count==0'],
    booking_http_5xx_rate: ['rate<0.01'],
    booking_peak_http_5xx_rate: ['rate<0.01'],
  },
  discovery: {
    hub_list_latency_ms: ['p(95)<300'],
    hub_detail_latency_ms: ['p(95)<300'],
    hub_list_unexpected: ['count==0'],
    hub_detail_unexpected: ['count==0'],
    discovery_peak_http_5xx_rate: ['rate<0.01'],
  },
  health: { health_availability: ['rate>=0.995'], health_requests: ['count==60'] },
}

export const options = {
  scenarios: { [suite]: suite === 'health' ? probe : load },
  thresholds: thresholds[suite],
  summaryTrendStats,
}

if (suite === 'booking') {
  // Cancellation URLs contain a fresh booking ID. Keep one series per request outcome.
  options.systemTags = ['status', 'method', 'scenario', 'expected_response', 'error_code']
}

function countServerError(rate, response, expectedConflict = false) {
  if (response.status !== 0 && !expectedConflict) rate.add(response.status >= 500)
}

function book(token, index, measured) {
  const mode = exec.vu.iterationInScenario % 20 === 0
  const kart = mode ? { hubId: 'perf-hub-5', kartId: 'perf-conflict' } : input.karts[index]
  const response = request('POST', '/api/bookings', token, { ...kart, ...input.window })
  const payload = json(response)
  const success = !mode && response.status === 200 && typeof payload?.id === 'string' &&
    payload.status === 'ACTIVE' && payload.kartId === kart.kartId
  const conflict = mode && response.status === 409 && payload?.message === 'The kart is no longer available.'

  if (measured) {
    recordBooking(response, success, conflict)
    bookingSuccessRate.add(success)
    countServerError(bookingPeakErrors, response, conflict)
    if (success) bookingSuccessLatency.add(response.timings.duration)
  }

  if (response.status === 200 && typeof payload?.id === 'string') {
    const cancellation = request('PATCH', `/api/bookings/${payload.id}`, token, { status: 'CANCELLED' })
    const cancelled = cancellation.status === 200 && json(cancellation)?.status === 'CANCELLED'
    if (measured) {
      recordCleanup(cancellation, cancelled)
      countServerError(bookingPeakErrors, cancellation)
    } else if (!cancelled) {
      exec.test.abort(`Warm-up cancellation failed with status ${cancellation.status}`)
    }
  }
  if (!measured && !success && !conflict) {
    exec.test.abort(`Warm-up booking failed with status ${response.status}`)
  }
}

function discover(token, index, measured) {
  const hubId = input.hubs[(index + exec.vu.iterationInScenario) % input.hubs.length]
  const list = request('GET', '/api/hubs', token)
  const detail = request('GET', `/api/hubs/${hubId}`, token)
  const hubs = json(list)
  const hub = json(detail)
  const listValid = list.status === 200 && Array.isArray(hubs) &&
    input.hubs.every((id) => hubs.some((item) =>
      item.id === id && typeof item.name === 'string' && Number.isInteger(item.availableKartCount)))
  const detailValid = detail.status === 200 && hub?.id === hubId && typeof hub.address === 'string' &&
    Array.isArray(hub.karts) && hub.karts.length > 0 && hub.karts.every((kart) =>
      typeof kart.id === 'string' && Number.isInteger(kart.batteryLevel) &&
      typeof kart.status === 'string' && typeof kart.selectable === 'boolean')
  if (measured) {
    recordHubList(list, listValid)
    recordHubDetail(detail, detailValid)
    countServerError(discoveryPeakErrors, list)
    countServerError(discoveryPeakErrors, detail)
  } else if (!listValid || !detailValid) {
    exec.test.abort(`Warm-up discovery failed with statuses ${list.status}/${detail.status}`)
  }
}

export default function () {
  if (suite === 'health') {
    const response = request('GET', '/actuator/health')
    const up = response.status === 200 && json(response)?.status === 'UP'
    recordBrowse(response, up)
    healthAvailability.add(up)
    return
  }
  const index = exec.vu.idInTest - 1
  const measured = Date.now() - exec.scenario.startTime >= warmupMs
  if (suite === 'booking') book(input.tokens[index], index, measured)
  else discover(input.tokens[index], index, measured)
  sleep(0.25)
}

export function handleSummary(data) {
  return writeSummary(suite, data)
}
