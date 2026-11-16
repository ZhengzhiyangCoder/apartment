<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElButton, ElDialog } from 'element-plus'
import { request, write } from '../../lib/http'
import type { PageResult } from '../housing/types'
import type { Rectification, RectificationDetail, RectificationLog } from './rectification-types'

const result = ref<PageResult<Rectification> | null>(null), loading = ref(false), error = ref('')
const status = ref('OPEN'), q = ref(''), page = ref(1)
let applied = { status: 'OPEN', q: '' }, requestId = 0, detailRequestId = 0
const selectedId = ref<number | null>(null), detail = ref<RectificationDetail | null>(null), detailLoading = ref(false)
const saving = ref(false), detailError = ref(''), success = ref(''), note = ref(''), operation = ref('follow-ups')
const pages = computed(() => Math.max(1, Math.ceil((result.value?.total ?? 0) / 10)))
const actionLabel: Record<RectificationLog['action'], string> = { TRIGGERED: '低分触发', FOLLOW_UP: '整改跟进', VERIFIED: '核实结果', CLOSED: '关闭说明', CORRECTED: '成绩更正' }
const batchLabel = { OPEN: '开放', CLOSED: '关闭', SUSPENDED: '停检，成绩不参与统计' }
const time = (value: string) => new Date(value).toLocaleString('zh-CN', { hour12: false })
const number = (value: number) => value.toFixed(2)
const stateLabel = (event: Rectification) => event.status === 'CLOSED' ? '已关闭' : event.verifiedAt ? '已核实，可关闭' : '待整改 / 待核实'
async function load() {
  const id = ++requestId; loading.value = true; error.value = ''; result.value = null
  const params = new URLSearchParams({ q: applied.q, page: String(page.value), size: '10' })
  if (applied.status) params.set('status', applied.status)
  try {
    const next = await request<PageResult<Rectification>>(`/api/hygiene/rectifications?${params}`)
    if (id !== requestId) return
    const last = Math.max(1, Math.ceil(next.total / 10))
    if (page.value > last) { page.value = last; await load(); return }
    result.value = next
  } catch (failure) { if (id === requestId) error.value = failure instanceof Error ? failure.message : '整改列表读取失败' }
  finally { if (id === requestId) loading.value = false }
}
function query() { applied = { status: status.value, q: q.value.trim() }; page.value = 1; void load() }
function turnPage(offset: number) { page.value += offset; void load() }
async function loadDetail() {
  if (selectedId.value === null || saving.value) return
  const id = ++detailRequestId, eventId = selectedId.value
  detailLoading.value = true; detailError.value = ''; success.value = ''; detail.value = null
  try {
    const next = await request<RectificationDetail>(`/api/hygiene/rectifications/${eventId}`)
    if (id === detailRequestId) detail.value = next
  } catch (failure) { if (id === detailRequestId) detailError.value = failure instanceof Error ? failure.message : '整改详情读取失败' }
  finally { if (id === detailRequestId) detailLoading.value = false }
}
function open(eventId: number) { selectedId.value = eventId; note.value = ''; operation.value = 'follow-ups'; void loadDetail() }
function close() { if (saving.value) return; selectedId.value = null; detailRequestId++; detail.value = null }
async function save() {
  if (saving.value || detailLoading.value || !detail.value) return
  detailError.value = ''; success.value = ''
  if (!note.value.trim()) { detailError.value = '请填写处理说明'; return }
  if (operation.value === 'close' && !detail.value.verifiedAt) { detailError.value = '请先记录核实结果，再关闭整改'; return }
  saving.value = true
  const id = detailRequestId, eventId = detail.value.id
  try {
    const next = await write<RectificationDetail>(`/api/hygiene/rectifications/${eventId}/${operation.value}`,
      JSON.stringify({ version: detail.value.version, note: note.value.trim() }), 'application/json')
    if (id !== detailRequestId) return
    detail.value = next; note.value = ''; success.value = '处理记录已保存'; void load()
  } catch (failure) { if (id === detailRequestId) detailError.value = failure instanceof Error ? failure.message : '保存失败，请保留输入并重新载入详情核对' }
  finally { saving.value = false }
}
function refresh() { void load() }
const route = useRoute()
onMounted(() => {
  void load(); window.addEventListener('focus', refresh)
  const id = Number(route.query.record)
  if (Number.isSafeInteger(id) && id > 0) open(id)
})
onUnmounted(() => { requestId++; detailRequestId++; window.removeEventListener('focus', refresh) })
</script>

<template>
  <section class="page-head account-heading"><div><p class="eyebrow">HYGIENE FOLLOW-UP / 卫生整改</p><h1>卫生整改</h1><p class="subtitle">记录每次低分依据，跟进整改，核实后关闭。</p></div><ElButton :loading="loading" :disabled="loading" @click="refresh">刷新整改</ElButton></section>
  <form class="card rectification-filters" @submit.prevent="query">
    <label>整改状态<select v-model="status" :disabled="loading" aria-label="整改状态"><option value="OPEN">未关闭</option><option value="CLOSED">已关闭</option><option value="">全部状态</option></select></label>
    <label>楼栋 / 寝室<input v-model="q" :disabled="loading" maxlength="64" placeholder="输入楼栋或房号" aria-label="整改关键词" /></label>
    <ElButton native-type="submit" type="primary" :disabled="loading">查询整改</ElButton>
    <p class="form-hint">单次正式评分低于当时阈值即触发；同寝室未关闭事件合并跟进。后续合格不自动关闭。</p>
  </form>
  <section v-if="loading" class="card rectification-card" role="status">正在读取整改事件…</section>
  <section v-else-if="error" class="card rectification-card"><p class="message error" role="alert">{{ error }}</p><ElButton @click="refresh">重新加载列表</ElButton></section>
  <section v-else-if="result" class="card rectification-card">
    <h2>{{ applied.status === 'OPEN' ? '待处理整改' : applied.status === 'CLOSED' ? '已关闭整改' : '全部整改' }}</h2><p class="form-hint">共 {{ result.total }} 条事件 · 显示触发时的楼栋、寝室名称。</p>
    <p v-if="!result.items.length" class="empty-state">暂无符合条件的整改事件。</p>
    <table v-else class="account-table" aria-label="整改事件"><thead><tr><th>楼栋 / 寝室</th><th>处理状态</th><th>首次触发</th><th>最近更新</th><th>操作</th></tr></thead><tbody>
      <tr v-for="event in result.items" :key="event.id"><td><b>{{ event.roomNo }}</b><small>{{ event.buildingName }} · 事件 #{{ event.id }}</small></td><td><span class="account-status" :class="{ inactive: event.status === 'CLOSED' }">{{ stateLabel(event) }}</span></td><td>{{ time(event.createdAt) }}</td><td>{{ time(event.updatedAt) }}</td><td><button type="button" class="text-button" @click="open(event.id)">查看与跟进</button></td></tr>
    </tbody></table>
    <div class="pagination"><ElButton :disabled="page <= 1" @click="turnPage(-1)">上一页</ElButton><span>第 {{ page }} / {{ pages }} 页</span><ElButton :disabled="page >= pages" @click="turnPage(1)">下一页</ElButton></div>
  </section>
  <ElDialog :model-value="selectedId !== null" title="整改详情与处理" width="900px" class="account-dialog" :close-on-click-modal="!saving" :close-on-press-escape="!saving" :show-close="!saving" @close="close">
    <p v-if="detailLoading" role="status">正在读取整改详情…</p>
    <p v-if="detailError" class="message error" role="alert">{{ detailError }}</p>
    <p v-if="success" class="message" role="status">{{ success }}</p>
    <div class="rectification-tools"><ElButton :disabled="saving || detailLoading" @click="loadDetail">重新载入详情</ElButton><small>保留未提交说明；核对最新记录后再保存。</small></div>
    <template v-if="detail">
      <div class="rectification-detail-head"><div><h2>{{ detail.buildingName }} · {{ detail.roomNo }}</h2><p class="form-hint">事件 #{{ detail.id }} · 首次触发 {{ time(detail.createdAt) }}</p></div><b data-testid="rectification-state">{{ stateLabel(detail) }}</b></div>
      <h3>触发依据</h3><p class="form-hint">保留每次触发时的分数与阈值。批次停检不删除整改，由管理人员核实处理。</p>
      <ul class="rectification-triggers" aria-label="触发依据"><li v-for="trigger in detail.triggers" :key="trigger.scoreId"><div><b>{{ trigger.isoYear }}年第{{ trigger.isoWeek }}周</b><span>触发成绩 {{ number(trigger.totalScore) }} &lt; 当时阈值 {{ number(trigger.threshold) }}</span></div><p>{{ trigger.schemeName }} · 方案 #{{ trigger.schemeId }} · 评分 #{{ trigger.scoreId }}</p><p v-if="trigger.corrected" class="message">触发成绩已更正 · 当前成绩 {{ number(trigger.currentScore) }}分{{ trigger.currentScore >= trigger.threshold ? (detail.status === 'CLOSED' ? '，当前达标；事件已人工关闭，历史保留。' : '，当前达标；保留历史，需人工核实关闭。') : '，当前仍低于原阈值。' }}</p><small>检查周一 {{ trigger.weekStart }} · 批次 #{{ trigger.batchId }} · {{ batchLabel[trigger.batchStatus] }}<br />实际评分 {{ time(trigger.submittedAt) }} · 触发记录 {{ time(trigger.triggeredAt) }}</small></li></ul>
      <h3>处理记录</h3><ol class="rectification-logs" aria-label="处理记录"><li v-for="log in detail.logs" :key="log.id"><b>{{ actionLabel[log.action] }}</b><small>{{ log.actorName }} · {{ time(log.createdAt) }}</small><p>{{ log.note }}</p></li></ol>
      <form v-if="detail.status === 'OPEN'" class="form-stack rectification-form" @submit.prevent="save">
        <h3>添加处理记录</h3><p class="form-hint">新增低分会要求重新核实；核实结果和关闭说明分别保留。</p>
        <label>处理操作<select v-model="operation" aria-label="处理操作" :disabled="saving"><option value="follow-ups">记录跟进</option><option value="verify">记录核实结果</option><option value="close" :disabled="!detail.verifiedAt">填写说明并关闭</option></select></label>
        <label>处理说明<textarea v-model="note" aria-label="处理说明" maxlength="1000" rows="3" :disabled="saving" placeholder="填写跟进情况、核实结果或关闭说明（最多1000字）" /></label>
        <ElButton native-type="submit" type="primary" :disabled="saving" :loading="saving">保存处理记录</ElButton>
      </form>
      <p v-else class="form-hint">已于 {{ detail.closedAt ? time(detail.closedAt) : '—' }} 关闭。历史依据和处理记录保留。</p>
      <div v-if="detail.status === 'CLOSED' && note.trim()" class="retained-note">
        <label>未提交说明<textarea :value="note" aria-label="未提交说明" readonly rows="3" /></label>
        <p class="form-hint">这段说明尚未保存。事件已由其他管理人员关闭，请在返回列表前复制需要保留的内容。</p>
      </div>
    </template>
    <template #footer><ElButton :disabled="saving" @click="close">返回列表</ElButton></template>
  </ElDialog>
</template>
<style scoped>
input, select, textarea { box-sizing: border-box; min-height: 44px; padding: 10px 12px; border: 0; border-radius: 12px; background: var(--base); color: inherit; box-shadow: var(--pressed); font: inherit; }
input:focus-visible, select:focus-visible, textarea:focus-visible { outline: 2px solid var(--primary); outline-offset: 2px; }
.rectification-filters { display: flex; align-items: end; flex-wrap: wrap; gap: 18px; padding: 24px; margin-bottom: 24px; }.rectification-filters label,.rectification-form label { display: grid; gap: 8px; }.rectification-filters input { width: 260px; }.rectification-filters .form-hint { width: 100%; margin-bottom: 0; }
.rectification-card { padding: 24px; }.rectification-card td,.rectification-card th { width: auto; padding: 16px 8px; overflow-wrap: anywhere; }.rectification-card small { display: block; margin-top: 8px; color: var(--muted); font-size: 12px; }.pagination { display: flex; justify-content: end; align-items: center; gap: 18px; margin-top: 24px; }
.rectification-tools,.rectification-detail-head { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 20px; }.rectification-tools small { color: var(--muted); }.rectification-detail-head b { white-space: nowrap; color: var(--primary); }
.rectification-triggers { padding: 0; list-style: none; }.rectification-triggers li { padding: 16px; margin-bottom: 12px; border: 1px solid #d4dce6; border-radius: 14px; }.rectification-triggers li>div { display: flex; justify-content: space-between; gap: 16px; }.rectification-triggers p { margin: 10px 0; }.rectification-triggers small,.rectification-logs small { color: var(--muted); line-height: 1.8; }
.rectification-logs { padding-left: 24px; }.rectification-logs li { padding: 8px 0; }.rectification-logs small { margin-left: 12px; }.rectification-logs p { margin: 8px 0; white-space: pre-wrap; overflow-wrap: anywhere; }.rectification-form { border-top: 1px solid #d4dce6; padding-top: 16px; margin-top: 20px; }.rectification-form textarea { resize: vertical; min-height: 90px; }.rectification-form select { width: 100%; }.retained-note { margin-top: 20px; }.retained-note label { display: grid; gap: 8px; }
</style>
