import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../AuthContext'

const DEMO_PASSWORD = 'Password@123'

const DEMO_USERS = [
  { name: 'Bharath', role: 'Member', email: 'bharath@abc.com' },
  { name: 'Sneha', role: 'Tenant admin', email: 'sneha@abc.com' },
  { name: 'Meghana', role: 'Org admin', email: 'meghana@xyz.com' },
]

const PREVIEW_COLUMNS = [
  {
    name: 'To Do',
    cards: [
      { title: 'Design dashboard', p: 'high' },
      { title: 'Fix API validation', p: 'urgent' },
    ],
  },
  {
    name: 'In Progress',
    cards: [
      { title: 'Contact form API', p: 'medium' },
      { title: 'Login screen', p: 'high' },
    ],
  },
  {
    name: 'Done',
    cards: [{ title: 'Deploy landing page', p: 'low' }],
  },
]

const FEATURES = ['Real-time sync', 'Multi-tenant', 'Role-based access', 'Conflict-safe edits']

function EyeIcon({ off }) {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor"
      strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M1 12s4-7 11-7 11 7 11 7-4 7-11 7-11-7-11-7z" />
      <circle cx="12" cy="12" r="3" />
      {off && <line x1="3" y1="3" x2="21" y2="21" />}
    </svg>
  )
}

export default function Login() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPw, setShowPw] = useState(false)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  async function handleSubmit(e) {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      await login(email, password)
      navigate('/')
    } catch (err) {
      setError(err.response?.data?.message || 'Invalid email or password')
    } finally {
      setLoading(false)
    }
  }

  function fillDemo(u) {
    setEmail(u.email)
    setPassword(DEMO_PASSWORD)
    setError('')
  }

  return (
    <div className="auth-page">
      <section className="auth-left">
        <div className="auth-brand">
          <span className="brand-dot" />
          Collaborative Kanban
        </div>

        <form className="auth-form" onSubmit={handleSubmit}>
          <h1>Welcome back</h1>
          <p className="auth-sub">Sign in to manage your boards and your team in real time.</p>

          <label>Email</label>
          <input
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="you@company.com"
            required
          />

          <label>Password</label>
          <div className="pw-wrap">
            <input
              type={showPw ? 'text' : 'password'}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="Enter your password"
              required
            />
            <button
              type="button"
              className="pw-toggle"
              onClick={() => setShowPw((v) => !v)}
              aria-label={showPw ? 'Hide password' : 'Show password'}
            >
              <EyeIcon off={showPw} />
            </button>
          </div>

          {error && <div className="error">{error}</div>}

          <button type="submit" disabled={loading}>
            {loading ? 'Signing in...' : 'Sign in'}
          </button>

          <div className="divider"><span>Try a demo account</span></div>

          <div className="demo-grid">
            {DEMO_USERS.map((u) => (
              <button
                type="button"
                key={u.email}
                className="demo-chip"
                onClick={() => fillDemo(u)}
              >
                <strong>{u.name}</strong>
                <span>{u.role}</span>
              </button>
            ))}
          </div>
        </form>

        <div className="auth-foot">© 2026 Collaborative Kanban</div>
      </section>

      <section className="auth-right">
        <div className="auth-glow" />
        <div className="auth-copy">
                     <h2 className="hero-title">
            <span>Collaborative</span>
            <span><em>Kanban</em> board</span>
          </h2>
          <p>
            Boards update live for everyone on your team, with role-based access
            and tenant isolation built in.
          </p>
        </div>

        <div className="pv-stage">
          <div className="pv-window">
            <div className="pv-bar">
              <span /><span /><span />
              <em>Website Development</em>
            </div>
            <div className="pv-cols">
              {PREVIEW_COLUMNS.map((c) => (
                <div className="pv-col" key={c.name}>
                  <h4>{c.name} <b>{c.cards.length}</b></h4>
                  {c.cards.map((card) => (
                    <div className={`pv-card pv-${card.p}`} key={card.title}>
                      {card.title}
                      <i />
                    </div>
                  ))}
                </div>
              ))}
            </div>
          </div>

          <div className="pv-float pv-live">
            <span className="pv-dot" />
            <div>
              <strong>Harika</strong> moved a task
              <small>To Do to In Progress</small>
            </div>
          </div>

          <div className="pv-float pv-stat">
            <small>Completion</small>
            <strong>68%</strong>
            <div className="pv-bar2"><div /></div>
          </div>
        </div>

        <div className="auth-chips">
          {FEATURES.map((f) => (
            <span key={f}>{f}</span>
          ))}
        </div>
      </section>
    </div>
  )
}