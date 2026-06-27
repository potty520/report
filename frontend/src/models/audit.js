import { getAuditList, getAuditDetail, doAudit, batchAudit } from '@/services/api';
import { message } from 'antd';

export default {
  namespace: 'audit',

  state: {
    list: [],
    total: 0,
    loading: false,
    current: {},
  },

  effects: {
    *fetchList({ payload }, { call, put }) {
      yield put({ type: 'updateState', payload: { loading: true } });
      const response = yield call(getAuditList, payload);
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

    *fetchDetail({ payload }, { call, put }) {
      const response = yield call(getAuditDetail, payload);
      if (response && response.code === 0) {
        yield put({ type: 'updateState', payload: { current: response.data } });
        return response.data;
      }
      return null;
    },

    *doAudit({ payload }, { call }) {
      const response = yield call(doAudit, payload);
      if (response && response.code === 0) {
        message.success('审核操作成功');
        return true;
      }
      return false;
    },

    *batchAudit({ payload }, { call }) {
      const response = yield call(batchAudit, payload);
      if (response && response.code === 0) {
        message.success('批量审核成功');
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
