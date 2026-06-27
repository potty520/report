import request from '@/utils/request';

const prefix = '/api';

// ==================== 登录 ====================
export async function login(data) {
  return request(`${prefix}/login`, { method: 'POST', data });
}

export async function logout() {
  return request(`${prefix}/logout`, { method: 'POST' });
}

export async function getCaptcha() {
  return request(`${prefix}/captcha`);
}

export async function getPublicKey() {
  return request(`${prefix}/publicKey`);
}

// ==================== 用户管理 ====================
export async function getUserList(params) {
  return request(`${prefix}/user/list`, { method: 'GET', params });
}

export async function getUserDetail(id) {
  return request(`${prefix}/user/${id}`, { method: 'GET' });
}

export async function addUser(data) {
  return request(`${prefix}/user`, { method: 'POST', data });
}

export async function updateUser(data) {
  return request(`${prefix}/user`, { method: 'PUT', data });
}

export async function deleteUser(id) {
  return request(`${prefix}/user/${id}`, { method: 'DELETE' });
}

export async function resetPassword(id, data) {
  return request(`${prefix}/user/${id}/resetPassword`, { method: 'PUT', data });
}

export async function assignRoles(userId, data) {
  return request(`${prefix}/user/${userId}/roles`, { method: 'PUT', data });
}

export async function changeUserStatus(id, status) {
  return request(`${prefix}/user/${id}/status`, { method: 'PUT', data: { status } });
}

// ==================== 角色管理 ====================
export async function getRoleList(params) {
  return request(`${prefix}/role/list`, { method: 'GET', params });
}

export async function getAllRoles() {
  return request(`${prefix}/role/all`, { method: 'GET' });
}

export async function addRole(data) {
  return request(`${prefix}/role`, { method: 'POST', data });
}

export async function updateRole(data) {
  return request(`${prefix}/role`, { method: 'PUT', data });
}

export async function deleteRole(id) {
  return request(`${prefix}/role/${id}`, { method: 'DELETE' });
}

export async function assignMenus(roleId, data) {
  return request(`${prefix}/role/${roleId}/menus`, { method: 'PUT', data });
}

export async function getRoleMenus(roleId) {
  return request(`${prefix}/role/${roleId}/menus`, { method: 'GET' });
}

// ==================== 菜单管理 ====================
export async function getMenuTree(params) {
  return request(`${prefix}/menu/tree`, { method: 'GET', params });
}

export async function getMenuList() {
  return request(`${prefix}/menu/list`, { method: 'GET' });
}

export async function addMenu(data) {
  return request(`${prefix}/menu`, { method: 'POST', data });
}

export async function updateMenu(data) {
  return request(`${prefix}/menu`, { method: 'PUT', data });
}

export async function deleteMenu(id) {
  return request(`${prefix}/menu/${id}`, { method: 'DELETE' });
}

// ==================== 登录日志 ====================
export async function getLogList(params) {
  return request(`${prefix}/log/list`, { method: 'GET', params });
}

export async function getLogDetail(id) {
  return request(`${prefix}/log/${id}`, { method: 'GET' });
}

// ==================== 部门管理 ====================
export async function getDeptTree() {
  return request(`${prefix}/dept/tree`, { method: 'GET' });
}

export async function getDeptList(params) {
  return request(`${prefix}/dept/list`, { method: 'GET', params });
}

export async function addDept(data) {
  return request(`${prefix}/dept`, { method: 'POST', data });
}

export async function updateDept(data) {
  return request(`${prefix}/dept`, { method: 'PUT', data });
}

export async function deleteDept(id) {
  return request(`${prefix}/dept/${id}`, { method: 'DELETE' });
}

// ==================== 项目管理 ====================
export async function getProjectTree() {
  return request(`${prefix}/project/tree`, { method: 'GET' });
}

export async function getProjectList(params) {
  return request(`${prefix}/project/list`, { method: 'GET', params });
}

export async function addProject(data) {
  return request(`${prefix}/project`, { method: 'POST', data });
}

export async function updateProject(data) {
  return request(`${prefix}/project`, { method: 'PUT', data });
}

export async function deleteProject(id) {
  return request(`${prefix}/project/${id}`, { method: 'DELETE' });
}

// ==================== 模板管理 ====================
export async function getTemplateList(params) {
  return request(`${prefix}/template/list`, { method: 'GET', params });
}

export async function getTemplateDetail(id) {
  return request(`${prefix}/template/${id}`, { method: 'GET' });
}

export async function addTemplate(data) {
  return request(`${prefix}/template`, { method: 'POST', data });
}

export async function updateTemplate(data) {
  return request(`${prefix}/template`, { method: 'PUT', data });
}

export async function deleteTemplate(id) {
  return request(`${prefix}/template/${id}`, { method: 'DELETE' });
}

export async function submitTemplate(id) {
  return request(`${prefix}/template/${id}/submit`, { method: 'PUT' });
}

export async function auditTemplate(id, data) {
  return request(`${prefix}/template/${id}/audit`, { method: 'PUT', data });
}

export async function uploadExcel(formData) {
  return request(`${prefix}/template/uploadExcel`, {
    method: 'POST',
    data: formData,
    requestType: 'form',
  });
}

export async function getFieldMapping(templateId) {
  return request(`${prefix}/template/${templateId}/fieldMapping`, { method: 'GET' });
}

export async function saveFieldMapping(templateId, data) {
  return request(`${prefix}/template/${templateId}/fieldMapping`, { method: 'PUT', data });
}

// ==================== 数据源管理 ====================
export async function getDatasourceList(params) {
  return request(`${prefix}/datasource/list`, { method: 'GET', params });
}

export async function addDatasource(data) {
  return request(`${prefix}/datasource`, { method: 'POST', data });
}

export async function updateDatasource(data) {
  return request(`${prefix}/datasource`, { method: 'PUT', data });
}

export async function deleteDatasource(id) {
  return request(`${prefix}/datasource/${id}`, { method: 'DELETE' });
}

export async function testConnection(id) {
  return request(`${prefix}/datasource/${id}/test`, { method: 'POST' });
}

// ==================== 任务管理 ====================
export async function getTaskList(params) {
  return request(`${prefix}/task/list`, { method: 'GET', params });
}

export async function addTask(data) {
  return request(`${prefix}/task`, { method: 'POST', data });
}

export async function updateTask(data) {
  return request(`${prefix}/task`, { method: 'PUT', data });
}

export async function deleteTask(id) {
  return request(`${prefix}/task/${id}`, { method: 'DELETE' });
}

export async function assignTask(id, data) {
  return request(`${prefix}/task/${id}/assign`, { method: 'PUT', data });
}

export async function getTaskDetail(id) {
  return request(`${prefix}/task/${id}`, { method: 'GET' });
}

export async function submitTask(id) {
  return request(`${prefix}/task/${id}/submit`, { method: 'PUT' });
}

export async function getLedgerList(params) {
  return request(`${prefix}/task/ledger`, { method: 'GET', params });
}

// ==================== 填报管理 ====================
export async function getFillTaskList(params) {
  return request(`${prefix}/fill/taskList`, { method: 'GET', params });
}

export async function getFillTaskDetail(id) {
  return request(`${prefix}/fill/task/${id}`, { method: 'GET' });
}

export async function saveFillData(data) {
  return request(`${prefix}/fill/save`, { method: 'POST', data });
}

export async function submitFillData(id, data) {
  return request(`${prefix}/fill/${id}/submit`, { method: 'PUT', data });
}

export async function uploadFillExcel(formData) {
  return request(`${prefix}/fill/uploadExcel`, {
    method: 'POST',
    data: formData,
    requestType: 'form',
  });
}

// ==================== 审核管理 ====================
export async function getAuditList(params) {
  return request(`${prefix}/audit/list`, { method: 'GET', params });
}

export async function getAuditDetail(id) {
  return request(`${prefix}/audit/${id}`, { method: 'GET' });
}

export async function doAudit(data) {
  return request(`${prefix}/audit`, { method: 'PUT', data });
}

export async function batchAudit(data) {
  return request(`${prefix}/audit/batch`, { method: 'PUT', data });
}

// ==================== 数据申请 ====================
export async function getDataApplyList(params) {
  return request(`${prefix}/dataApply/list`, { method: 'GET', params });
}

export async function addDataApply(data) {
  return request(`${prefix}/dataApply`, { method: 'POST', data });
}

export async function deleteDataApply(id) {
  return request(`${prefix}/dataApply/${id}`, { method: 'DELETE' });
}

export async function getDataApplyDetail(id) {
  return request(`${prefix}/dataApply/${id}`, { method: 'GET' });
}

export async function getApproveList(params) {
  return request(`${prefix}/dataApply/approveList`, { method: 'GET', params });
}

export async function approveDataApply(data) {
  return request(`${prefix}/dataApply/approve`, { method: 'PUT', data });
}

// ==================== 总览 ====================
export async function getOverviewStats() {
  return request(`${prefix}/overview/stats`, { method: 'GET' });
}

export async function getOverviewCharts(params) {
  return request(`${prefix}/overview/charts`, { method: 'GET', params });
}

// ==================== 个人中心 ====================
export async function changePassword(data) {
  return request(`${prefix}/user/changePassword`, { method: 'PUT', data });
}

export async function getCurrentUserInfo() {
  return request(`${prefix}/user/current`, { method: 'GET' });
}
