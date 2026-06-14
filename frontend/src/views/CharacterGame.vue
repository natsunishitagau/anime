<template>
  <div class="game-container">
    <div class="game-header">
      <h1 class="game-title">角色收藏数比分</h1>
      <p class="game-subtitle">选择人气更高的角色</p>
    </div>

    <div class="game-controls">
      <div class="control-group">
        <label class="control-label">🔥人气范围</label>

        <span class="year-labels">{{ favoritesRange[0].toLocaleString() }}</span>
        <div class="slider-block">
          <el-slider 
            v-model="favoritesRange" 
            :disabled="isPlaying"
            range 
            :min="0" 
            :max="200000"
            :step="1000"
          />
        </div>
        <span class="year-labels">{{ favoritesRange[1].toLocaleString() }}</span>
      </div>
      <button @click="startGame" :disabled="isLoading" class="btn btn-primary game-btn">
        <span v-if="isLoading" class="loading-spinner"></span>
        {{ isLoading ? '加载中...' : '开始游戏' }}
      </button>
    </div>

    <div v-if="isLoading" class="loading-area">
      <div class="loading-card">
        <div class="loading-spinner-lg"></div>
        <p class="loading-text">正在生成游戏序列...</p>
      </div>
    </div>

    <div v-if="isPlaying" class="game-area">
      <div class="comparison-section">
        <div 
          @click="selectCharacter(leftCharacter)" 
          :class="['anime-card', leftCharacter.selected ? 'selected' : '', leftCharacter.revealed ? 'revealed' : '']"
          :disabled="showResult"
        >
          <div class="image-container">
            <img :src="leftCharacter.image" class="anime-image" />
          </div>
          <div v-if="currentRound > 1 || leftCharacter.revealed" class="anime-info">
            <p class="anime-title">{{ leftCharacter.name }}</p>
            <p v-if="leftCharacter.animeTitleJp" class="anime-anime">{{ leftCharacter.animeTitleJp }}</p>
            <p class="anime-score">{{ leftCharacter.favorites.toLocaleString() }}</p>
          </div>
          <div v-else class="anime-placeholder">
            <span>点击选择</span>
          </div>
        </div>

        <div 
          @click="selectCharacter(rightCharacter)" 
          :class="['anime-card', rightCharacter.selected ? 'selected' : '', rightCharacter.revealed ? 'revealed' : '']"
          :disabled="showResult"
        >
          <div class="image-container">
            <img :src="rightCharacter.image" class="anime-image" />
          </div>
          <div v-if="rightCharacter.revealed" class="anime-info">
            <p class="anime-title">{{ rightCharacter.name }}</p>
            <p v-if="rightCharacter.animeTitleJp" class="anime-anime">{{ rightCharacter.animeTitleJp }}</p>
            <p class="anime-score">{{ rightCharacter.favorites.toLocaleString() }}</p>
          </div>
          <div v-else class="anime-placeholder">
            <span>点击选择</span>
          </div>
        </div>
      </div>

      <div v-if="showResult" class="result-section">
        <div :class="['result-card', isCorrect ? 'correct' : 'wrong']">
          <span class="result-icon">{{ isCorrect ? '🎉' : '😢' }}</span>
          <p class="result-text">{{ isCorrect ? '回答正确！' : `回答错误！${correctCharacter.name}(${correctCharacter.favorites.toLocaleString()}) > ${wrongCharacter.name}(${wrongCharacter.favorites.toLocaleString()})` }}</p>
        </div>
      </div>

      <div class="score-board">
        <span class="score-label">得分：</span>
        <span class="score-value">{{ score }}</span>
        <span class="score-divider">|</span>
        <span class="round-label">当前回合：</span>
        <span class="round-value">{{ currentRound }} / {{ totalRounds }}</span>
      </div>
    </div>

    <div v-if="!isPlaying&&!isLoading" class="welcome-area">
      <div class="welcome-card">
        <h2>📺 游戏规则</h2>
        <ul class="rules-list">
          <li>根据图片选择人气更高的角色</li>
          <li>可以通过人气范围筛选题目</li>
          <li>选择正确得10分，错误游戏结束</li>
          <li>共{{ totalRounds }}道题目</li>
        </ul>
        <p class="welcome-tip">🎯 点击"开始游戏"按钮开始挑战！</p>
      </div>
    </div>

    <div v-if="gameOver" class="game-over">
      <div class="game-over-card">
        <h2>🏆 游戏结束！</h2>
        <p class="final-score">最终得分：{{ score }} / {{ totalRounds * 10 }}</p>
        <p class="final-rating">{{ getRating() }}</p>
        <button @click="resetGame" class="btn btn-primary restart-btn">重新开始</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import axios from '../utils/axios'

const favoritesRange = ref([0, 200000])
const isPlaying = ref(false)
const isLoading = ref(false)
const showResult = ref(false)
const isCorrect = ref(false)
const score = ref(0)
const currentRound = ref(0)
const totalRounds = ref(10)
const gameOver = ref(false)
const gameSequence = ref([])
const currentSeqIndex = ref(0)

const leftCharacter = reactive({
  name: '',
  image: '',
  favorites: 0,
  animeTitleJp: '',
  revealed: false,
  selected: false
})

const rightCharacter = reactive({
  name: '',
  image: '',
  favorites: 0,
  animeTitleJp: '',
  revealed: false,
  selected: false
})

const correctCharacter = reactive({
  name: '',
  favorites: 0
})

const wrongCharacter = reactive({
  name: '',
  favorites: 0
})

const resetCharacterState = (character) => {
  character.name = ''
  character.image = ''
  character.favorites = 0
  character.animeTitleJp = ''
  character.revealed = false
  character.selected = false
}

const fetchGameSequence = async () => {
  try {
    const response = await axios.post('/character/game-sequence', {
      minFavorites: favoritesRange.value[0] || 0,
      maxFavorites: favoritesRange.value[1] || 200000,
      count: totalRounds.value + 1
    })
    if (response.data.success) {
      console.log(response.data.data)
      return response.data.data.map(character => ({
        name: character.nameJp,
        image: character.imageUrl,
        favorites: parseInt(character.favorites) || 0,
        animeTitleJp: character.animeTitleJp || ''
      }))
    }
    return []
  } catch (error) {
    console.error('获取游戏序列失败:', error)
    return []
  }
}

const startGame = async () => {
  isLoading.value = true
  
  try {
    const sequence = await fetchGameSequence()
    
    if (sequence.length < totalRounds.value + 1) {
      alert('当前筛选条件下题目不足，请调整筛选条件')
      return
    }
    
    gameSequence.value = sequence
    currentSeqIndex.value = 0
    
    isPlaying.value = true
    gameOver.value = false
    score.value = 0
    currentRound.value = 1
    showResult.value = false
    
    initRound()
  } finally {
    isLoading.value = false
  }
}

const initRound = () => {
  resetCharacterState(leftCharacter)
  resetCharacterState(rightCharacter)
  
  if (currentSeqIndex.value + 1 >= gameSequence.value.length) {
    endGame()
    return
  }
  
  const character1 = gameSequence.value[currentSeqIndex.value]
  const character2 = gameSequence.value[currentSeqIndex.value + 1]
  
  leftCharacter.name = character1.name
  leftCharacter.image = character1.image
  leftCharacter.favorites = character1.favorites
  leftCharacter.animeTitleJp = character1.animeTitleJp
  
  rightCharacter.name = character2.name
  rightCharacter.image = character2.image
  rightCharacter.favorites = character2.favorites
  rightCharacter.animeTitleJp = character2.animeTitleJp
  
  if (currentRound.value > 1) {
    leftCharacter.revealed = true
  }
}

const selectCharacter = (character) => {
  if (showResult.value) return
  
  leftCharacter.revealed = true
  rightCharacter.revealed = true
  character.selected = true
  
  const higherFavorites = leftCharacter.favorites > rightCharacter.favorites ? leftCharacter : rightCharacter
  const lowerFavorites = leftCharacter.favorites <= rightCharacter.favorites ? leftCharacter : rightCharacter
  
  correctCharacter.name = higherFavorites.name
  correctCharacter.favorites = higherFavorites.favorites
  wrongCharacter.name = lowerFavorites.name
  wrongCharacter.favorites = lowerFavorites.favorites
  
  isCorrect.value = character.favorites === higherFavorites.favorites
  
  if (isCorrect.value) {
    score.value += 10
    currentSeqIndex.value++
  }
  
  showResult.value = true
  
  setTimeout(() => {
    if (isCorrect.value) {
      nextRound()
    } else {
      endGame()
    }
  }, 3000)
}

const nextRound = () => {
  currentRound.value++
  showResult.value = false
  
  if (currentRound.value > totalRounds.value) {
    endGame()
  } else {
    initRound()
  }
}

const endGame = () => {
  gameOver.value = true
  isPlaying.value = false
}

const resetGame = () => {
  gameOver.value = false
  isPlaying.value = false
  score.value = 0
  currentRound.value = 0
  gameSequence.value = []
  resetCharacterState(leftCharacter)
  resetCharacterState(rightCharacter)
}

const getRating = () => {
  const percentage = (score.value / (totalRounds.value * 10)) * 100
  if (percentage === 100) return '🌟 完美！你是真正的角色达人！'
  if (percentage >= 80) return '⭐ 优秀！角色知识很扎实！'
  if (percentage >= 60) return '✨ 不错！继续加油！'
  if (percentage >= 40) return '💪 还可以！多了解一些角色吧！'
  return '📚 需要多了解一些角色了！'
}

</script>

<style scoped>
.game-container {
  min-height: calc(100vh - 80px);
  padding: 2rem 1rem;
  display: flex;
  flex-direction: column;
  align-items: center;
  max-width: 1000px;
  margin: 0 auto;
}

.game-header {
  text-align: center;
  margin-bottom: 2rem;
}

.game-title {
  font-size: 2.5rem;
  font-weight: 700;
  background: linear-gradient(135deg, var(--primary-color), var(--secondary-color));
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  margin-bottom: 0.5rem;
}

.game-subtitle {
  color: var(--text-secondary);
  font-size: 1.125rem;
}

.game-controls {
  display: flex;
  gap: 1rem;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 2rem;
  padding: 1.5rem;
  background: var(--background-light);
  border-radius: 1rem;
  width: 60%;
}

.control-group {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  min-width: 140px;
  flex: 1;
}

.control-label {
  font-size: 0.8rem;
  width: 80px;
  font-weight: 500;
  color: var(--text-secondary);
  text-transform: uppercase;
}

.slider-block {
  width: 170px;
  display: flex;
  align-items: center;
}

.slider-block .el-slider {
  margin: 0 12px;
}

.year-labels {
  font-size: 0.75rem;
  min-width: 45px;
  color: var(--text-secondary);
}

.game-btn {
  padding: 0.625rem 1.5rem;
  font-size: 1rem;
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.game-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.loading-spinner {
  width: 16px;
  height: 16px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: white;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

.loading-area {
  width: 100%;
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 4rem 0;
}

.loading-card {
  background: var(--background-light);
  border-radius: 1rem;
  padding: 3rem;
  text-align: center;
}

.loading-spinner-lg {
  width: 48px;
  height: 48px;
  border: 4px solid var(--border-color);
  border-top-color: var(--primary-color);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  margin: 0 auto 1rem;
}

.loading-text {
  color: var(--text-secondary);
  font-size: 1rem;
  margin: 0;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.game-area {
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1.5rem;
}

.comparison-section {
  display: flex;
  gap: 4rem;
  align-items: stretch;
  width: 80%;
  justify-content: center;
}

.anime-card {
  flex: 1;
  max-width: 350px;
  min-height: 420px;
  display: flex;
  flex-direction: column;
  background: var(--background-light);
  border-radius: 1rem;
  overflow: hidden;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
  cursor: pointer;
  transition: all 0.3s ease;
  border: 2px solid transparent;
}

.anime-card:hover:not(:disabled) {
  transform: translateY(-5px);
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.15);
}

.anime-card.selected {
  border-color: var(--primary-color);
}

.anime-card.revealed {
  cursor: default;
}

.anime-card:disabled {
  cursor: not-allowed;
}

.image-container {
  width: 100%;
  height: 320px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #1e293b;
}

.anime-image {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
}

.anime-info {
  padding: 1rem;
  text-align: center;
}

.anime-title {
  margin: 0 0 0.25rem 0;
  color: var(--text-primary);
  font-size: 1rem;
  font-weight: 500;
}

.anime-anime {
  margin: 0 0 0.5rem 0;
  color: var(--text-secondary);
  font-size: 0.875rem;
  font-style: italic;
}

.anime-score {
  margin: 0;
  color: var(--primary-color);
  font-size: 1.5rem;
  font-weight: 700;
}

.anime-placeholder {
  padding: 1rem;
  text-align: center;
  color: var(--text-secondary);
  font-size: 0.875rem;
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.result-section {
  width: 100%;
  max-width: 500px;
}

.result-card {
  padding: 1.5rem;
  border-radius: 1rem;
  text-align: center;
}

.result-card.correct {
  background: rgba(34, 197, 94, 0.1);
  border: 1px solid rgba(34, 197, 94, 0.3);
}

.result-card.wrong {
  background: rgba(239, 68, 68, 0.1);
  border: 1px solid rgba(239, 68, 68, 0.3);
}

.result-icon {
  font-size: 2.5rem;
  display: block;
  margin-bottom: 0.5rem;
}

.result-text {
  margin: 0;
  font-size: 1.125rem;
  font-weight: 500;
}

.score-board {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 1rem 1.5rem;
  background: var(--background-light);
  border-radius: 9999px;
}

.score-label, .round-label {
  color: var(--text-secondary);
  font-size: 0.875rem;
}

.score-value, .round-value {
  color: var(--primary-color);
  font-size: 1.25rem;
  font-weight: 700;
}

.score-divider {
  color: var(--border-color);
}

.welcome-area {
  width: 100%;
  max-width: 500px;
}

.welcome-card {
  background: var(--background-light);
  border-radius: 1rem;
  padding: 2rem;
  text-align: center;
}

.welcome-card h2 {
  margin-bottom: 1.5rem;
  color: var(--text-primary);
}

.rules-list {
  text-align: left;
  margin: 0 0 1.5rem 0;
  padding-left: 1.5rem;
  color: var(--text-secondary);
}

.rules-list li {
  margin-bottom: 0.5rem;
}

.welcome-tip {
  color: var(--primary-color);
  font-weight: 500;
  margin: 0;
}

.game-over {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(15, 23, 42, 0.9);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.game-over-card {
  background: var(--background-light);
  border-radius: 1.5rem;
  padding: 3rem;
  text-align: center;
  max-width: 400px;
  width: 90%;
}

.game-over-card h2 {
  font-size: 2rem;
  margin-bottom: 1rem;
  color: var(--text-primary);
}

.final-score {
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--primary-color);
  margin-bottom: 0.5rem;
}

.final-rating {
  font-size: 1.125rem;
  color: var(--text-secondary);
  margin-bottom: 2rem;
}

.restart-btn {
  padding: 0.75rem 2rem;
  font-size: 1rem;
}

@media (max-width: 768px) {
  .game-title {
    font-size: 2rem;
  }
  
  .game-controls {
    flex-direction: column;
    align-items: stretch;
  }
  
  .comparison-section {
    flex-direction: column;
  }
  
  .anime-card {
    max-width: 100%;
  }
}
</style>