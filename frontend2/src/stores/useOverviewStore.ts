import { create } from 'zustand';
import {
  getOverviewStatsApi, getOverviewChartDataApi, getOverviewTrendApi,
  OverviewStats, ChartDataItem, TrendDataItem,
} from '@/services/api';

interface OverviewState {
  stats: OverviewStats | null;
  chartData: ChartDataItem[];
  trendData: TrendDataItem[];
  loading: boolean;

  fetchStats: () => Promise<void>;
  fetchChartData: (type: string) => Promise<void>;
  fetchTrend: () => Promise<void>;
}

export const useOverviewStore = create<OverviewState>((set) => ({
  stats: null,
  chartData: [],
  trendData: [],
  loading: false,

  fetchStats: async () => {
    set({ loading: true });
    try {
      const res = await getOverviewStatsApi();
      set({ stats: res.data.data, loading: false });
    } catch {
      set({ loading: false });
    }
  },

  fetchChartData: async (type: string) => {
    try {
      const res = await getOverviewChartDataApi(type);
      set({ chartData: res.data.data });
    } catch {
      // ignore
    }
  },

  fetchTrend: async () => {
    try {
      const res = await getOverviewTrendApi();
      set({ trendData: res.data.data });
    } catch {
      // ignore
    }
  },
}));
