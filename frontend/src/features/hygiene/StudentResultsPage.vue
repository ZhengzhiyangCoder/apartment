<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ElButton } from 'element-plus'
import { HttpError, request } from '../../lib/http'
import ResultTrend from './ResultTrend.vue'
import ResultWeeks from './ResultWeeks.vue'
import type { PeriodKind, StudentResults } from './result-types'

const result = ref<StudentResults | null>(null), loading = ref(false), error = ref(''), notResident = ref(false)
const kind = ref<PeriodKind>('WEEK'), date = ref(''), page = ref(1)
let requestId = 0
const number = (value: number | null) => value === null ? '—' : value.toFixed(2)
async function load(resetPage = false) {
  if (resetPage) page.value = 1
  const id = ++requestId
  loading.value = true; error.value = ''; notResident.value = false; result.value = null
  const params = new URLSearchParams({ period: kind.value, page: String(page.value), size: '10' })
  if (date.value) params.set('date', date.value)
  try {
    const next = await request<StudentResults>(`/api/hygiene/student/results?${params}`)
    if (id !== requestId) return
    // A shrinking leaderboard should not leave a previously valid last page empty.
    const lastPage = Math.max(1, Math.ceil(next.rankings.total / next.rankings.size))
    if (page.value > lastPage) {
      page.value = lastPage; await load(); return
    }
    result.value = next
    if (!date.value) date.value = next.period.start
  } catch (failure) {
    if (id !== requestId) return
    if (failure instanceof HttpError && failure.code === 'NOT_RESIDENT') notResident.value = true
    else error.value = failure instanceof Error ? failure.message : '成绩读取失败，请重试'
  } finally { if (id === requestId) loading.value = false }
}
function refresh() { void load() }
function turnPage(offset: number) { page.value += offset; void load() }
onMounted(() => { void load(); window.addEventListener('focus', refresh) })
onUnmounted(() => { requestId++; window.removeEventListener('focus', refresh) })
</script>

<template>
  <section class="page-head account-heading"><div><p class="eyebrow">ROOM RESULTS / 寝室成绩</p><h1>寝室成绩与排行</h1><p class="subtitle">回看每一次检查，了解当前寝室的卫生表现。</p></div><ElButton :loading="loading" :disabled="loading" @click="refresh">刷新成绩</ElButton></section>
  <form class="card result-filters" @submit.prevent="load(true)">
    <label>统计周期<select v-model="kind" :disabled="loading" aria-label="统计周期"><option value="WEEK">按周</option><option value="MONTH">按月</option></select></label>
    <label>周期内日期<input v-model="date" type="date" min="1901-01-01" max="9998-12-31" :disabled="loading" aria-label="周期内日期" /></label>
    <ElButton native-type="submit" type="primary" :disabled="loading">查询成绩</ElButton>
    <p class="form-hint">选择周期内任意日期。周按 ISO 年周，月按检查周一归属。</p>
  </form>
  <section v-if="loading" class="card" role="status">正在核对当前住宿并读取成绩…</section>
  <section v-else-if="notResident" class="card"><h2>尚未入住</h2><p class="subtitle">请联系系统管理员办理入住，入住后可查看当前寝室成绩与当前楼栋排行。</p><RouterLink to="/my-residency" class="text-button">查看我的住宿</RouterLink><p class="form-hint">本人历史报修仍可在“我的报修”查看。</p></section>
  <section v-else-if="error" class="card"><p class="message error" role="alert">{{ error }}</p><ElButton @click="refresh">重新加载</ElButton></section>
  <template v-else-if="result">
    <section class="card result-overview" data-testid="result-summary">
      <div><p class="eyebrow">{{ result.period.label }} · {{ result.period.kind === 'MONTH' ? '月成绩' : '周成绩' }}</p><h2>{{ result.scope.buildingName }} · {{ result.scope.roomNo }}</h2><p class="form-hint">归属范围：{{ result.period.start }} 至 {{ result.period.end }}（不含末日）</p></div>
      <div><strong class="result-score">{{ number(result.summary.score) }}</strong><p>{{ result.summary.score === null ? '未检查 / 数据不足' : '周期综合成绩' }}</p></div>
      <div><b>{{ result.summary.rank === null ? '暂不参与排名' : `第 ${result.summary.rank} 名` }}</b><p>有效参评 {{ result.summary.samples }} 次</p><small>楼栋参评 {{ result.rankings.total }} 间</small></div>
    </section>
    <ResultTrend :points="result.trend" :period="result.period" />
    <div class="results-grid">
      <ResultWeeks :weeks="result.weeks" :period="result.period" :samples="result.summary.samples" />
      <section class="card result-table-card"><h2>楼栋排行榜</h2><p class="form-hint">{{ result.scope.buildingName }} · {{ result.period.label }} · {{ result.rankings.total }} 间参评</p>
        <p v-if="!result.rankings.total" class="empty-state">本周期未检查 / 数据不足，暂无参评寝室。</p>
        <table v-else class="account-table" aria-label="楼栋排行榜"><thead><tr><th>名次</th><th>寝室编号</th><th>周期成绩</th><th>有效次数</th></tr></thead><tbody><tr v-for="(row, index) in result.rankings.items" :key="index"><td>{{ row.rank }}</td><td>{{ row.roomNo }}</td><td>{{ number(row.score) }}</td><td>{{ row.samples }}</td></tr></tbody></table>
        <div class="pagination"><ElButton :disabled="loading || page <= 1" @click="turnPage(-1)">上一页</ElButton><span>第 {{ page }} / {{ Math.max(1, Math.ceil(result.rankings.total / result.rankings.size)) }} 页</span><ElButton :disabled="loading || page * result.rankings.size >= result.rankings.total" @click="turnPage(1)">下一页</ElButton></div>
        <p class="form-hint">成绩保留两位后排名，同分并列，后续名次顺延；同分按房号稳定展示。</p>
      </section>
    </div>
  </template>
</template>

<style scoped>
.result-filters { display: flex; align-items: end; gap: 20px; margin-bottom: 24px; flex-wrap: wrap; }
.result-filters label { display: grid; gap: 8px; font-size: 13px; }
.result-filters input,.result-filters select { box-sizing: border-box; width: 180px; min-height: 44px; padding: 10px 16px; border: 0; border-radius: 12px; background: var(--base); color: var(--text); box-shadow: var(--pressed); font: inherit; }
.result-filters input:focus-visible,.result-filters select:focus-visible { outline: 2px solid var(--primary); outline-offset: 2px; }
.result-filters p { margin-bottom: 10px; flex: 1; min-width: 200px; }
.result-overview { display: grid; grid-template-columns: minmax(0, 1fr) 170px 170px; align-items: center; gap: 24px; margin-bottom: 24px; }
.result-overview h2 { overflow-wrap: anywhere; }.result-overview p,.result-overview small { color: var(--muted); }
.result-score { color: var(--primary); font-size: 42px; font-weight: 500; }
.results-grid { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 24px; }
.result-table-card { padding: 24px; min-width: 0; }.result-table-card td,.result-table-card th { padding: 14px 8px; overflow-wrap: anywhere; }
.result-table-card small { display: block; margin-top: 6px; color: var(--muted); font-size: 12px; }
.pagination { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin: 20px 0; color: var(--muted); font-size: 12px; }
@media (max-width: 1400px) { .results-grid { grid-template-columns: minmax(0, 1fr); }.result-overview { grid-template-columns: minmax(0, 1fr) 140px 150px; } }
</style>
