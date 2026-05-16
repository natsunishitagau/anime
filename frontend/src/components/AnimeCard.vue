<template>
  <router-link :to="`/anime/${anime.id}`" class="anime-card">
    <div class="card-image">
      <img :src="anime.imageUrl || 'https://via.placeholder.com/300x400/1e293b/475569?text=No+Image'" :alt="anime.title" />
      <div class="card-overlay">
        <span class="score" v-if="anime.score">⭐ {{ anime.score.toFixed(1) }}</span>
        <span class="episodes" v-if="anime.episodes">{{ anime.episodes }}话</span>
      </div>
    </div>
    <div class="card-content">
      <h3 class="card-title">{{ anime.title }}</h3>
      <p class="card-subtitle" v-if="anime.titleJp">{{ anime.titleJp }}</p>
      <div class="card-meta">
        <span class="badge badge-primary" v-if="anime.type">{{ anime.type }}</span>
        <span class="badge badge-success" v-if="anime.status">{{ anime.status }}</span>
      </div>
    </div>
  </router-link>
</template>

<script setup>
defineProps({
  anime: {
    type: Object,
    required: true
  }
})
</script>

<style scoped>
.anime-card {
  display: block;
  text-decoration: none;
  color: inherit;
  border-radius: 1rem;
  overflow: hidden;
  background: var(--background-light);
  transition: transform 0.3s ease, box-shadow 0.3s ease;
}

.anime-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 12px 24px rgba(0, 0, 0, 0.3);
}

.card-image {
  position: relative;
  aspect-ratio: 3/4;
  overflow: hidden;
}

.card-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.3s ease;
}

.anime-card:hover .card-image img {
  transform: scale(1.05);
}

.card-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  padding: 0.75rem;
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  background: linear-gradient(180deg, rgba(0,0,0,0.7) 0%, transparent 100%);
}

.score {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  background: rgba(0, 0, 0, 0.7);
  padding: 0.25rem 0.5rem;
  border-radius: 0.25rem;
  font-size: 0.75rem;
  font-weight: 600;
  color: #fbbf24;
}

.episodes {
  background: rgba(0, 0, 0, 0.7);
  padding: 0.25rem 0.5rem;
  border-radius: 0.25rem;
  font-size: 0.75rem;
  color: var(--text-primary);
}

.card-content {
  padding: 1rem;
}

.card-title {
  font-size: 0.875rem;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 0.25rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-subtitle {
  font-size: 0.75rem;
  color: var(--text-muted);
  margin-bottom: 0.5rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-meta {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.card-meta .badge {
  font-size: 0.625rem;
  padding: 0.125rem 0.5rem;
}
</style>