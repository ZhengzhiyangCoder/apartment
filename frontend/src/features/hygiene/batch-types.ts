import type { SchemeDetail } from './types'
export interface Batch {
  id: number; isoYear: number; isoWeek: number; weekStart: string
  schemeId: number; schemeName: string; threshold: number; status: 'OPEN' | 'CLOSED' | 'SUSPENDED'
  createdBy: number; createdAt: string; expectedCount: number; inspectedCount: number
  version: number; historicalReopened: boolean; suspendedFrom: 'OPEN' | 'CLOSED' | null
  scoringAllowed: boolean; completionRate: number | null; dimensions: SchemeDetail['dimensions']
  buildings: { id: number; name: string }[]
}
export interface CurrentBatch { isoYear: number; isoWeek: number; weekStart: string; batch: Batch | null }
export interface InspectionRoom { roomId: number; buildingId: number; buildingName: string; roomNo: string; scoreId: number | null; totalScore: number | null }
export interface InspectionPage { items: InspectionRoom[]; total: number; page: number; size: number }
export interface FormalScore {
  id: number; batchId: number; roomId: number; totalScore: number; threshold: number; submittedBy: number; submittedAt: string
  dimensions: { dimensionId: number; name: string; weight: number; score: number; sortOrder: number }[]
}
export interface InspectionDetail { room: InspectionRoom; score: FormalScore | null }
export const batchStatuses = { OPEN: '开放', CLOSED: '关闭', SUSPENDED: '停检' }

export interface PersonalDraft {
  batchId: number; roomId: number; buildingName: string; roomNo: string; isoYear: number; isoWeek: number
  status: Batch['status']; version: number; updatedAt: string; dimensions: { dimensionId: number; value: string }[]
}
export interface DraftPage { items: PersonalDraft[]; total: number; page: number; size: number }
export interface BatchPage { items: Batch[]; total: number; page: number; size: number }
export interface EligibleRoom { roomId: number; buildingId: number; buildingName: string; roomNo: string }
export interface EligiblePage { items: EligibleRoom[]; total: number; page: number; size: number }
export interface BatchChange {
  id: number; actorId: number; action: string; createdAt: string
  before: { batch: Batch; room?: InspectionRoom | null }
  after: { batch: Batch; room?: InspectionRoom | null; reason: string }
}
export interface BatchChangePage { items: BatchChange[]; total: number; page: number; size: number }
