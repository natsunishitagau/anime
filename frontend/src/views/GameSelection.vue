<template>
  <div class="game-selection-container">
    <div class="game-header">
      <h1 class="game-title">游戏中心</h1>
      <p class="game-subtitle">选择一个游戏开始挑战</p>
    </div>

    <div class="game-cards">
      <div @click="goToAnimeGame" class="game-card anime-game-card">
        <div class="card-content">
          <div class="card-icon">
            <img src="/src/assets/games/anime.svg" alt="番剧游戏" />
          </div>
          <h2 class="card-title">番剧分比分</h2>
          <p class="card-description">根据图片选择分数更高的番剧，测试你的番剧知识！</p>
        </div>
        <div class="card-features">
          <span class="feature">📅 年份筛选</span>
          <span class="feature">🎬 类型筛选</span>
          <span class="feature">⭐ 评分对比</span>
        </div>
      </div>

      <div @click="goToCharacterGame" class="game-card character-game-card">
        <div class="card-content">
          <div class="card-icon2">
            <img src="/src/assets/games/character.svg" alt="角色游戏" />
          </div>
          <h2 class="card-title">角色收藏数比分</h2>
          <p class="card-description">根据图片选择人气更高的角色，看看谁是真正的角色达人！</p>
        </div>
        <div class="card-features">
          <span class="feature">🔥 人气筛选</span>
          <span class="feature">💖 收藏对比</span>
          <span class="feature">🎭 角色识别</span>
        </div>
      </div>

      <div class="game-card upcoming-game-card">
        <div class="card-content">
          <div class="card-icon">
            <span class="upcoming-icon">🔮</span>
          </div>
          <h2 class="card-title">猜动漫角色</h2>
          <p class="card-description">每个轮次根据回答的动漫角色人气/作品日期/标签等与答案的偏差，逐步缩小范围，挑战你的角色识别能力！</p>
        </div>
        <div class="card-features">
          <span class="feature upcoming">🚧 开发中</span>
          <span class="feature">🎯 角色识别</span>
          <span class="feature">⏳ 即将上线</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()

const goToAnimeGame = () => {
  router.push('/game/anime')
}

const goToCharacterGame = () => {
  router.push('/game/character')
}
</script>

<style scoped>
.game-selection-container {
  min-height: calc(100vh - 80px);
  padding: 2rem 1rem;
  display: flex;
  flex-direction: column;
  align-items: center;
  max-width: 1200px;
  margin: 0 auto;
}

.game-header {
  text-align: center;
  margin-bottom: 3rem;
}

.game-title {
  font-size: 3rem;
  font-weight: 700;
  background: linear-gradient(135deg, var(--primary-color), var(--secondary-color));
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  margin-bottom: 0.5rem;
}

.game-subtitle {
  color: var(--text-secondary);
  font-size: 1.25rem;
}

.game-cards {
  display: flex;
  gap: 2rem;
  justify-content: center;
  align-items: stretch;
  margin-bottom: 3rem;
  width: 100%;
}

.game-card {
  flex: 1;
  max-width: 450px;
  background: var(--background-light);
  border-radius: 1.5rem;
  padding: 2.5rem;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  text-align: center;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
  cursor: pointer;
  transition: all 0.3s ease;
  border: 2px solid transparent;
}

.game-card:hover {
  transform: translateY(-8px);
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.2);
}

.anime-game-card:hover {
  border-color: var(--primary-color);
}

.character-game-card:hover {
  border-color: var(--secondary-color);
}

.upcoming-game-card {
  opacity: 0.7;
  cursor: not-allowed;
  border-color: var(--border-color);
}

.upcoming-game-card:hover {
  transform: none;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
}

.upcoming-icon {
  font-size: 4rem;
}

.feature.upcoming {
  background: rgba(251, 191, 36, 0.1);
  border-color: rgba(251, 191, 36, 0.3);
  color: #f59e0b;
}

.card-content {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.card-icon {
  width: 100px;
  height: 100px;
  margin-bottom: 1.5rem;
}

.card-icon img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.card-icon2 {
  width: 80px;
  height: 80px;
  margin-bottom: 3rem;
}

.card-icon2 img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.card-title {
  font-size: 1.75rem;
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 1rem;
}

.card-description {
  color: var(--text-secondary);
  font-size: 1rem;
  line-height: 1.6;
  margin-bottom: 1.5rem;
}

.card-features {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  justify-content: center;
}

.feature {
  padding: 0.5rem 1rem;
  background: rgba(99, 102, 241, 0.1);
  border: 1px solid rgba(99, 102, 241, 0.2);
  border-radius: 9999px;
  font-size: 0.875rem;
  color: var(--primary-color);
  font-weight: 500;
}

.card-btn {
  padding: 0.75rem 2rem;
  font-size: 1rem;
  width: 100%;
  max-width: 200px;
}

@media (max-width: 768px) {
  .game-title {
    font-size: 2rem;
  }
  
  .game-cards {
    flex-direction: column;
  }
  
  .game-card {
    max-width: 100%;
  }
  
  .card-icon {
    font-size: 3rem;
  }
  
  .card-title {
    font-size: 1.5rem;
  }
}
</style>