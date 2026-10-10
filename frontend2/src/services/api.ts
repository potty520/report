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
