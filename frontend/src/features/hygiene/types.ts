export const schemeStatuses = { DRAFT: '草稿', ACTIVE: '已启用', ARCHIVED: '历史版本' }
export interface Scheme {
  id: number; name: string; threshold: number; status: keyof typeof schemeStatuses; version: number
  sourceId: number | null; createdAt: string; updatedAt: string; activatedAt: string | null
  totalWeight: number; dimensionCount: number
}
export interface SchemeDetail extends Scheme { dimensions: { id: number; name: string; weight: number; sortOrder: number }[] }
export interface SchemePage { items: Scheme[]; total: number; page: number; size: number; active: Scheme | null }
