import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import Header from '../components/Header'
import api from '../api'

export default function Boards() {
  const { orgId, tenantId } = useParams()
  const [boards, setBoards] = useState([])
  const [error, setError] = useState('')

  useEffect(() => {
    api
      .get(`/organizations/${orgId}/tenants/${tenantId}/boards`)
      .then((res) => setBoards(res.data))
      .catch((err) =>
        setError(err.response?.status === 403 ? 'Access denied' : 'Failed to load boards')
      )
  }, [orgId, tenantId])

  return (
    <div className="page">
      <Header />
      <main>
        <div className="crumbs">
          <Link to="/">Organizations</Link> /{' '}
          <Link to={`/orgs/${orgId}`}>Tenants</Link> / <span>Boards</span>
        </div>
        <h2>Boards</h2>
        {error && <div className="error">{error}</div>}
        <div className="board-grid">
          {boards.map((b) => (
            <Link
              to={`/orgs/${orgId}/tenants/${tenantId}/boards/${b.id}`}
              className="card board-card link"
              key={b.id}
            >
              <h3>{b.name}</h3>
              <p className="muted">{b.description || 'No description'}</p>
              <div className="col-chips">
                {b.columns.map((c) => (
                  <span className="badge" key={c.id}>{c.name}</span>
                ))}
              </div>
            </Link>
          ))}
        </div>
        {!error && boards.length === 0 && <p className="muted">No boards found.</p>}
      </main>
    </div>
  )
}