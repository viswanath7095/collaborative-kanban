import { useEffect, useRef, useState } from 'react'
import { Client } from '@stomp/stompjs'

export default function useTenantSocket(tenantId, onEvent) {
  const [connected, setConnected] = useState(false)
  const handlerRef = useRef(onEvent)

  useEffect(() => {
    handlerRef.current = onEvent
  }, [onEvent])

  useEffect(() => {
    const token = localStorage.getItem('token')
    if (!token || !tenantId) return

    const client = new Client({
      brokerURL: import.meta.env.VITE_WS_URL || 'ws://localhost:5000/ws',
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 3000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        setConnected(true)
        client.subscribe(`/topic/tenants/${tenantId}`, (msg) => {
          try {
            handlerRef.current(JSON.parse(msg.body))
          } catch (e) {
            console.error('Bad realtime message', e)
          }
        })
        handlerRef.current({ type: 'socket:connected' })
      },
      onWebSocketClose: () => setConnected(false),
      onStompError: (frame) => console.error('STOMP error', frame.headers['message']),
    })

    client.activate()
    return () => {
      client.deactivate()
    }
  }, [tenantId])

  return connected
}