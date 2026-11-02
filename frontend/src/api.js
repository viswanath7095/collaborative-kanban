import axios from 'axios'

// In dev the Vite proxy forwards /api. In production, set VITE_API_URL to the backend URL.
const API_URL = import.meta.env.VITE_API_URL
const api = axios.create({ baseURL: API_URL ? `${API_URL}/api` : '/api' })

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

api.interceptors.response.use(
  (res) => res,
  (err) => {
    const isLogin = err.config?.url?.includes('/auth/login')
    if (err.response?.status === 401 && !isLogin) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      window.location.href = '/login'
    }
    return Promise.reject(err)
  }
)

export default api