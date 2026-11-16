<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElButton, ElDialog, ElInput } from 'element-plus'
import { HttpError, request, write } from '../../lib/http'
import { currentUser, handleAuthorizationFailure } from '../identity/session'
import { batchStatuses, type Batch, type CurrentBatch, type InspectionPage, type InspectionDetail, type FormalScore } from './batch-types'

const router = useRouter(), isManager = computed(() => currentUser.value?.role === 'DORM_MANAGER')
const current = ref<CurrentBatch | null>(null), loading = ref(false), error = ref(''), success = ref('')
const query = reactive({ q: '', buildingId: '', inspected: '', size: 10 })
const result = ref<InspectionPage>({ items: [], total: 0, page: 1, size: 10 })
const pages = computed(() => Math.max(1, Math.ceil(result.value.total / result.value.size)))
const prepareOpen = ref(false), preparing = ref(false), prepareError = ref('')
const entryOpen = ref(false), entryLoading = ref(false), saving = ref(false), entryError = ref(''), blocked = ref(false)
const entryBatch = ref<Batch | null>(null), entry = ref<InspectionDetail | null>(null), entryId = ref(0), batchId = ref(0)
const values = ref<Record<number, string>>({}), preserveInput = ref(false)
const authorize = (failure: unknown) => handleAuthorizationFailure(failure, router)
const message = (failure: unknown) => failure instanceof Error ? failure.message : '服务暂时不可用，请重试'
const decimal = (value: number) => Number(value).toFixed(2)
const time = (value: string) => new Date(value).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai' })
const canSubmit = computed(() => isManager.value && !!entryBatch.value?.scoringAllowed && entry.value?.score === null && !blocked.value)
function validScore(value: string | undefined) { return value !== undefined && /^\d{1,3}$/.test(value) && Number(value) <= 100 }
const preview = computed(() => {
  const dimensions = entryBatch.value?.dimensions
  if (!dimensions || dimensions.some(d => !validScore(values.value[d.id]))) return null
  // Integer hundredths of weight keep the preview exact. The server recalculates the saved score.
  const weighted = dimensions.reduce((sum, d) => sum + Math.round(d.weight * 100) * Number(values.value[d.id]), 0)
  return Math.floor((weighted + 50) / 100) / 100
})
async function load(page = 1) {
  if (loading.value) return
  loading.value = true; error.value = ''; current.value = null; result.value = { items: [], total: 0, page, size: query.size }
  try {
    const data = await request<CurrentBatch>('/api/hygiene/batches/current')
    if (data.batch) {
      const params = new URLSearchParams({ q: query.q, page: String(page), size: String(query.size) })
      if (query.buildingId) params.set('buildingId', query.buildingId)
      if (query.inspected) params.set('inspected', query.inspected)
      let rows = await request<InspectionPage>(`/api/hygiene/batches/${data.batch.id}/rooms?${params}`)
      const last = Math.max(1, Math.ceil(rows.total / rows.size))
      if (page > last) { params.set('page', String(last)); rows = await request<InspectionPage>(`/api/hygiene/batches/${data.batch.id}/rooms?${params}`) }
      result.value = rows
    }
    current.value = data
  } catch (failure) { if (!await authorize(failure)) error.value = message(failure) }
  finally { loading.value = false }
}
async function prepare() {
  if (preparing.value) return
  preparing.value = true; prepareError.value = ''
  try {
    await write<Batch>('/api/hygiene/batches/current/prepare', '{}', 'application/json')
    prepareOpen.value = false; success.value = '本周检查已准备，名单与评分标准已固定。'; await load()
  } catch (failure) { if (!await authorize(failure)) prepareError.value = message(failure) }
  finally { preparing.value = false }
}
async function openRoom(id: number) {
  if (!current.value?.batch || entryLoading.value) return
  entryId.value = id; batchId.value = current.value.batch.id; entryOpen.value = true; values.value = {}; preserveInput.value = false
  await refreshEntry()
}
async function refreshEntry() {
  if (entryLoading.value || saving.value) return
  entryLoading.value = true; entryError.value = ''; blocked.value = false; entry.value = null; entryBatch.value = null
  try {
    const [batch, detail] = await Promise.all([
      request<Batch>(`/api/hygiene/batches/${batchId.value}`),
      request<InspectionDetail>(`/api/hygiene/batches/${batchId.value}/rooms/${entryId.value}`),
    ])
    entryBatch.value = batch; entry.value = detail
    if (current.value?.batch?.id === batch.id) current.value.batch = batch
    for (const dimension of batch.dimensions) values.value[dimension.id] ??= ''
    if (!batch.scoringAllowed && !detail.score) entryError.value = '该批次当前不可录分。已输入分值仍保留，请返回刷新本周。'
  } catch (failure) { if (!await authorize(failure)) entryError.value = message(failure) }
  finally { entryLoading.value = false }
}
async function submit() {
  if (!canSubmit.value || !entryBatch.value || saving.value || entryLoading.value) return
  entryError.value = ''; preserveInput.value = true
  if (preview.value === null) { entryError.value = '请为每个维度填写0–100的整数分值。'; return }
  saving.value = true
  try {
    const dimensions = entryBatch.value.dimensions.map(d => ({ dimensionId: d.id, score: Number(values.value[d.id]) }))
    const saved = await write<FormalScore>(`/api/hygiene/batches/${batchId.value}/rooms/${entryId.value}/score`, JSON.stringify({ dimensions }), 'application/json')
    entryOpen.value = false; success.value = `正式评分已保存：${decimal(saved.totalScore)}分。`; await load(result.value.page)
  } catch (failure) {
    if (!await authorize(failure)) {
      entryError.value = message(failure)
      if (failure instanceof HttpError && [404, 409].includes(failure.status)) blocked.value = true
    }
  } finally { saving.value = false }
}
onMounted(() => load())
</script>

<template>
  <section class="page-head account-heading"><div><p class="eyebrow">HYGIENE / 每周检查</p><h1>本周卫生检查</h1><p class="subtitle">{{ isManager ? '按本周固定名单检查寝室，提交后保留评分依据。' : '准备全校检查名单，查看宿管部已检与未检进度。' }}</p></div><ElButton :disabled="loading || preparing" @click="load(result.page)">刷新本周</ElButton></section>
  <p v-if="error" class="message error" role="alert">{{ error }} <button class="text-button" @click="load()">重新加载</button></p>
  <p v-if="success" class="message success" role="status">{{ success }}</p>
  <section v-if="loading" class="card account-card" role="status">正在读取本周检查…</section>
  <template v-else-if="current">
    <section v-if="!current.batch" class="card inspection-unprepared"><p class="eyebrow">{{ current.isoYear }} · 第 {{ current.isoWeek }} 周</p><h2>本周尚未准备检查批次</h2><p>本周归属日：{{ current.weekStart }}（周一，上海时间）。</p><p class="subtitle">准备时固定当前启用且有人入住的寝室与启用评分方案。</p><ElButton @click="prepareError = ''; prepareOpen = true">准备本周检查</ElButton></section>
    <template v-else>
      <section class="card inspection-overview"><div data-testid="batch-standard"><p class="eyebrow">{{ current.batch.isoYear }} · 第 {{ current.batch.isoWeek }} 周 <span class="account-status">{{ batchStatuses[current.batch.status] }}</span></p><h2>#{{ current.batch.schemeId }} · {{ current.batch.schemeName }}</h2><p class="subtitle">归属日 {{ current.batch.weekStart }} · {{ current.batch.dimensions.length }}个维度 · 整改阈值 {{ decimal(current.batch.threshold) }}分</p><p class="form-hint">本周名单与标准已固定，后续入住和方案变更不改写本批次。</p></div><div class="inspection-progress" data-testid="batch-progress"><span>本周已检 / 应检</span><strong>{{ current.batch.inspectedCount }} / {{ current.batch.expectedCount }}</strong><template v-if="current.batch.completionRate !== null"><progress max="100" :value="current.batch.completionRate" aria-label="本周检查完成率" /><span>完成率 {{ decimal(current.batch.completionRate) }}%</span></template><span v-else>无应检寝室</span></div></section>
      <p v-if="!current.batch.scoringAllowed" class="message">当前批次不可普通录分，请刷新本周检查。</p>
      <section class="card account-card">
        <h2 v-if="current.batch.expectedCount === 0" class="account-empty">本批次无应检寝室</h2>
        <template v-else>
          <form class="account-filters" @submit.prevent="load()"><div class="field search-field"><label for="inspection-search">搜索应检寝室</label><ElInput id="inspection-search" v-model="query.q" maxlength="64" placeholder="按楼栋或房号搜索" /></div><div class="field"><label for="inspection-building">所属楼栋</label><select id="inspection-building" v-model="query.buildingId"><option value="">全校楼栋</option><option v-for="building in current.batch.buildings" :key="building.id" :value="String(building.id)">{{ building.name }}</option></select></div><div class="field"><label for="inspection-state">检查状态</label><select id="inspection-state" v-model="query.inspected"><option value="">全部</option><option value="false">未检查</option><option value="true">已检查</option></select></div><ElButton native-type="submit">查询</ElButton></form>
          <div class="account-table-wrap"><table class="account-table inspection-table"><caption class="sr-only">本周应检寝室</caption><thead><tr><th>楼栋与寝室</th><th>检查状态</th><th>综合得分</th><th>操作</th></tr></thead><tbody><tr v-for="room in result.items" :key="room.roomId"><td><b>{{ room.roomNo }}</b><span class="account-name">{{ room.buildingName }}</span></td><td><span class="account-status" :class="{ inactive: room.scoreId === null }">{{ room.scoreId === null ? '未检查' : '已检查' }}</span></td><td>{{ room.totalScore === null ? '—' : `${decimal(room.totalScore)}分` }}</td><td><div class="account-actions"><button v-if="isManager && current.batch.scoringAllowed && room.scoreId === null" @click="openRoom(room.roomId)">录入评分</button><button v-else @click="openRoom(room.roomId)">{{ room.scoreId === null ? '查看标准' : '查看结果' }}</button></div></td></tr></tbody></table><p v-if="!result.items.length" class="account-empty">暂无符合条件的应检寝室。</p></div>
          <div class="account-pagination"><span>共 {{ result.total }} 条记录</span><label>每页 <select v-model.number="query.size" aria-label="每页条数" @change="load()"><option :value="10">10</option><option :value="20">20</option><option :value="50">50</option></select> 条</label><div><ElButton :disabled="result.page <= 1" @click="load(result.page - 1)">上一页</ElButton><span>{{ result.page }} / {{ pages }}</span><ElButton :disabled="result.page >= pages" @click="load(result.page + 1)">下一页</ElButton></div></div>
        </template>
      </section>
    </template>
  </template>
  <ElDialog v-model="prepareOpen" title="准备本周检查" width="560px" class="account-dialog" :close-on-click-modal="false" :close-on-press-escape="!preparing" :show-close="!preparing" destroy-on-close>
    <p>将固定当前启用且有学生入住的应检寝室，并绑定当前启用评分标准。</p><p class="form-hint">本周已有批次时直接进入，不重复创建。请先核对入住资料与评分方案。</p><p v-if="prepareError" class="message error" role="alert">{{ prepareError }}</p><div class="form-actions"><ElButton :disabled="preparing" :loading="preparing" @click="prepare">确认准备本周</ElButton><ElButton :disabled="preparing" @click="prepareOpen = false">返回</ElButton></div>
  </ElDialog>
  <ElDialog v-model="entryOpen" title="寝室正式评分" width="700px" class="account-dialog" :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving" destroy-on-close>
    <p v-if="entryLoading" role="status">正在核对批次与寝室…</p>
    <template v-if="entry && entryBatch"><div class="repair-detail-head"><h2>{{ entry.room.buildingName }} · {{ entry.room.roomNo }}</h2><span class="account-status">{{ entry.score ? '已检查' : '未检查' }}</span></div><p class="form-hint">{{ entryBatch.isoYear }}年第{{ entryBatch.isoWeek }}周 · 归属日 {{ entryBatch.weekStart }}<br />标准 #{{ entryBatch.schemeId }} · {{ entryBatch.schemeName }} · 阈值 {{ decimal(entryBatch.threshold) }}分</p>
      <template v-if="entry.score"><p class="inspection-saved">正式得分 <strong>{{ decimal(entry.score.totalScore) }}</strong>分</p><p class="form-hint">实际提交：{{ time(entry.score.submittedAt) }}（上海时间） · 记录 #{{ entry.score.id }}</p><table class="account-table"><caption class="sr-only">正式评分依据</caption><thead><tr><th>维度</th><th>权重</th><th>分值</th></tr></thead><tbody><tr v-for="d in entry.score.dimensions" :key="d.dimensionId"><td>{{ d.name }}</td><td>{{ decimal(d.weight) }}%</td><td>{{ d.score }}</td></tr></tbody></table></template>
      <template v-else-if="!isManager"><p class="form-hint">该寝室尚未检查，由宿舍管理员提交正式评分。</p><p v-for="d in entryBatch.dimensions" :key="d.id">{{ d.name }} · {{ decimal(d.weight) }}%</p></template>
      <form v-if="isManager && (!entry.score || preserveInput)" class="form-stack" @submit.prevent="submit"><p v-if="entry.score" class="form-hint">以下为本次未提交输入，已保留供核对；正式结果以上方为准。</p><div v-for="d in entryBatch.dimensions" :key="d.id" class="inspection-dimension"><div><b>{{ d.name }}</b><span class="account-name">权重 {{ decimal(d.weight) }}%</span></div><div class="field"><label :for="`inspection-value-${d.id}`">{{ d.name }}分值</label><ElInput :id="`inspection-value-${d.id}`" v-model="values[d.id]" inputmode="numeric" maxlength="4" placeholder="0–100整数" :disabled="saving || !canSubmit" /></div></div><p class="inspection-preview" data-testid="score-preview">综合分预览：<strong>{{ preview === null ? '请填写全部整数分值' : decimal(preview) }}</strong></p><p class="form-hint">提交后记为正式检查；同一寝室本周只能提交一份正式评分。</p><ElButton v-if="canSubmit" native-type="submit" :disabled="saving || entryLoading" :loading="saving">提交正式评分</ElButton></form>
    </template>
    <p v-if="entryError" class="message error" role="alert">{{ entryError }}</p><div class="form-actions"><ElButton v-if="entryError" :disabled="entryLoading || saving" @click="refreshEntry">核对最新状态（保留输入）</ElButton><ElButton :disabled="saving" @click="entryOpen = false">返回</ElButton></div>
  </ElDialog>
</template>
