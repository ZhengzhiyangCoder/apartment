<script setup lang="ts">
import { ref } from 'vue'
import { ElButton, ElDialog } from 'element-plus'
import type { ResultPeriod, ResultWeek } from './result-types'
defineProps<{ weeks: ResultWeek[]; period: ResultPeriod; samples: number }>()
const selectedWeek = ref<ResultWeek | null>(null)
const number = (value: number | null) => value === null ? '—' : value.toFixed(2)
const statuses: Record<ResultWeek['status'], string> = { NOT_PREPARED: '尚未准备检查', NOT_EXPECTED: '未纳入应检', UNINSPECTED: '未检查', SUSPENDED: '停检，不计成绩', INSPECTED: '已检查' }
</script>
<template>
      <section class="card result-table-card"><h2>周期检查明细</h2><p class="form-hint">{{ period.label }} · 有效样本 {{ samples }} 次</p>
        <table class="account-table" aria-label="周期检查明细"><thead><tr><th>检查周</th><th>成绩 / 状态</th><th>明细</th></tr></thead><tbody><tr v-for="week in weeks" :key="week.period.start"><td>{{ week.period.label }}<small>{{ week.period.start }}</small></td><td>{{ week.totalScore === null ? statuses[week.status] : number(week.totalScore) }}<small>有效样本 {{ week.totalScore === null ? 0 : 1 }} 次</small></td><td><button v-if="week.batchId" type="button" class="text-button" @click="selectedWeek = week">查看标准与评分</button><span v-else>—</span></td></tr></tbody></table>
      </section>
  <ElDialog :model-value="selectedWeek !== null" title="历史标准与成绩" width="660px" class="account-dialog" @close="selectedWeek = null">
    <template v-if="selectedWeek">
      <h2>{{ selectedWeek.period.label }}</h2><p class="subtitle">{{ selectedWeek.period.start }} · {{ statuses[selectedWeek.status] }}</p>
      <p>{{ selectedWeek.schemeName }} · 当时合格阈值 {{ number(selectedWeek.threshold) }}</p>
      <p class="inspection-saved">综合成绩 {{ number(selectedWeek.totalScore) }} · 有效样本 {{ selectedWeek.totalScore === null ? 0 : 1 }} 次</p>
      <table class="account-table" aria-label="历史维度评分"><thead><tr><th>原始维度</th><th>原始权重</th><th>分值</th></tr></thead><tbody><tr v-for="(dimension, index) in selectedWeek.dimensions" :key="index"><td>{{ dimension.name }}</td><td>{{ number(dimension.weight) }}%</td><td>{{ dimension.score === null ? '—' : dimension.score }}</td></tr></tbody></table>
      <p v-if="selectedWeek.submittedAt" class="form-hint">实际提交：{{ new Date(selectedWeek.submittedAt).toLocaleString('zh-CN') }}。历史补录仍归原检查周。</p>
      <p v-else class="form-hint">该周无有效成绩，原评分标准供核对。</p>
    </template>
    <template #footer><ElButton @click="selectedWeek = null">关闭</ElButton></template>
  </ElDialog>
</template>
<style scoped>
.result-table-card { padding: 24px; min-width: 0; }.result-table-card td,.result-table-card th { padding: 14px 8px; overflow-wrap: anywhere; }
.result-table-card small { display: block; margin-top: 6px; color: var(--muted); font-size: 12px; }
</style>
