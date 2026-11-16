export const targets: Record<string,string> = { ACCOUNT:'账号', BUILDING:'楼栋', ROOM:'寝室', BED:'床位', STUDENT:'学生资料', RESIDENCY:'住宿关系', REPAIR:'报修工单', SCHEME:'评分方案', BATCH:'检查批次', SCORE:'正式评分', RECTIFICATION:'整改事件' }
export const actions: Record<string,string> = {
  ACCOUNT_CREATED:'创建账号', ACCOUNT_ROLE_CHANGED:'变更角色', ACCOUNT_ENABLED:'启用账号', ACCOUNT_DISABLED:'停用账号', ACCOUNT_PASSWORD_RESET:'重置密码',
  BUILDING_CREATED:'新增楼栋', BUILDING_UPDATED:'修改楼栋', BUILDING_ENABLED:'启用楼栋', BUILDING_DISABLED:'停用楼栋',
  ROOM_CREATED:'新增寝室', ROOM_UPDATED:'修改寝室', ROOM_ENABLED:'启用寝室', ROOM_DISABLED:'停用寝室',
  BED_CREATED:'新增床位', BED_UPDATED:'修改床位', BED_ENABLED:'启用床位', BED_DISABLED:'停用床位',
  STUDENT_CREATED:'新增学生资料', STUDENT_UPDATED:'修改学生资料', STUDENT_ENABLED:'启用学生资料', STUDENT_DISABLED:'停用学生资料',
  RESIDENCY_CHECKED_IN:'办理入住', RESIDENCY_TRANSFERRED:'办理调寝', RESIDENCY_CHECKED_OUT:'办理退宿',
  REPAIR_SUBMITTED:'提交报修', REPAIR_ASSIGNED:'分配维修人员', REPAIR_REASSIGNED:'重分配维修人员', REPAIR_STARTED:'开始维修', REPAIR_COMPLETED:'完成维修', REPAIR_CANCELLED:'取消工单',
  SCHEME_CREATED:'创建方案', SCHEME_UPDATED:'修改方案草稿', SCHEME_COPIED:'复制方案', SCHEME_ARCHIVED:'归档方案', SCHEME_ACTIVATED:'启用方案',
  BATCH_EXPIRED:'关闭过期录入', BATCH_PREPARED:'准备检查批次', BATCH_CLOSED:'关闭批次', BATCH_REOPENED:'重新开放批次', BATCH_SUSPENDED:'停检批次', BATCH_RESTORED:'恢复批次', BATCH_ROOM_ADDED:'补入应检寝室', BATCH_ROOM_REMOVED:'移除应检寝室',
  SCORE_SUBMITTED:'提交正式评分', SCORE_DRAFT_SAVED:'保存个人草稿', SCORE_CORRECTED:'更正正式成绩',
  RECTIFICATION_TRIGGERED:'触发整改', RECTIFICATION_CORRECTED:'更正成绩关联整改', RECTIFICATION_FOLLOW_UP:'记录整改跟进', RECTIFICATION_VERIFIED:'核实整改', RECTIFICATION_CLOSED:'关闭整改',
}
const fields: Record<string,string> = { id:'记录编号', username:'账号', displayName:'显示名称', role:'角色', enabled:'启用状态', name:'名称', buildingId:'楼栋编号', buildingName:'楼栋', roomId:'寝室编号', roomNo:'房号', floor:'楼层', bedId:'床位编号', bedNo:'床位号', studentId:'学生资料编号', userId:'账号编号', studentNo:'学号', fullName:'姓名', college:'学院', major:'专业', className:'班级', startedAt:'开始时间', endedAt:'结束时间', endReason:'结束类型', endNote:'办理说明', previous:'原住宿', current:'新住宿', category:'报修分类', status:'状态', assigneeId:'维修人员编号', assigneeName:'维修人员', assignedAt:'派单时间', completedAt:'完成时间', completionNote:'处理说明', version:'版本', threshold:'整改阈值', sourceId:'来源方案编号', activatedAt:'启用时间', dimensions:'维度', weight:'权重', sortOrder:'排列位置', batch:'批次', room:'应检寝室', isoYear:'ISO 年', isoWeek:'ISO 周', weekStart:'归属日', schemeId:'方案编号', schemeName:'方案', historicalReopened:'历史补录开放', suspendedFrom:'停检前状态', expectedCount:'应检寝室数', inspectedCount:'已检寝室数', scoreId:'评分编号', totalScore:'综合得分', batchId:'批次编号', submittedBy:'原评分人编号', submittedAt:'原提交时间', dimensionId:'维度编号', score:'分值', createdAt:'创建时间', updatedAt:'更新时间', verifiedAt:'核实时间', closedAt:'关闭时间' }
const values: Record<string,string> = { SYSTEM_ADMIN:'系统管理员', DORM_MANAGER:'宿舍管理员', STUDENT:'学生', MAINTAINER:'维修人员', TRANSFER:'调寝', CHECKOUT:'退宿', PENDING_ASSIGNMENT:'待分配', PENDING:'待处理', IN_PROGRESS:'处理中', COMPLETED:'已完成', CANCELLED:'已取消', DRAFT:'草稿', ACTIVE:'启用', ARCHIVED:'归档', OPEN:'开放 / 未关闭', CLOSED:'关闭', SUSPENDED:'停检', PLUMBING:'水暖', ELECTRICAL:'电路', DOORS:'门窗', FURNITURE:'家具', OTHER:'其他' }
export const time = (value: string) => new Date(value).toLocaleString('zh-CN',{hour12:false})
export type Snapshot = null | string | number | boolean | Snapshot[] | { [key: string]: Snapshot }
type Scalar = string | number | boolean | null
interface Cell { text:string; value:Scalar }
const enumFields = new Set(['role','status','category','endReason','suspendedFrom'])
const timeFields = new Set(['startedAt','endedAt','assignedAt','completedAt','activatedAt','submittedAt','createdAt','updatedAt','verifiedAt','closedAt'])
function display(value:Scalar,field:string):string {
  if(value===null) return '—'
  if(typeof value==='boolean') return value?'是':'否'
  if(typeof value!=='string') return String(value)
  if(enumFields.has(field)) return values[value]??value
  if(timeFields.has(field) && !Number.isNaN(Date.parse(value))) return time(value)
  return value
}
export function flatten(data:Snapshot,prefix='',result:Record<string,Cell>={},field='') {
  if(data===null || typeof data!=='object') {
    if(prefix) result[prefix]={text:display(data,field),value:data}
    return result
  }
  for(const [key,value] of Object.entries(data)) {
    const label=Array.isArray(data)?`第 ${Number(key)+1} 项`:(fields[key]??key)
    flatten(value,prefix?`${prefix} · ${label}`:label,result,key)
  }
  return result
}
