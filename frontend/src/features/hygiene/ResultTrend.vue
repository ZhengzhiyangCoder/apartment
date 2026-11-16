<script setup lang="ts">
import { computed } from 'vue'
import type { ResultPeriod } from './result-types'
const props = defineProps<{ points: { period: ResultPeriod; score: number | null; samples: number }[]; period: ResultPeriod }>()
const number = (value: number | null) => value === null ? '—' : value.toFixed(2)
const trendPoints = computed(() => props.points.map((item, index) => ({ ...item, x: 52 + index * 72, y: item.score === null ? null : 190 - item.score * 1.5 })))
const segments = computed(() => {
  const lines: string[][] = []; let line: string[] = []
  for (const point of trendPoints.value) {
    if (point.y === null) { if (line.length) lines.push(line); line = [] }
    else line.push(`${point.x},${point.y}`)
  }
  if (line.length) lines.push(line)
  return lines.map(points => points.join(' '))
})
const trendSamples = computed(() => props.points.reduce((sum, point) => sum + point.samples, 0))
</script>
<template>
    <section class="card result-trend">
      <div class="result-section-head"><div><h2>历史趋势</h2><p class="form-hint">{{ points[0]?.period.label }} — {{ period.label }} · 有效样本 {{ trendSamples }} 次</p></div><span class="role-badge">近 12 {{ period.kind === 'MONTH' ? '个月' : '周' }}</span></div>
      <svg viewBox="0 0 900 235" role="img" :aria-label="`成绩趋势，${points[0]?.period.label}至${period.label}，有效样本${trendSamples}次，空缺不连线`">
        <g v-for="score in [0, 50, 100]" :key="score"><line x1="40" x2="870" :y1="190 - score * 1.5" :y2="190 - score * 1.5" class="trend-grid" /><text x="30" :y="194 - score * 1.5" text-anchor="end">{{ score }}</text></g>
        <polyline v-for="(points, index) in segments" :key="index" :points="points" class="trend-line" />
        <g v-for="point in trendPoints" :key="point.period.start">
          <circle v-if="point.y !== null" :cx="point.x" :cy="point.y" r="5" class="trend-dot"><title>{{ point.period.label }}：{{ number(point.score) }}，有效参评 {{ point.samples }} 次</title></circle>
          <text v-if="point.y !== null" :x="point.x" :y="point.y - 12" text-anchor="middle" class="trend-value">{{ number(point.score) }}</text>
          <text v-else :x="point.x" y="175" text-anchor="middle">空缺</text>
          <text :x="point.x" y="216" text-anchor="middle">{{ period.kind === 'MONTH' ? point.period.start.slice(0, 7) : point.period.start.slice(5) }}</text>
        </g>
      </svg>
      <p class="form-hint">缺检、停检及草稿不补零，不计平均。有效零分照常参与统计；不同方案仅比较综合成绩，原标准见明细。</p>
      <details><summary>查看趋势数据</summary><table class="account-table" aria-label="趋势数据"><thead><tr><th>周期</th><th>周期成绩</th><th>有效参评次数</th></tr></thead><tbody><tr v-for="point in points" :key="point.period.start"><td>{{ point.period.label }}</td><td>{{ point.score === null ? '未检查 / 数据不足' : number(point.score) }}</td><td>{{ point.samples }} 次</td></tr></tbody></table></details>
    </section>
</template>
<style scoped>
.result-trend { margin-bottom: 24px; }.result-trend svg { display: block; width: 100%; }
.result-trend svg text { fill: var(--muted); font-size: 12px; }.result-trend .trend-value { fill: var(--primary); }
.trend-grid { stroke: #ccd5df; stroke-width: 1; }.trend-line { fill: none; stroke: var(--primary); stroke-width: 3; }.trend-dot { fill: var(--primary); }
.result-section-head { display: flex; justify-content: space-between; align-items: start; gap: 18px; }
summary { cursor: pointer; color: var(--primary); padding: 12px 0; }
</style>
