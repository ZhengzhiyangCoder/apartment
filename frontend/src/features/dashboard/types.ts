import type { Role } from '../identity/session'
import type { PageResult, Residency } from '../housing/types'
import type { CurrentBatch } from '../hygiene/batch-types'
import type { Rectification } from '../hygiene/rectification-types'
import type { ResultPeriod, ResultSample } from '../hygiene/result-types'
import type { Repair } from '../repair/types'
export interface Dashboard {
  role: Role; title: string
  inspection: CurrentBatch | null; rectifications: PageResult<Rectification> | null
  residency: Residency | null; results: { period: ResultPeriod; summary: ResultSample } | null
  repairs: { status: Repair['status'] | null; page: PageResult<Repair> }[]
}
