<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { ElButton, ElInput } from 'element-plus'
import { HttpError, request, write } from '../../lib/http'
import type { FormalScore } from './batch-types'
import type { PageResult } from '../housing/types'

interface Correction {
  id: number; version: number; actorId: number; actorName: string; reason: string; createdAt: string
  before: FormalScore; after: FormalScore
}
const props = defineProps<{ score: FormalScore; canCorrect: boolean }>()
const emit = defineEmits<{ changed: [score: FormalScore]; busy: [value: boolean] }>()
const history = ref<PageResult<Correction> | null>(null), historyLoading = ref(false), historyError = ref(''), page = ref(1)
const editing = ref(false), saving = ref(false), refreshing = ref(false), error = ref(''), success = ref(''), blocked = ref(false)
const values = ref<Record<number, string>>({}), reason = ref(''), version = ref(props.score.version)
let historyRequest = 0, currentRequest = 0
const pages = computed(() => Math.max(1, Math.ceil((history.value?.total ?? 0) / 10)))
const decimal = (value: number) => value.toFixed(2)
const time = (value: string) => new Date(value).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai', hour12: false })
const message = (failure: unknown) => failure instanceof Error ? failure.message : '服务暂时不可用，请重试'
async function loadHistory() {
  const id = ++historyRequest; historyLoading.value = true; historyError.value = ''; history.value = null
  try {
    const result = await request<PageResult<Correction>>(`/api/hygiene/scores/${props.score.id}/corrections?page=${page.value}&size=10`)
    if (id !== historyRequest) return
    const last = Math.max(1, Math.ceil(result.total / 10))
    if (page.value > last) { page.value = last; await loadHistory(); return }
    history.value = result
  } catch (failure) { if (id === historyRequest) historyError.value = message(failure) }
  finally { if (id === historyRequest) historyLoading.value = false }
}
function start() {
  values.value = Object.fromEntries(props.score.dimensions.map(d => [d.dimensionId, String(d.score)]))
  reason.value = ''; version.value = props.score.version; editing.value = true; error.value = ''; success.value = ''; blocked.value = false
}
async function refresh() {
  if (saving.value || refreshing.value) return
  const id = ++currentRequest; refreshing.value = true; error.value = ''
  try {
    const score = await request<FormalScore>(`/api/hygiene/scores/${props.score.id}`)
    if (id !== currentRequest) return
    version.value = score.version; emit('changed', score); blocked.value = false
    success.value = '已载入最新成绩，上方为当前结果；下方分值和原因仍保留，请核对后提交。'
    await loadHistory()
  } catch (failure) { if (id === currentRequest) { error.value = message(failure); blocked.value = true } }
  finally { if (id === currentRequest) refreshing.value = false }
}
async function save() {
  if (saving.value || refreshing.value || blocked.value || !props.canCorrect) return
  error.value = ''; success.value = ''
  if (!reason.value.trim()) { error.value = '请填写更正原因。'; return }
  if (props.score.dimensions.some(d => !/^\d{1,3}$/.test(values.value[d.dimensionId] ?? '') || Number(values.value[d.dimensionId]) > 100)) {
    error.value = '请为每个维度填写0–100的整数分值。'; return
  }
  saving.value = true; emit('busy', true)
  try {
    const dimensions = props.score.dimensions.map(d => ({ dimensionId: d.dimensionId, score: Number(values.value[d.dimensionId]) }))
    const saved = await write<FormalScore>(`/api/hygiene/scores/${props.score.id}/corrections`, JSON.stringify({ version: version.value, reason: reason.value, dimensions }), 'application/json')
    emit('changed', saved); version.value = saved.version; editing.value = false; reason.value = ''; values.value = {}
    success.value = '成绩更正已保存，统计与排行使用当前值，整改历史保留。'; page.value = 1
    await loadHistory()
  } catch (failure) {
    error.value = message(failure)
    if (failure instanceof HttpError && [401, 403, 404, 409].includes(failure.status)) blocked.value = true
  } finally { saving.value = false; emit('busy', false) }
}
function changePage(next: number) { page.value = next; void loadHistory() }
onMounted(() => { void loadHistory() })
onUnmounted(() => { historyRequest++; currentRequest++ })
</script>

<template>
  <section class="score-corrections" aria-label="成绩更正">
    <div class="correction-heading"><h3>成绩更正与记录</h3><ElButton v-if="canCorrect && !editing" @click="start">更正成绩</ElButton></div>
    <p class="form-hint">当前版本 {{ score.version }} · 更正沿用原标准和原检查周，保留完整前后值；关闭批次可更正，停检成绩仍不参与统计。</p>
    <form v-if="editing" class="form-stack" @submit.prevent="save">
      <p class="form-hint">核对上方当前成绩后填写分值和原因。更正不会自动关闭关联整改。</p>
      <div v-for="dimension in score.dimensions" :key="dimension.dimensionId" class="inspection-dimension">
        <div><b>{{ dimension.name }}</b><span class="account-name">原权重 {{ decimal(dimension.weight) }}%</span></div>
        <div class="field"><label :for="`correction-${dimension.dimensionId}`">更正{{ dimension.name }}分值</label><ElInput :id="`correction-${dimension.dimensionId}`" v-model="values[dimension.dimensionId]" inputmode="numeric" maxlength="4" :disabled="saving || refreshing" /></div>
      </div>
      <div class="field"><label for="correction-reason">更正原因</label><textarea id="correction-reason" v-model="reason" rows="3" maxlength="1000" :disabled="saving || refreshing" /></div>
      <div class="form-actions"><ElButton native-type="submit" :loading="saving" :disabled="saving || refreshing || blocked">保存成绩更正</ElButton><ElButton :disabled="saving || refreshing" @click="refresh">核对最新成绩（保留输入）</ElButton><ElButton :disabled="saving || refreshing" @click="editing = false; error = ''; success = ''">取消更正</ElButton></div>
    </form>
    <p v-if="refreshing" role="status">正在读取最新成绩…</p>
    <p v-if="error" class="message error" role="alert">{{ error }}</p><p v-if="success" class="message success" role="status">{{ success }}</p>
    <p v-if="historyLoading" role="status">正在读取更正记录…</p>
    <p v-else-if="historyError" class="message error" role="alert">{{ historyError }} <button class="text-button" @click="loadHistory">重试更正记录</button></p>
    <template v-else-if="history">
      <p v-if="!history.items.length" class="form-hint">暂无成绩更正记录。</p>
      <ol v-else class="correction-history" aria-label="成绩更正记录"><li v-for="item in history.items" :key="item.id">
        <b>版本 {{ item.before.version }} → {{ item.after.version }} · {{ decimal(item.before.totalScore) }} → {{ decimal(item.after.totalScore) }}分</b>
        <p>{{ item.reason }}</p><small>{{ item.actorName }} · {{ time(item.createdAt) }}（上海时间）</small>
        <div class="account-table-wrap"><table class="account-table"><caption class="sr-only">版本{{ item.version }}更正前后维度</caption><thead><tr><th>原维度</th><th>原权重</th><th>更正前</th><th>更正后</th></tr></thead><tbody><tr v-for="d in item.before.dimensions" :key="d.dimensionId"><td>{{ d.name }}</td><td>{{ decimal(d.weight) }}%</td><td>{{ d.score }}</td><td>{{ item.after.dimensions.find(a => a.dimensionId === d.dimensionId)?.score }}</td></tr></tbody></table></div>
      </li></ol>
      <div class="account-pagination"><span>共 {{ history.total }} 条更正</span><div><ElButton :disabled="page <= 1 || saving" @click="changePage(page - 1)">上一页更正</ElButton><span>{{ page }} / {{ pages }}</span><ElButton :disabled="page >= pages || saving" @click="changePage(page + 1)">下一页更正</ElButton></div></div>
    </template>
  </section>
</template>

<style scoped>
.score-corrections { border-top: 1px solid #d4dce6; margin-top: 24px; padding-top: 12px; }
.correction-heading { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.correction-history { list-style: none; padding: 0; }.correction-history li { margin-top: 16px; padding: 16px; border: 1px solid #d4dce6; border-radius: 14px; }
.correction-history p { overflow-wrap: anywhere; white-space: pre-wrap; }.correction-history small { color: var(--muted); }.correction-history table { margin-top: 12px; }
textarea { width: 100%; box-sizing: border-box; padding: 12px; border-radius: 10px; border: 1px solid #c9d3df; background: var(--bg); color: var(--text); font: inherit; resize: vertical; }
</style>
