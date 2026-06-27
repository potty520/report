import { create } from 'zustand';
import {
  getDeptTreeApi, getDeptDetailApi, createDeptApi, updateDeptApi, deleteDeptApi,
  DeptItem, DeptFormParams,
} from '@/services/api';

interface DeptState {
  tree: DeptItem[];
  loading: boolean;
  currentItem: DeptItem | null;
  selectedKey: number | null;

  fetchTree: () => Promise<void>;
  fetchDetail: (id: number) => Promise<void>;
  create: (data: DeptFormParams) => Promise<void>;
  update: (data: DeptFormParams) => Promise<void>;
  remove: (id: number) => Promise<void>;
  setCurrentItem: (item: DeptItem | null) => void;
  setSelectedKey: (key: number | null) => void;
}

export const useDeptStore = create<DeptState>((set) => ({
  tree: [],
  loading: false,
  currentItem: null,
  selectedKey: null,

  fetchTree: async () => {
    set({ loading: true });
    try {
      const res = await getDeptTreeApi();
      set({ tree: res.data.data, loading: false });
    } catch {
      set({ loading: false });
    }
  },

  fetchDetail: async (id: number) => {
    const res = await getDeptDetailApi(id);
    set({ currentItem: res.data.data });
  },

  create: async (data: DeptFormParams) => {
    await createDeptApi(data);
  },

  update: async (data: DeptFormParams) => {
    await updateDeptApi(data);
  },

  remove: async (id: number) => {
    await deleteDeptApi(id);
  },

  setCurrentItem: (item: DeptItem | null) => {
    set({ currentItem: item });
  },

  setSelectedKey: (key: number | null) => {
    set({ selectedKey: key });
  },
}));
