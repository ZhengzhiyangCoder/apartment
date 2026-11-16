export const categories = { PLUMBING: '给排水', ELECTRICAL: '电器照明', DOORS: '门窗', FURNITURE: '家具设施', OTHER: '其他问题' }
export const statuses = { PENDING_ASSIGNMENT: '待分配', PENDING: '待处理', IN_PROGRESS: '处理中', COMPLETED: '已完成', CANCELLED: '已取消' }
export interface Repair {
  id: number; submittedBy: number; studentName: string; buildingName: string; roomNo: string; bedNo: string
  category: keyof typeof categories; description: string; status: keyof typeof statuses; createdAt: string; updatedAt: string
  assigneeId: number | null; assigneeName: string | null; assignedByName: string | null; assignedAt: string | null
  startedAt: string | null; completedAt: string | null; completionNote: string | null; version: number
}
export interface RepairDetail extends Repair {
  images: { id: number; url: string; byteSize: number }[]
  logs: { id: number; fromStatus: keyof typeof statuses | null; toStatus: keyof typeof statuses; actorName: string; note: string; createdAt: string; previousAssigneeName: string | null; assigneeName: string | null }[]
}
