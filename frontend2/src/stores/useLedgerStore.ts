import { create } from 'zustand';
import {
  getLedgerTypesApi, getLedgerRatesApi, getLedgerViewApi,
  LedgerTypeItem, LedgerRate,
} from '@/services/api';

export interface LedgerView {
  headers: Record<string, string>;
  list: Record<string, any>[];
  total: number;
}

interface LedgerState {
  types: LedgerTypeItem[];
  rates: LedgerRate;
  view: LedgerView;
  loading: boolean;
  activeType?: string;
  fetchTypes: () => Promise<LedgerTypeItem[]>;
  fetchRates: (params?: { deptId?: number; taskId?: number }) => Promise<void>;
  fetchView: (type: string, page?: number, pageSize?: number) => Promise<void>;
  setActiveType: (type?: string) => void;
}

export const useLedgerStore = create<LedgerState>((set) => ({
  types: [],
  rates: {},
  view: { headers: {}, list: [], total: 0 },
  loading: false,
  activeType: undefined,

  fetchTypes: async () => {
    try {
      const res = await getLedgerTypesApi();
      const types = res.data.data || [];
      set({ types });
      return types;
    } catch {
      set({ types: [] });
      return [];
    }
  },

  fetchRates: async (params) => {
    try {
      const res = await getLedgerRatesApi(params);
      set({ rates: res.data.data || {} });
    } catch {
      set({ rates: {} });
    }
  },

  fetchView: async (type, page = 1, pageSize = 20) => {
    set({ loading: true, activeType: type });
    try {
      const res = await getLedgerViewApi(type, { page, pageSize });
      const data = res.data.data;
      set({
        view: {
          headers: data?.headers || {},
          list: data?.list || [],
          total: data?.total || 0,
        },
        loading: false,
      });
    } catch {
      set({ view: { headers: {}, list: [], total: 0 }, loading: false });
    }
  },

  setActiveType: (type) => set({ activeType: type }),
}));
