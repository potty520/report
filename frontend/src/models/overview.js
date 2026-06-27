import { getOverviewStats, getOverviewCharts } from '@/services/api';

export default {
  namespace: 'overview',

  state: {
    stats: {},
    chartData: {},
    loading: false,
  },

  effects: {
    *fetchStats(_, { call, put }) {
      yield put({ type: 'updateState', payload: { loading: true } });
      const response = yield call(getOverviewStats);
      if (response && response.code === 0) {
        yield put({
          type: 'updateState',
          payload: { stats: response.data || {} },
        });
      }
      yield put({ type: 'updateState', payload: { loading: false } });
    },

    *fetchCharts({ payload }, { call, put }) {
      const response = yield call(getOverviewCharts, payload);
      if (response && response.code === 0) {
        yield put({
          type: 'updateState',
          payload: { chartData: response.data || {} },
        });
      }
    },
  },

  reducers: {
    updateState(state, { payload }) {
      return { ...state, ...payload };
    },
  },
};
