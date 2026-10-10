import request from '@/utils/request';

// ==================== Type Definitions ====================

export interface ApiResponse<T = any> {
  code: number;
  data: T;
  msg: string;
}

export interface PageResult<T = any> {
  list: T[];
  total: number;
  pageSize: number;
  pageNum: number;
  pages: number;
}

export interface PageParams {
  current?: number;
  size?: number;
  [key: string]: any;
}

// ==================== Auth ====================
export interface LoginParams {
  username: string;
  password: string;
  uuid: string;
  captcha: string;
}

export interface LoginResult {
  token: string;
  userInfo: UserInfo;
  permissions: string[];
  menus: MenuItem[];
}

export interface UserInfo {
  id: number;
  username: string;
  nickname: string;
  avatar?: string;
  email?: string;
  phone?: string;
  deptId?: number;
  deptName?: string;
  status: number;
  createTime: string;
}

export interface MenuItem {
  id: number;
  parentId: number;
  menuName: string;
  path: string;
  component?: string;
  icon?: string;
  sort: number;
  menuType: string; // C=页面 M=目录
  permission?: string;
  visible: number;
  children?: MenuItem[];
}

export interface CaptchaResult {
  uuid: string;
  captchaImage: string; // base64
}

export interface ChangePasswordParams {
  oldPassword: string;
  newPassword: string;
  confirmPassword: string;
}

// ==================== User Management ====================
export interface UserItem {
  id: number;
  username: string;
  nickname: string;
  email: string;
  phone: string;
  gender: number;
  deptId: number;
  deptName: string;
  status: number;
  roleIds: number[];
  roleNames: string[];
  createTime: string;
}

export interface UserFormParams {
  id?: number;
  username: string;
  nickname: string;
  email?: string;
  phone?: string;
  gender?: number;
  deptId?: number;
  roleIds?: number[];
  status?: number;
  password?: string;
}

// ==================== Role Management ====================
export interface RoleItem {
  id: number;
  name: string;
  code: string;
  sort: number;
  status: number;
  remark?: string;
  menuIds?: number[];
  createTime: string;
}

export interface RoleFormParams {
  id?: number;
  name: string;
  code: string;
  sort?: number;
  status?: number;
  remark?: string;
  menuIds?: number[];
}

// ==================== Menu Management ====================
export interface MenuTreeItem extends MenuItem {
  children?: MenuTreeItem[];
}

export interface MenuFormParams {
  id?: number;
  parentId: number;
  menuName: string;
  menuType: string; // C=页面 M=目录 F=按钮
  path?: string;
  component?: string;
  icon?: string;
  sort?: number;
  permission?: string;
  visible?: number;
}

// ==================== Log Management ====================
export interface LogItem {
  id: number;
  username: string;
  ip: string;
  location?: string;
  browser?: string;
  os?: string;
  status: number;
  msg?: string;
  createTime: string;
}

// ==================== Dept Management ====================
export interface DeptItem {
  id: number;
  parentId: number;
  deptName: string;
  deptCode?: string;
  sort: number;
  leader?: string;
  phone?: string;
  email?: string;
  status: number;
  children?: DeptItem[];
  projects?: ProjectItem[];
  createTime?: string;
}

export interface DeptFormParams {
  id?: number;
  parentId: number;
  deptName: string;
  sort?: number;
  leader?: string;
  phone?: string;
  email?: string;
  status?: number;
}

// ==================== Project Management ====================
export interface ProjectItem {
  id: number;
  parentId: number;
  projectName: string;
  projectCode?: string;
  sort: number;
  status: number;
  remark?: string;
  children?: ProjectItem[];
  createTime?: string;
}

export interface ProjectFormParams {
  id?: number;
  parentId: number;
  projectName: string;
  projectCode?: string;
  sort?: number;
  status?: number;
  remark?: string;
}

// ==================== Template ====================
export interface TemplateItem {
  id: number;
  name: string;
  code: string;
  description?: string;
  version: number;
  status: number; // 0=draft 1=published 2=disabled
  excelFileName?: string;
  fields: TemplateField[];
  datasourceId?: number;
  datasourceName?: string;
  createTime: string;
  updateTime: string;
}

export interface TemplateField {
  id?: number;
  templateId?: number;
  fieldName: string;
  fieldLabel: string;
  fieldType: string; // string/number/date/datetime
  excelColumn: string; // e.g. A, B, C
  required: number;
  sort: number;
  options?: string; // JSON for select options
}

export interface TemplateFormParams {
  id?: number;
  name: string;
  code: string;
  description?: string;
  status?: number;
  fields?: TemplateField[];
  datasourceId?: number;
}

// ==================== Datasource ====================
export interface DatasourceItem {
  id: number;
  name: string;
  type: string; // mysql/postgresql/sqlserver
  host: string;
  port: number;
  databaseName: string;
  username: string;
  status: number;
  remark?: string;
  createTime: string;
}

export interface DatasourceFormParams {
  id?: number;
  name: string;
  type: string;
  host: string;
  port: number;
  databaseName: string;
  username: string;
  password?: string;
  status?: number;
  remark?: string;
}

export interface TestConnectionResult {
  success: boolean;
  message: string;
}

// ==================== Task ====================
export interface TaskItem {
  id: number;
  name: string;
  templateId: number;
  templateName: string;
  year: number;
  period: string;
  startDate: string;
  endDate: string;
  status: number; // 0=draft 1=published 2=closed
  description?: string;
  createTime: string;
}

export interface TaskFormParams {
  id?: number;
  name: string;
  templateId: number;
  year: number;
  period: string;
  startDate: string;
  endDate: string;
  status?: number;
  description?: string;
}

export interface TaskAssignItem {
  id: number;
  taskId: number;
  taskName: string;
  deptId: number;
  deptName: string;
  projectId?: number;
  projectName?: string;
  status: number; // 0=未开始 1=进行中 2=已完成
  assignTime: string;
}

export interface TaskAssignFormParams {
  taskId: number;
  deptIds: number[];
  projectId?: number;
}

export interface TaskLedgerItem {
  id: number;
  taskId: number;
  taskName: string;
  templateName: string;
  deptName: string;
  projectName?: string;
  status: number;
  fillStatus: number; // 0=未填报 1=已填报 2=已提交
  assignTime: string;
  fillTime?: string;
  submitTime?: string;
}

// ==================== Fill ====================
export interface FillTaskItem {
  id: number;
  assignId: number;
  taskName: string;
  templateName: string;
  status: number; // 0=未填报 1=已填报 2=已提交
  startDate: string;
  endDate: string;
  assignTime: string;
}

export interface FillDataForm {
  assignId: number;
  fields: { fieldName: string; fieldValue: string }[];
}

export interface AuditItem {
  id: number;
  fillId: number;
  taskName: string;
  templateName: string;
  deptName: string;
  projectName?: string;
  fillTime: string;
  submitTime: string;
  status: number; // 0=待审核 1=审核通过 2=审核拒绝
  auditType: string; // dept / task
}

export interface AuditFormParams {
  ids: number[];
  status: number; // 1=通过 2=拒绝
  remark?: string;
}

// ==================== Data Apply ====================
export interface DataApplyItem {
  id: number;
  title: string;
  reason: string;
  dataScope: string;
  applyTime: string;
  status: number; // 0=待审批 1=已通过 2=已拒绝
  applicantName: string;
  approverName?: string;
  approveTime?: string;
  approveRemark?: string;
}

export interface DataApplyFormParams {
  title: string;
  reason: string;
  dataScope: string;
}

export interface ApproveFormParams {
  id: number;
  status: number;
  remark?: string;
}

// ==================== Overview ====================
export interface OverviewStats {
  totalTasks: number;
  completedTasks: number;
  pendingTasks: number;
  overdueTasks: number;
  totalFills: number;
  submittedFills: number;
  auditPassRate: number;
  totalDepts: number;
}

export interface ChartDataItem {
  name: string;
  value: number;
}

export interface TrendDataItem {
  date: string;
  fillCount: number;
  submitCount: number;
  auditCount: number;
}

// ==================== API Methods ====================

// --- Auth ---
export async function loginApi(params: LoginParams) {
  return request.post<ApiResponse<LoginResult>>('/auth/login', params);
}

export async function logoutApi() {
  return request.post<ApiResponse<null>>('/auth/logout');
}

export async function getCaptchaApi() {
  return request.get<ApiResponse<CaptchaResult>>('/auth/captcha');
}

export async function getCurrentUserApi() {
  return request.get<ApiResponse<UserInfo>>('/auth/current-user');
}

export async function changePasswordApi(params: ChangePasswordParams) {
  return request.put<ApiResponse<null>>('/auth/password', params);
}

// --- User ---
export async function getUserListApi(params: PageParams) {
  return request.get<ApiResponse<PageResult<UserItem>>>('/user/page', { params });
}

export async function getUserDetailApi(id: number) {
  return request.get<ApiResponse<UserItem>>(`/user/${id}`);
}

export async function createUserApi(data: UserFormParams) {
  return request.post<ApiResponse<null>>('/user', data);
}

export async function updateUserApi(data: UserFormParams) {
  return request.put<ApiResponse<null>>(`/user/${data.id}`, data);
}

export async function deleteUserApi(id: number) {
  return request.delete<ApiResponse<null>>(`/user/${id}`);
}

export async function updateUserStatusApi(id: number, status: number) {
  return request.put<ApiResponse<null>>(`/user/${id}/status`, { status });
}

export async function assignUserRolesApi(userId: number, roleIds: number[]) {
  return request.put<ApiResponse<null>>(`/user/${userId}/roles`, { roleIds });
}

// --- Role ---
export async function getRoleListApi(params: PageParams) {
  return request.get<ApiResponse<PageResult<RoleItem>>>('/role/page', { params });
}

export async function getAllRolesApi() {
  return request.get<ApiResponse<RoleItem[]>>('/role/all');
}

export async function getRoleDetailApi(id: number) {
  return request.get<ApiResponse<RoleItem>>(`/role/${id}`);
}

export async function createRoleApi(data: RoleFormParams) {
  return request.post<ApiResponse<null>>('/role', data);
}

export async function updateRoleApi(data: RoleFormParams) {
  return request.put<ApiResponse<null>>(`/role/${data.id}`, data);
}

export async function deleteRoleApi(id: number) {
  return request.delete<ApiResponse<null>>(`/role/${id}`);
}

export async function getRoleMenuIdsApi(roleId: number) {
  return request.get<ApiResponse<number[]>>(`/role/${roleId}/menus`);
}

// --- Menu ---
export async function getMenuTreeApi() {
  return request.get<ApiResponse<MenuTreeItem[]>>('/menu/tree');
}

export async function getMenuDetailApi(id: number) {
  return request.get<ApiResponse<MenuTreeItem>>(`/menu/${id}`);
}

export async function createMenuApi(data: MenuFormParams) {
  return request.post<ApiResponse<null>>('/menu', data);
}

export async function updateMenuApi(data: MenuFormParams) {
  return request.put<ApiResponse<null>>(`/menu/${data.id}`, data);
}

export async function deleteMenuApi(id: number) {
  return request.delete<ApiResponse<null>>(`/menu/${id}`);
}

// --- Log ---
export async function getLogListApi(params: PageParams) {
  return request.get<ApiResponse<PageResult<LogItem>>>('/system/log/page', { params });
}

export async function getLogDetailApi(id: number) {
  return request.get<ApiResponse<LogItem>>(`/system/log/${id}`);
}

// --- Dept ---
export async function getDeptTreeApi() {
  return request.get<ApiResponse<DeptItem[]>>('/dept/tree');
}

export async function getDeptDetailApi(id: number) {
  return request.get<ApiResponse<DeptItem>>(`/org/dept/${id}`);
}

export async function createDeptApi(data: DeptFormParams) {
  return request.post<ApiResponse<null>>('/dept', data);
}

export async function updateDeptApi(data: DeptFormParams) {
  return request.put<ApiResponse<null>>(`/org/dept/${data.id}`, data);
}

export async function deleteDeptApi(id: number) {
  return request.delete<ApiResponse<null>>(`/org/dept/${id}`);
}

// --- Project ---
export async function getProjectTreeApi() {
  return request.get<ApiResponse<DeptItem[]>>('/project/tree');
}

export async function getProjectDetailApi(id: number) {
  return request.get<ApiResponse<ProjectItem>>(`/org/project/${id}`);
}

export async function createProjectApi(data: ProjectFormParams) {
  return request.post<ApiResponse<null>>('/project', data);
}

export async function updateProjectApi(data: ProjectFormParams) {
  return request.put<ApiResponse<null>>(`/org/project/${data.id}`, data);
}

export async function deleteProjectApi(id: number) {
  return request.delete<ApiResponse<null>>(`/org/project/${id}`);
}

// --- Template ---
export async function getTemplateListApi(params: PageParams) {
  return request.get<ApiResponse<PageResult<TemplateItem>>>('/template/page', { params });
}

export async function getTemplateDetailApi(id: number) {
  return request.get<ApiResponse<TemplateItem>>(`/template/${id}`);
}

export async function createTemplateApi(data: TemplateFormParams) {
  return request.post<ApiResponse<null>>('/template', data);
}

export async function updateTemplateApi(data: TemplateFormParams) {
  return request.put<ApiResponse<null>>(`/template/${data.id}`, data);
}

export async function deleteTemplateApi(id: number) {
  return request.delete<ApiResponse<null>>(`/template/${id}`);
}

export async function updateTemplateStatusApi(id: number, status: number) {
  return request.put<ApiResponse<null>>(`/template/${id}/status`, { status });
}

export async function uploadTemplateExcelApi(templateId: number, formData: FormData) {
  return request.post<ApiResponse<null>>(`/template/${templateId}/upload-excel`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
}

export async function submitTemplateApi(id: number) {
  return request.post<ApiResponse<null>>(`/template/${id}/submit`);
}

export async function auditTemplateApi(id: number, status: number, remark?: string) {
  return request.post<ApiResponse<null>>(`/template/${id}/audit`, { status, remark });
}

// --- Datasource ---
export async function getDatasourceListApi(params: PageParams) {
  return request.get<ApiResponse<PageResult<DatasourceItem>>>('/datasource/page', { params });
}

export async function getDatasourceDetailApi(id: number) {
  return request.get<ApiResponse<DatasourceItem>>(`/datasource/${id}`);
}

export async function createDatasourceApi(data: DatasourceFormParams) {
  return request.post<ApiResponse<null>>('/datasource', data);
}

export async function updateDatasourceApi(data: DatasourceFormParams) {
  return request.put<ApiResponse<null>>(`/datasource/${data.id}`, data);
}

export async function deleteDatasourceApi(id: number) {
  return request.delete<ApiResponse<null>>(`/datasource/${id}`);
}

export async function testDatasourceConnectionApi(data: DatasourceFormParams) {
  return request.post<ApiResponse<TestConnectionResult>>('/datasource/test-connection', data);
}

// --- Task ---
export async function getTaskListApi(params: PageParams) {
  return request.get<ApiResponse<PageResult<TaskItem>>>('/task/page', { params });
}

export async function getTaskDetailApi(id: number) {
  return request.get<ApiResponse<TaskItem>>(`/task/${id}`);
}

export async function createTaskApi(data: TaskFormParams) {
  return request.post<ApiResponse<null>>('/task', data);
}

export async function updateTaskApi(data: TaskFormParams) {
  return request.put<ApiResponse<null>>(`/task/${data.id}`, data);
}

export async function deleteTaskApi(id: number) {
  return request.delete<ApiResponse<null>>(`/task/${id}`);
}

export async function publishTaskApi(id: number) {
  return request.put<ApiResponse<null>>(`/task/${id}/publish`);
}

export async function closeTaskApi(id: number) {
  return request.put<ApiResponse<null>>(`/task/${id}/close`);
}

// --- Task Assign ---
export async function getTaskAssignListApi(params: PageParams) {
  return request.get<ApiResponse<PageResult<TaskAssignItem>>>('/task/assign/page', { params });
}

export async function assignTaskApi(data: TaskAssignFormParams) {
  return request.post<ApiResponse<null>>('/task/assign', data);
}

export async function cancelAssignApi(id: number) {
  return request.delete<ApiResponse<null>>(`/task/assign/${id}`);
}

// --- Task Ledger ---
export async function getTaskLedgerListApi(params: PageParams & { perspective?: 'host' | 'executor' }) {
  return request.get<ApiResponse<PageResult<TaskLedgerItem>>>('/task/ledger/page', { params });
}

// --- Fill ---
export async function getFillTaskListApi(params: PageParams) {
  return request.get<ApiResponse<PageResult<FillTaskItem>>>('/fill/task/page', { params });
}

export async function getFillTaskDetailApi(assignId: number) {
  return request.get<ApiResponse<FillDataForm>>(`/fill/task/${assignId}`);
}

export async function submitFillDataApi(data: FillDataForm) {
  return request.post<ApiResponse<null>>('/fill/submit', data);
}

export async function saveFillDraftApi(data: FillDataForm) {
  return request.post<ApiResponse<null>>('/fill/draft', data);
}

// --- Audit ---
export async function getAuditListApi(params: PageParams & { auditType?: 'dept' | 'task' }) {
  return request.get<ApiResponse<PageResult<AuditItem>>>('/fill/audit/page', { params });
}

export async function getAuditDetailApi(id: number) {
  return request.get<ApiResponse<any>>(`/fill/audit/${id}`);
}

export async function submitAuditApi(data: AuditFormParams) {
  return request.post<ApiResponse<null>>('/fill/audit/submit', data);
}

// --- Data Apply ---
export async function getDataApplyListApi(params: PageParams) {
  return request.get<ApiResponse<PageResult<DataApplyItem>>>('/data-apply/page', { params });
}

export async function getDataApplyDetailApi(id: number) {
  return request.get<ApiResponse<DataApplyItem>>(`/data-apply/${id}`);
}

export async function createDataApplyApi(data: DataApplyFormParams) {
  return request.post<ApiResponse<null>>('/data-apply', data);
}

export async function updateDataApplyApi(id: number, data: DataApplyFormParams) {
  return request.put<ApiResponse<null>>(`/data-apply/${id}`, data);
}

export async function deleteDataApplyApi(id: number) {
  return request.delete<ApiResponse<null>>(`/data-apply/${id}`);
}

// Data Apply - Approval
export async function getApproveListApi(params: PageParams) {
  return request.get<ApiResponse<PageResult<DataApplyItem>>>('/data-apply/approve/page', { params });
}

export async function submitApprovalApi(data: ApproveFormParams) {
  return request.post<ApiResponse<null>>('/data-apply/approve', data);
}

// --- Overview ---
export async function getOverviewStatsApi() {
  return request.get<ApiResponse<OverviewStats>>('/overview/stats');
}

export async function getOverviewChartDataApi(type: string) {
  return request.get<ApiResponse<ChartDataItem[]>>('/overview/chart', { params: { type } });
}

export async function getOverviewTrendApi() {
  return request.get<ApiResponse<TrendDataItem[]>>('/overview/trend');
}

// ==================== P0-C 双通道审核 / 流转轨迹 / 补正 ====================

export interface AuditDataItem {
  id: number;
  taskId?: number;
  templateId?: number;
  deptId?: number;
  fillUserId?: number;
  dataJson?: string;
  status?: number;
  submitTime?: string;
  auditStatus?: number;
  auditComment?: string;
  auditTime?: string;
  deptAuditStatus?: number;
  deptAuditComment?: string;
  deptAuditTime?: string;
  taskAuditStatus?: number;
  taskAuditComment?: string;
  taskAuditTime?: string;
  correctStatus?: number;
  correctComment?: string;
  inboundStatus?: number;
  inboundRows?: number;
  // 关联展示字段
  deptName?: string;
  taskName?: string;
  templateName?: string;
  fillUserName?: string;
  fillRealName?: string;
  belongTime?: string;
  createTime?: string;
}

export interface TrajectoryNode {
  id?: number;
  node_code?: string;
  node_name?: string;
  action?: string;
  operator_name?: string;
  comment?: string;
  create_time?: string;
}

export interface CorrectBatch {
  id?: number;
  task_id?: number;
  data_ids?: string;
  status?: number;
  comment?: string;
  start_time?: string;
  close_time?: string;
  start_user_name?: string;
}

/** 我的审核任务列表（分页） */
export async function getAuditMyTasksApi(params: PageParams & { auditStatus?: number }) {
  return request.get<ApiResponse<PageResult<AuditDataItem>>>('/audit/myTasks', { params });
}

/** 审核详情：某任务下的填报数据列表 */
export async function getAuditTaskDataApi(taskId: number) {
  return request.get<ApiResponse<PageResult<AuditDataItem>>>(`/audit/task/${taskId}`);
}

/** 审核统计（待审/通过/驳回/合计） */
export async function getAuditStatisticsApi() {
  return request.get<ApiResponse<Record<string, number>>>('/audit/statistics');
}

/** 部门审核（第一级） */
export async function deptAuditApi(data: { taskDataId: number; pass: boolean; comment?: string }) {
  return request.post<ApiResponse<null>>('/audit/dept-audit', null, { params: data });
}

/** 任务方审核（第二级，需部门审核通过） */
export async function taskAuditApi(data: { taskDataId: number; pass: boolean; comment?: string }) {
  return request.post<ApiResponse<null>>('/audit/task-audit', null, { params: data });
}

/** 任务流转轨迹 */
export async function getTrajectoryApi(taskId: number) {
  return request.get<ApiResponse<TrajectoryNode[]>>('/audit/trajectory', { params: { taskId } });
}

/** 发起补正 */
export async function startCorrectApi(data: { taskId: number; dataIds?: string; comment?: string }) {
  return request.post<ApiResponse<number>>('/audit/correct/start', null, { params: data });
}

/** 关闭补正 */
export async function closeCorrectApi(data: { taskId: number; comment?: string }) {
  return request.post<ApiResponse<null>>('/audit/correct/close', null, { params: data });
}

/** 补正批次列表 */
export async function getCorrectListApi(taskId: number) {
  return request.get<ApiResponse<CorrectBatch[]>>('/audit/correct/list', { params: { taskId } });
}

// ==================== P1-E 任务台账 ====================

export interface LedgerTypeItem {
  type: string;
  label: string;
}

export interface LedgerRate {
  dueTask?: number;
  doneTask?: number;
  ontimeTask?: number;
  overdueTask?: number;
  completeRate?: number;
  ontimeRate?: number;
  overdueRate?: number;
  deptName?: string;
  [key: string]: any;
}

/** 台账视图类型（6 个） */
export async function getLedgerTypesApi() {
  return request.get<ApiResponse<LedgerTypeItem[]>>('/ledger/types');
}

/** 三率统计 */
export async function getLedgerRatesApi(params?: { deptId?: number; taskId?: number }) {
  return request.get<ApiResponse<LedgerRate>>('/ledger/rates', { params });
}

/** 台账视图（分页） */
export async function getLedgerViewApi(type: string, params: PageParams) {
  return request.get<ApiResponse<{ list: Record<string, any>[]; total: number; headers?: Record<string, string> }>>(
    `/ledger/${type}`, { params }
  );
}

/** 导出台账（xlsx blob） */
export async function exportLedgerApi(type: string, limit = 20000) {
  return request.get<Blob>('/ledger/export', { params: { type, limit }, responseType: 'blob' });
}

// ==================== P1-D 附件与分片上传 ====================

export interface FileItem {
  id: number;
  taskId?: number;
  dataId?: number;
  templateId?: number;
  fileName: string;
  storePath?: string;
  fileSize?: number;
  fileType?: string;
  contentType?: string;
  md5?: string;
  uploadBy?: number;
  uploadByName?: string;
  createTime?: string;
}

export interface ChunkCheckResult {
  fileKey: string;
  uploadedChunks: number[];
  skipUpload: boolean;
  fileId?: number;
}

/** 普通上传 */
export async function uploadFileApi(file: File, extra?: Record<string, any>) {
  const fd = new FormData();
  fd.append('file', file);
  Object.entries(extra || {}).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') fd.append(k, String(v));
  });
  return request.post<ApiResponse<FileItem>>('/file/upload', fd, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
}

/** 分片检查（秒传 / 断点续传） */
export async function checkChunkApi(params: { fileKey: string; fileName?: string; md5?: string }) {
  return request.get<ApiResponse<ChunkCheckResult>>('/file/chunk/check', { params });
}

/** 上传单个分片 */
export async function uploadChunkApi(fileKey: string, chunkIndex: number, chunk: Blob) {
  const fd = new FormData();
  fd.append('fileKey', fileKey);
  fd.append('chunkIndex', String(chunkIndex));
  fd.append('file', chunk);
  return request.post<ApiResponse<{ received: number }>>('/file/chunk', fd, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
}

/** 合并分片 */
export async function mergeChunkApi(data: {
  fileKey: string; fileName: string; chunkTotal: number;
  taskId?: number; dataId?: number; fileType?: string;
}) {
  return request.post<ApiResponse<FileItem>>('/file/chunk/merge', null, { params: data });
}

/** 附件列表（按任务） */
export async function getFileListApi(taskId: number) {
  return request.get<ApiResponse<FileItem[]>>('/file/list', { params: { taskId } });
}

/** 删除附件（软删除） */
export async function deleteFileApi(id: number) {
  return request.delete<ApiResponse<null>>(`/file/${id}`);
}

/** 下载附件（blob，需带 token） */
export async function downloadFileApi(id: number) {
  return request.get<Blob>(`/file/${id}/download`, { responseType: 'blob' });
}

// ==================== P1-F / P2-G 待办 / 字典 / 数据范围 / 审计日志 ====================

export interface TodoSummary {
  todoCount?: number;
  doneCount?: number;
  todayCount?: number;
  overdueCount?: number;
  [key: string]: any;
}

export interface TodoMessage {
  id: number;
  userId?: number;
  title: string;
  content?: string;
  msgType?: string;
  bizTaskId?: number;
  readFlag?: number;
  createBy?: number;
  createTime?: string;
}

/** 待办汇总 */
export async function getTodoSummaryApi() {
  return request.get<ApiResponse<TodoSummary>>('/todo/summary');
}

/** 我的消息（分页） */
export async function getTodoListApi(params: PageParams & { readFlag?: number; msgType?: string }) {
  return request.get<ApiResponse<{ list: TodoMessage[]; total: number }>>('/todo/list', { params });
}

/** 标记已读 */
export async function readTodoApi(id: number) {
  return request.post<ApiResponse<null>>(`/todo/${id}/read`);
}

/** 全部已读 */
export async function readAllTodoApi() {
  return request.post<ApiResponse<{ updated: number }>>('/todo/read-all');
}

/** 催办 */
export async function remindTodoApi(data: { taskId: number; comment?: string }) {
  return request.post<ApiResponse<Record<string, any>>>('/todo/remind', null, { params: data });
}

/** 手动触发超期扫描 */
export async function scanOverdueApi() {
  return request.post<ApiResponse<Record<string, any>>>('/todo/scan');
}

export interface DictItem {
  id: number;
  dictType: string;
  dictCode: string;
  dictLabel: string;
  sort?: number;
  status?: number;
  remark?: string;
}

/** 全部字典（按类型分组） */
export async function getDictAllApi() {
  return request.get<ApiResponse<Record<string, DictItem[]>>>('/dict/all');
}

/** 按类型查询（含停用，管理用） */
export async function getDictManageApi(dictType?: string) {
  return request.get<ApiResponse<DictItem[]>>(`/dict/manage/${dictType || ''}`);
}

export async function addDictApi(data: Partial<DictItem>) {
  return request.post<ApiResponse<number>>('/dict', data);
}

export async function updateDictApi(data: Partial<DictItem>) {
  return request.put<ApiResponse<null>>('/dict', data);
}

export async function deleteDictApi(id: number) {
  return request.delete<ApiResponse<null>>(`/dict/${id}`);
}

export interface DataRangeConfig {
  id?: number;
  configName?: string;
  datasourceId?: number;
  sqlText?: string;
  remark?: string;
  status?: number;
  createTime?: string;
  [key: string]: any;
}

export interface DataRangeRunResult {
  sql?: string;
  columns?: string[];
  rows?: Record<string, any>[];
  rowCount?: number;
  costMs?: number;
}

export async function getDataRangeListApi() {
  return request.get<ApiResponse<DataRangeConfig[]>>('/dataRange/list');
}

export async function getDataRangeApi(id: number) {
  return request.get<ApiResponse<DataRangeConfig>>(`/dataRange/${id}`);
}

export async function saveDataRangeApi(data: DataRangeConfig) {
  return request.post<ApiResponse<number>>('/dataRange', data);
}

export async function updateDataRangeApi(data: DataRangeConfig) {
  return request.put<ApiResponse<number>>('/dataRange', data);
}

export async function deleteDataRangeApi(id: number) {
  return request.delete<ApiResponse<null>>(`/dataRange/${id}`);
}

/** 预览（执行配置里的 SQL） */
export async function previewDataRangeApi(id: number, deptId?: number) {
  return request.get<ApiResponse<DataRangeRunResult>>(`/dataRange/${id}/preview`, { params: { deptId } });
}

/** SQL 试跑（不落库） */
export async function runDataRangeSqlApi(data: {
  datasourceId?: number; sql: string; deptId?: number; limit?: number;
}) {
  return request.post<ApiResponse<DataRangeRunResult>>('/dataRange/run', data);
}

export interface OperLogItem {
  id: number;
  userId?: number;
  username?: string;
  module?: string;
  action?: string;
  target?: string;
  ip?: string;
  costMs?: number;
  status?: number;
  errorMsg?: string;
  createTime?: string;
}

export async function getOperLogPageApi(params: PageParams & {
  module?: string; action?: string; userId?: number; status?: number;
}) {
  return request.get<ApiResponse<{ list: OperLogItem[]; total: number }>>('/operLog/page', { params });
}
