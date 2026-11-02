import { useState } from 'react'
import api from '../api'

const PRIORITIES = ['LOW', 'MEDIUM', 'HIGH', 'URGENT']

function toDateInput(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day}`
}

export default function TaskModal({
  mode,
  task,
  columnId,
  columnName,
  members,
  tasksPath,
  canDelete,
  onClose,
  onSaved,
  onDeleted,
  onConflict,
}) {
  const editing = mode === 'edit'
  const [title, setTitle] = useState(editing ? task.title : '')
  const [description, setDescription] = useState(editing ? task.description || '' : '')
  const [priority, setPriority] = useState(editing ? task.priority : 'MEDIUM')
  const [dueDate, setDueDate] = useState(editing ? toDateInput(task.dueDate) : '')
  const [assignedTo, setAssignedTo] = useState(editing ? task.assignedTo || '' : '')
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)
  const [confirmDelete, setConfirmDelete] = useState(false)

  function dueIso() {
    if (!dueDate) return null
    // keep the original value if the date was not changed (avoids a pointless update)
    if (editing && task.dueDate && dueDate === toDateInput(task.dueDate)) return task.dueDate
    return new Date(`${dueDate}T00:00:00`).toISOString()
  }

  function handleError(err) {
    const status = err.response?.status
    if (status === 409) {
      onConflict()
    } else if (status === 403) {
      setError('You do not have permission to do this.')
    } else if (status === 400) {
      setError('Please check the fields (title is required, max 255 characters).')
    } else {
      setError('Something went wrong. Please try again.')
    }
  }

  async function handleSubmit(e) {
    e.preventDefault()
    if (!title.trim()) {
      setError('Title is required')
      return
    }
    setSaving(true)
    setError('')
    const payload = {
      title,
      description: description.trim() ? description : null,
      priority,
      dueDate: dueIso(),
      assignedTo: assignedTo || null,
    }
    try {
      if (editing) {
        const { data } = await api.put(`${tasksPath}/${task.id}`, {
          ...payload,
          expectedVersion: task.version,
        })
        onSaved(data)
      } else {
        const { data } = await api.post(tasksPath, { ...payload, columnId })
        onSaved(data)
      }
    } catch (err) {
      handleError(err)
      setSaving(false)
    }
  }

  async function handleDelete() {
    setSaving(true)
    setError('')
    try {
      await api.delete(`${tasksPath}/${task.id}`)
      onDeleted(task.id)
    } catch (err) {
      handleError(err)
      setConfirmDelete(false)
      setSaving(false)
    }
  }

  const assigneeKnown = !assignedTo || members.some((m) => m.userId === assignedTo)

  return (
    <div className="overlay" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <form className="card modal" onSubmit={handleSubmit}>
        <h2>{editing ? 'Edit task' : `New task in "${columnName}"`}</h2>

        <label>Title</label>
        <input
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          maxLength={255}
          autoFocus
        />

        <label>Description</label>
        <textarea
          rows={3}
          value={description}
          onChange={(e) => setDescription(e.target.value)}
        />

        <div className="two-col">
          <div>
            <label>Priority</label>
            <select value={priority} onChange={(e) => setPriority(e.target.value)}>
              {PRIORITIES.map((p) => (
                <option key={p} value={p}>{p}</option>
              ))}
            </select>
          </div>
          <div>
            <label>Due date</label>
            <input type="date" value={dueDate} onChange={(e) => setDueDate(e.target.value)} />
          </div>
        </div>

        <label>Assignee</label>
        <select value={assignedTo} onChange={(e) => setAssignedTo(e.target.value)}>
          <option value="">Unassigned</option>
          {members.map((m) => (
            <option key={m.userId} value={m.userId}>{m.name}</option>
          ))}
          {!assigneeKnown && <option value={assignedTo}>Current assignee</option>}
        </select>

        {error && <div className="error">{error}</div>}

        <div className="modal-actions">
          {editing && canDelete && !confirmDelete && (
            <button type="button" className="danger" onClick={() => setConfirmDelete(true)}>
              Delete
            </button>
          )}
          {editing && canDelete && confirmDelete && (
            <button type="button" className="danger" disabled={saving} onClick={handleDelete}>
              Confirm delete
            </button>
          )}
          <span className="spacer" />
          <button type="button" className="secondary" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" disabled={saving}>
            {saving ? 'Saving...' : editing ? 'Save' : 'Create'}
          </button>
        </div>
      </form>
    </div>
  )
}