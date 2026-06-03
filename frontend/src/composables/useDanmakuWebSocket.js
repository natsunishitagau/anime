import { ref, onUnmounted } from 'vue'

export function useDanmakuWebSocket(videoIdRef, onDanmakuReceived, onDanmakuDeleted) {
  const isConnected = ref(false)
  const onlineCount = ref(0)
  let ws = null
  let reconnectTimer = null
  let heartbeatTimer = null
  const RECONNECT_DELAY = 3000
  const HEARTBEAT_INTERVAL = 30000

  function connect() {
    const videoId = videoIdRef.value
    if (!videoId) return

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const host = window.location.host
    const url = `${protocol}//${host}/ws/danmaku/${videoId}`

    ws = new WebSocket(url)

    ws.onopen = () => {
      isConnected.value = true
      startHeartbeat()
    }

    ws.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data)
        if (data.type === 'heartbeat') {
          onlineCount.value = data.onlineCount || 0
          return
        }
        if (data.type === 'online_count') {
          onlineCount.value = data.count || 0
          return
        }
        if (data.type === 'delete') {
          if (onDanmakuDeleted) onDanmakuDeleted(data.danmakuId)
          return
        }
        onDanmakuReceived(data)
      } catch (e) {
        console.error('Failed to parse danmaku message:', e)
      }
    }

    ws.onclose = () => {
      isConnected.value = false
      stopHeartbeat()
      scheduleReconnect()
    }

    ws.onerror = (err) => {
      console.error('WebSocket error:', err)
    }
  }

  function disconnect() {
    if (reconnectTimer) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
    stopHeartbeat()
    if (ws) {
      ws.close()
      ws = null
    }
  }

  function scheduleReconnect() {
    if (reconnectTimer) return
    reconnectTimer = setTimeout(() => {
      reconnectTimer = null
      if (videoIdRef.value) {
        connect()
      }
    }, RECONNECT_DELAY)
  }

  function startHeartbeat() {
    stopHeartbeat()
    heartbeatTimer = setInterval(() => {
      if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({ type: 'heartbeat' }))
      }
    }, HEARTBEAT_INTERVAL)
  }

  function stopHeartbeat() {
    if (heartbeatTimer) {
      clearInterval(heartbeatTimer)
      heartbeatTimer = null
    }
  }

  onUnmounted(() => {
    disconnect()
  })

  return {
    isConnected,
    onlineCount,
    connect,
    disconnect
  }
}