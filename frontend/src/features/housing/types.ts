export interface PageResult<T> { items: T[]; total: number; page: number; size: number }
export interface Building { id: number; name: string; enabled: boolean; version: number }
export interface Room { id: number; buildingId: number; buildingName: string; buildingEnabled: boolean; roomNo: string; floor: number; enabled: boolean; version: number }
export interface Bed { id: number; roomId: number; buildingId: number; buildingName: string; roomNo: string; buildingEnabled: boolean; roomEnabled: boolean; bedNo: string; enabled: boolean; version: number }
export interface Student { id: number; userId: number; username: string; accountEnabled: boolean; studentNo: string; fullName: string; college: string; major: string; className: string; enabled: boolean; version: number }
export type CatalogItem = Building | Room | Bed | Student
export type CatalogKind = 'buildings' | 'rooms' | 'beds' | 'students'
