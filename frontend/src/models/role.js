import { getRoleList, addRole, updateRole, deleteRole, assignMenus, getRoleMenus } from '@/services/api';
import { message } from 'antd';

export default {
  namespace: 'role',

  state: {
    list: [],
    total: 0,
    loading: false,
    roleMenus: [],
  },

  effects: {
    *fetchList({ payload }, { call, put }) {
      yield put({ type: 'updateState', payload: { loading: true } });
      const response = yield call(getRoleList, payload);
      if (response && response.code === 0) {
        yield put({
          type: 'updateState',
          payload: {
            list: response.data.records || response.data.list || [],
            total: response.data.total || 0,
          },
        });
      }
      yield put({ type: 'updateState', payload: { loading: false } });
    },

    *add({ payload }, { call }) {
      const response = yield call(addRole, payload);
      if (response && response.code === 0) {
        message.success('新增角色成功');
        return true;
      }
      return false;
    },

    *update({ payload }, { call }) {
      const response = yield call(updateRole, payload);
      if (response && response.code === 0) {
        message.success('更新角色成功');
        return true;
      }
      return false;
    },

    *delete({ payload }, { call }) {
      const response = yield call(deleteRole, payload);
      if (response && response.code === 0) {
        message.success('删除角色成功');
        return true;
      }
      return false;
    },

    *fetchRoleMenus({ payload }, { call, put }) {
      const response = yield call(getRoleMenus, payload);
      if (response && response.code === 0) {
        yield put({ type: 'updateState', payload: { roleMenus: response.data || [] } });
        return response.data || [];
      }
      return [];
    },

    *assignMenus({ payload }, { call }) {
      const { roleId, menuIds } = payload;
      const response = yield call(assignMenus, roleId, { menuIds });
      if (response && response.code === 0) {
        message.success('分配权限成功');
        return true;
      }
      return false;
    },
  },

  reducers: {
    updateState(state, { payload }) {
      return { ...state, ...payload };
    },
  },
};
