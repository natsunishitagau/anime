<template>
  <div class="video-player-page">
    <div class="video-container" v-if="video">
      <div class="video-header">
        <button @click="goBack" class="back-btn">
          返回
        </button>
        <h1>{{ video.title }}</h1>
      </div>

      <div class="video-wrapper" ref="videoWrapperRef" @mouseenter="showVideoControls" @mousemove="showVideoControls" @mouseleave="hideVideoControls">
        <video
          ref="videoElement"
          class="video-player"
          @timeupdate="handleTimeUpdate"
          @loadedmetadata="handleLoadedMetadata"
          @pause="handlePause"
          @play="handlePlay"
          @volumechange="handleVolumeChange"
          @progress="handleProgress"
        >
          <source :src="videoUrl" type="video/mp4" />
        </video>
        <canvas v-show="danmakuEnabled"
          ref="danmakuCanvasRef"
          class="danmaku-canvas"
        ></canvas>
        <VideoControls
          :currentTime="currentTime"
          :duration="duration"
          :volume="volume"
          :isMuted="isMuted"
          :isPlaying="isPlaying"
          :isFullscreen="isFullscreen"
          :buffered="buffered"
          :isVisible="controlsVisible"
          @play-pause="togglePlayPause"
          @seek="handleSeek"
          @volume-change="setVolume"
          @mute-toggle="toggleMute"
          @toggle-fullscreen="toggleFullscreen"
          @skip-forward="skipForward"
          @skip-backward="skipBackward"
        />
      </div>

      <div class="danmaku-controls">
        <div class="danmaku-bar">
          <button
            class="danmaku-toggle-btn"
            :class="{ active: danmakuEnabled }"
            @click="toggleDanmaku"
          >
            {{ danmakuEnabled ? '弹幕 ON' : '弹幕 OFF' }}
          </button>
          <span class="danmaku-count" v-if="danmakuCount > 0">{{ danmakuCount }}条弹幕</span>
          <div class="danmaku-color-picker">
            <label class="color-label">颜色</label>
            <input type="color" v-model="danmakuColor" class="color-input" @input="onDanmakuColorChange" />
          </div>
          <input
            v-model="danmakuInput"
            class="danmaku-input"
            placeholder="发送弹幕，Enter发送"
            maxlength="50"
            :disabled="!danmakuEnabled"
            @keyup.enter="sendDanmaku"
          />
          <button class="danmaku-send-btn" @click="sendDanmaku" :disabled="!danmakuEnabled || !danmakuInput.trim()">
            发送
          </button>
        </div>
      </div>

      <div class="episode-list">
        <h2>剧集列表</h2>
        <div class="episodes-grid">
          <div
            v-for="ep in episodes"
            :key="ep.id"
            class="episode-card"
            :class="{ active: ep.id === video.id, 'last-watched': isLastWatched(ep.id) }"
            @click="playEpisode(ep)"
          >
            <div class="episode-number">第{{ ep.episodeNumber }}话</div>
            <div class="episode-title">{{ ep.title }}</div>
            <div class="episode-duration" v-if="ep.duration">{{ formatTime(ep.duration) }}</div>
          </div>
        </div>
      </div>
    </div>

    <div v-else-if="loading" class="loading">
      <div class="spinner"></div>
      <p>加载中...</p>
    </div>

    <div v-else class="error-state">
      <p>视频不存在</p>
      <button @click="goBack" class="btn btn-primary">返回</button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch, nextTick, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import axios from '../utils/axios'
import { useDanmakuEngine } from '../composables/useDanmakuEngine'
import { useDanmakuWebSocket } from '../composables/useDanmakuWebSocket'
import VideoControls from '../components/VideoControls.vue'

let Hls = null
const initHls = async () => {
  if (!Hls) {
    const { default: HlsModule } = await import('hls.js')
    Hls = HlsModule.default || HlsModule
  }
  return Hls
}

let hlsInstance = null

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const video = ref(null)
const episodes = ref([])
const loading = ref(true)
const currentTime = ref(0)
const duration = ref(0)
const videoElement = ref(null)
const savedProgress = ref(0)
const lastSavedTime = ref(0)
const lastWatchedEpisodeId = ref(null)
const isPaused = ref(true)

const danmakuInput = ref('')
const danmakuColor = ref('#FFFFFF')
const danmakuEnabled = ref(true)
const danmakuCanvasRef = ref(null)
const videoWrapperRef = ref(null)
const videoIdRef = computed(() => video.value?.id || null)
const wsConnected = ref(false)

const videoUrl = computed(() => {
  if (!video.value) return ''
  if (video.value.videoType === 'HLS') {
    return `/api/videos/hls/${video.value.id}`
  }
  return `/api/videos/stream/${video.value.id}`
})

const initVideoPlayer = async () => {
  if (!videoElement.value || !video.value) return
  
  destroyHls()
  
  if (video.value.videoType === 'HLS') {
    await initHlsPlayer()
  } else {
    videoElement.value.load()
  }
  
  if (danmakuCanvasRef.value && videoWrapperRef.value) {
    initEngine(danmakuCanvasRef.value, videoWrapperRef.value)
    startRendering()
  }
  
  if (savedProgress.value > 0 && lastWatchedEpisodeId.value === video.value.id) {
    videoElement.value.currentTime = savedProgress.value
  }
}

const initHlsPlayer = async () => {
  if (!videoElement.value || !video.value) return
  
  try {
    const Hls = await initHls()
    
    if (Hls.isSupported()) {
      hlsInstance = new Hls({
        enableWorker: true,
        lowLatencyMode: false,
        maxBufferLength: 30,
        maxBufferSize: 10 * 1024 * 1024,
        maxMaxBufferLength: 60
      })
      
      const hlsResponse = await axios.get(`/videos/hls/${video.value.id}`)
      const hlsPath = hlsResponse.data.data
      
      hlsInstance.loadSource(hlsPath)
      hlsInstance.attachMedia(videoElement.value)
      
      hlsInstance.on(Hls.Events.MANIFEST_PARSED, () => {
        // console.log('HLS manifest parsed')
      })
      
      hlsInstance.on(Hls.Events.ERROR, (event, data) => {
        console.error('HLS error:', data)
        if (data.fatal) {
          switch (data.type) {
            case Hls.ErrorTypes.NETWORK_ERROR:
              console.error('Network error, trying to recover...')
              hlsInstance.startLoad()
              break
            case Hls.ErrorTypes.MEDIA_ERROR:
              console.error('Media error, trying to recover...')
              hlsInstance.recoverMediaError()
              break
            default:
              console.error('Fatal error, cannot recover')
              destroyHls()
              break
          }
        }
      })
    } else {
      console.warn('HLS not supported in this browser')
    }
  } catch (error) {
    console.error('Failed to initialize HLS player:', error)
  }
}

const destroyHls = () => {
  if (hlsInstance) {
    hlsInstance.destroy()
    hlsInstance = null
  }
}

const volume = ref(0.4)
const isMuted = ref(false)
const isPlaying = ref(false)
const isFullscreen = ref(false)
const buffered = ref([])
const controlsVisible = ref(true)
let controlsHideTimeout = null

const {
  danmakuCount,
  init: initEngine,
  enable: enableDanmaku,
  disable: disableDanmaku,
  toggle,
  setDanmakuCount,
  handleTimeUpdate: engineHandleTimeUpdate,
  handleResize,
  handleVisibilityChange,
  receiveRealtimeDanmaku,
  handleDanmakuDelete,
  removeDanmaku,
  startRendering,
  destroy: destroyEngine
} = useDanmakuEngine(videoElement, videoIdRef, currentTime, isPaused, computed(() => authStore.user?.id))

const {
  isConnected,
  connect: connectWS,
  disconnect: disconnectWS
} = useDanmakuWebSocket(videoIdRef, receiveRealtimeDanmaku, handleDanmakuDelete)

let lastDanmakuTimeUpdate = 0
const DANMAKU_TIME_THROTTLE = 150

const loadDanmakuCount = async () => {
  if (!video.value) return
  try {
    const res = await axios.get(`/danmaku/video/${video.value.id}/count`)
    setDanmakuCount(res.data.data.count || 0)
  } catch (error) {
    console.error('Failed to load danmaku count:', error)
  }
}

const fetchVideo = async (videoId) => {
  loading.value = true
  try {
    const response = await axios.get(`/videos/${videoId}`)
    video.value = response.data.data
    await fetchEpisodes(video.value.animeId)
    await loadWatchHistory(video.value.animeId)
    await nextTick(() => {
      initVideoPlayer()
    })
    connectWS()
    loadDanmakuCount()
    loadUserSettings()
  } catch (error) {
    console.error('Failed to fetch video:', error)
  } finally {
    loading.value = false
  }
}

const loadUserSettings = async () => {
  try {
    const res = await axios.get('/user-settings')
    const settings = res.data
    if (settings) {
      if (settings.danmakuEnabled !== undefined) {
        danmakuEnabled.value = settings.danmakuEnabled
        if (danmakuEnabled.value) {
          enableDanmaku()
        } else {
          disableDanmaku()
        }
      }
      if (settings.danmakuColor) {
        danmakuColor.value = settings.danmakuColor
      }
      if (settings.defaultVolume !== undefined) {
        volume.value = settings.defaultVolume
        setVolume(settings.defaultVolume)
      }
    }
  } catch (error) {
    console.error('Failed to load user settings:', error)
  }
}

const saveUserSettings = async (settings) => {
  try {
    await axios.put('/user-settings', settings)
  } catch (error) {
    console.error('Failed to save user settings:', error)
  }
}

let settingsSaveTimeout = null
const debounceSaveSettings = (settings) => {
  if (settingsSaveTimeout) {
    clearTimeout(settingsSaveTimeout)
  }
  settingsSaveTimeout = setTimeout(() => {
    saveUserSettings(settings)
  }, 500)
}

const fetchEpisodes = async (animeId) => {
  try {
    const response = await axios.get(`/videos/anime/${animeId}`)
    episodes.value = response.data.data
  } catch (error) {
    console.error('Failed to fetch episodes:', error)
  }
}

const loadWatchHistory = async (animeId) => {
  try {
    const response = await axios.get(`/watch-history/anime/${animeId}`)
    if (response.data.data) {
      lastWatchedEpisodeId.value = response.data.data.episodeId
      savedProgress.value = response.data.data.progress || 0
    }
  } catch (error) {
    console.error('Failed to load watch history:', error)
  }
}

const saveWatchHistory = async (progress, completed) => {
  if (!video.value) return
  try {
    await axios.post('/watch-history/progress', null, {
      params: {
        animeId: video.value.animeId,
        episodeId: video.value.id,
        episodeNumber: video.value.episodeNumber,
        progress: Math.floor(progress),
        completed: completed
      }
    })
  } catch (error) {
    console.error('Failed to save watch history:', error)
  }
}

const isLastWatched = (episodeId) => {
  return lastWatchedEpisodeId.value === episodeId
}

const playEpisode = (ep) => {
  router.push(`/watch/${ep.id}`)
}

const handleTimeUpdate = () => {
  if (videoElement.value) {
    currentTime.value = videoElement.value.currentTime
    
    const now = Date.now()
    if (now - lastSavedTime.value > 10000) {
      saveWatchHistory(currentTime.value, false)
      lastSavedTime.value = now
    }

    if (now - lastDanmakuTimeUpdate > DANMAKU_TIME_THROTTLE) {
      lastDanmakuTimeUpdate = now
      engineHandleTimeUpdate()
    }
  }
}

const handleLoadedMetadata = () => {
  if (videoElement.value) {
    duration.value = videoElement.value.duration
  }
}

const handlePause = () => {
  isPaused.value = true
  isPlaying.value = false
  if (videoElement.value) {
    saveWatchHistory(videoElement.value.currentTime, false)
  }
}

const handlePlay = () => {
  isPaused.value = false
  isPlaying.value = true
}

const handleVolumeChange = () => {
  if (videoElement.value) {
    volume.value = videoElement.value.volume
    isMuted.value = videoElement.value.muted
  }
}

const handleProgress = () => {
  if (videoElement.value && videoElement.value.buffered.length > 0) {
    const bufferedRanges = []
    for (let i = 0; i < videoElement.value.buffered.length; i++) {
      bufferedRanges.push({
        start: videoElement.value.buffered.start(i),
        end: videoElement.value.buffered.end(i)
      })
    }
    buffered.value = bufferedRanges
  }
}

const togglePlayPause = () => {
  if (videoElement.value) {
    if (videoElement.value.paused) {
      videoElement.value.play()
    } else {
      videoElement.value.pause()
    }
  }
}

const handleSeek = (time) => {
  if (videoElement.value) {
    videoElement.value.currentTime = time
  }
}

const setVolume = (newVolume) => {
  if (videoElement.value) {
    videoElement.value.volume = newVolume
    volume.value = newVolume
    if (newVolume > 0 && isMuted.value) {
      videoElement.value.muted = false
      isMuted.value = false
    }
    debounceSaveSettings({ defaultVolume: newVolume })
  }
}

const toggleMute = () => {
  if (videoElement.value) {
    videoElement.value.muted = !videoElement.value.muted
    isMuted.value = videoElement.value.muted
  }
}

const toggleFullscreen = async () => {
  if (!videoWrapperRef.value) return
  
  try {
    if (!document.fullscreenElement) {
      await videoWrapperRef.value.requestFullscreen()
      isFullscreen.value = true
    } else {
      await document.exitFullscreen()
      isFullscreen.value = false
    }
  } catch (error) {
    console.error('Fullscreen error:', error)
  }
}

const skipForward = () => {
  if (videoElement.value) {
    videoElement.value.currentTime = Math.min(videoElement.value.duration, videoElement.value.currentTime + 10)
  }
}

const skipBackward = () => {
  if (videoElement.value) {
    videoElement.value.currentTime = Math.max(0, videoElement.value.currentTime - 10)
  }
}

const showVideoControls = () => {
  controlsVisible.value = true
  if (controlsHideTimeout) {
    clearTimeout(controlsHideTimeout)
  }
  if (isPlaying.value) {
    controlsHideTimeout = setTimeout(() => {
      controlsVisible.value = false
    }, 3000)
  }
}

const hideVideoControls = () => {
  if (isPlaying.value) {
    if (controlsHideTimeout) {
      clearTimeout(controlsHideTimeout)
    }
    controlsHideTimeout = setTimeout(() => {
      controlsVisible.value = false
    }, 500)
  }
}

const sendDanmaku = async () => {
  if (!danmakuEnabled.value) return
  
  const content = danmakuInput.value.trim()
  if (!content || !video.value) return

  const localDanmaku = {
    id: Date.now(),
    videoId: video.value.id,
    userId: authStore.user?.id,
    content: content,
    time: Math.floor(currentTime.value * 1000) / 1000,
    color: danmakuColor.value,
    fontSize: 25
  }

  try {
    await axios.post('/danmaku', {
      videoId: video.value.id,
      content: content,
      time: Math.floor(currentTime.value * 1000) / 1000,
      color: danmakuColor.value,
      fontSize: 25
    })
    danmakuInput.value = ''
  } catch (error) {
    console.error('Failed to send danmaku:', error)
    danmakuInput.value = ''
  }
}

const toggleDanmaku = () => {
  const enabled = toggle()
  danmakuEnabled.value = enabled
  debounceSaveSettings({ danmakuEnabled: enabled })
}

const onDanmakuColorChange = (e) => {
  debounceSaveSettings({ danmakuColor: e.target.value })
}

const formatTime = (seconds) => {
  if (!seconds || isNaN(seconds)) return '00:00'
  const hours = Math.floor(seconds / 3600)
  const mins = Math.floor((seconds % 3600) / 60)
  const secs = Math.floor(seconds % 60)
  
  if (hours > 0) {
    return `${hours}:${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`
  } else {
    return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`
  }
}

const goBack = () => {
  if (videoElement.value) {
    saveWatchHistory(videoElement.value.currentTime, false)
  }
  if (video.value) {
    router.push(`/anime/${video.value.animeId}`)
  } else {
    router.push('/')
  }
}

let resizeObserver = null

const handleFullscreenChange = () => {
  isFullscreen.value = !!document.fullscreenElement
}

onMounted(() => {
  const videoId = route.params.id
  if (videoId) {
    fetchVideo(videoId)
  }

  document.addEventListener('visibilitychange', () => handleVisibilityChange(handleTimeUpdate))
  window.addEventListener('resize', handleResize)
  document.addEventListener('fullscreenchange', handleFullscreenChange)

  if (videoWrapperRef.value) {
    resizeObserver = new ResizeObserver(() => {
      handleResize()
    })
    resizeObserver.observe(videoWrapperRef.value)
  }
})

onUnmounted(() => {
  disconnectWS()
  destroyEngine()
  destroyHls()
  if (videoElement.value) {
    saveWatchHistory(videoElement.value.currentTime, false)
  }
  document.removeEventListener('visibilitychange', () => handleVisibilityChange(handleTimeUpdate))
  window.removeEventListener('resize', handleResize)
  document.removeEventListener('fullscreenchange', handleFullscreenChange)
  if (resizeObserver) {
    resizeObserver.disconnect()
    resizeObserver = null
  }
  if (controlsHideTimeout) {
    clearTimeout(controlsHideTimeout)
    controlsHideTimeout = null
  }
})

watch(isConnected, (val) => {
  wsConnected.value = val
})

watch(() => route.params.id, (newId) => {
  if (newId) {
    savedProgress.value = 0
    lastWatchedEpisodeId.value = null
    disconnectWS()
    destroyEngine()
    destroyHls()
    fetchVideo(newId)
  }
})
</script>

<style scoped>
.video-player-page {
  min-height: 100vh;
  background: var(--background-dark);
  padding-bottom: 2rem;
}

.video-container {
  max-width: 1400px;
  min-width: 1000px;
  margin: 0 auto;
  padding: 0 1.5rem;
}

.video-header {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 1.5rem 0;
}

.back-btn {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem 1rem;
  background: var(--surface-color);
  border: none;
  border-radius: 0.5rem;
  color: var(--text-primary);
  cursor: pointer;
  transition: background 0.2s;
}

.back-btn:hover {
  background: var(--border-color);
}

.video-header h1 {
  font-size: 1.5rem;
  font-weight: 600;
}

.video-wrapper {
  background: #000;
  border-radius: 1rem;
  overflow: hidden;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.4);
  position: relative;
}

.video-player {
  width: 100%;
  height: 62vh;
  display: block;
}

.video-wrapper:fullscreen .video-player {
  height: 100vh;
}

.video-wrapper:-webkit-full-screen .video-player {
  height: 100vh;
}

.danmaku-canvas {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 5;
}

.video-player::-webkit-media-controlsclosed-caption {
  display: none !important;
}

.video-player::-webkit-media-controls-toggle-closed-captions-button {
  display: none !important;
}

.video-info {
  padding: 1rem 1.5rem;
  background: rgba(0, 0, 0, 0.5);
}

.progress-info {
  color: var(--text-secondary);
  font-size: 0.875rem;
}

.episode-list {
  margin-top: 2rem;
}

.episode-list h2 {
  font-size: 1.25rem;
  font-weight: 600;
  margin-bottom: 1rem;
  padding-bottom: 0.5rem;
  border-bottom: 1px solid var(--border-color);
}

.episodes-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 1rem;
}

.episode-card {
  padding: 1rem;
  background: var(--background-light);
  border-radius: 0.75rem;
  cursor: pointer;
  transition: all 0.2s;
  border: 2px solid transparent;
}

.episode-card:hover {
  background: var(--surface-color);
  transform: translateY(-2px);
}

.episode-card.active {
  border-color: var(--primary-color);
  background: rgba(99, 102, 241, 0.1);
}

.episode-card.last-watched {
  border-color: var(--success-color);
}

.episode-number {
  font-weight: 600;
  color: var(--primary-color);
  margin-bottom: 0.25rem;
}

.episode-title {
  font-size: 0.875rem;
  color: var(--text-primary);
  margin-bottom: 0.25rem;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.episode-duration {
  font-size: 0.75rem;
  color: var(--text-muted);
}

.loading, .error-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 50vh;
  gap: 1rem;
}

.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0.5rem 1rem;
  border-radius: 6px;
  border: none;
  cursor: pointer;
  font-weight: 500;
  font-size: 0.875rem;
  transition: background 0.2s;
}

.btn-primary {
  background: var(--primary-color);
  color: #fff;
}

.danmaku-controls {
  margin-top: 0.75rem;
}

.danmaku-bar {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.6rem 0.9rem;
  background: var(--surface-color, #1e1e2e);
  border-radius: 0.75rem;
  flex-wrap: wrap;
}

.danmaku-toggle-btn {
  width: 80px;
  padding: 0.35rem 0.75rem;
  border: 1px solid var(--border-color, #444);
  border-radius: 0.5rem;
  background: transparent;
  color: var(--text-secondary, #888);
  cursor: pointer;
  font-size: 0.8rem;
  font-weight: 500;
  white-space: nowrap;
  transition: all 0.2s;
}

.danmaku-toggle-btn:hover {
  border-color: var(--primary-color, #6366f1);
  color: var(--primary-color, #6366f1);
}

.danmaku-toggle-btn.active {
  background: rgba(99, 102, 241, 0.15);
  border-color: var(--primary-color, #6366f1);
  color: var(--primary-color, #6366f1);
}

.danmaku-count {
  font-size: 0.75rem;
  color: var(--text-muted, #666);
  white-space: nowrap;
}

.danmaku-online {
  font-size: 0.75rem;
  color: var(--text-muted, #666);
  white-space: nowrap;
}

.danmaku-online.connected {
  color: #22c55e;
}

.danmaku-color-picker {
  display: flex;
  align-items: center;
  gap: 0.25rem;
}

.color-label {
  font-size: 0.75rem;
  color: var(--text-secondary, #888);
}

.color-input {
  width: 28px;
  height: 28px;
  border: 1px solid var(--border-color, #444);
  border-radius: 4px;
  cursor: pointer;
  padding: 1px;
  background: transparent;
}

.danmaku-input {
  flex: 1;
  min-width: 120px;
  padding: 0.35rem 0.75rem;
  background: var(--background-dark, #111);
  border: 1px solid var(--border-color, #444);
  border-radius: 0.5rem;
  color: var(--text-primary, #fff);
  font-size: 0.85rem;
  outline: none;
  transition: border-color 0.2s;
}

.danmaku-input:focus {
  border-color: var(--primary-color, #6366f1);
}

.danmaku-input::placeholder {
  color: var(--text-muted, #555);
}

.danmaku-send-btn {
  padding: 0.35rem 1rem;
  background: var(--primary-color, #6366f1);
  border: none;
  border-radius: 0.5rem;
  color: #fff;
  cursor: pointer;
  font-size: 0.85rem;
  font-weight: 500;
  white-space: nowrap;
  transition: background 0.2s;
}

.danmaku-send-btn:hover {
  background: #5558e6;
}

.danmaku-send-btn:disabled {
  background: #444;
  cursor: not-allowed;
}

</style>
