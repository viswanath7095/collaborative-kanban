import { Routes, Route, Navigate } from 'react-router-dom'
import { useAuth } from './AuthContext'
import Login from './pages/Login'
import Dashboard from './pages/Dashboard'
import Tenants from './pages/Tenants'
import Boards from './pages/Boards'
import Board from './pages/Board'

function PrivateRoute({ children }) {
  const { user } = useAuth()
  return user ? children : <Navigate to="/login" replace />
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/" element={<PrivateRoute><Dashboard /></PrivateRoute>} />
      <Route path="/orgs/:orgId" element={<PrivateRoute><Tenants /></PrivateRoute>} />
      <Route
        path="/orgs/:orgId/tenants/:tenantId"
        element={<PrivateRoute><Boards /></PrivateRoute>}
      />
      <Route
        path="/orgs/:orgId/tenants/:tenantId/boards/:boardId"
        element={<PrivateRoute><Board /></PrivateRoute>}
      />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}