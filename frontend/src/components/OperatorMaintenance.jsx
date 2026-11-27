import { useEffect, useState } from 'react'
import { getFaults } from '../api/faultsApi.js'
import { useAuth } from '../auth/AuthContext.jsx'

export default function OperatorMaintenance() {
  const { token } = useAuth()
  const [records, setRecords] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    let current = true
    Promise.all([
      getFaults('/api/fault-reports', token),
      getFaults('/api/maintenance-tickets', token),
    ]).then(([reports, tickets]) => {
      if (current) setRecords({ reports, tickets })
    }).catch((loadError) => {
      if (current) setError(loadError.message)
    })
    return () => { current = false }
  }, [token])

  if (error) return <p className="hub-message hub-message--error" role="alert">{error}</p>
  if (!records) return <p role="status">Loading maintenance records…</p>

  return (
    <>
      <h3>Fault reports</h3>
      {records.reports.length === 0 ? <p>No fault reports.</p> : (
        <ul>
          {records.reports.map((report) => (
            <li key={report.id}>
              <span>{report.kartId} · {report.category}</span>
              <span>{report.description}</span>
            </li>
          ))}
        </ul>
      )}
      <h3>Urgent maintenance tickets</h3>
      {records.tickets.length === 0 ? <p>No urgent maintenance tickets.</p> : (
        <ul>
          {records.tickets.map((ticket) => (
            <li key={ticket.id}>
              <span>{ticket.kartId} · {ticket.status}</span>
              <time dateTime={ticket.openedAt}>{new Date(ticket.openedAt).toLocaleString()}</time>
            </li>
          ))}
        </ul>
      )}
    </>
  )
}
