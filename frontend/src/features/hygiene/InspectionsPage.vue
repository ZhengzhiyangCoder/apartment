<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElButton, ElDialog, ElInput } from 'element-plus'
import { HttpError, request, write } from '../../lib/http'
import { currentUser, handleAuthorizationFailure } from '../identity/session'
import { batchStatuses, type Batch, type CurrentBatch, type InspectionPage, type InspectionDetail, type FormalScore, type PersonalDraft, type DraftPage } from './batch-types'
import BatchGovernance from './BatchGovernance.vue'

const router = useRouter(), isManager = computed(() => currentUser.value?.role === 'DORM_MANAGER')
const current = ref<CurrentBatch | null>(null), loading = ref(false), error = ref(''), success = ref('')
const selectedBatchId = ref<number | null>(null), governance = ref<InstanceType<typeof BatchGovernance> | null>(null)
const isHistorical = computed(() => !!current.value?.batch && current.value.batch.weekStart !== current.value.weekStart)
const query = reactive({ q: '', buildingId: '', inspected: '', size: 10 })
const result = ref<InspectionPage>({ items: [], total: 0, page: 1, size: 10 })
const pages = computed(() => Math.max(1, Math.ceil(result.value.total / result.value.size)))
const prepareOpen = ref(false), preparing = ref(false), prepareError = ref('')
const entryOpen = ref(false), entryLoading = ref(false), saving = ref(false), entryError = ref(''), blocked = ref(false)
const entryBatch = ref<Batch | null>(null), entry = ref<InspectionDetail | null>(null), entryId = ref(0), batchId = ref(0)
const values = ref<Record<number, string>>({}), preserveInput = ref(false)
const draft = ref<PersonalDraft | null>(null), draftVersion = ref(0), draftLoaded = ref(false), entryNotice = ref('')
const draftPage = ref<DraftPage>({ items: [], total: 0, page: 1, size: 10 }), draftsLoading = ref(false), draftsError = ref('')
const appliedScope = ref({ q: '', buildingId: '' }), entryScope = ref({ q: '', buildingId: '' }), nextReady = ref(false)
const draftPages = computed(() => Math.max(1, Math.ceil(draftPage.value.total / draftPage.value.size)))
const entryExpected = ref(true)
const authorize = (failure: unknown) => handleAuthorizationFailure(failure, router)
const message = (failure: unknown) => failure instanceof Error ? failure.message : '服务暂时不可用，请重试'
const decimal = (value: number) => Number(value).toFixed(2)
const time = (value: string) => new Date(value).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai' })
const canSubmit = computed(() => isManager.value && entryExpected.value && !!entryBatch.value?.scoringAllowed && entry.value?.score === null && !blocked.value)
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
    if (selectedBatchId.value !== null) data.batch = await request<Batch>(`/api/hygiene/batches/${selectedBatchId.value}`)
    if (data.batch) {
      const params = new URLSearchParams({ q: query.q, page: String(page), size: String(query.size) })
      if (query.buildingId) params.set('buildingId', query.buildingId)
      if (query.inspected) params.set('inspected', query.inspected)
      let rows = await request<InspectionPage>(`/api/hygiene/batches/${data.batch.id}/rooms?${params}`)
      const last = Math.max(1, Math.ceil(rows.total / rows.size))
      if (page > last) { params.set('page', String(last)); rows = await request<InspectionPage>(`/api/hygiene/batches/${data.batch.id}/rooms?${params}`) }
      result.value = rows
    }
    current.value = data; appliedScope.value = { q: query.q, buildingId: query.buildingId }
  } catch (failure) { if (!await authorize(failure)) error.value = message(failure) }
  finally { loading.value = false }
}
async function selectBatch(id: number | null) {
  selectedBatchId.value = id; query.q = ''; query.buildingId = ''; query.inspected = ''; success.value = ''
  await load()
}
async function governanceChanged() {
  success.value = '批次变更已保存，名单、进度与操作记录已更新。'
  await load(result.value.page); await loadDrafts()
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
async function loadDrafts(page = draftPage.value.page) {
  if (!isManager.value || draftsLoading.value) return
  draftsLoading.value = true; draftsError.value = ''
  try {
    let data = await request<DraftPage>(`/api/hygiene/drafts?page=${page}&size=10`)
    const last = Math.max(1, Math.ceil(data.total / data.size))
    if (page > last) data = await request<DraftPage>(`/api/hygiene/drafts?page=${last}&size=10`)
    draftPage.value = data
  } catch (failure) { if (!await authorize(failure)) draftsError.value = message(failure) }
  finally { draftsLoading.value = false }
}
function restoreDraft() {
  values.value = {}
  for (const d of entryBatch.value?.dimensions ?? []) values.value[d.id] = ''
  for (const d of draft.value?.dimensions ?? []) values.value[d.dimensionId] = d.value
  draftVersion.value = draft.value?.version ?? 0; draftLoaded.value = true; blocked.value = false
  preserveInput.value = draft.value !== null
  entryNotice.value = draft.value ? '已恢复本人服务端草稿。' : ''
}
async function openRoom(id: number, targetBatch = current.value?.batch?.id) {
  if (!targetBatch || entryLoading.value || saving.value) return
  entryId.value = id; batchId.value = targetBatch; entryOpen.value = true; values.value = {}; preserveInput.value = false
  draft.value = null; draftVersion.value = 0; draftLoaded.value = false; entryNotice.value = ''; nextReady.value = false
  entryScope.value = { ...appliedScope.value }
  await refreshEntry()
}
async function refreshEntry() {
  if (entryLoading.value) return
  entryLoading.value = true; entryError.value = ''; blocked.value = false; entry.value = null; entryBatch.value = null
  try {
    const [batch, detail, personal] = await Promise.all([
      request<Batch>(`/api/hygiene/batches/${batchId.value}`),
      request<InspectionDetail>(`/api/hygiene/batches/${batchId.value}/rooms/${entryId.value}`).catch(failure => {
        if (failure instanceof HttpError && failure.code === 'NOT_EXPECTED_ROOM') return null
        throw failure
      }),
      isManager.value ? request<{ draft: PersonalDraft | null }>(`/api/hygiene/batches/${batchId.value}/rooms/${entryId.value}/draft`) : Promise.resolve({ draft: null }),
    ])
    if (!detail && !personal.draft) throw new HttpError(404, '该寝室已移出应检名单，且没有本人草稿可查看。', 'NOT_EXPECTED_ROOM')
    entryExpected.value = detail !== null
    entryBatch.value = batch; entry.value = detail ?? { room: { roomId: entryId.value, buildingId: 0, buildingName: personal.draft!.buildingName, roomNo: personal.draft!.roomNo, scoreId: null, totalScore: null }, score: null }; draft.value = personal.draft
    if (!draftLoaded.value) restoreDraft()
    if (current.value?.batch?.id === batch.id) current.value.batch = batch
    for (const dimension of batch.dimensions) values.value[dimension.id] ??= ''
    if (!batch.scoringAllowed && !entry.value.score) entryError.value = '该批次当前不可录分。草稿与已输入分值仍保留，仅供核对。'
    if (!entryExpected.value) entryError.value = '该寝室已移出应检名单，个人草稿仅供查看，不可暂存或发布。'
    if (isManager.value && !entry.value.score && draftVersion.value !== (draft.value?.version ?? 0)) {
      blocked.value = true; entryError.value = '个人草稿已在另一窗口更新，当前输入仍保留。请核对后载入服务端草稿。'
    }
  } catch (failure) { if (!await authorize(failure)) entryError.value = message(failure) }
  finally { entryLoading.value = false }
}
async function saveDraft() {
  if (!canSubmit.value || !entryBatch.value || saving.value || entryLoading.value) return
  saving.value = true; entryError.value = ''; entryNotice.value = ''; preserveInput.value = true
  try {
    const dimensions = entryBatch.value.dimensions.map(d => ({ dimensionId: d.id, value: values.value[d.id] ?? '' }))
    draft.value = await write<PersonalDraft>(`/api/hygiene/batches/${batchId.value}/rooms/${entryId.value}/draft`, JSON.stringify({ version: draftVersion.value, dimensions }), 'application/json')
    draftVersion.value = draft.value.version; entryNotice.value = '个人草稿已保存，重新登录后可恢复；尚未计入检查进度。'
    await loadDrafts()
  } catch (failure) { await entryFailure(failure) }
  finally { saving.value = false }
}
async function entryFailure(failure: unknown) {
  if (!await authorize(failure)) {
    entryError.value = message(failure)
    if (failure instanceof HttpError && [404, 409].includes(failure.status)) blocked.value = true
  }
}
async function advanceNext() {
  // Always query the server after a confirmed save; never derive the queue from a stale page.
  const params = new URLSearchParams({ q: entryScope.value.q, inspected: 'false', page: '1', size: '1' })
  if (entryScope.value.buildingId) params.set('buildingId', entryScope.value.buildingId)
  try {
    const next = await request<InspectionPage>(`/api/hygiene/batches/${batchId.value}/rooms?${params}`)
    if (!next.items.length) { entryOpen.value = false; nextReady.value = false; success.value += ' 当前筛选范围已全部检查。'; return }
    entryId.value = next.items[0]!.roomId; values.value = {}; preserveInput.value = false; draftLoaded.value = false
    draft.value = null; draftVersion.value = 0; entryNotice.value = ''; nextReady.value = false
    await refreshEntry()
  } catch (failure) {
    if (!await authorize(failure)) entryError.value = `正式评分已保存，下一间暂未载入：${message(failure)}`
  }
}
async function continueNext() {
  if (saving.value || entryLoading.value) return
  saving.value = true; entryError.value = ''
  try { await advanceNext() } finally { saving.value = false }
}
async function submit(next = false) {
  if (!canSubmit.value || !entryBatch.value || saving.value || entryLoading.value) return
  entryError.value = ''; entryNotice.value = ''; preserveInput.value = true
  if (preview.value === null) { entryError.value = '请为每个维度填写0–100的整数分值。'; return }
  saving.value = true
  try {
    const dimensions = entryBatch.value.dimensions.map(d => ({ dimensionId: d.id, score: Number(values.value[d.id]) }))
    const saved = await write<FormalScore>(`/api/hygiene/batches/${batchId.value}/rooms/${entryId.value}/score`, JSON.stringify({ dimensions, draftVersion: draftVersion.value }), 'application/json')
    entry.value!.score = saved; preserveInput.value = false; draft.value = null; draftVersion.value = 0
    success.value = `正式评分已保存：${decimal(saved.totalScore)}分。`; nextReady.value = next
    await load(result.value.page); await loadDrafts()
    if (next) await advanceNext()
    else entryOpen.value = false
  } catch (failure) { await entryFailure(failure) }
  finally { saving.value = false }
}
onMounted(() => { void load(); void loadDrafts() })
</script>

<template>
  <section class="page-head account-heading"><div><p class="eyebrow">HYGIENE / 每周检查</p><h1>{{ isHistorical ? '历史卫生检查' : '本周卫生检查' }}</h1><p class="subtitle">{{ isManager ? '按所选批次的固定名单检查寝室，可暂存草稿或跨楼栋连续评分。' : '管理全校检查批次与名单，查看宿管部已检与未检进度。' }}</p></div><ElButton :disabled="loading || preparing" @click="load(result.page)">{{ isHistorical ? '刷新批次' : '刷新本周' }}</ElButton></section>
  <BatchGovernance ref="governance" :batch="current?.batch ?? null" :current-week="current?.weekStart" @select="selectBatch" @changed="governanceChanged" />
  <p v-if="error" class="message error" role="alert">{{ error }} <button class="text-button" @click="load()">重新加载</button></p>
  <p v-if="success" class="message success" role="status">{{ success }}</p>
  <section v-if="loading" class="card account-card" role="status">正在读取本周检查…</section>
  <template v-else-if="current">
    <section v-if="!current.batch" class="card inspection-unprepared"><p class="eyebrow">{{ current.isoYear }} · 第 {{ current.isoWeek }} 周</p><h2>本周尚未准备检查批次</h2><p>本周归属日：{{ current.weekStart }}（周一，上海时间）。</p><p class="subtitle">准备时固定当前启用且有人入住的寝室与启用评分方案。</p><ElButton @click="prepareError = ''; prepareOpen = true">准备本周检查</ElButton></section>
    <template v-else>
      <section class="card inspection-overview"><div data-testid="batch-standard"><p class="eyebrow">{{ current.batch.isoYear }} · 第 {{ current.batch.isoWeek }} 周 <span class="account-status">{{ batchStatuses[current.batch.status] }}</span></p><h2>#{{ current.batch.schemeId }} · {{ current.batch.schemeName }}</h2><p class="subtitle">归属日 {{ current.batch.weekStart }} · {{ current.batch.dimensions.length }}个维度 · 整改阈值 {{ decimal(current.batch.threshold) }}分</p><p class="form-hint">本批次名单与标准已固定；入住和方案变更不自动改写名单，明确调整见变更记录。</p></div><div class="inspection-progress" data-testid="batch-progress"><span>{{ isHistorical ? '本批次' : '本周' }}已检 / 应检</span><strong>{{ current.batch.inspectedCount }} / {{ current.batch.expectedCount }}</strong><template v-if="current.batch.completionRate !== null"><progress max="100" :value="current.batch.completionRate" aria-label="本周检查完成率" /><span>完成率 {{ decimal(current.batch.completionRate) }}%</span></template><span v-else>无应检寝室</span></div></section>
      <p v-if="!current.batch.scoringAllowed" class="message">当前批次不可录分。关闭、停检或未授权的旧周只可查看；历史补录须由系统管理员重开。</p>
      <p v-if="current.batch.status === 'SUSPENDED'" class="message">停检批次保留既有成绩和草稿，不纳入排名、平均、趋势有效样本及完成率考核。</p>
      <p v-else-if="isHistorical && current.batch.scoringAllowed" class="message success">已授权历史补录：沿用原周名单、方案和阈值，实际提交时间据实记录。</p>
      <section class="card account-card">
        <h2 v-if="current.batch.expectedCount === 0" class="account-empty">本批次无应检寝室</h2>
        <template v-else>
          <form class="account-filters" @submit.prevent="load()"><div class="field search-field"><label for="inspection-search">搜索应检寝室</label><ElInput id="inspection-search" v-model="query.q" maxlength="64" placeholder="按楼栋或房号搜索" /></div><div class="field"><label for="inspection-building">所属楼栋</label><select id="inspection-building" v-model="query.buildingId"><option value="">全校楼栋</option><option v-for="building in current.batch.buildings" :key="building.id" :value="String(building.id)">{{ building.name }}</option></select></div><div class="field"><label for="inspection-state">检查状态</label><select id="inspection-state" v-model="query.inspected"><option value="">全部</option><option value="false">未检查</option><option value="true">已检查</option></select></div><ElButton native-type="submit">查询</ElButton></form>
          <div class="account-table-wrap"><table class="account-table inspection-table"><caption class="sr-only">本周应检寝室</caption><thead><tr><th>楼栋与寝室</th><th>检查状态</th><th>综合得分</th><th>操作</th></tr></thead><tbody><tr v-for="room in result.items" :key="room.roomId"><td><b>{{ room.roomNo }}</b><span class="account-name">{{ room.buildingName }}</span></td><td><span class="account-status" :class="{ inactive: room.scoreId === null }">{{ room.scoreId === null ? '未检查' : '已检查' }}</span></td><td>{{ room.totalScore === null ? '—' : `${decimal(room.totalScore)}分` }}</td><td><div class="account-actions"><button v-if="isManager && current.batch.scoringAllowed && room.scoreId === null" @click="openRoom(room.roomId)">录入评分</button><button v-else @click="openRoom(room.roomId)">{{ room.scoreId === null ? '查看标准' : '查看结果' }}</button><button v-if="!isManager && room.scoreId === null" @click="governance?.openRemoval(room)">移出名单</button></div></td></tr></tbody></table><p v-if="!result.items.length" class="account-empty">暂无符合条件的应检寝室。</p></div>
          <div class="account-pagination"><span>共 {{ result.total }} 条记录</span><label>每页 <select v-model.number="query.size" aria-label="每页条数" @change="load()"><option :value="10">10</option><option :value="20">20</option><option :value="50">50</option></select> 条</label><div><ElButton :disabled="result.page <= 1" @click="load(result.page - 1)">上一页</ElButton><span>{{ result.page }} / {{ pages }}</span><ElButton :disabled="result.page >= pages" @click="load(result.page + 1)">下一页</ElButton></div></div>
        </template>
      </section>
    </template>
  </template>
  <section v-if="isManager" class="card account-card" data-testid="personal-drafts">
    <div class="page-head"><div><h2>我的评分草稿</h2><p class="form-hint">仅本人可见，包含历史批次；草稿不计正式成绩和检查进度。</p></div><ElButton :disabled="draftsLoading" @click="loadDrafts()">刷新草稿</ElButton></div>
    <p v-if="draftsLoading" role="status">正在读取个人草稿…</p>
    <p v-else-if="draftsError" class="message error" role="alert">{{ draftsError }}</p>
    <template v-else><div class="account-table-wrap"><table v-if="draftPage.items.length" class="account-table"><caption class="sr-only">个人评分草稿</caption><thead><tr><th>寝室</th><th>批次</th><th>暂存时间</th><th>操作</th></tr></thead><tbody><tr v-for="item in draftPage.items" :key="`${item.batchId}-${item.roomId}`"><td>{{ item.buildingName }} · {{ item.roomNo }}</td><td>{{ item.isoYear }}年第{{ item.isoWeek }}周 · {{ batchStatuses[item.status] }}</td><td>{{ time(item.updatedAt) }}</td><td><ElButton :disabled="entryLoading || saving" @click="openRoom(item.roomId, item.batchId)">恢复草稿</ElButton></td></tr></tbody></table><p v-else class="account-empty">暂无个人草稿。</p></div><div class="account-pagination"><span>共 {{ draftPage.total }} 份草稿</span><div><ElButton :disabled="draftPage.page <= 1" @click="loadDrafts(draftPage.page - 1)">上一页草稿</ElButton><span>{{ draftPage.page }} / {{ draftPages }}</span><ElButton :disabled="draftPage.page >= draftPages" @click="loadDrafts(draftPage.page + 1)">下一页草稿</ElButton></div></div></template>
  </section>
  <ElDialog v-model="prepareOpen" title="准备本周检查" width="560px" class="account-dialog" :close-on-click-modal="false" :close-on-press-escape="!preparing" :show-close="!preparing" destroy-on-close>
    <p>将固定当前启用且有学生入住的应检寝室，并绑定当前启用评分标准。</p><p class="form-hint">本周已有批次时直接进入，不重复创建。请先核对入住资料与评分方案。</p><p v-if="prepareError" class="message error" role="alert">{{ prepareError }}</p><div class="form-actions"><ElButton :disabled="preparing" :loading="preparing" @click="prepare">确认准备本周</ElButton><ElButton :disabled="preparing" @click="prepareOpen = false">返回</ElButton></div>
  </ElDialog>
  <ElDialog v-model="entryOpen" title="寝室正式评分" width="700px" class="account-dialog" :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving" destroy-on-close>
    <p v-if="entryLoading" role="status">正在核对批次与寝室…</p>
    <template v-if="entry && entryBatch"><div class="repair-detail-head"><h2>{{ entry.room.buildingName }} · {{ entry.room.roomNo }}</h2><span class="account-status">{{ !entryExpected ? '已移出名单' : entry.score ? '已检查' : '未检查' }}</span></div><p class="form-hint">{{ entryBatch.isoYear }}年第{{ entryBatch.isoWeek }}周 · 归属日 {{ entryBatch.weekStart }}<br />标准 #{{ entryBatch.schemeId }} · {{ entryBatch.schemeName }} · 阈值 {{ decimal(entryBatch.threshold) }}分</p>
      <template v-if="entry.score"><p class="inspection-saved">正式得分 <strong>{{ decimal(entry.score.totalScore) }}</strong>分</p><p class="form-hint">实际提交：{{ time(entry.score.submittedAt) }}（上海时间） · 记录 #{{ entry.score.id }}</p><table class="account-table"><caption class="sr-only">正式评分依据</caption><thead><tr><th>维度</th><th>权重</th><th>分值</th></tr></thead><tbody><tr v-for="d in entry.score.dimensions" :key="d.dimensionId"><td>{{ d.name }}</td><td>{{ decimal(d.weight) }}%</td><td>{{ d.score }}</td></tr></tbody></table></template>
      <template v-else-if="!isManager"><p class="form-hint">该寝室尚未检查，由宿舍管理员提交正式评分。</p><p v-for="d in entryBatch.dimensions" :key="d.id">{{ d.name }} · {{ decimal(d.weight) }}%</p></template>
      <form v-if="isManager && (!entry.score || preserveInput)" class="form-stack" @submit.prevent="submit(false)"><p v-if="entry.score" class="form-hint">以下为本次未提交输入，已保留供核对；正式结果以上方为准。</p><div v-for="d in entryBatch.dimensions" :key="d.id" class="inspection-dimension"><div><b>{{ d.name }}</b><span class="account-name">权重 {{ decimal(d.weight) }}%</span></div><div class="field"><label :for="`inspection-value-${d.id}`">{{ d.name }}分值</label><ElInput :id="`inspection-value-${d.id}`" v-model="values[d.id]" inputmode="numeric" maxlength="4" placeholder="0–100整数" :disabled="saving || !canSubmit" /></div></div><p class="inspection-preview" data-testid="score-preview">综合分预览：<strong>{{ preview === null ? '请填写全部整数分值' : decimal(preview) }}</strong></p><p class="form-hint">提交后记为正式检查；同一寝室每批次只能提交一份正式评分。下一间按当前楼栋/关键词范围查找未检寝室。</p><div v-if="canSubmit" class="form-actions"><ElButton native-type="submit" :disabled="saving || entryLoading" :loading="saving">提交正式评分</ElButton><ElButton :disabled="saving || entryLoading" @click="submit(true)">保存并下一间</ElButton><ElButton :disabled="saving || entryLoading" @click="saveDraft">暂存个人草稿</ElButton></div></form>
    </template>
    <p v-if="entryNotice" class="message success" role="status">{{ entryNotice }}</p>
    <p v-if="draft" class="form-hint">本人草稿：{{ time(draft.updatedAt) }} · 版本 {{ draft.version }}</p>
    <div v-if="draft && draftVersion !== draft.version" class="message"><p>服务端草稿：<span v-for="d in draft.dimensions" :key="d.dimensionId">{{ entryBatch?.dimensions.find(item => item.id === d.dimensionId)?.name }} {{ d.value || '未填' }}；</span></p><ElButton :disabled="saving || entryLoading" @click="restoreDraft(); entryError = ''">载入服务端草稿（替换输入）</ElButton></div>
    <ElButton v-if="nextReady" :disabled="saving || entryLoading" @click="continueNext">继续下一间</ElButton>
    <p v-if="entryError" class="message error" role="alert">{{ entryError }}</p><div class="form-actions"><ElButton v-if="entryError" :disabled="entryLoading || saving" @click="refreshEntry">核对最新状态（保留输入）</ElButton><ElButton :disabled="saving" @click="entryOpen = false">返回</ElButton></div>
  </ElDialog>
</template>
