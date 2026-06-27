import { getDeptTree, getDeptList, addDept, updateDept, deleteDept } from '@/services/api';
import { message } from 'antd';

export default {
  namespace: 'dept',

  state: {
    treeData: [],
    list: [],
    total: 0,
    loading: false,
  },

  effects: {
    *fetchTree(_, { call, put }) {
      const response = yield call(getDeptTree);
      if (response && response.code === 0) {
        yield put({
          type: 'updateState',
          payload: { treeData: response.data || [] },
        });
      }
    },

    *fetchList({ payload }, { call, put }) {
      yield put({ type: 'updateState', payload: { loading: true } });
      const response = yield call(getDeptList, payload);
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
      const response = yield call(addDept, payload);
      if (response && response.code === 0) {
        message.success('新增部门成功');
        return true;
      }
      return false;
    },

    *update({ payload }, { call }) {
      const response = yield call(updateDept, payload);
      if (response && response.code === 0) {
        message.success('更新部门成功');
        return true;
      }
      return false;
    },

    *delete({ payload }, { call }) {
      const response = yield call(deleteDept, payload);
      if (response && response.code === 0) {
        message.success('删除部门成功');
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
