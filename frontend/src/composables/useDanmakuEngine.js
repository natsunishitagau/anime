import { ref } from 'vue'
import axios from '../utils/axios'

const TRACK_COUNT = 12
const DANMAKU_DURATION = 8
const DANMAKU_FONT_SIZE = 25
const DANMAKU_LINE_HEIGHT = 32
const DANMAKU_PADDING = 4
const SEGMENT_DURATION = 60
const PRELOAD_SEGMENTS = 2
const PRUNE_THRESHOLD = 3
const MIN_DANMAKU_GAP = 50

function hexToRgb(hex) {
  if (!hex) return { r: 255, g: 255, b: 255 }
  const result = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex)
  return result ? {
    r: parseInt(result[1], 16),
    g: parseInt(result[2], 16),
    b: parseInt(result[3], 16)
  } : { r: 255, g: 255, b: 255 }
}

export function useDanmakuEngine(videoElementRef, videoIdRef, currentTimeRef, isPausedRef) {
  let canvas = null
  let ctx = null
  let animationId = null
  let containerEl = null
  let dpr = window.devicePixelRatio || 1

  const isVisible = ref(false)
  const isEnabled = ref(true)
  const loadingSegments = ref(new Set())
  const loadedSegments = ref(new Set())
  const danmakuCount = ref(0)
  const trackPool = []
  const activeDanmaku = []
  const danmakuCache = new Map()

  let lastCleanupTime = 0
  const CLEANUP_INTERVAL = 2000

  let lastSegmentCheck = 0
  const SEGMENT_CHECK_THROTTLE = 500

  function init(canvasElement, containerElement) {
    canvas = canvasElement
    containerEl = containerElement
    ctx = canvas.getContext('2d')
    isVisible.value = document.visibilityState === 'visible'
    updateCanvasSize()
    initTracks()
  }

  function updateCanvasSize() {
    if (!canvas || !containerEl) return
    const rect = containerEl.getBoundingClientRect()
    const width = rect.width
    const height = rect.height

    dpr = window.devicePixelRatio || 1
    canvas.width = width * dpr
    canvas.height = height * dpr
    canvas.style.width = width + 'px'
    canvas.style.height = height + 'px'
    ctx.setTransform(1, 0, 0, 1, 0, 0)
    ctx.scale(dpr, dpr)
  }

  function initTracks() {
    trackPool.length = 0
    for (let i = 0; i < TRACK_COUNT; i++) {
      trackPool.push({
        index: i,
        endX: -Infinity,
        danmaku: null
      })
    }
  }

  function getContainerWidth() {
    return containerEl ? containerEl.getBoundingClientRect().width : 800
  }

  function measureTextWidth(text, fontSize) {
    if (!ctx) return text.length * (fontSize || DANMAKU_FONT_SIZE)
    const prevFont = ctx.font
    ctx.font = `${fontSize || DANMAKU_FONT_SIZE}px "Microsoft YaHei", "PingFang SC", sans-serif`
    const width = ctx.measureText(text).width
    ctx.font = prevFont
    return width
  }

  function findAvailableTrack(startX, textWidth) {    
    for (let i = 0; i < trackPool.length; i++) {
      const track = trackPool[i]
      
      if (track.endX < startX - textWidth - MIN_DANMAKU_GAP) {
        return track
      }
    }
    
    let minEndX = Infinity
    let minTrack = null
    let minGap = Infinity
    
    for (let i = 0; i < trackPool.length; i++) {
      const track = trackPool[i]
      const gap = startX - track.endX - textWidth
      
      if (gap > minGap || (gap >= -MIN_DANMAKU_GAP && track.endX < minEndX)) {
        minGap = gap
        minEndX = track.endX
        minTrack = track
      }
    }
    
    return minTrack
  }

  function addDanmaku(danmakuData) {
    if (!isEnabled.value) return
    const containerWidth = getContainerWidth()
    const fontSize = danmakuData.fontSize || DANMAKU_FONT_SIZE
    const textWidth = measureTextWidth(danmakuData.content, fontSize) + DANMAKU_PADDING * 2
    const track = findAvailableTrack(containerWidth, textWidth)
    if (!track) return

    const y = track.index * DANMAKU_LINE_HEIGHT + DANMAKU_LINE_HEIGHT / 2

    const entry = {
      id: danmakuData.id,
      content: danmakuData.content,
      color: danmakuData.color || '#FFFFFF',
      fontSize: fontSize,
      x: containerWidth,
      y: y,
      width: textWidth,
      speed: (containerWidth + textWidth) / (DANMAKU_DURATION * 60),
      track: track.index,
      rgb: hexToRgb(danmakuData.color),
      opacity: 1.0,
      isOwn: danmakuData.isOwn || false
    }

    track.endX = containerWidth + textWidth + MIN_DANMAKU_GAP
    track.danmaku = entry
    activeDanmaku.push(entry)
  }

  function addDanmakuAtPosition(danmakuData, startX) {
    if (!isEnabled.value) return
    const containerWidth = getContainerWidth()
    const fontSize = danmakuData.fontSize || DANMAKU_FONT_SIZE
    const textWidth = measureTextWidth(danmakuData.content, fontSize) + DANMAKU_PADDING * 2
    const track = findAvailableTrack(Math.max(startX, containerWidth - textWidth), textWidth)
    if (!track) return

    const y = track.index * DANMAKU_LINE_HEIGHT + DANMAKU_LINE_HEIGHT / 2

    const entry = {
      id: danmakuData.id,
      content: danmakuData.content,
      color: danmakuData.color || '#FFFFFF',
      fontSize: fontSize,
      x: startX,
      y: y,
      width: textWidth,
      speed: (containerWidth + textWidth) / (DANMAKU_DURATION * 60),
      track: track.index,
      rgb: hexToRgb(danmakuData.color),
      opacity: 1.0,
      isOwn: danmakuData.isOwn || false
    }

    track.endX = startX + textWidth + MIN_DANMAKU_GAP
    track.danmaku = entry
    activeDanmaku.push(entry)
  }

  function restoreVisibleDanmaku() {
    const currentTime = currentTimeRef.value
    const containerWidth = getContainerWidth()

    for (const [id, danmakuData] of danmakuCache) {
      if (activeDanmaku.some(d => d.id === id)) continue
      
      const timeDiff = danmakuData.time - currentTime
      if (timeDiff >= -DANMAKU_DURATION && timeDiff <= 0.5) {
        const fontSize = danmakuData.fontSize || DANMAKU_FONT_SIZE
        const textWidth = measureTextWidth(danmakuData.content, fontSize) + DANMAKU_PADDING * 2
        const totalDistance = containerWidth + textWidth
        const progress = (DANMAKU_DURATION + timeDiff) / DANMAKU_DURATION
        const startX = containerWidth - progress * totalDistance

        if (startX + textWidth > 0 && startX < containerWidth + textWidth) {
          addDanmakuAtPosition(danmakuData, startX)
        }
      }
    }
  }

  function removeDanmaku(danmakuId) {
    const idx = activeDanmaku.findIndex(d => d.id === danmakuId)
    if (idx !== -1) {
      const entry = activeDanmaku[idx]
      activeDanmaku.splice(idx, 1)
      const track = trackPool[entry.track]
      if (track && track.danmaku && track.danmaku.id === danmakuId) {
        track.endX = -Infinity
        track.danmaku = null
      }
    }
  }

  function renderFrame(timestamp) {
    if (!ctx || !canvas) return

    const containerWidth = getContainerWidth()
    const containerHeight = containerEl ? containerEl.getBoundingClientRect().height : canvas.height / dpr
    ctx.clearRect(0, 0, containerWidth, containerHeight)

    for (let i = activeDanmaku.length - 1; i >= 0; i--) {
      const danmaku = activeDanmaku[i]
      danmaku.x -= danmaku.speed

      if (danmaku.x + danmaku.width < 0) {
        const track = trackPool[danmaku.track]
        if (track && track.danmaku === danmaku) {
          track.endX = -Infinity
          track.danmaku = null
        }
        activeDanmaku.splice(i, 1)
        continue
      }

      if (danmaku.x > containerWidth) {
        const track = trackPool[danmaku.track]
        if (track && track.danmaku === danmaku) {
          track.endX = danmaku.x + danmaku.width + MIN_DANMAKU_GAP
        }
        continue
      }

      ctx.save()
      ctx.globalAlpha = danmaku.opacity
      ctx.font = `${danmaku.fontSize}px "Microsoft YaHei", "PingFang SC", sans-serif`
      ctx.textBaseline = 'middle'

      ctx.shadowColor = 'rgba(0, 0, 0, 0.8)'
      ctx.shadowBlur = 2

      ctx.fillStyle = `rgb(${danmaku.rgb.r}, ${danmaku.rgb.g}, ${danmaku.rgb.b})`
      ctx.fillText(danmaku.content, danmaku.x, danmaku.y)

      if (danmaku.isOwn) {
        ctx.strokeStyle = '#FFD700'
        ctx.lineWidth = 2
        const textMetrics = ctx.measureText(danmaku.content)
        const textHeight = danmaku.fontSize
        ctx.strokeRect(danmaku.x - 2, danmaku.y - textHeight / 2 - 2, textMetrics.width + 4, textHeight + 4)
      }

      ctx.shadowBlur = 0
      ctx.restore()

      const track = trackPool[danmaku.track]
      if (track && track.danmaku === danmaku) {
        track.endX = danmaku.x + danmaku.width + MIN_DANMAKU_GAP
      }
    }
  }

  function animationLoop(timestamp) {
    if (!isVisible.value || !isEnabled.value) {
      animationId = requestAnimationFrame(animationLoop)
      return
    }

    if (!isPausedRef.value) {
      renderFrame(timestamp)
    }

    const now = performance.now()
    if (now - lastCleanupTime > CLEANUP_INTERVAL) {
      lastCleanupTime = now
    }

    animationId = requestAnimationFrame(animationLoop)
  }

  function startRendering() {
    if (animationId) return
    animationId = requestAnimationFrame(animationLoop)
  }

  function stopRendering() {
    if (animationId) {
      cancelAnimationFrame(animationId)
      animationId = null
    }
  }

  let resizeTimer = null
  function handleResize() {
    if (resizeTimer) clearTimeout(resizeTimer)
    resizeTimer = setTimeout(() => {
      updateCanvasSize()
      for (let i = 0; i < trackPool.length; i++) {
        trackPool[i].endX = -Infinity
        trackPool[i].danmaku = null
      }
      activeDanmaku.length = 0
      restoreVisibleDanmaku()
      renderFrame()
    }, 200)
  }

  function getCurrentSegment() {
    const time = currentTimeRef.value || 0
    return Math.floor(time / SEGMENT_DURATION)
  }

  async function loadSegment(segment) {
    if (!videoIdRef.value) return
    if (loadingSegments.value.has(segment)) return
    if (loadedSegments.value.has(segment)) return

    loadingSegments.value.add(segment)
    try {
      const response = await axios.get(`/danmaku/video/${videoIdRef.value}/minute/${segment}`)
      const danmakuList = response.data.data || []
      for (const danmaku of danmakuList) {
        danmakuCache.set(danmaku.id, danmaku)
      }
    } catch (error) {
      console.error('Failed to load danmaku segment:', error)
    } finally {
      loadingSegments.value.delete(segment)
      loadedSegments.value = new Set(loadedSegments.value).add(segment)
    }
  }

  function checkAndLoadSegments() {
    const now = Date.now()
    if (now - lastSegmentCheck < SEGMENT_CHECK_THROTTLE) return
    lastSegmentCheck = now

    const currentSeg = getCurrentSegment()
    for (let i = -1; i <= PRELOAD_SEGMENTS; i++) {
      const seg = currentSeg + i
      if (seg >= 0) {
        loadSegment(seg)
      }
    }

    const pruneSeg = currentSeg - PRUNE_THRESHOLD
    for (const seg of loadedSegments.value) {
      if (seg < pruneSeg) {
        const nextSet = new Set(loadedSegments.value)
        nextSet.delete(seg)
        loadedSegments.value = nextSet
      }
    }
  }

  function handleTimeUpdate() {
    const currentTime = currentTimeRef.value
    checkAndLoadSegments()

    for (const [id, danmakuData] of danmakuCache) {
      const timeDiff = danmakuData.time - currentTime
      if (timeDiff >= -0.5 && timeDiff <= 0.3 && !activeDanmaku.some(d => d.id === id)) {
        addDanmaku(danmakuData)
      }
    }
  }

  function receiveRealtimeDanmaku(danmakuData) {
    if (!danmakuData || !danmakuData.id) return
    const isNew = !danmakuCache.has(danmakuData.id)
    danmakuCache.set(danmakuData.id, danmakuData)
    if (isNew) {
      danmakuCount.value++
    }
    const currentTime = currentTimeRef.value
    if (Math.abs(danmakuData.time - currentTime) < 1.0) {
      addDanmaku(danmakuData)
    }
  }

  function setDanmakuCount(count) {
    danmakuCount.value = count
  }

  function incrementDanmakuCount() {
    danmakuCount.value++
  }

  function decrementDanmakuCount() {
    if (danmakuCount.value > 0) {
      danmakuCount.value--
    }
  }

  function handleDanmakuDelete(danmakuId) {
    danmakuCache.delete(danmakuId)
    removeDanmaku(danmakuId)
    decrementDanmakuCount()
  }

  function handleVisibilityChange(timeUpdateCallback) {
    isVisible.value = document.visibilityState === 'visible'
    if (isVisible.value) {
      updateCanvasSize()
      restoreVisibleDanmaku()
      renderFrame()
      if (timeUpdateCallback) {
        timeUpdateCallback()
      }
    }
  }

  function enable() {
    isEnabled.value = true
    startRendering()
  }

  function disable() {
    isEnabled.value = false
    stopRendering()
  }

  function toggle() {
    if (isEnabled.value) {
      disable()
    } else {
      enable()
    }
    return isEnabled.value
  }

  function destroy() {
    stopRendering()
    if (resizeTimer) {
      clearTimeout(resizeTimer)
      resizeTimer = null
    }
    activeDanmaku.length = 0
    danmakuCache.clear()
    loadedSegments.value.clear()
    loadingSegments.value.clear()
    initTracks()
  }

  return {
    init,
    startRendering,
    stopRendering,
    toggle,
    enable,
    disable,
    handleTimeUpdate,
    handleResize,
    handleVisibilityChange,
    receiveRealtimeDanmaku,
    handleDanmakuDelete,
    removeDanmaku,
    setDanmakuCount,
    incrementDanmakuCount,
    decrementDanmakuCount,
    danmakuCount,
    isEnabled,
    destroy
  }
}