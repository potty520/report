import { getMenuTree, addMenu, updateMenu, deleteMenu } from '@/services/api';
import { message } from 'antd';

export default {
  namespace: 'menu',

  state: {
    treeData: [],
    loading: false,
  },

  effects: {
    *fetchTree(_, { call, put }) {
      yield put({ type: 'updateState', payload: { loading: true } });
      const response = yield call(getMenuTree);
      if (response && response.code === 0) {
        yield put({
          type: 'updateState',
          payload: { treeData: response.data || [] },
        });
      }
      yield put({ type: 'updateState', payload: { loading: false } });
    },

    *add({ payload }, { call }) {
      const response = yield call(addMenu, payload);
      if (response && response.code === 0) {
        message.success('新增菜单成功');
        return true;
      }
      return false;
    },

    *update({ payload }, { call }) {
      const response = yield call(updateMenu, payload);
      if (response && response.code === 0) {
        message.success('更新菜单成功');
        return true;
      }
      return false;
    },

    *delete({ payload }, { call }) {
      const response = yield call(deleteMenu, payload);
      if (response && response.code === 0) {
        message.success('删除菜单成功');
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
