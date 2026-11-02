import { useEffect, useState } from 'react'
import api from '../api'

function timeAgo(iso) {
  const secs = Math.max(0, Math.floor((Date.now() - new Date(iso).getTime()) / 1000))
  if (secs < 60) return 'just now'
  const mins = Math.floor(secs / 60)
  if (mins < 60) return `${mins} min ago`
  const hrs = Math.floor(mins / 60)
  if (hrs < 24) return `${hrs} hr ago`
  const days = Math.floor(hrs / 24)
  if (days < 7) return `${days} day${days > 1 ? 's' : ''} ago`
  return new Date(iso).toLocaleDateString('en-IN', { day: 'numeric', month: 'short' })
}

function parseDetails(d) {
  if (!d) return {}
  if (typeof d === 'string') {
    try {
      return JSON.parse(d)
    } catch {
      return {}
    }
  }
  return d
}

function describe(a, titleOf) {
  const d = parseDetails(a.details)
  switch (`${a.entityType}:${a.action}`) {
    case 'TASK:CREATED':
      return `created task "${d.title}" in ${d.column}`
    case 'TASK:UPDATED':
      return `updated ${titleOf(a.entityId)}${d.changed ? ` (${d.changed.split(',').join(', ')})` : ''}`
    case 'TASK:MOVED':
      return `moved "${d.title}" from ${d.fromColumn} to ${d.toColumn}`
    case 'TASK:DELETED':
      return `deleted task "${d.title}"`
    case 'BOARD:CREATED':
      return `created board "${d.name}"`
    case 'BOARD:UPDATED':
            return `updated the board${d.changed ? ` (${d.changed.split(',').join(', ')})` : ''}`
    case 'BOARD:DELETED':
      return `deleted board "${d.name}"`
         case 'COLUMN:CREATED':
      return `added column "${d.name || ''}"`
    case 'COLUMN:UPDATED':
      return `renamed column "${d.oldName}" to "${d.newName}"`
    case 'COLUMN:DELETED':
      return `deleted column "${d.name}"`
    case 'TENANT:UPDATED':
      return 'updated the tenant'
    default:
      return `${a.action.toLowerCase()} ${a.entityType.toLowerCase()}`
  }
}

export default function ActivityPanel({ orgId, tenantId, refreshKey, tasks, onClose }) {
  const [items, setItems] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    api
      .get(`/organizations/${orgId}/tenants/${tenantId}/activity?limit=50`)
      .then((res) => {
        setItems(res.data)
        setError('')
      })
      .catch(() => setError('Failed to load activity'))
      .finally(() => setLoading(false))
  }, [orgId, tenantId, refreshKey])

  function titleOf(taskId) {
    const t = tasks.find((x) => x.id === taskId)
    return t ? `"${t.title}"` : 'a task'
  }

  return (
    <aside className="activity-panel">
      <div className="activity-head">
        <strong>Activity</strong>
        <button className="secondary small" onClick={onClose}>Close</button>
      </div>

      {loading && <p className="muted">Loading...</p>}
      {error && <div className="error">{error}</div>}
      {!loading && !error && items.length === 0 && <p className="muted">No activity yet.</p>}

      <ul className="activity-list">
        {items.map((a) => (
          <li key={a.id}>
            <div>
              <strong>{a.userName || 'Someone'}</strong> {describe(a, titleOf)}
            </div>
            <div className="muted">{timeAgo(a.createdAt)}</div>
          </li>
        ))}
      </ul>
    </aside>
  )
}