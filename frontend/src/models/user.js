import { getUserList, addUser, updateUser, deleteUser, assignRoles, changeUserStatus, resetPassword } from '@/services/api';
import { message } from 'antd';

export default {
  namespace: 'user',

  state: {
    list: [],
    total: 0,
    current: {},
    loading: false,
  },

  effects: {
    *fetchList({ payload }, { call, put }) {
      yield put({ type: 'updateState', payload: { loading: true } });
      const response = yield call(getUserList, payload);
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

    *add({ payload }, { call, put }) {
      const response = yield call(addUser, payload);
      if (response && response.code === 0) {
        message.success('新增用户成功');
        return true;
      }
      return false;
    },

    *update({ payload }, { call, put }) {
      const response = yield call(updateUser, payload);
      if (response && response.code === 0) {
        message.success('更新用户成功');
        return true;
      }
      return false;
    },

    *delete({ payload }, { call, put }) {
      const response = yield call(deleteUser, payload);
      if (response && response.code === 0) {
        message.success('删除用户成功');
        return true;
      }
      return false;
    },

    *assignRoles({ payload }, { call }) {
      const { userId, roleIds } = payload;
      const response = yield call(assignRoles, userId, { roleIds });
      if (response && response.code === 0) {
        message.success('分配角色成功');
        return true;
      }
      return false;
    },

    *changeStatus({ payload }, { call }) {
      const { id, status } = payload;
      const response = yield call(changeUserStatus, id, status);
      if (response && response.code === 0) {
        message.success('状态更新成功');
        return true;
      }
      return false;
    },

    *resetPassword({ payload }, { call }) {
      const { id, password } = payload;
      const response = yield call(resetPassword, id, { password });
      if (response && response.code === 0) {
        message.success('密码重置成功');
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
