import { getProjectTree, getProjectList, addProject, updateProject, deleteProject } from '@/services/api';
import { message } from 'antd';

export default {
  namespace: 'project',

  state: {
    treeData: [],
    list: [],
    total: 0,
    loading: false,
  },

  effects: {
    *fetchTree(_, { call, put }) {
      const response = yield call(getProjectTree);
      if (response && response.code === 0) {
        yield put({
          type: 'updateState',
          payload: { treeData: response.data || [] },
        });
      }
    },

    *fetchList({ payload }, { call, put }) {
      yield put({ type: 'updateState', payload: { loading: true } });
      const response = yield call(getProjectList, payload);
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
      const response = yield call(addProject, payload);
      if (response && response.code === 0) {
        message.success('新增项目成功');
        return true;
      }
      return false;
    },

    *update({ payload }, { call }) {
      const response = yield call(updateProject, payload);
      if (response && response.code === 0) {
        message.success('更新项目成功');
        return true;
      }
      return false;
    },

    *delete({ payload }, { call }) {
      const response = yield call(deleteProject, payload);
      if (response && response.code === 0) {
        message.success('删除项目成功');
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
