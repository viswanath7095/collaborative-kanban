import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import Header from '../components/Header'
import api from '../api'

export default function Tenants() {
  const { orgId } = useParams()
  const [tenants, setTenants] = useState([])
  const [error, setError] = useState('')

  useEffect(() => {
    api
      .get(`/organizations/${orgId}/tenants`)
      .then((res) => setTenants(res.data))
      .catch((err) =>
        setError(err.response?.status === 403 ? 'Access denied' : 'Failed to load tenants')
      )
  }, [orgId])

  return (
    <div className="page">
      <Header />
      <main>
        <div className="crumbs">
          <Link to="/">Organizations</Link> / <span>Tenants</span>
        </div>
        <h2>Tenants</h2>
        {error && <div className="error">{error}</div>}
        {tenants.map((t) => (
          <Link to={`/orgs/${orgId}/tenants/${t.id}`} className="card row link" key={t.id}>
            <span>{t.name}</span>
            <span className="badge">{t.role}</span>
          </Link>
        ))}
        {!error && tenants.length === 0 && <p className="muted">No tenants found.</p>}
      </main>
    </div>
  )
}