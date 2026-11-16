<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElButton, ElDialog, ElInput } from 'element-plus'
import { request, write } from '../../lib/http'
import { currentUser, handleAuthorizationFailure } from '../identity/session'
import { batchStatuses, type Batch, type BatchPage, type BatchChangePage, type EligiblePage, type InspectionRoom } from './batch-types'

const props = defineProps<{ batch: Batch | null; currentWeek: string | undefined }>()
const emit = defineEmits<{ select: [id: number | null]; changed: [] }>()
const router = useRouter(), isAdmin = computed(() => currentUser.value?.role === 'SYSTEM_ADMIN')
const historyOpen = ref(false), historyLoading = ref(false), historyError = ref('')
const history = ref<BatchPage>({ items: [], total: 0, page: 1, size: 10 })
const historyPages = computed(() => Math.max(1, Math.ceil(history.value.total / history.value.size)))
const managementOpen = ref(false), managed = ref<Batch | null>(null), checking = ref(false), saving = ref(false), managementError = ref('')
const reason = ref(''), action = ref('CLOSE'), removedRoom = ref<InspectionRoom | null>(null), selectedRoom = ref(''), candidateQuery = ref('')
const candidates = ref<EligiblePage>({ items: [], total: 0, page: 1, size: 10 }), candidatesLoading = ref(false), candidateError = ref('')
const candidatePages = computed(() => Math.max(1, Math.ceil(candidates.value.total / candidates.value.size)))
const changesOpen = ref(false), changesLoading = ref(false), changesError = ref(''), changesBatchId = ref(0), changesLabel = ref('')
let candidateRequest = 0, changesRequest = 0
const changes = ref<BatchChangePage>({ items: [], total: 0, page: 1, size: 10 })
const changePages = computed(() => Math.max(1, Math.ceil(changes.value.total / changes.value.size)))
const message = (e: unknown) => e instanceof Error ? e.message : '服务暂时不可用，请重试'
const authorize = (e: unknown) => handleAuthorizationFailure(e, router)
const time = (v: string) => new Date(v).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai' })
const actionNames: Record<string, string> = { BATCH_ROOM_ADDED: '补入应检寝室', BATCH_ROOM_REMOVED: '移出应检寝室', BATCH_CLOSED: '关闭批次', BATCH_REOPENED: '重开批次', BATCH_SUSPENDED: '停检批次', BATCH_RESTORED: '恢复批次', BATCH_EXPIRED: '跨周关闭普通录入' }
const options = computed(() => {
  const b = managed.value
  if (!b) return []
  const entries = [{ value: 'ADD', label: '补入应检寝室' }]
  if (b.status === 'OPEN') entries.push({ value: 'CLOSE', label: '关闭批次' })
  if (b.status !== 'SUSPENDED' && !b.scoringAllowed) entries.push({ value: 'REOPEN', label: '重开批次 / 授权历史补录' })
  entries.push(b.status === 'SUSPENDED' ? { value: 'RESTORE', label: '恢复停检前状态' } : { value: 'SUSPEND', label: '设置停检' })
  return entries
})
async function loadHistory(page = 1) {
  if (historyLoading.value) return
  historyOpen.value = true; historyLoading.value = true; historyError.value = ''
  try { history.value = await request<BatchPage>(`/api/hygiene/batches?page=${page}&size=10`) }
  catch (e) { if (!await authorize(e)) historyError.value = message(e) }
  finally { historyLoading.value = false }
}
function selectBatch(id: number | null) { historyOpen.value = false; emit('select', id) }
async function openManagement(room: InspectionRoom | null = null) {
  if (!props.batch || saving.value || checking.value) return
  managed.value = props.batch; removedRoom.value = room; managementOpen.value = true; reason.value = ''; managementError.value = ''; selectedRoom.value = ''; candidateQuery.value = ''
  candidateRequest++; candidatesLoading.value = false; candidateError.value = ''; candidates.value = { items: [], total: 0, page: 1, size: 10 }
  action.value = room ? 'REMOVE' : props.batch.status === 'SUSPENDED' ? 'RESTORE' : props.batch.scoringAllowed ? 'CLOSE' : 'REOPEN'
  await checkManagement()
}
async function checkManagement() {
  if (!managed.value || checking.value) return
  checking.value = true; managementError.value = ''
  try {
    managed.value = await request<Batch>(`/api/hygiene/batches/${managed.value.id}`)
    if (action.value === 'ADD') await loadCandidates()
  } catch (e) { if (!await authorize(e)) managementError.value = message(e) }
  finally { checking.value = false }
}
async function loadCandidates(page = 1) {
  if (!managed.value) return
  const target = managed.value.id, requestId = ++candidateRequest
  candidatesLoading.value = true; candidateError.value = ''
  try {
    const data = await request<EligiblePage>(`/api/hygiene/batches/${target}/eligible-rooms?${new URLSearchParams({ q: candidateQuery.value, page: String(page), size: '10' })}`)
    if (requestId !== candidateRequest || managed.value?.id !== target) return
    candidates.value = data
    if (!data.items.some(room => String(room.roomId) === selectedRoom.value)) selectedRoom.value = ''
  } catch (e) { if (requestId === candidateRequest && !await authorize(e)) candidateError.value = message(e) }
  finally { if (requestId === candidateRequest) candidatesLoading.value = false }
}
async function save() {
  if (!managed.value || saving.value || checking.value) return
  managementError.value = ''
  if (!reason.value.trim()) { managementError.value = '请填写操作原因。'; return }
  if (action.value === 'ADD' && !selectedRoom.value) { managementError.value = '请选择需要补入的寝室。'; return }
  saving.value = true
  try {
    const base = `/api/hygiene/batches/${managed.value.id}`
    const path = action.value === 'ADD' ? `${base}/rooms/${selectedRoom.value}/add` : action.value === 'REMOVE' ? `${base}/rooms/${removedRoom.value!.roomId}/remove` : `${base}/state`
    await write<Batch>(path, JSON.stringify({ version: managed.value.version, action: action.value, reason: reason.value }), 'application/json')
    managementOpen.value = false; emit('changed')
  } catch (e) { if (!await authorize(e)) managementError.value = message(e) }
  finally { saving.value = false }
}
async function loadChanges(page = 1) {
  if (!changesBatchId.value) return
  const target = changesBatchId.value, requestId = ++changesRequest
  changesLoading.value = true; changesError.value = ''
  changes.value = { items: [], total: 0, page, size: 10 }
  try {
    const data = await request<BatchChangePage>(`/api/hygiene/batches/${target}/changes?page=${page}&size=10`)
    if (requestId === changesRequest) changes.value = data
  } catch (e) { if (requestId === changesRequest && !await authorize(e)) changesError.value = message(e) }
  finally { if (requestId === changesRequest) changesLoading.value = false }
}
function openChanges() {
  if (!props.batch) return
  changesBatchId.value = props.batch.id; changesLabel.value = `${props.batch.isoYear}年第${props.batch.isoWeek}周 · 归属日 ${props.batch.weekStart}`
  changesOpen.value = true; void loadChanges()
}
defineExpose({ openRemoval: (room: InspectionRoom) => openManagement(room) })
</script>

<template>
  <section class="card batch-tools" aria-label="批次入口">
    <div><b>{{ batch && batch.weekStart !== currentWeek ? '正在查看历史批次' : '检查批次管理' }}</b><p class="form-hint">历史补录须由系统管理员带原因重开；仍使用原周名单和评分标准。</p></div>
    <div class="account-actions"><ElButton @click="loadHistory()">历史批次</ElButton><ElButton v-if="batch && batch.weekStart !== currentWeek" @click="selectBatch(null)">返回本周</ElButton><ElButton v-if="isAdmin && batch" @click="openManagement()">批次管理</ElButton><ElButton v-if="batch" @click="openChanges">变更记录</ElButton></div>
  </section>
  <ElDialog v-model="historyOpen" title="选择检查批次" width="800px" class="account-dialog" destroy-on-close>
    <p v-if="historyLoading" role="status">正在读取检查批次…</p><p v-else-if="historyError" class="message error" role="alert">{{ historyError }} <button class="text-button" @click="loadHistory(history.page)">重新加载批次</button></p>
    <template v-else><div class="account-table-wrap"><table class="account-table"><caption class="sr-only">检查批次历史</caption><thead><tr><th>年周 / 归属日</th><th>状态</th><th>已检 / 应检</th><th>操作</th></tr></thead><tbody><tr v-for="item in history.items" :key="item.id"><td>{{ item.isoYear }}年第{{ item.isoWeek }}周<span class="account-name">{{ item.weekStart }}</span></td><td>{{ batchStatuses[item.status] }}<span class="account-name">{{ item.scoringAllowed ? item.historicalReopened ? '已授权历史补录' : '可录分' : '当前不可录分' }}</span></td><td>{{ item.inspectedCount }} / {{ item.expectedCount }}</td><td><ElButton @click="selectBatch(item.id)">进入批次</ElButton></td></tr></tbody></table><p v-if="!history.items.length" class="account-empty">暂无检查批次。</p></div><div class="account-pagination"><span>共 {{ history.total }} 个批次</span><div><ElButton :disabled="history.page <= 1" @click="loadHistory(history.page - 1)">上一页批次</ElButton><span>{{ history.page }} / {{ historyPages }}</span><ElButton :disabled="history.page >= historyPages" @click="loadHistory(history.page + 1)">下一页批次</ElButton></div></div></template>
  </ElDialog>
  <ElDialog v-model="managementOpen" title="调整检查批次" width="700px" class="account-dialog" :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving" destroy-on-close>
    <p v-if="checking" role="status">正在核对批次…</p>
    <form v-if="managed" class="form-stack" @submit.prevent="save">
      <p>{{ managed.isoYear }}年第{{ managed.isoWeek }}周 · {{ batchStatuses[managed.status] }} · 已检 {{ managed.inspectedCount }} / 应检 {{ managed.expectedCount }}</p>
      <p v-if="removedRoom">移出：{{ removedRoom.buildingName }} · {{ removedRoom.roomNo }}。已有正式成绩不可移出；个人草稿保留供核对。</p>
      <div v-else class="field"><label for="batch-action">批次操作</label><select id="batch-action" v-model="action" :disabled="saving || checking" @change="action === 'ADD' && loadCandidates()"><option v-for="item in options" :key="item.value" :value="item.value">{{ item.label }}</option></select></div>
      <p class="form-hint">{{ action === 'REOPEN' ? '历史批次重开后允许宿管补录，跨周不会自动撤销此次授权；可再次关闭。' : action === 'SUSPEND' ? '停检后禁止录分，既有成绩和草稿保留，停检批次不纳入统计考核。' : action === 'RESTORE' ? '恢复停检前状态；未经历史补录授权的旧周恢复为关闭。' : action === 'CLOSE' ? '关闭后禁止录分与草稿发布，已保存的成绩和草稿保留。' : '应检名单只通过本次明确操作调整，入住和退宿不自动改写名单。' }}</p>
      <div v-if="action === 'ADD'">
        <div class="account-filters"><div class="field search-field"><label for="batch-candidate-query">搜索可补入寝室</label><ElInput id="batch-candidate-query" v-model="candidateQuery" maxlength="64" :disabled="saving" /></div><ElButton :disabled="saving || candidatesLoading" @click="loadCandidates()">查找寝室</ElButton></div>
        <p v-if="candidatesLoading" role="status">正在核对可补入寝室…</p><p v-else-if="candidateError" class="message error" role="alert">{{ candidateError }}</p>
        <template v-else><div class="field"><label for="batch-candidate">补入寝室</label><select id="batch-candidate" v-model="selectedRoom" :disabled="saving"><option value="">请选择启用且有人入住的寝室</option><option v-for="room in candidates.items" :key="room.roomId" :value="String(room.roomId)">{{ room.buildingName }} · {{ room.roomNo }}</option></select></div><p v-if="!candidates.items.length" class="form-hint">暂无符合条件的可补入寝室。</p><div class="account-pagination"><span>共 {{ candidates.total }} 间</span><div><ElButton :disabled="saving || candidates.page <= 1" @click="selectedRoom = ''; loadCandidates(candidates.page - 1)">上一页寝室</ElButton><span>{{ candidates.page }} / {{ candidatePages }}</span><ElButton :disabled="saving || candidates.page >= candidatePages" @click="selectedRoom = ''; loadCandidates(candidates.page + 1)">下一页寝室</ElButton></div></div></template>
      </div>
      <div class="field"><label for="batch-reason">操作原因</label><ElInput id="batch-reason" v-model="reason" type="textarea" :rows="3" maxlength="500" :disabled="saving" placeholder="说明本次变更原因（必填）" /></div>
      <p v-if="managementError" class="message error" role="alert">{{ managementError }}</p>
      <div class="form-actions"><ElButton native-type="submit" :loading="saving" :disabled="saving || checking || candidatesLoading">确认变更</ElButton><ElButton v-if="managementError" :disabled="saving || checking" @click="checkManagement">核对批次（保留原因）</ElButton><ElButton :disabled="saving" @click="managementOpen = false">返回</ElButton></div>
    </form>
  </ElDialog>
  <ElDialog v-model="changesOpen" title="批次变更记录" width="800px" class="account-dialog" destroy-on-close>
    <p class="form-hint">{{ changesLabel }}</p>
    <p v-if="changesLoading" role="status">正在读取变更记录…</p><p v-else-if="changesError" class="message error" role="alert">{{ changesError }} <button class="text-button" @click="loadChanges(changes.page)">重新加载记录</button></p>
    <template v-else><div class="batch-change-list"><article v-for="item in changes.items" :key="item.id" class="batch-change"><b>{{ actionNames[item.action] ?? item.action }}</b><p>{{ item.after.reason }}</p><p class="form-hint">{{ batchStatuses[item.before.batch.status] }} → {{ batchStatuses[item.after.batch.status] }} · 应检 {{ item.before.batch.expectedCount }} → {{ item.after.batch.expectedCount }} · 已检 {{ item.after.batch.inspectedCount }}<br /><span v-if="item.before.room || item.after.room">{{ (item.after.room ?? item.before.room)?.buildingName }} · {{ (item.after.room ?? item.before.room)?.roomNo }}<br /></span>操作人 #{{ item.actorId }} · {{ time(item.createdAt) }} · 记录 #{{ item.id }}</p></article><p v-if="!changes.items.length" class="account-empty">暂无批次变更记录。</p></div><div class="account-pagination"><span>共 {{ changes.total }} 条变更</span><div><ElButton :disabled="changes.page <= 1" @click="loadChanges(changes.page - 1)">上一页记录</ElButton><span>{{ changes.page }} / {{ changePages }}</span><ElButton :disabled="changes.page >= changePages" @click="loadChanges(changes.page + 1)">下一页记录</ElButton></div></div></template>
  </ElDialog>
</template>

<style scoped>
.batch-tools { display: flex; align-items: center; justify-content: space-between; gap: 20px; padding: 20px 24px; margin-bottom: 20px; }
.batch-tools .form-hint { margin: 8px 0 0; }
.batch-tools .account-actions { flex-wrap: wrap; }
.batch-change-list { display: grid; gap: 16px; }
.batch-change { padding: 16px; border: 1px solid var(--line, #d3dce6); border-radius: 14px; }
.batch-change p { overflow-wrap: anywhere; }
</style>
