import { create } from 'zustand';
import {
  getDatasourceListApi, getDatasourceDetailApi, createDatasourceApi,
  updateDatasourceApi, deleteDatasourceApi, testDatasourceConnectionApi,
  PageParams, DatasourceItem, DatasourceFormParams, TestConnectionResult,
} from '@/services/api';

interface DatasourceState {
  list: DatasourceItem[];
  total: number;
  loading: boolean;
  currentItem: DatasourceItem | null;
  testResult: TestConnectionResult | null;

  fetchList: (params: PageParams) => Promise<void>;
  fetchDetail: (id: number) => Promise<void>;
  create: (data: DatasourceFormParams) => Promise<void>;
  update: (data: DatasourceFormParams) => Promise<void>;
  remove: (id: number) => Promise<void>;
  testConnection: (data: DatasourceFormParams) => Promise<void>;
  setCurrentItem: (item: DatasourceItem | null) => void;
  clearTestResult: () => void;
}

export const useDatasourceStore = create<DatasourceState>((set) => ({
  list: [],
  total: 0,
  loading: false,
  currentItem: null,
  testResult: null,

  fetchList: async (params: PageParams) => {
    set({ loading: true });
    try {
      const res = await getDatasourceListApi(params);
      const { list: records, total } = res.data.data;
      set({ list: records, total, loading: false });
    } catch {
      set({ loading: false });
    }
  },

  fetchDetail: async (id: number) => {
    const res = await getDatasourceDetailApi(id);
    set({ currentItem: res.data.data });
  },

  create: async (data: DatasourceFormParams) => {
    await createDatasourceApi(data);
  },

  update: async (data: DatasourceFormParams) => {
    await updateDatasourceApi(data);
  },

  remove: async (id: number) => {
    await deleteDatasourceApi(id);
  },

  testConnection: async (data: DatasourceFormParams) => {
    const res = await testDatasourceConnectionApi(data);
    set({ testResult: res.data.data });
  },

  setCurrentItem: (item: DatasourceItem | null) => {
    set({ currentItem: item, testResult: null });
  },

  clearTestResult: () => {
    set({ testResult: null });
  },
}));
