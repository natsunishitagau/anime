<template>
  <div class="video-player-page">
    <div class="video-container" v-if="video">
      <div class="video-header">
        <button @click="goBack" class="back-btn">
          返回
        </button>
        <h1>{{ video.title }}</h1>
      </div>

      <div class="video-wrapper">
        <video
          ref="videoElement"
          class="video-player"
          controls
          controlsList="nodownload"
          @timeupdate="handleTimeUpdate"
          @loadedmetadata="handleLoadedMetadata"
          @pause="handlePause"
        >
          <source :src="videoUrl" type="video/mp4" />
        </video>
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
import axios from '../utils/axios'

const route = useRoute()
const router = useRouter()

const video = ref(null)
const episodes = ref([])
const loading = ref(true)
const currentTime = ref(0)
const duration = ref(0)
const videoElement = ref(null)
const savedProgress = ref(0)
const lastSavedTime = ref(0)
const lastWatchedEpisodeId = ref(null)

const USER_ID = 1

const videoUrl = computed(() => {
  if (!video.value) return ''
  return `/api/videos/stream/${video.value.id}`
})

const fetchVideo = async (videoId) => {
  loading.value = true
  try {
    const response = await axios.get(`/videos/${videoId}`)
    video.value = response.data.data
    await fetchEpisodes(video.value.animeId)
    await loadWatchHistory(video.value.animeId)
    await nextTick(() => {
      if (videoElement.value) {
        videoElement.value.load()
        if (savedProgress.value > 0 && lastWatchedEpisodeId.value === video.value.id) {
          videoElement.value.currentTime = savedProgress.value
        }
      }
    })
  } catch (error) {
    console.error('Failed to fetch video:', error)
  } finally {
    loading.value = false
  }
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
    const response = await axios.get(`/watch-history/user/${USER_ID}/anime/${animeId}`)
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
        userId: USER_ID,
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
  }
}

const handleLoadedMetadata = () => {
  if (videoElement.value) {
    duration.value = videoElement.value.duration
  }
}

const handlePause = () => {
  if (videoElement.value) {
    saveWatchHistory(videoElement.value.currentTime, false)
  }
}

const formatTime = (seconds) => {
  if (!seconds || isNaN(seconds)) return '00:00'
  const mins = Math.floor(seconds / 60)
  const secs = Math.floor(seconds % 60)
  return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`
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

onMounted(() => {
  const videoId = route.params.id
  if (videoId) {
    fetchVideo(videoId)
  }
})

onUnmounted(() => {
  if (videoElement.value) {
    saveWatchHistory(videoElement.value.currentTime, false)
  }
})

watch(() => route.params.id, (newId) => {
  if (newId) {
    savedProgress.value = 0
    lastWatchedEpisodeId.value = null
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
}

.video-player {
  width: 100%;
  height: 62vh;
  display: block;
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

</style>