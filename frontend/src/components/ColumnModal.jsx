import { useState } from 'react'
import api from '../api'

export default function ColumnModal({ mode, column, columnsPath, onClose, onChanged }) {
  const renaming = mode === 'rename'
  const [name, setName] = useState(renaming ? column.name : '')
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)
  const [confirmDelete, setConfirmDelete] = useState(false)

  function handleError(err, action) {
    const status = err.response?.status
    if (status === 403) {
      setError('Only an organization admin or tenant admin can do this.')
    } else if (status === 409 && action === 'delete') {
      setError('This column still has tasks. Move or delete them first.')
    } else if (status === 409) {
      setError('A column with this name already exists, or it was changed by someone else.')
    } else if (status === 400) {
      setError('Name is required (max 100 characters).')
    } else {
      setError('Something went wrong. Please try again.')
    }
  }

  async function handleSubmit(e) {
    e.preventDefault()
    if (!name.trim()) {
      setError('Name is required')
      return
    }
    setSaving(true)
    setError('')
    try {
      if (renaming) {
        await api.put(`${columnsPath}/${column.id}`, {
          name,
          expectedVersion: column.version,
        })
        onChanged(`Column renamed to "${name.trim()}"`)
      } else {
        await api.post(columnsPath, { name })
        onChanged(`Column "${name.trim()}" added`)
      }
    } catch (err) {
      handleError(err, renaming ? 'rename' : 'create')
      setSaving(false)
    }
  }

  async function handleDelete() {
    setSaving(true)
    setError('')
    try {
      await api.delete(`${columnsPath}/${column.id}`)
      onChanged(`Column "${column.name}" deleted`)
    } catch (err) {
      handleError(err, 'delete')
      setConfirmDelete(false)
      setSaving(false)
    }
  }

  return (
    <div className="overlay" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <form className="card modal" onSubmit={handleSubmit}>
        <h2>{renaming ? 'Edit column' : 'New column'}</h2>

        <label>Name</label>
        <input
          value={name}
          onChange={(e) => setName(e.target.value)}
          maxLength={100}
          autoFocus
        />

        {error && <div className="error">{error}</div>}

        <div className="modal-actions">
          {renaming && !confirmDelete && (
            <button type="button" className="danger" onClick={() => setConfirmDelete(true)}>
              Delete
            </button>
          )}
          {renaming && confirmDelete && (
            <button type="button" className="danger" disabled={saving} onClick={handleDelete}>
              Confirm delete
            </button>
          )}
          <span className="spacer" />
          <button type="button" className="secondary" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" disabled={saving}>
            {saving ? 'Saving...' : renaming ? 'Save' : 'Add'}
          </button>
        </div>
      </form>
    </div>
  )
}