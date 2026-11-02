import { useNavigate, Link } from 'react-router-dom'
import { useAuth } from '../AuthContext'

export default function Header() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  function handleLogout() {
    logout()
    navigate('/login')
  }

  return (
    <header className="topbar">
      <Link to="/" className="brand">Collaborative Kanban</Link>
      <div>
        <span className="muted">{user.name} ({user.email})</span>
        <button className="small" onClick={handleLogout}>Logout</button>
      </div>
    </header>
  )
}