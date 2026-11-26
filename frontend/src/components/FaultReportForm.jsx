import { useState } from 'react'
import { submitFault } from '../api/faultsApi.js'

export default function FaultReportForm({ kartId, token, onCancel, onSubmitted }) {
  const [category, setCategory] = useState('NORMAL')
  const [description, setDescription] = useState('')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  async function submit(event) {
    event.preventDefault()
    if (!description.trim()) {
      setError('Describe the issue')
      return
    }
    setSubmitting(true)
    setError('')
    try {
      const result = await submitFault({ kartId, category, description }, token)
      onSubmitted(result.message, category)
    } catch (submitError) {
      setError(submitError.message)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form aria-label={`Report Fault for ${kartId}`} className="booking-panel fault-form" onSubmit={submit}>
      <label>
        Category
        <select value={category} onChange={(event) => setCategory(event.target.value)}>
          <option value="NORMAL">Normal</option>
          <option value="CRITICAL">Critical</option>
        </select>
      </label>
      <label>
        Description
        <textarea
          value={description}
          onChange={(event) => setDescription(event.target.value)}
          aria-invalid={error === 'Describe the issue'}
          aria-required="true"
        />
      </label>
      {error && <p className="hub-message hub-message--error" role="alert">{error}</p>}
      <div className="active-trip__actions">
        <button className="booking-confirm" disabled={submitting} type="submit">Submit</button>
        <button className="trip-start" disabled={submitting} onClick={onCancel} type="button">Cancel</button>
      </div>
    </form>
  )
}
