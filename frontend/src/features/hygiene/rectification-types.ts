export interface Rectification {
  id: number; roomId: number; buildingId: number; buildingName: string; roomNo: string
  status: 'OPEN' | 'CLOSED'; version: number; createdAt: string; updatedAt: string; verifiedAt: string | null; closedAt: string | null
}
export interface RectificationTrigger {
  scoreId: number; batchId: number; isoYear: number; isoWeek: number; weekStart: string; schemeId: number; schemeName: string
  batchStatus: 'OPEN' | 'CLOSED' | 'SUSPENDED'; totalScore: number; threshold: number; currentScore: number; submittedAt: string; triggeredAt: string
}
export interface RectificationLog {
  id: number; action: 'TRIGGERED' | 'FOLLOW_UP' | 'VERIFIED' | 'CLOSED'; actorId: number; actorName: string; note: string; createdAt: string
}
export interface RectificationDetail extends Rectification { triggers: RectificationTrigger[]; logs: RectificationLog[] }
