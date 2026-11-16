<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, shallowRef } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { ElButton, ElDialog } from 'element-plus'
import { request } from '../../lib/http'
import { currentUser, handleAuthorizationFailure } from '../identity/session'
import { targets, actions, flatten, time, type Snapshot } from './labels'

interface Entry { id:number;actorId:number;actorName:string;action:string;targetType:string;targetId:number;createdAt:string }
interface Result { items:Entry[];total:number;page:number;size:number;scope:'ALL'|'OWN' }
interface Detail { entry:Entry;before:Snapshot;after:Snapshot;reason:string;references:{label:string;path:string}[] }
const router=useRouter(),route=useRoute(),admin=computed(()=>currentUser.value?.role==='SYSTEM_ADMIN')
const filters=reactive({actorId:'',targetType:'',targetId:'',action:'',from:'',until:'',sort:'NEWEST',size:'10'})
let applied={...filters},listRequest=0,detailRequest=0
const result=ref<Result|null>(null),loading=ref(false),error=ref(''),page=ref(1)
const selected=ref<number|null>(null),detail=shallowRef<Detail|null>(null),detailLoading=ref(false),detailError=ref(''),unchanged=ref(false)
const pages=computed(()=>Math.max(1,Math.ceil((result.value?.total??0)/Number(applied.size))))
const rows=computed(()=>{
  const before=flatten(detail.value?.before??null),after=flatten(detail.value?.after??null)
  return [...new Set([...Object.keys(before),...Object.keys(after)])].map(key=>({key,before:before[key]?.text??'—',after:after[key]?.text??'—',beforeRaw:before[key]?.value??null,afterRaw:after[key]?.value??null})).filter(row=>unchanged.value||row.beforeRaw!==row.afterRaw)
})
async function load() {
  const id=++listRequest;loading.value=true;error.value='';result.value=null
  const params=new URLSearchParams({page:String(page.value),size:applied.size,sort:applied.sort})
  for(const key of ['actorId','targetType','targetId','action'] as const) if(applied[key]) params.set(key,applied[key])
  if(applied.from) params.set('from',new Date(`${applied.from}T00:00:00`).toISOString())
  if(applied.until) { const end=new Date(`${applied.until}T00:00:00`);end.setDate(end.getDate()+1);params.set('until',end.toISOString()) }
  try {
    const next=await request<Result>(`/api/audit/records?${params}`)
    if(id!==listRequest) return
    const last=Math.max(1,Math.ceil(next.total/next.size));if(page.value>last) {page.value=last;await load();return}
    result.value=next
  } catch(failure) { if(id===listRequest&&!await handleAuthorizationFailure(failure,router)) error.value=failure instanceof Error?failure.message:'读取失败，请重试' }
  finally {if(id===listRequest) loading.value=false}
}
function query() {applied={...filters};page.value=1;void load()}
function turn(offset:number) {page.value+=offset;void load()}
async function open(id:number) {
  selected.value=id;const current=++detailRequest;detailLoading.value=true;detailError.value='';detail.value=null;unchanged.value=false
  try {const next=await request<Detail>(`/api/audit/records/${id}`);if(current===detailRequest) detail.value=next}
  catch(failure) {if(current===detailRequest&&!await handleAuthorizationFailure(failure,router)) detailError.value=failure instanceof Error?failure.message:'读取详情失败'}
  finally {if(current===detailRequest) detailLoading.value=false}
}
function close() {selected.value=null;detailRequest++;detail.value=null}
onMounted(()=>{void load();const id=Number(route.query.record);if(Number.isSafeInteger(id)&&id>0) void open(id)})
onUnmounted(()=>{listRequest++;detailRequest++})
</script>
<template>
  <section class="page-head account-heading"><div><p class="eyebrow">OPERATION HISTORY / 操作追溯</p><h1>{{ admin?'管理操作审计':'本人操作记录' }}</h1><p class="subtitle">从一次操作，查清当时的变化与原因。</p></div><ElButton :disabled="loading" @click="load">刷新记录</ElButton></section>
  <p class="quiet-note">{{ admin?'可查询全系统关键操作。':'仅显示你执行的业务操作。' }} 工单进度、成绩更正明细和整改处理记录，可从详情进入对应业务查看。</p>
  <form class="card audit-filters" @submit.prevent="query">
    <label v-if="admin">操作者 ID<input v-model="filters.actorId" type="number" min="1" step="1" aria-label="操作者 ID" :disabled="loading" placeholder="全部操作者" /></label>
    <label>业务对象<select v-model="filters.targetType" aria-label="业务对象" :disabled="loading"><option value="">全部对象</option><option v-for="(label,value) in targets" :key="value" :value="value">{{ label }}</option></select></label>
    <label>对象 ID<input v-model="filters.targetId" type="number" min="1" step="1" aria-label="对象 ID" :disabled="loading" placeholder="全部对象编号" /></label>
    <label>操作动作<select v-model="filters.action" aria-label="操作动作" :disabled="loading"><option value="">全部动作</option><option v-for="(label,value) in actions" :key="value" :value="value">{{ label }}</option></select></label>
    <label>开始日期<input v-model="filters.from" type="date" aria-label="开始日期" :disabled="loading" /></label><label>结束日期<input v-model="filters.until" type="date" aria-label="结束日期" :disabled="loading" /></label>
    <label>排列顺序<select v-model="filters.sort" aria-label="排列顺序" :disabled="loading"><option value="NEWEST">时间从新到旧</option><option value="OLDEST">时间从旧到新</option></select></label>
    <label>每页条数<select v-model="filters.size" aria-label="每页条数" :disabled="loading"><option value="10">10</option><option value="20">20</option><option value="50">50</option></select></label>
    <ElButton native-type="submit" type="primary" :disabled="loading">查询记录</ElButton><small>日期包含起止当天，按浏览器本地时区查询。</small>
  </form>
  <section class="card audit-card">
    <p v-if="loading" role="status">正在读取操作记录…</p>
    <template v-else-if="error"><p class="message error" role="alert">{{ error }}</p><ElButton @click="load">重新加载记录</ElButton></template>
    <template v-else-if="result">
      <div class="audit-list-head"><h2>{{ admin?'关键操作记录':'本人执行的操作' }}</h2><span>共 {{ result.total }} 条记录</span></div>
      <p v-if="!result.items.length" class="empty-state">暂无符合条件的操作记录。</p>
      <div v-else class="account-table-wrap"><table class="account-table" aria-label="操作记录"><thead><tr><th>时间 / 编号</th><th>操作者</th><th>动作</th><th>业务对象</th><th>操作</th></tr></thead><tbody><tr v-for="entry in result.items" :key="entry.id"><td>{{ time(entry.createdAt) }}<small>#{{ entry.id }}</small></td><td>{{ entry.actorName }}<small>ID {{ entry.actorId }}</small></td><td>{{ actions[entry.action]??entry.action }}</td><td>{{ targets[entry.targetType]??entry.targetType }} #{{ entry.targetId }}</td><td><button type="button" class="text-button" @click="open(entry.id)">查看记录</button></td></tr></tbody></table></div>
      <div class="pagination"><ElButton :disabled="page<=1" @click="turn(-1)">上一页</ElButton><span>第 {{ page }} / {{ pages }} 页</span><ElButton :disabled="page>=pages" @click="turn(1)">下一页</ElButton></div>
    </template>
  </section>
  <ElDialog :model-value="selected!==null" title="操作审计详情" width="900px" class="account-dialog" @close="close">
    <p v-if="detailLoading" role="status">正在读取记录详情…</p>
    <template v-else-if="detailError"><p class="message error" role="alert">{{ detailError }}</p><ElButton @click="open(selected!)">重新加载详情</ElButton></template>
    <template v-else-if="detail">
      <div class="audit-detail-head"><h2>{{ actions[detail.entry.action]??detail.entry.action }}</h2><p>{{ targets[detail.entry.targetType] }} #{{ detail.entry.targetId }} · 记录 #{{ detail.entry.id }}</p><p>{{ detail.entry.actorName }}（ID {{ detail.entry.actorId }}） · {{ time(detail.entry.createdAt) }}</p></div>
      <h3>原因 / 处理说明</h3><p class="audit-reason">{{ detail.reason||'此操作未要求单独填写原因。' }}</p>
      <div class="audit-links"><RouterLink v-for="link in detail.references" :key="link.path" :to="link.path" class="text-button">{{ link.label }}</RouterLink></div>
      <p class="form-hint">下方为操作发生时的必要字段快照；来源页面显示当前业务记录及其历史。操作者名称为当前账号显示名称。</p>
      <label class="audit-unchanged"><input v-model="unchanged" type="checkbox" /> 显示未变化字段</label>
      <div class="account-table-wrap"><table class="account-table audit-comparison" aria-label="操作前后对照"><thead><tr><th>字段</th><th>操作前</th><th>操作后</th></tr></thead><tbody><tr v-for="row in rows" :key="row.key" :class="{changed:row.beforeRaw!==row.afterRaw}"><th>{{ row.key }}</th><td :title="String(row.beforeRaw??'—')">{{ row.before }}</td><td :title="String(row.afterRaw??'—')">{{ row.after }}</td></tr></tbody></table></div>
      <p v-if="!rows.length" class="form-hint">必要字段没有变化，请结合动作及业务记录查看。</p>
    </template>
    <div class="form-actions"><ElButton @click="close">关闭详情</ElButton></div>
  </ElDialog>
</template>
<style scoped>
.audit-filters{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:18px;padding:24px;margin-bottom:22px}.audit-filters label{display:flex;flex-direction:column;gap:8px;font-size:13px;color:var(--text-muted)}.audit-filters input,.audit-filters select{width:100%;box-sizing:border-box;min-width:0;padding:10px;border:1px solid #c4cdd7;border-radius:10px;background:#eef2f6;color:#33475d;font:inherit}.audit-filters small{grid-column:span 3;align-self:center;color:#687b90}.audit-card{padding:24px}.audit-list-head{display:flex;align-items:center;justify-content:space-between;gap:16px;margin-bottom:14px}.audit-card small{display:block;margin-top:5px;color:#718295}.audit-detail-head p{color:#63758a}.audit-reason{white-space:pre-wrap;overflow-wrap:anywhere;background:#e6edf4;border-radius:10px;padding:14px;line-height:1.7}.audit-links{display:flex;flex-wrap:wrap;gap:20px;margin:20px 0}.audit-unchanged{display:flex;gap:8px;align-items:center;margin:20px 0;font-size:13px}.audit-comparison{table-layout:fixed;min-width:0}.audit-comparison th,.audit-comparison td{white-space:pre-wrap;overflow-wrap:anywhere;vertical-align:top}.audit-comparison th{width:28%}.audit-comparison .changed td{background:#e9f0f6}.audit-card .pagination{margin-top:22px}
@media(max-width:1100px){.audit-filters{grid-template-columns:repeat(3,minmax(0,1fr))}.audit-filters small{grid-column:span 2}}
</style>
