<template>
  <Teleport to="body">
    <Transition name="dialog">
    <div v-if="visible" class="confirm-overlay" @click.self="handleCancel">
      <div class="confirm-dialog">
        <div class="confirm-icon-wrapper">
          <div class="confirm-icon-circle">
            <svg class="confirm-icon-svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" />
              <line x1="12" y1="9" x2="12" y2="13" />
              <line x1="12" y1="17" x2="12.01" y2="17" />
            </svg>
          </div>
        </div>
        <div class="confirm-header">
          <h3>确认操作</h3>
        </div>
        <div class="confirm-body">
          <p>{{ message }}</p>
        </div>
        <div class="confirm-footer">
          <button class="btn btn-secondary" @click="handleCancel">
            <svg class="btn-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <line x1="18" y1="6" x2="6" y2="18" />
              <line x1="6" y1="6" x2="18" y2="18" />
            </svg>
            取消
          </button>
          <button class="btn btn-danger" @click="handleConfirm">
            <svg class="btn-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <polyline points="20 6 9 17 4 12" />
            </svg>
            确认
          </button>
        </div>
      </div>
    </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { ref } from 'vue'

const visible = ref(false)
const message = ref('')
let resolvePromise = null

const show = (msg) => {
  message.value = msg
  visible.value = true
  return new Promise((resolve) => {
    resolvePromise = resolve
  })
}

const handleConfirm = () => {
  visible.value = false
  if (resolvePromise) {
    resolvePromise(true)
    resolvePromise = null
  }
}

const handleCancel = () => {
  visible.value = false
  if (resolvePromise) {
    resolvePromise(false)
    resolvePromise = null
  }
}

defineExpose({ show })
</script>

<style scoped>
.confirm-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.65);
  backdrop-filter: blur(6px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 10001;
}

.confirm-dialog {
  background: linear-gradient(160deg, #1e293b 0%, #0f172a 100%);
  border-radius: 16px;
  max-width: 360px;
  width: calc(100% - 2rem);
  box-shadow:
    0 0 0 1px rgba(239, 68, 68, 0.15),
    0 20px 60px rgba(0, 0, 0, 0.5),
    0 0 40px rgba(239, 68, 68, 0.08);
  border: 1px solid rgba(239, 68, 68, 0.15);
  overflow: hidden;
  padding: 1.5rem 1.5rem 1rem;
}

.confirm-icon-wrapper {
  display: flex;
  justify-content: center;
  margin-bottom: 1rem;
}

.confirm-icon-circle {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: rgba(239, 68, 68, 0.12);
  display: flex;
  align-items: center;
  justify-content: center;
  animation: iconPulse 2s ease-in-out infinite;
}

@keyframes iconPulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(239, 68, 68, 0.2); }
  50% { box-shadow: 0 0 0 8px rgba(239, 68, 68, 0); }
}

.confirm-icon-svg {
  width: 28px;
  height: 28px;
  color: #ef4444;
}

.confirm-header {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 0.5rem;
}

.confirm-header h3 {
  margin: 0;
  color: #f8fafc;
  font-size: 1.125rem;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.confirm-body {
  padding: 0.5rem 0 0.25rem;
}

.confirm-body p {
  margin: 0;
  color: #94a3b8;
  font-size: 0.9375rem;
  line-height: 1.7;
  text-align: center;
}

.confirm-footer {
  display: flex;
  gap: 0.875rem;
  padding: 1.25rem 0 0;
}

.confirm-footer .btn {
  flex: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  padding: 0.75rem 0.5rem;
  border-radius: 10px;
  font-weight: 600;
  font-size: 0.8125rem;
  cursor: pointer;
  transition: all 0.25s ease;
  border: none;
  letter-spacing: 0.01em;
}

.btn-icon {
  width: 16px;
  height: 16px;
  flex-shrink: 0;
}

.btn-secondary {
  background: rgba(71, 85, 105, 0.35);
  color: #e2e8f0;
  border: 1px solid rgba(100, 116, 139, 0.2);
}

.btn-secondary:hover {
  background: rgba(71, 85, 105, 0.55);
  border-color: rgba(100, 116, 139, 0.35);
  transform: translateY(-1px);
}

.btn-danger {
  background: linear-gradient(135deg, #ef4444, #dc2626);
  color: white;
  box-shadow: 0 4px 14px rgba(239, 68, 68, 0.3);
  border: 1px solid rgba(248, 113, 113, 0.2);
}

.btn-danger:hover {
  background: linear-gradient(135deg, #f87171, #ef4444);
  box-shadow: 0 6px 20px rgba(239, 68, 68, 0.4);
  transform: translateY(-1px);
}

.btn-danger:active {
  transform: translateY(0);
  box-shadow: 0 2px 8px rgba(239, 68, 68, 0.3);
}

.dialog-enter-active {
  transition: all 0.25s ease-out;
}

.dialog-leave-active {
  transition: all 0.2s ease-in;
}

.dialog-enter-from,
.dialog-leave-to {
  opacity: 0;
}

.dialog-enter-from .confirm-dialog,
.dialog-leave-to .confirm-dialog {
  transform: scale(0.92) translateY(10px);
}

.dialog-enter-from .confirm-overlay,
.dialog-leave-to .confirm-overlay {
  backdrop-filter: blur(0);
}

.dialog-enter-active .confirm-dialog {
  transition: transform 0.25s ease-out, opacity 0.25s ease-out;
}

.dialog-leave-active .confirm-dialog {
  transition: transform 0.2s ease-in, opacity 0.2s ease-in;
}
</style>
