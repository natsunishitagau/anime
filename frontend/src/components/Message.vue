<template>
  <Teleport to="body">
    <div class="message-container">
      <TransitionGroup name="message">
        <div
          v-for="msg in messages"
          :key="msg.id"
          :class="['message', `message-${msg.type}`]"
        >
          <span class="message-icon">{{ getIcon(msg.type) }}</span>
          <span class="message-content">{{ msg.content }}</span>
        </div>
      </TransitionGroup>
    </div>
  </Teleport>
</template>

<script setup>
import { ref } from 'vue'

const messages = ref([])
let messageId = 0

const getIcon = (type) => {
  const icons = {
    success: '✓',
    error: '✕',
    warning: '⚠',
    info: 'ℹ'
  }
  return icons[type] || icons.info
}

const addMessage = (content, type = 'info', duration = 3000) => {
  const id = ++messageId
  messages.value.push({ id, content, type })

  if (duration > 0) {
    setTimeout(() => {
      removeMessage(id)
    }, duration)
  }

  return id
}

const removeMessage = (id) => {
  const index = messages.value.findIndex(m => m.id === id)
  if (index > -1) {
    messages.value.splice(index, 1)
  }
}

const success = (content, duration) => addMessage(content, 'success', duration)
const error = (content, duration) => addMessage(content, 'error', duration)
const warning = (content, duration) => addMessage(content, 'warning', duration)
const info = (content, duration) => addMessage(content, 'info', duration)

defineExpose({ success, error, warning, info, addMessage, removeMessage })
</script>

<style scoped>
.message-container {
  position: fixed;
  top: 1rem;
  left: 50%;
  transform: translateX(-50%);
  z-index: 10000;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
  pointer-events: none;
}

.message {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.75rem 1.25rem;
  border-radius: 8px;
  font-size: 0.875rem;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  pointer-events: auto;
  min-width: 200px;
  max-width: 400px;
}

.message-success {
  background: linear-gradient(145deg, #10b981, #059669);
  color: white;
}

.message-error {
  background: linear-gradient(145deg, #ef4444, #dc2626);
  color: white;
}

.message-warning {
  background: linear-gradient(145deg, #f59e0b, #d97706);
  color: white;
}

.message-info {
  background: linear-gradient(145deg, #3b82f6, #2563eb);
  color: white;
}

.message-icon {
  font-size: 1rem;
  font-weight: bold;
}

.message-content {
  flex: 1;
}

.message-enter-active,
.message-leave-active {
  transition: all 0.3s ease;
}

.message-enter-from {
  opacity: 0;
  transform: translateY(-20px);
}

.message-leave-to {
  opacity: 0;
  transform: translateY(-20px);
}
</style>
