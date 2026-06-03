<template>
  <div class="video-controls" :class="{ visible: isVisible }" @mouseenter="showControls" @mouseleave="hideControls">
    <div class="progress-container" @mouseenter="handleProgressHover" @mousemove="handleProgressMove" @mouseleave="handleProgressLeave" @click="handleProgressClick" @mousedown="startDrag">
      <div class="progress-bar">
        <div class="progress-buffered" :style="{ width: bufferedPercent + '%' }"></div>
        <div class="progress-played" :style="{ width: progressPercent + '%' }"></div>
      </div>
      <div class="progress-thumb" :style="{ left: progressPercent + '%' }"></div>
      <div v-if="hoverTime !== null || dragPreviewTime !== null" class="time-tooltip" :style="{ left: hoverPosition + 'px' }">
        {{ formatTime(isDragging ? dragPreviewTime : hoverTime) }}
      </div>
    </div>

    <div class="controls-row">
      <div class="controls-left">
        <button class="control-btn" @click="$emit('play-pause')">
          <svg v-if="!isPlaying" viewBox="0 0 24 24" fill="currentColor">
            <path d="M8 5v14l11-7z"/>
          </svg>
          <svg v-else viewBox="0 0 24 24" fill="currentColor">
            <path d="M6 19h4V5H6v14zm8-14v14h4V5h-4z"/>
          </svg>
        </button>

        <button class="control-btn" @click="$emit('skip-backward')">
          <svg viewBox="0 0 24 24" fill="currentColor">
            <path d="M11 18V6l-8.5 6 8.5 6zm.5-6l8.5 6V6l-8.5 6z"/>
          </svg>
        </button>

        <button class="control-btn" @click="$emit('skip-forward')">
          <svg viewBox="0 0 24 24" fill="currentColor">
            <path d="M4 18l8.5-6L4 6v12zm9-12v12l8.5-6L13 6z"/>
          </svg>
        </button>

        <span class="time-display">{{ formatTime(currentTime) }} / {{ formatTime(duration) }}</span>
      
        <div class="volume-control">
          <button class="control-btn" @click="toggleMute">
            <svg v-if="volume === 0 || isMuted" viewBox="0 0 24 24" fill="currentColor">
              <path d="M16.5 12c0-1.77-1.02-3.29-2.5-4.03v2.21l2.45 2.45c.03-.2.05-.41.05-.63zm2.5 0c0 .94-.2 1.82-.54 2.64l1.51 1.51C20.63 14.91 21 13.5 21 12c0-4.28-2.99-7.86-7-8.77v2.06c2.89.86 5 3.54 5 6.71zM4.27 3L3 4.27 7.73 9H3v6h4l5 5v-6.73l4.25 4.25c-.67.52-1.42.93-2.25 1.18v2.06c1.38-.31 2.63-.95 3.69-1.81L19.73 21 21 19.73l-9-9L4.27 3zM12 4L9.91 6.09 12 8.18V4z"/>
            </svg>
            <svg v-else-if="volume < 0.5" viewBox="0 0 24 24" fill="currentColor">
              <path d="M18.5 12c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02zM5 9v6h4l5 5V4L9 9H5z"/>
            </svg>
            <svg v-else viewBox="0 0 24 24" fill="currentColor">
              <path d="M3 9v6h4l5 5V4L7 9H3zm13.5 3c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02zM14 3.23v2.06c2.89.86 5 3.54 5 6.71s-2.11 5.85-5 6.71v2.06c4.01-.91 7-4.49 7-8.77s-2.99-7.86-7-8.77z"/>
            </svg>
          </button>
          <div class="volume-slider-container">
            <input type="range" min="0" max="1" step="0.1" :value="isMuted ? 0 : volume" @input="handleVolumeChange" class="volume-slider" />
            <span class="volume-display">{{ Math.round((isMuted ? 0 : volume) * 100) }}%</span>
          </div>
          
        </div>
      </div>

      <div class="controls-right">
        <button class="control-btn" @click="$emit('toggle-fullscreen')">
          <svg v-if="!isFullscreen" viewBox="0 0 24 24" fill="currentColor">
            <path d="M7 14H5v5h5v-2H7v-3zm-2-4h2V7h3V5H5v5zm12 7h-3v2h5v-5h-2v3zM14 5v2h3v3h2V5h-5z"/>
          </svg>
          <svg v-else viewBox="0 0 24 24" fill="currentColor">
            <path d="M5 16h3v3h2v-5H5v2zm3-8H5v2h5V5H8v3zm6 11h2v-3h3v-2h-5v5zm2-11V5h-2v5h5V8h-3z"/>
          </svg>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'

const props = defineProps({
  currentTime: { type: Number, default: 0 },
  duration: { type: Number, default: 0 },
  volume: { type: Number, default: 0.4 },
  isMuted: { type: Boolean, default: false },
  isPlaying: { type: Boolean, default: false },
  isFullscreen: { type: Boolean, default: false },
  buffered: { type: Array, default: () => [] }
})

const emit = defineEmits(['play-pause', 'seek', 'volume-change', 'mute-toggle', 'toggle-fullscreen', 'skip-forward', 'skip-backward'])

const isVisible = ref(true)
const hoverTime = ref(null)
const hoverPosition = ref(0)
const isDragging = ref(false)
const dragPreviewTime = ref(null)
let hideTimeout = null

const progressPercent = computed(() => {
  if (!props.duration) return 0
  return (props.currentTime / props.duration) * 100
})

const bufferedPercent = computed(() => {
  if (!props.buffered.length || !props.duration) return 0
  const lastBuffer = props.buffered[props.buffered.length - 1]
  return (lastBuffer.end / props.duration) * 100
})

const showControls = () => {
  isVisible.value = true
  if (hideTimeout) {
    clearTimeout(hideTimeout)
  }
}

const hideControls = () => {
  if (props.isPlaying) {
    hideTimeout = setTimeout(() => {
      isVisible.value = false
    }, 3000)
  }
}

const handleProgressHover = () => {
  showControls()
}

const handleProgressMove = (e) => {
  const progressContainer = e.currentTarget
  const rect = progressContainer.getBoundingClientRect()
  const x = e.clientX - rect.left
  const percent = Math.max(0, Math.min(1, x / rect.width))
  
  if (isDragging.value) {
    dragPreviewTime.value = percent * props.duration
    hoverPosition.value = x
  } else {
    hoverTime.value = percent * props.duration
    hoverPosition.value = x
  }
}

const handleProgressLeave = () => {
  if (!isDragging.value) {
    hoverTime.value = null
  }
}

const startDrag = (e) => {
  e.preventDefault()
  isDragging.value = true
  
  const progressContainer = e.currentTarget
  const rect = progressContainer.getBoundingClientRect()
  const x = e.clientX - rect.left
  const percent = Math.max(0, Math.min(1, x / rect.width))
  
  dragPreviewTime.value = percent * props.duration
  hoverPosition.value = x
  
  document.addEventListener('mousemove', onDragMove)
  document.addEventListener('mouseup', onDragEnd)
}

const onDragMove = (e) => {
  const progressContainer = document.querySelector('.progress-container')
  if (!progressContainer) return
  
  const rect = progressContainer.getBoundingClientRect()
  const x = e.clientX - rect.left
  const percent = Math.max(0, Math.min(1, x / rect.width))
  
  dragPreviewTime.value = percent * props.duration
  hoverPosition.value = x
}

const onDragEnd = (e) => {
  if (dragPreviewTime.value !== null) {
    emit('seek', dragPreviewTime.value)
  }
  
  isDragging.value = false
  dragPreviewTime.value = null
  hoverTime.value = null
  
  document.removeEventListener('mousemove', onDragMove)
  document.removeEventListener('mouseup', onDragEnd)
}

const handleProgressClick = (e) => {
  const progressContainer = e.currentTarget
  const rect = progressContainer.getBoundingClientRect()
  const x = e.clientX - rect.left
  const percent = Math.max(0, Math.min(1, x / rect.width))
  const time = percent * props.duration
  
  emit('seek', time)
}

const handleVolumeChange = (e) => {
  const newVolume = parseFloat(e.target.value)
  emit('volume-change', newVolume)
}

const toggleMute = () => {
  emit('mute-toggle')
}

const formatTime = (seconds) => {
  if (!seconds || isNaN(seconds)) return '00:00'
  const hrs = Math.floor(seconds / 3600)
  const mins = Math.floor((seconds % 3600) / 60)
  const secs = Math.floor(seconds % 60)
  
  if (hrs > 0) {
    return `${hrs.toString().padStart(2, '0')}:${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`
  }
  return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`
}

onMounted(() => {
  if (props.isPlaying) {
    hideControls()
  }
})

onUnmounted(() => {
  if (hideTimeout) {
    clearTimeout(hideTimeout)
  }
})
</script>

<style scoped>
.video-controls {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  background: linear-gradient(transparent, rgba(0, 0, 0, 0.9));
  padding: 1rem 1rem 0.75rem;
  opacity: 0;
  transition: opacity 0.3s ease;
  z-index: 10;
}

.video-controls.visible {
  opacity: 1;
}

.progress-container {
  position: relative;
  height: 12px;
  margin-bottom: 0.5rem;
  cursor: pointer;
  display: flex;
  align-items: center;
}

.progress-bar {
  width: 100%;
  height: 4px;
  background: rgba(255, 255, 255, 0.3);
  border-radius: 2px;
  position: relative;
  transition: height 0.2s;
}

.progress-container:hover .progress-bar {
  height: 6px;
}

.progress-buffered {
  position: absolute;
  top: 0;
  left: 0;
  height: 100%;
  background: rgba(255, 255, 255, 0.5);
  border-radius: 2px;
}

.progress-played {
  position: absolute;
  top: 0;
  left: 0;
  height: 100%;
  background: var(--primary-color, #6366f1);
  border-radius: 2px;
}

.progress-thumb {
  position: absolute;
  width: 12px;
  height: 12px;
  background: #fff;
  border-radius: 50%;
  transform: translateX(-50%);
  opacity: 0;
  transition: opacity 0.2s;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.3);
}

.progress-container:hover .progress-thumb {
  opacity: 1;
}

.time-tooltip {
  position: absolute;
  bottom: 100%;
  transform: translateX(-50%);
  background: rgba(0, 0, 0, 0.9);
  color: #fff;
  padding: 0.25rem 0.5rem;
  border-radius: 4px;
  font-size: 0.75rem;
  white-space: nowrap;
  pointer-events: none;
  margin-bottom: 8px;
}

.time-tooltip::after {
  content: '';
  position: absolute;
  top: 100%;
  left: 50%;
  transform: translateX(-50%);
  border: 6px solid transparent;
  border-top-color: rgba(0, 0, 0, 0.9);
}

.controls-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.controls-left,
.controls-right {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.control-btn {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  border: none;
  color: #fff;
  cursor: pointer;
  border-radius: 50%;
  transition: background 0.2s;
}

.control-btn:hover {
  background: rgba(255, 255, 255, 0.1);
}

.control-btn svg {
  width: 20px;
  height: 20px;
}

.volume-control {
  display: flex;
  align-items: center;
  position: relative;
}

.volume-slider-container {
  width: 0;
  transition: width 0.2s;
  display: flex;
  align-items: center;
  margin-left: 8px;
}

.volume-control:hover .volume-slider-container {
  width: 80px;
  margin-left: 0.5rem;
}

.volume-slider {
  width: 80px;
  height: 4px;
  -webkit-appearance: none;
  background: rgba(255, 255, 255, 0.3);
  border-radius: 2px;
  cursor: pointer;
  vertical-align: middle;
}

.volume-slider::-webkit-slider-thumb {
  -webkit-appearance: none;
  width: 12px;
  height: 12px;
  background: #fff;
  border-radius: 50%;
  cursor: pointer;
}

.time-display {
  color: #fff;
  font-size: 0.875rem;
  margin-left: 0.5rem;
  font-variant-numeric: tabular-nums;
}

.volume-display {
  color: #fff;
  font-size: 0.75rem;
  margin-left: 0.2rem;
  font-variant-numeric: tabular-nums;
  min-width: 35px;
  text-align: right;
}
</style>