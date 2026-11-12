<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElButton, ElInput } from 'element-plus'
import { request } from '../../lib/http'
import type { PageResult } from './types'

interface Choice { id: number; name?: string; roomNo?: string; buildingName?: string; buildingEnabled?: boolean; username?: string; displayName?: string; bedNo?: string; studentNo?: string; fullName?: string; accountEnabled?: boolean }
const props = defineProps<{ source: 'buildings' | 'rooms' | 'accounts' | 'students' | 'vacant-beds'; label: string; disabled: boolean }>()
const selected = defineModel<number | null>({ required: true })
const emit = defineEmits<{ failure: [unknown] }>()
const query = ref(''), error = ref(''), loading = ref(false)
const result = ref<PageResult<Choice>>({ items: [], total: 0, page: 1, size: 10 })
function choiceLabel(item: Choice) { return item.studentNo ? `${item.studentNo} · ${item.fullName}` : item.bedNo ? `${item.buildingName} · ${item.roomNo} · ${item.bedNo} 号床` : item.name ?? (item.roomNo ? `${item.buildingName} · ${item.roomNo}${item.buildingEnabled === false ? '（楼栋已停用）' : ''}` : `${item.username} · ${item.displayName}`) }
async function load(page = 1) {
  loading.value = true; error.value = ''; selected.value = null
  try {
    const params = new URLSearchParams({ q: query.value, page: String(page), size: '10', enabled: 'true' })
    if (props.source === 'accounts') params.set('role', 'STUDENT')
    const path = props.source === 'accounts' ? '/api/identity/accounts' : `/api/housing/${props.source}`
    result.value = await request<PageResult<Choice>>(`${path}?${params}`)
  } catch (failure) { error.value = failure instanceof Error ? failure.message : '加载失败，请重试'; emit('failure', failure) }
  finally { loading.value = false }
}
onMounted(() => load())
</script>
<template>
  <fieldset class="catalog-picker" :disabled="disabled || loading">
    <legend>{{ label }}</legend>
    <div class="picker-search"><ElInput v-model="query" :aria-label="`搜索${label}`" :maxlength="64" placeholder="输入关键词查找" @keydown.enter.prevent="load()" /><ElButton :disabled="disabled || loading" @click="load()">查找</ElButton></div>
    <label class="sr-only" :for="`select-${source}`">{{ label }}</label>
    <select :id="`select-${source}`" v-model="selected" required :disabled="disabled || loading"><option :value="null">请选择</option><option v-for="item in result.items" :key="item.id" :value="item.id" :disabled="item.buildingEnabled === false || item.accountEnabled === false">{{ choiceLabel(item) }}</option></select>
    <div class="picker-pages"><button type="button" :disabled="disabled || loading || result.page <= 1" @click="load(result.page - 1)">上一组</button><span>{{ result.page }} / {{ Math.max(1, Math.ceil(result.total / result.size)) }} · 共 {{ result.total }} 条</span><button type="button" :disabled="disabled || loading || result.page * result.size >= result.total" @click="load(result.page + 1)">下一组</button></div>
    <p v-if="loading" class="form-hint" role="status">正在读取选项…</p><p v-else-if="!result.total && !error" class="form-hint">暂无可选资料，请调整搜索或先创建、启用相关资料。</p>
    <p v-if="error" class="message error" role="alert">{{ error }} <button type="button" class="text-button" @click="load()">重试</button></p>
  </fieldset>
</template>
