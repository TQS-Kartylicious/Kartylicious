import { sleep } from 'k6'
import exec from 'k6/execution'
import { Trend } from 'k6/metrics'
import { json, request, requestMetrics, summaryTrendStats, writeSummary } from './common.js'

const suite = __ENV.SUITE
const input = JSON.parse(open('/performance/input.json'))
const contenders = 20
const dispatchOffset = new Trend('dispatch_offset_ms', true)
const recordRequest = requestMetrics('burst')

export const options = {
  scenarios: { contention: {
    executor: 'per-vu-iterations', vus: contenders, iterations: 1, maxDuration: '15s',
  } },
  thresholds: {
    burst_requests: ['count==20'],
    burst_successes: ['count==1'],
    burst_conflicts: ['count==19'],
    burst_unexpected: ['count==0'],
    dispatch_offset_ms: ['min>=0', 'max<250'],
  },
  summaryTrendStats,
}

export function setup() {
  return { dispatchAt: Date.now() + 3000 }
}

export default function ({ dispatchAt }) {
  const index = exec.vu.idInTest - 1
  const booking = suite === 'booking-contention'
  const path = booking ? '/api/bookings' : `/api/rides/${input.rides[index]}`
  const body = booking
    ? { hubId: 'perf-hub-5', kartId: 'perf-race', ...input.window }
    : { hubId: 'perf-return' }
  const remaining = dispatchAt - Date.now()
  if (remaining > 0) sleep(remaining / 1000)
  dispatchOffset.add(Date.now() - dispatchAt)
  const response = request(booking ? 'POST' : 'PATCH', path, input.tokens[index], body)
  const payload = json(response)
  const success = response.status === 200 && (booking
    ? payload?.status === 'ACTIVE' && payload.kartId === 'perf-race'
    : payload?.status === 'COMPLETED' && payload.id === input.rides[index] &&
      payload.endHubId === 'perf-return')
  const expected = booking ? 'The kart is no longer available.' : 'The hub is full'
  recordRequest(response, success, response.status === 409 && payload?.message === expected)
}

export function handleSummary(data) {
  return writeSummary(suite, data)
}
