import { getFillTaskList, getFillTaskDetail, saveFillData, submitFillData, uploadFillExcel } from '@/services/api';
import { message } from 'antd';

export default {
  namespace: 'fill',

  state: {
    list: [],
    total: 0,
    loading: false,
    current: {},
    currentData: {},
  },

  effects: {
    *fetchList({ payload }, { call, put }) {
      yield put({ type: 'updateState', payload: { loading: true } });
      const response = yield call(getFillTaskList, payload);
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
      const response = yield call(getFillTaskDetail, payload);
      if (response && response.code === 0) {
        yield put({
          type: 'updateState',
          payload: {
            current: response.data.task || {},
            currentData: response.data.fillData || {},
          },
        });
        return response.data;
      }
      return null;
    },

    *saveData({ payload }, { call }) {
      const response = yield call(saveFillData, payload);
      if (response && response.code === 0) {
        message.success('数据保存成功');
        return true;
      }
      return false;
    },

    *submit({ payload }, { call }) {
      const { id, data } = payload;
      const response = yield call(submitFillData, id, data);
      if (response && response.code === 0) {
        message.success('提交成功');
        return true;
      }
      return false;
    },

    *uploadExcel({ payload }, { call }) {
      const response = yield call(uploadFillExcel, payload);
      if (response && response.code === 0) {
        message.success('Excel导入成功');
        return response.data;
      }
      return null;
    },
  },

  reducers: {
    updateState(state, { payload }) {
      return { ...state, ...payload };
    },
  },
};
