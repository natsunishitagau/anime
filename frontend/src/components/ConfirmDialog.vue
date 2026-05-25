<template>
  <Teleport to="body">
    <div v-if="visible" class="confirm-overlay" @click.self="handleCancel">
      <div class="confirm-dialog">
        <div class="confirm-header">
          <h3>确认提示</h3>
        </div>
        <div class="confirm-body">
          <p>{{ message }}</p>
        </div>
        <div class="confirm-footer">
          <button class="btn btn-secondary" @click="handleCancel">取消</button>
          <button class="btn btn-danger" @click="handleConfirm">确认</button>
        </div>
      </div>
    </div>
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
  background: rgba(0, 0, 0, 0.6);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 10001;
}

.confirm-dialog {
  background: linear-gradient(145deg, #1e293b 0%, #0f172a 100%);
  border-radius: 16px;
  width: 100%;
  max-width: 360px;
  box-shadow:
    0 0 0 1px rgba(99, 102, 241, 0.2),
    0 20px 60px rgba(0, 0, 0, 0.5);
  border: 1px solid rgba(99, 102, 241, 0.2);
  overflow: hidden;
}

.confirm-header {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.75rem;
  padding-top: 1.5rem;
}

.confirm-icon {
  font-size: 1.5rem;
}

.confirm-header h3 {
  margin: 0;
  color: #f1f5f9;
  font-size: 1.125rem;
  font-weight: 600;
}

.confirm-body {
  padding-top: 1rem;
  padding-bottom: 1rem;
}

.confirm-body p {
  margin: 0;
  color: #e2e8f0;
  font-size: 0.9375rem;
  line-height: 1.6;
  text-align: center;
}

.confirm-footer {
  display: flex;
  gap: 0.75rem;
  padding: 1rem 1.5rem;
}

.confirm-footer .btn {
  flex: 1;
  padding: 0.75rem 1rem;
  border-radius: 8px;
  font-weight: 500;
  font-size: 0.875rem;
  cursor: pointer;
  transition: all 0.2s;
  border: none;
}

.btn-secondary {
  background: rgba(71, 85, 105, 0.5);
  color: #e2e8f0;
}

.btn-secondary:hover {
  background: rgba(71, 85, 105, 0.7);
}

.btn-danger {
  background: linear-gradient(145deg, #ef4444, #dc2626);
  color: white;
}

.btn-danger:hover {
  background: linear-gradient(145deg, #f87171, #ef4444);
}
</style>
