import { getDataApplyList, addDataApply, deleteDataApply, getDataApplyDetail, getApproveList, approveDataApply } from '@/services/api';
import { message } from 'antd';

export default {
  namespace: 'dataApply',

  state: {
    list: [],
    total: 0,
    loading: false,
    current: {},
    approveList: [],
    approveTotal: 0,
  },

  effects: {
    *fetchList({ payload }, { call, put }) {
      yield put({ type: 'updateState', payload: { loading: true } });
      const response = yield call(getDataApplyList, payload);
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
      const response = yield call(getDataApplyDetail, payload);
      if (response && response.code === 0) {
        yield put({ type: 'updateState', payload: { current: response.data } });
        return response.data;
      }
      return null;
    },

    *add({ payload }, { call }) {
      const response = yield call(addDataApply, payload);
      if (response && response.code === 0) {
        message.success('申请提交成功');
        return true;
      }
      return false;
    },

    *delete({ payload }, { call }) {
      const response = yield call(deleteDataApply, payload);
      if (response && response.code === 0) {
        message.success('删除成功');
        return true;
      }
      return false;
    },

    *fetchApproveList({ payload }, { call, put }) {
      yield put({ type: 'updateState', payload: { loading: true } });
      const response = yield call(getApproveList, payload);
      if (response && response.code === 0) {
        yield put({
          type: 'updateState',
          payload: {
            approveList: response.data.records || response.data.list || [],
            approveTotal: response.data.total || 0,
          },
        });
      }
      yield put({ type: 'updateState', payload: { loading: false } });
    },

    *approve({ payload }, { call }) {
      const response = yield call(approveDataApply, payload);
      if (response && response.code === 0) {
        message.success('审批完成');
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
