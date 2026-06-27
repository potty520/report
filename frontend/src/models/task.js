import { getTaskList, addTask, updateTask, deleteTask, assignTask, getTaskDetail, submitTask, getLedgerList } from '@/services/api';
import { message } from 'antd';

export default {
  namespace: 'task',

  state: {
    list: [],
    total: 0,
    loading: false,
    current: {},
    ledgerList: [],
    ledgerTotal: 0,
  },

  effects: {
    *fetchList({ payload }, { call, put }) {
      yield put({ type: 'updateState', payload: { loading: true } });
      const response = yield call(getTaskList, payload);
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
      const response = yield call(getTaskDetail, payload);
      if (response && response.code === 0) {
        yield put({ type: 'updateState', payload: { current: response.data } });
        return response.data;
      }
      return null;
    },

    *add({ payload }, { call }) {
      const response = yield call(addTask, payload);
      if (response && response.code === 0) {
        message.success('新增任务成功');
        return true;
      }
      return false;
    },

    *update({ payload }, { call }) {
      const response = yield call(updateTask, payload);
      if (response && response.code === 0) {
        message.success('更新任务成功');
        return true;
      }
      return false;
    },

    *delete({ payload }, { call }) {
      const response = yield call(deleteTask, payload);
      if (response && response.code === 0) {
        message.success('删除任务成功');
        return true;
      }
      return false;
    },

    *assignTask({ payload }, { call }) {
      const { id, ...data } = payload;
      const response = yield call(assignTask, id, data);
      if (response && response.code === 0) {
        message.success('任务分派成功');
        return true;
      }
      return false;
    },

    *submit({ payload }, { call }) {
      const response = yield call(submitTask, payload);
      if (response && response.code === 0) {
        message.success('任务提交成功');
        return true;
      }
      return false;
    },

    *fetchLedger({ payload }, { call, put }) {
      yield put({ type: 'updateState', payload: { loading: true } });
      const response = yield call(getLedgerList, payload);
      if (response && response.code === 0) {
        yield put({
          type: 'updateState',
          payload: {
            ledgerList: response.data.records || response.data.list || [],
            ledgerTotal: response.data.total || 0,
          },
        });
      }
      yield put({ type: 'updateState', payload: { loading: false } });
    },
  },

  reducers: {
    updateState(state, { payload }) {
      return { ...state, ...payload };
    },
  },
};
