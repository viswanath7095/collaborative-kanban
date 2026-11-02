import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { DragDropContext, Droppable, Draggable } from '@hello-pangea/dnd'
import Header from '../components/Header'
import TaskModal from '../components/TaskModal'
import ColumnModal from '../components/ColumnModal'
import ActivityPanel from '../components/ActivityPanel'
import { useAuth } from '../AuthContext'
import useTenantSocket from '../useTenantSocket'
import api from '../api'

const PRIORITY_CLASS = {
  LOW: 'p-low',
  MEDIUM: 'p-medium',
  HIGH: 'p-high',
  URGENT: 'p-urgent',
}

function formatDate(iso) {
  return new Date(iso).toLocaleDateString('en-IN', { day: 'numeric', month: 'short' })
}

function sortTasks(list) {
  return [...list].sort(
    (a, b) => a.position.localeCompare(b.position) || a.id.localeCompare(b.id)
  )
}

function upsertTask(list, task) {
  const i = list.findIndex((t) => t.id === task.id)
  if (i === -1) return sortTasks([...list, task])
  if (list[i].version >= task.version) return list
  const copy = [...list]
  copy[i] = task
  return sortTasks(copy)
}
const AVATAR_COLORS = ['#112638', '#c2540a', '#1d4e89', '#067647', '#7a3e9d', '#b42318']

function colorFor(id) {
  let h = 0
  for (const ch of id) h = (h * 31 + ch.charCodeAt(0)) >>> 0
  return AVATAR_COLORS[h % AVATAR_COLORS.length]
}
export default function Board() {
  const { orgId, tenantId, boardId } = useParams()
  const { user } = useAuth()
  const [board, setBoard] = useState(null)
  const [tasks, setTasks] = useState([])
  const [members, setMembers] = useState([])
  const [membersLoaded, setMembersLoaded] = useState(false)
  const [online, setOnline] = useState([])
  const [modal, setModal] = useState(null) // { mode: 'create'|'edit', task?, column? }
  const [columnModal, setColumnModal] = useState(null) // { mode: 'create'|'rename', column? }
  const [showActivity, setShowActivity] = useState(false)
  const [activityTick, setActivityTick] = useState(0)
  const [search, setSearch] = useState('')
  const [fPriority, setFPriority] = useState('ALL')
  const [fAssignee, setFAssignee] = useState('ALL') // ALL | ME | UNASSIGNED | userId
  const [onlyOverdue, setOnlyOverdue] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [loading, setLoading] = useState(true)
  const onlineRef = useRef([])
  const connectedOnce = useRef(false)

  const base = `/organizations/${orgId}/tenants/${tenantId}/boards`
  const tasksPath = `${base}/${boardId}/tasks`
  const columnsPath = `${base}/${boardId}/columns`

  useEffect(() => {
    onlineRef.current = online
  }, [online])

  const load = useCallback(() => {
    return Promise.all([api.get(base), api.get(`${base}/${boardId}/tasks`)])
      .then(([boardsRes, tasksRes]) => {
        const found = boardsRes.data.find((b) => b.id === boardId)
        if (!found) {
          setError('Board not found')
          setBoard(null)
        } else {
          setError('')
          setBoard(found)
          setTasks(sortTasks(tasksRes.data))
        }
      })
      .catch((err) =>
        setError(err.response?.status === 403 ? 'Access denied' : 'Failed to load board')
      )
  }, [base, boardId])

  const loadPresence = useCallback(() => {
    return api
      .get(`/organizations/${orgId}/tenants/${tenantId}/presence`)
      .then((res) =>
        setOnline((cur) => {
          const merged = [...res.data]
          cur.forEach((u) => {
            if (!merged.some((m) => m.userId === u.userId)) merged.push(u)
          })
          return merged
        })
      )
      .catch(() => {})
  }, [orgId, tenantId])

  useEffect(() => {
    load().finally(() => setLoading(false))
  }, [load])

  useEffect(() => {
    api
      .get(`/organizations/${orgId}/tenants/${tenantId}/members`)
      .then((res) => setMembers(res.data))
      .catch(() => setMembers([]))
      .finally(() => setMembersLoaded(true))
  }, [orgId, tenantId])

  useEffect(() => {
    if (!notice) return
    const t = setTimeout(() => setNotice(''), 4000)
    return () => clearTimeout(t)
  }, [notice])

  const handleEvent = useCallback(
    (ev) => {
      if (ev.type === 'socket:connected') {
        loadPresence()
        if (connectedOnce.current) load()
        connectedOnce.current = true
        return
      }

      if (ev.type === 'presence:online') {
        const { userId, name } = ev.data
        setOnline((cur) =>
          cur.some((u) => u.userId === userId) ? cur : [...cur, { userId, name }]
        )
        return
      }
      if (ev.type === 'presence:offline') {
        setOnline((cur) => cur.filter((u) => u.userId !== ev.data.userId))
        return
      }

      setActivityTick((n) => n + 1)
      if (ev.boardId !== boardId) return

      const mine = ev.actorId === user.userId
      const who =
        onlineRef.current.find((u) => u.userId === ev.actorId)?.name || 'Someone'

      switch (ev.type) {
        case 'task:created':
          setTasks((cur) => upsertTask(cur, ev.data.task))
          if (!mine) setNotice(`${who} created "${ev.data.task.title}"`)
          break
        case 'task:updated':
          setTasks((cur) => upsertTask(cur, ev.data.task))
          if (!mine) setNotice(`${who} updated "${ev.data.task.title}"`)
          break
        case 'task:moved':
          setTasks((cur) => upsertTask(cur, ev.data.task))
          if (!mine) setNotice(`${who} moved "${ev.data.task.title}"`)
          break
        case 'task:deleted':
          setTasks((cur) => cur.filter((t) => t.id !== ev.data.taskId))
          setModal((m) => (m && m.task && m.task.id === ev.data.taskId ? null : m))
          if (!mine) setNotice(`${who} deleted a task`)
          break
        case 'column:created':
          load()
          if (!mine) setNotice(`${who} added a column`)
          break
        case 'column:updated':
          load()
          if (!mine) setNotice(`${who} renamed a column`)
          break
        case 'column:deleted':
          load()
          setColumnModal((m) =>
            m && m.column && m.column.id === ev.data.columnId ? null : m
          )
          if (!mine) setNotice(`${who} deleted a column`)
          break
        case 'board:updated':
          load()
          break
        case 'board:deleted':
          setBoard(null)
          setError('This board was deleted')
          break
        default:
          break
      }
    },
    [boardId, user.userId, load, loadPresence]
  )

  const connected = useTenantSocket(tenantId, handleEvent)

  const columns = board
    ? [...board.columns].sort((a, b) => a.position.localeCompare(b.position))
    : []

  const myRole = members.find((m) => m.userId === user.userId)?.role
  const isManager = membersLoaded && myRole !== 'MEMBER'

  function canDelete(task) {
    return myRole !== 'MEMBER' || task.createdBy === user.userId
  }

  function memberName(id) {
    if (id === user.userId) return 'Me'
    return members.find((m) => m.userId === id)?.name || 'Assigned'
  }

     function colName(columnId) {
    return board?.columns.find((c) => c.id === columnId)?.name
  }

  function isOverdue(t) {
    return !!t.dueDate && new Date(t.dueDate) < new Date() && colName(t.columnId) !== 'Done'
  }

  function personName(id) {
    if (id === user.userId) return user.name
    return members.find((m) => m.userId === id)?.name || '?'
  }

  const filtersActive =
    search.trim() !== '' || fPriority !== 'ALL' || fAssignee !== 'ALL' || onlyOverdue

  function clearFilters() {
    setSearch('')
    setFPriority('ALL')
    setFAssignee('ALL')
    setOnlyOverdue(false)
  }

  function matches(t) {
    const q = search.trim().toLowerCase()
    if (q && !(t.title.toLowerCase().includes(q) || (t.description || '').toLowerCase().includes(q))) {
      return false
    }
    if (fPriority !== 'ALL' && t.priority !== fPriority) return false
    if (fAssignee === 'ME' && t.assignedTo !== user.userId) return false
    if (fAssignee === 'UNASSIGNED' && t.assignedTo) return false
    if (!['ALL', 'ME', 'UNASSIGNED'].includes(fAssignee) && t.assignedTo !== fAssignee) return false
    if (onlyOverdue && !isOverdue(t)) return false
    return true
  }

  function tasksFor(columnId) {
    return tasks.filter((t) => t.columnId === columnId && matches(t))
  }

  const stats = {
    total: tasks.length,
    inProgress: tasks.filter((t) => colName(t.columnId) === 'In Progress').length,
    done: tasks.filter((t) => colName(t.columnId) === 'Done').length,
    overdue: tasks.filter(isOverdue).length,
  }
  const pct = stats.total ? Math.round((stats.done / stats.total) * 100) : 0

  function handleSaved(task) {
    const creating = modal?.mode === 'create'
    setTasks((cur) => upsertTask(cur, task))
    setModal(null)
    setNotice(creating ? 'Task created' : 'Task updated')
  }

  function handleDeleted(taskId) {
    setTasks((cur) => cur.filter((t) => t.id !== taskId))
    setModal(null)
    setNotice('Task deleted')
  }

  function handleConflict() {
    setModal(null)
    setNotice('Someone else changed this task. Board reloaded with the latest data.')
    load()
  }

  function handleColumnChanged(message) {
    setColumnModal(null)
    setNotice(message)
    load()
  }

  async function onDragEnd(result) {
    const { destination, source, draggableId } = result
    if (!destination) return
    if (destination.droppableId === source.droppableId && destination.index === source.index) {
      return
    }

    const moving = tasks.find((t) => t.id === draggableId)
    if (!moving) return

    const toColumnId = destination.droppableId
    const destList = tasks.filter((t) => t.columnId === toColumnId && t.id !== moving.id)
    const prev = destList[destination.index - 1] || null
    const next = destList[destination.index] || null

    const without = tasks.filter((t) => t.id !== moving.id)
    const moved = { ...moving, columnId: toColumnId }
    let insertAt = without.length
    if (next) {
      insertAt = without.findIndex((t) => t.id === next.id)
    } else if (prev) {
      insertAt = without.findIndex((t) => t.id === prev.id) + 1
    }
    const snapshot = tasks
    setTasks([...without.slice(0, insertAt), moved, ...without.slice(insertAt)])

    try {
      const { data } = await api.patch(`${tasksPath}/${moving.id}/move`, {
        toColumnId,
        prevTaskId: prev ? prev.id : null,
        nextTaskId: next ? next.id : null,
        expectedVersion: moving.version,
      })
      setTasks((cur) => upsertTask(cur, data))
    } catch (err) {
      setTasks(snapshot)
      const status = err.response?.status
      if (status === 409) {
        setNotice('Someone else changed this task. Board reloaded with the latest data.')
        load()
      } else if (status === 403) {
        setNotice('You do not have permission to move this task.')
      } else {
        setNotice('Could not move the task. Please try again.')
      }
    }
  }

  return (
    <div className="page">
      <Header />
      <main className="wide">
        <div className="crumbs">
          <Link to="/">Organizations</Link> /{' '}
          <Link to={`/orgs/${orgId}`}>Tenants</Link> /{' '}
          <Link to={`/orgs/${orgId}/tenants/${tenantId}`}>Boards</Link> /{' '}
          <span>{board ? board.name : '...'}</span>
        </div>

        {loading && <p className="muted">Loading...</p>}
        {error && <div className="error">{error}</div>}
        {notice && <div className="notice">{notice}</div>}

        {board && (
          <>
            <div className="board-head">
              <h2>{board.name}</h2>
              <div className="presence">
                <button className="small ghost" onClick={() => setShowActivity((v) => !v)}>
                  Activity
                </button>
                <span className={`live ${connected ? 'on' : ''}`}>
                  <span className="dot" /> {connected ? 'Live' : 'Reconnecting...'}
                </span>
                <span className="muted">Online:</span>
                {online.map((u) => (
                  <span
                    className="avatar"
                    key={u.userId}
                    title={u.userId === user.userId ? `${u.name} (you)` : u.name}
                  >
                    {u.name.charAt(0).toUpperCase()}
                  </span>
                ))}
              </div>
            </div>
                            <div className="stats">
              <div className="stat">
                <span className="stat-num">{stats.total}</span>
                <span className="stat-label">Total tasks</span>
              </div>
              <div className="stat">
                <span className="stat-num">{stats.inProgress}</span>
                <span className="stat-label">In progress</span>
              </div>
              <div className="stat">
                <span className="stat-num">{stats.done}</span>
                <span className="stat-label">Done</span>
              </div>
              <div className="stat">
                <span className={`stat-num ${stats.overdue ? 'alert' : ''}`}>{stats.overdue}</span>
                <span className="stat-label">Overdue</span>
              </div>
              <div className="stat progress">
                <span className="stat-label">Completion {pct}%</span>
                <div className="bar">
                  <div className="bar-fill" style={{ width: `${pct}%` }} />
                </div>
              </div>
            </div>

            <div className="filters">
              <input
                className="search"
                placeholder="Search tasks..."
                value={search}
                onChange={(e) => setSearch(e.target.value)}
              />
              <select value={fPriority} onChange={(e) => setFPriority(e.target.value)}>
                <option value="ALL">All priorities</option>
                <option value="URGENT">Urgent</option>
                <option value="HIGH">High</option>
                <option value="MEDIUM">Medium</option>
                <option value="LOW">Low</option>
              </select>
              <select value={fAssignee} onChange={(e) => setFAssignee(e.target.value)}>
                <option value="ALL">Everyone</option>
                <option value="ME">My tasks</option>
                <option value="UNASSIGNED">Unassigned</option>
                {members.map((m) => (
                  <option key={m.userId} value={m.userId}>{m.name}</option>
                ))}
              </select>
              <label className="chk">
                <input
                  type="checkbox"
                  checked={onlyOverdue}
                  onChange={(e) => setOnlyOverdue(e.target.checked)}
                />
                Overdue only
              </label>
              {filtersActive && (
                <button className="secondary small" onClick={clearFilters}>Clear</button>
              )}
            </div>
            {filtersActive && (
              <p className="muted">Filters are on, so drag and drop is paused.</p>
            )}
            <DragDropContext onDragEnd={onDragEnd}>
              <div className="kanban">
                {columns.map((col) => {
                  const colTasks = tasksFor(col.id)
                  return (
                    <div className="kcol" key={col.id}>
                      <div className="kcol-head">
                        <span>{col.name}</span>
                        <span className="head-right">
                          <span className="count">{colTasks.length}</span>
                          {isManager && (
                            <button
                              className="icon-btn"
                              title="Edit column"
                              onClick={() => setColumnModal({ mode: 'rename', column: col })}
                            >
                              ✎
                            </button>
                          )}
                        </span>
                      </div>

                      <Droppable droppableId={col.id}>
                        {(provided, snapshot) => (
                          <div
                            ref={provided.innerRef}
                            {...provided.droppableProps}
                            className={`kdrop ${snapshot.isDraggingOver ? 'over' : ''}`}
                          >
                            {colTasks.map((t, index) => {
                              const overdue =
                                t.dueDate &&
                                new Date(t.dueDate) < new Date() &&
                                col.name !== 'Done'
                              return (
                        <Draggable key={t.id}
                                  draggableId={t.id}
                                  index={index}
                                  isDragDisabled={filtersActive}
                                >
                                  {(prov, snap) => (
                                    <div
                                      ref={prov.innerRef}
                                      {...prov.draggableProps}
                                      {...prov.dragHandleProps}
                                      style={prov.draggableProps.style}
                                      onClick={() => setModal({ mode: 'edit', task: t })}
                                      className={`tcard ${overdue ? 'overdue' : ''} ${
                                        snap.isDragging ? 'dragging' : ''
                                      }`}
                                    >
                                      <div className="ttitle">{t.title}</div>
                                      {t.description && (
                                        <div className="tdesc">{t.description}</div>
                                      )}
                                      <div className="tmeta">
                                        <span className={`prio ${PRIORITY_CLASS[t.priority]}`}>
                                          {t.priority}
                                        </span>
                                        {t.dueDate && (
                                          <span className={overdue ? 'due red' : 'due'}>
                                            {overdue ? 'Overdue ' : 'Due '}
                                            {formatDate(t.dueDate)}
                                          </span>
                                        )}
                                                                                 {t.assignedTo && (
                                          <span className="assignee" title={personName(t.assignedTo)}>
                                            <span
                                              className="mini-avatar"
                                              style={{ background: colorFor(t.assignedTo) }}
                                            >
                                              {personName(t.assignedTo).charAt(0).toUpperCase()}
                                            </span>
                                            {memberName(t.assignedTo)}
                                          </span>
                                        )}
                                      </div>
                                    </div>
                                  )}
                                </Draggable>
                              )
                            })}
                            {provided.placeholder}
                            {colTasks.length === 0 && !snapshot.isDraggingOver && (
                              <p className="muted empty">No tasks</p>
                            )}
                          </div>
                        )}
                      </Droppable>

                      <button
                        className="add-task"
                        onClick={() => setModal({ mode: 'create', column: col })}
                      >
                        + Add task
                      </button>
                    </div>
                  )
                })}

                {isManager && (
                  <button
                    className="add-column"
                    onClick={() => setColumnModal({ mode: 'create' })}
                  >
                    + Add column
                  </button>
                )}
              </div>
            </DragDropContext>
          </>
        )}
      </main>

      {showActivity && (
        <ActivityPanel
          orgId={orgId}
          tenantId={tenantId}
          refreshKey={activityTick}
          tasks={tasks}
          onClose={() => setShowActivity(false)}
        />
      )}

      {modal && (
        <TaskModal
          key={modal.task ? modal.task.id + modal.task.version : modal.column.id}
          mode={modal.mode}
          task={modal.task}
          columnId={modal.column?.id}
          columnName={modal.column?.name}
          members={members}
          tasksPath={tasksPath}
          canDelete={modal.task ? canDelete(modal.task) : false}
          onClose={() => setModal(null)}
          onSaved={handleSaved}
          onDeleted={handleDeleted}
          onConflict={handleConflict}
        />
      )}

      {columnModal && (
        <ColumnModal
          key={columnModal.column ? columnModal.column.id + columnModal.column.version : 'new'}
          mode={columnModal.mode}
          column={columnModal.column}
          columnsPath={columnsPath}
          onClose={() => setColumnModal(null)}
          onChanged={handleColumnChanged}
        />
      )}
    </div>
  )
}