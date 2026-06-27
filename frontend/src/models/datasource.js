import { getDatasourceList, addDatasource, updateDatasource, deleteDatasource, testConnection } from '@/services/api';
import { message } from 'antd';

export default {
  namespace: 'datasource',

  state: {
    list: [],
    total: 0,
    loading: false,
  },

  effects: {
    *fetchList({ payload }, { call, put }) {
      yield put({ type: 'updateState', payload: { loading: true } });
      const response = yield call(getDatasourceList, payload);
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
      const response = yield call(addDatasource, payload);
      if (response && response.code === 0) {
        message.success('新增数据源成功');
        return true;
      }
      return false;
    },

    *update({ payload }, { call }) {
      const response = yield call(updateDatasource, payload);
      if (response && response.code === 0) {
        message.success('更新数据源成功');
        return true;
      }
      return false;
    },

    *delete({ payload }, { call }) {
      const response = yield call(deleteDatasource, payload);
      if (response && response.code === 0) {
        message.success('删除数据源成功');
        return true;
      }
      return false;
    },

    *testConnection({ payload }, { call }) {
      const response = yield call(testConnection, payload);
      if (response && response.code === 0) {
        message.success('连接测试成功');
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
