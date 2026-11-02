import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import Header from '../components/Header'
import api from '../api'

export default function Dashboard() {
  const [orgs, setOrgs] = useState([])
  const [error, setError] = useState('')

  useEffect(() => {
    api
      .get('/organizations')
      .then((res) => setOrgs(res.data))
      .catch(() => setError('Failed to load organizations'))
  }, [])

  return (
    <div className="page">
      <Header />
      <main>
        <h2>Your organizations</h2>
        {error && <div className="error">{error}</div>}
        {orgs.map((o) => (
          <Link to={`/orgs/${o.id}`} className="card row link" key={o.id}>
            <span>{o.name}</span>
            <span className="badge">{o.role}</span>
          </Link>
        ))}
      </main>
    </div>
  )
}