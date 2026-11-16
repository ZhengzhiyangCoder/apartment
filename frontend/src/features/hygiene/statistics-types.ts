import type { ResultPeriod, ResultWeek } from './result-types'
export interface Progress { expected: number; inspected: number; missing: number; completionRate: number | null }
export interface ManagementStatistics {
  scope: { buildingId: number | null; buildingName: string | null; roomId: number | null; roomNo: string | null }
  period: ResultPeriod
  summary: { participants: number; samples: number; progress: Progress }
  buildings: { buildingId: number; buildingName: string; score: number | null; participants: number; samples: number; progress: Progress }[]
  trend: { buildingId: number; buildingName: string; roomId: number | null; roomNo: string | null; points: { period: ResultPeriod; score: number | null; participants: number; samples: number }[] }[]
  rankings: { items: { roomId: number; buildingId: number; buildingName: string; roomNo: string; score: number; samples: number; rank: number }[]; total: number; page: number; size: number }
  dimensions: { schemeId: number; schemeName: string; buildingId: number; buildingName: string; dimensions: { dimensionId: number; name: string; weight: number; score: number; samples: number }[] }[]
  weeks: ResultWeek[]
}
