export type PeriodKind = 'WEEK' | 'MONTH'
export interface ResultPeriod { kind: PeriodKind; start: string; end: string; label: string }
export interface ResultSample { score: number | null; samples: number; rank: number | null }
export interface ResultWeek {
  period: ResultPeriod; batchId: number | null; status: 'NOT_PREPARED' | 'NOT_EXPECTED' | 'UNINSPECTED' | 'SUSPENDED' | 'INSPECTED'
  schemeName: string | null; threshold: number | null; totalScore: number | null; submittedAt: string | null
  dimensions: { name: string; weight: number; score: number | null }[]
}
export interface StudentResults {
  scope: { buildingId: number; buildingName: string; roomId: number; roomNo: string }
  period: ResultPeriod; summary: ResultSample
  trend: { period: ResultPeriod; score: number | null; samples: number }[]
  weeks: ResultWeek[]
  rankings: { items: { roomNo: string; score: number; samples: number; rank: number }[]; total: number; page: number; size: number }
}
