<template>
  <div class="app">
    <Navbar />
    <main class="main-content">
      <router-view />
    </main>
    <Footer />
    <Message />
    <ConfirmDialog ref="confirmDialog" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import Navbar from './components/Navbar.vue'
import Footer from './components/Footer.vue'
import Message from './components/Message.vue'
import ConfirmDialog from './components/ConfirmDialog.vue'
import { setConfirmHandler } from './utils/confirm'

const confirmDialog = ref(null)

const $confirm = (message) => {
  return confirmDialog.value?.show(message)
}

onMounted(() => {
  setConfirmHandler($confirm)
})
</script>

<style scoped>
.app {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.main-content {
  flex: 1;
  padding-top: 5rem;
  padding-bottom: 3rem;
}
</style>