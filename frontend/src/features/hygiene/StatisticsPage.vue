<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { ElButton } from 'element-plus'
import { request } from '../../lib/http'
import type { Building, Room, PageResult } from '../housing/types'
import type { PeriodKind } from './result-types'
import type { ManagementStatistics } from './statistics-types'
import ResultTrend from './ResultTrend.vue'
import ResultWeeks from './ResultWeeks.vue'

const result = ref<ManagementStatistics | null>(null), loading = ref(false), error = ref(''), optionsError = ref('')
const buildings = ref<Building[]>([]), rooms = ref<Room[]>([]), optionsLoading = ref(false)
const kind = ref<PeriodKind>('WEEK'), date = ref(''), buildingId = ref(''), roomId = ref(''), page = ref(1), trendId = ref(0)
let requestId = 0, optionsId = 0
let applied = { kind: kind.value, date: '', buildingId: '', roomId: '' }
const roomOptions = computed(() => rooms.value.filter(r => !buildingId.value || String(r.buildingId) === buildingId.value))
const series = computed(() => result.value?.trend.find(t => t.buildingId === trendId.value) ?? result.value?.trend[0])
const number = (value: number | null) => value === null ? '—' : value.toFixed(2)
const scopeLabel = computed(() => result.value?.scope.roomId ? `${result.value.scope.buildingName} · ${result.value.scope.roomNo}` : result.value?.scope.buildingName ?? '全校 · 按楼栋展示')
async function allOptions<T>(path: string): Promise<T[]> {
  const rows: T[] = []
  for (let page = 1; ; page++) {
    const next = await request<PageResult<T>>(`${path}?page=${page}&size=100`)
    rows.push(...next.items)
    if (rows.length >= next.total || !next.items.length) return rows
  }
}
async function loadOptions() {
  const id = ++optionsId; optionsLoading.value = true; optionsError.value = ''
  try {
    const [nextBuildings, nextRooms] = await Promise.all([allOptions<Building>('/api/housing/buildings'), allOptions<Room>('/api/housing/rooms')])
    if (id !== optionsId) return
    buildings.value = nextBuildings; rooms.value = nextRooms
  } catch (failure) { if (id === optionsId) optionsError.value = failure instanceof Error ? failure.message : '范围读取失败' }
  finally { if (id === optionsId) optionsLoading.value = false }
}
async function load() {
  const id = ++requestId; loading.value = true; error.value = ''; result.value = null
  const params = new URLSearchParams({ period: applied.kind, page: String(page.value), size: '10' })
  for (const key of ['date', 'buildingId', 'roomId'] as const) if (applied[key]) params.set(key, applied[key])
  try {
    const next = await request<ManagementStatistics>(`/api/hygiene/statistics?${params}`)
    if (id !== requestId) return
    const last = Math.max(1, Math.ceil(next.rankings.total / next.rankings.size))
    if (page.value > last) { page.value = last; await load(); return }
    result.value = next
    if (!applied.date) { applied.date = next.period.start; date.value = applied.date }
    if (!next.trend.some(t => t.buildingId === trendId.value)) trendId.value = next.trend[0]?.buildingId ?? 0
  } catch (failure) { if (id === requestId) error.value = failure instanceof Error ? failure.message : '统计读取失败，请重试' }
  finally { if (id === requestId) loading.value = false }
}
function query() { applied = { kind: kind.value, date: date.value, buildingId: buildingId.value, roomId: roomId.value }; page.value = 1; void load() }
function refresh() { void load() }
function turnPage(offset: number) { page.value += offset; void load() }
function drill(building: number, room = '') {
  if (!result.value) return
  kind.value = result.value.period.kind; date.value = result.value.period.start
  buildingId.value = String(building); roomId.value = room; query()
}
onMounted(() => { void loadOptions(); void load(); window.addEventListener('focus', refresh) })
onUnmounted(() => { requestId++; optionsId++; window.removeEventListener('focus', refresh) })
</script>

<template>
  <section class="page-head account-heading"><div><p class="eyebrow">HYGIENE STATISTICS / 卫生统计</p><h1>卫生统计</h1><p class="subtitle">从楼栋到寝室，核对成绩、检查覆盖与原始标准。</p></div><ElButton :loading="loading" :disabled="loading" @click="refresh">刷新统计</ElButton></section>
  <form class="card statistics-filters" @submit.prevent="query">
    <label>统计周期<select v-model="kind" :disabled="loading" aria-label="统计周期"><option value="WEEK">按周</option><option value="MONTH">按月</option></select></label>
    <label>周期内日期<input v-model="date" type="date" min="1901-01-01" max="9998-12-31" :disabled="loading" aria-label="周期内日期" /></label>
    <label>楼栋范围<select v-model="buildingId" :disabled="loading || optionsLoading" aria-label="楼栋范围" @change="roomId = ''"><option value="">全校</option><option v-for="b in buildings" :key="b.id" :value="String(b.id)">{{ b.name }}{{ b.enabled ? '' : '（已停用）' }}</option></select></label>
    <label>寝室范围<select v-model="roomId" :disabled="loading || optionsLoading" aria-label="寝室范围"><option value="">全部寝室</option><option v-for="r in roomOptions" :key="r.id" :value="String(r.id)">{{ r.buildingName }} · {{ r.roomNo }}{{ r.enabled ? '' : '（已停用）' }}</option></select></label>
    <ElButton native-type="submit" type="primary" :disabled="loading || optionsLoading">查询统计</ElButton>
    <p class="form-hint">周按 ISO 年周，月按检查周一归属。修改范围后点击查询；刷新沿用已查询范围。</p>
    <p v-if="optionsError" class="message error" role="alert">{{ optionsError }} <button type="button" class="text-button" @click="loadOptions">重试范围</button></p>
  </form>
  <section v-if="loading" class="card" role="status">正在读取统计…</section>
  <section v-else-if="error" class="card"><p class="message error" role="alert">{{ error }}</p><ElButton @click="refresh">重新加载</ElButton></section>
  <template v-else-if="result">
    <section class="card statistics-overview" data-testid="statistics-summary">
      <div><p class="eyebrow">{{ result.period.label }}</p><h2>{{ scopeLabel }}</h2><p class="form-hint">{{ result.period.start }} 至 {{ result.period.end }}（不含末日）</p></div>
      <div><strong>{{ result.summary.participants }} <small>间</small></strong><p>有效参评 {{ result.summary.samples }} 次</p></div>
      <div><strong>{{ number(result.summary.progress.completionRate) }}<small v-if="result.summary.progress.completionRate !== null">%</small></strong><p>{{ result.summary.progress.expected ? `已检 ${result.summary.progress.inspected} / 应检 ${result.summary.progress.expected} 寝室次` : '暂无应检寝室' }}</p><small>未检 {{ result.summary.progress.missing }} 寝室次 · 停检已排除</small></div>
    </section>
    <section v-if="!result.scope.roomId" class="card statistics-card">
      <h2>楼栋成绩对比</h2><p class="form-hint">{{ result.period.label }} · 先算各寝室周期分，再对有成绩寝室等权平均。缺检不算零分。</p>
      <p v-if="!result.buildings.length" class="empty-state">本周期暂无有效应检记录，数据不足。</p>
      <table v-else class="account-table" aria-label="楼栋成绩对比"><thead><tr><th>楼栋</th><th>楼栋均分</th><th>参评寝室 / 次数</th><th>已检 / 应检</th><th>完成率</th><th>查看</th></tr></thead><tbody>
        <tr v-for="b in result.buildings" :key="b.buildingId"><td>{{ b.buildingName }}</td><td><b>{{ number(b.score) }}</b><div v-if="b.score !== null" class="score-track" aria-hidden="true"><span :style="{ width: `${b.score}%` }" /></div><small v-else>未检查 / 数据不足</small></td><td>{{ b.participants }} 间 / {{ b.samples }} 次</td><td>{{ b.progress.inspected }} / {{ b.progress.expected }}<small>{{ b.progress.expected ? `未检 ${b.progress.missing} 寝室次` : '暂无应检寝室' }}</small></td><td>{{ b.progress.completionRate === null ? '—' : `${number(b.progress.completionRate)}%` }}</td><td><button class="text-button" type="button" @click="drill(b.buildingId)">查看楼栋</button></td></tr>
      </tbody></table>
    </section>
    <div v-if="result.trend.length > 1" class="trend-selection"><label>趋势楼栋 <select v-model="trendId" aria-label="趋势楼栋"><option v-for="t in result.trend" :key="t.buildingId" :value="t.buildingId">{{ t.buildingName }}</option></select></label></div>
    <div v-if="series"><p class="form-hint">{{ series.buildingName }}{{ series.roomNo ? ` · ${series.roomNo} · 寝室周期成绩` : ' · 楼栋等权均分' }}</p><ResultTrend :points="series.points" :period="result.period" /></div>
    <section v-else class="card statistics-card"><h2>历史趋势</h2><p class="empty-state">近12个周期无有效样本，暂无趋势。</p></section>
    <section class="card statistics-card"><h2>寝室成绩排行</h2><p class="form-hint">{{ result.period.label }} · {{ result.rankings.total }} 间参评。名次始终为各楼栋内排名，同分并列；跨楼栋分别展示。</p>
      <p v-if="!result.rankings.total" class="empty-state">本周期未检查 / 数据不足，暂无参评寝室。</p>
      <table v-else class="account-table" aria-label="寝室成绩排行"><thead><tr><th>楼栋</th><th>楼内名次</th><th>寝室</th><th>周期成绩</th><th>有效次数</th><th>明细</th></tr></thead><tbody><tr v-for="r in result.rankings.items" :key="r.roomId"><td>{{ r.buildingName }}</td><td>{{ r.rank }}</td><td>{{ r.roomNo }}</td><td>{{ number(r.score) }}</td><td>{{ r.samples }}</td><td><button class="text-button" type="button" @click="drill(r.buildingId, String(r.roomId))">查看寝室</button></td></tr></tbody></table>
      <div class="pagination"><ElButton :disabled="page <= 1" @click="turnPage(-1)">上一页</ElButton><span>第 {{ page }} / {{ Math.max(1, Math.ceil(result.rankings.total / result.rankings.size)) }} 页</span><ElButton :disabled="page * result.rankings.size >= result.rankings.total" @click="turnPage(1)">下一页</ElButton></div>
    </section>
    <div v-if="result.scope.roomId" class="statistics-card"><ResultWeeks :weeks="result.weeks" :period="result.period" :samples="result.summary.samples" /></div>
    <section class="card statistics-card"><h2>同方案维度对比</h2><p class="form-hint">{{ result.period.label }} · 每次有效正式维度评分等权平均，保留两位。按方案版本、楼栋及原始维度分别统计；当前寝室筛选同样生效。</p>
      <p v-if="!result.dimensions.length" class="empty-state">本周期无有效维度样本。</p>
      <section v-for="group in result.dimensions" :key="`${group.schemeId}-${group.buildingId}`" class="dimension-group" :aria-label="`${group.schemeName} 版本 ${group.schemeId} ${group.buildingName}`">
        <h3>{{ group.schemeName }} <span class="role-badge">版本 #{{ group.schemeId }}</span></h3><p class="form-hint">{{ group.buildingName }}{{ result.scope.roomNo ? ` · ${result.scope.roomNo}` : '' }}</p>
        <table class="account-table" :aria-label="`${group.schemeName}维度均分`"><thead><tr><th>原始维度</th><th>原始权重</th><th>维度均分</th><th>有效样本</th></tr></thead><tbody><tr v-for="d in group.dimensions" :key="d.dimensionId"><td>{{ d.name }}</td><td>{{ number(d.weight) }}%</td><td>{{ number(d.score) }}<div class="score-track" aria-hidden="true"><span :style="{ width: `${d.score}%` }" /></div></td><td>{{ d.samples }} 次</td></tr></tbody></table>
      </section>
    </section>
  </template>
</template>

<style scoped>
.statistics-filters { display: flex; align-items: end; gap: 16px; flex-wrap: wrap; margin-bottom: 24px; }
.statistics-filters label { display: grid; gap: 8px; font-size: 13px; min-width: 0; }.statistics-filters p { flex-basis: 100%; margin: 0; }
input, select { box-sizing: border-box; width: 180px; min-height: 44px; padding: 10px 12px; border: 0; border-radius: 12px; background: var(--base); color: var(--text); box-shadow: var(--pressed); font: inherit; }
input:focus-visible,select:focus-visible { outline: 2px solid var(--primary); outline-offset: 2px; }
.statistics-overview { display: grid; grid-template-columns: minmax(0,1fr) 180px 240px; gap: 24px; align-items: center; margin-bottom: 24px; }.statistics-overview strong { font-size: 32px; font-weight: 500; color: var(--primary); }.statistics-overview small { font-size: 12px; }.statistics-overview p,.statistics-overview small { color: var(--muted); }
.statistics-card { margin-bottom: 24px; }.statistics-card td,.statistics-card th { padding: 14px 12px; overflow-wrap: anywhere; }.statistics-card small { display: block; margin-top: 5px; color: var(--muted); }.statistics-overview h2,.dimension-group h3 { overflow-wrap: anywhere; }
.statistics-card .account-table th:first-child,.statistics-card .account-table th:last-child { width: auto; }
.score-track { height: 5px; margin-top: 8px; width: 100%; max-width: 150px; background: #d9e0e7; border-radius: 4px; overflow: hidden; }.score-track span { height: 100%; background: var(--primary); display: block; }
.pagination { display: flex; justify-content: space-between; align-items: center; margin-top: 20px; color: var(--muted); font-size: 12px; }.dimension-group { margin-top: 26px; padding-top: 16px; border-top: 1px solid #d4dce6; }.dimension-group h3 { display: flex; align-items: center; gap: 12px; }.trend-selection { margin-bottom: 16px; }
@media (max-width: 1400px) { .statistics-overview { grid-template-columns: minmax(0,1fr) 140px 220px; }.statistics-filters input,.statistics-filters select { width: 160px; } }
</style>
