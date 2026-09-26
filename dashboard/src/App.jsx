import { useEffect, useRef, useState } from 'react'
import './App.css'

// Audit Service's SSE endpoint. Hardcoded to localhost for this local-dev
// portfolio project -- in a real deployment this would come from an env var.
const STREAM_URL = 'http://localhost:8082/api/v1/compliance/stream'

function formatMoney(amount, currency) {
  try {
    return new Intl.NumberFormat('en-US', { style: 'currency', currency }).format(amount)
  } catch {
    return `${amount} ${currency}`
  }
}

function formatReason(code) {
  // SANCTIONED_COUNTERPARTY_COUNTRY -> "Sanctioned counterparty country"
  const lower = code.toLowerCase().replaceAll('_', ' ')
  return lower.charAt(0).toUpperCase() + lower.slice(1)
}

function AlertCard({ alert }) {
  return (
    <li className="alert-card">
      <div className="alert-card__header">
        <span className="alert-card__trade-id">{alert.tradeId}</span>
        <span className="alert-card__time">
          {new Date(alert.flaggedAt).toLocaleTimeString()}
        </span>
      </div>
      <div className="alert-card__body">
        <div className="alert-card__amount">
          {formatMoney(alert.notionalAmount, alert.currency)}
        </div>
        <div className="alert-card__meta">
          {alert.accountId} &rarr; {alert.counterparty} ({alert.countryCode})
        </div>
      </div>
      <div className="alert-card__reasons">
        {alert.reasons.map((reason) => (
          <span key={reason} className="reason-pill">
            {formatReason(reason)}
          </span>
        ))}
      </div>
    </li>
  )
}

export default function App() {
  const [alerts, setAlerts] = useState([])
  const [status, setStatus] = useState('connecting') // connecting | connected | disconnected
  const eventSourceRef = useRef(null)

  useEffect(() => {
    const source = new EventSource(STREAM_URL)
    eventSourceRef.current = source

    source.onopen = () => setStatus('connected')

    source.addEventListener('compliance-alert', (event) => {
      const alert = JSON.parse(event.data)
      setAlerts((prev) => [alert, ...prev].slice(0, 100)) // keep the most recent 100
    })

    source.onerror = () => {
      // The browser's EventSource retries automatically on its own -- we
      // just reflect connection state, we don't need to reconnect manually.
      setStatus('disconnected')
    }

    return () => source.close()
  }, [])

  return (
    <div className="app">
      <header className="app__header">
        <h1>Compliance Audit Dashboard</h1>
        <div className={`status-badge status-badge--${status}`}>
          <span className="status-badge__dot" />
          {status === 'connected' && 'Live'}
          {status === 'connecting' && 'Connecting...'}
          {status === 'disconnected' && 'Reconnecting...'}
        </div>
      </header>

      <p className="app__subtitle">
        Real-time compliance alerts, pushed the moment a flagged trade is processed
        &mdash; no polling, no refresh.
      </p>

      {alerts.length === 0 ? (
        <div className="empty-state">
          <p>No alerts yet.</p>
          <p className="empty-state__hint">
            Submit a trade that trips a rule (e.g. a sanctioned country, or an amount
            over the configured threshold) through Order Service and it'll appear here
            within moments.
          </p>
        </div>
      ) : (
        <ul className="alert-list">
          {alerts.map((alert, index) => (
            <AlertCard key={`${alert.tradeId}-${index}`} alert={alert} />
          ))}
        </ul>
      )}
    </div>
  )
}
