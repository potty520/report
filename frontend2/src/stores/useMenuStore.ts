import { create } from 'zustand';
import {
  getMenuTreeApi, getMenuDetailApi, createMenuApi, updateMenuApi, deleteMenuApi,
  MenuTreeItem, MenuFormParams,
} from '@/services/api';

interface MenuState {
  tree: MenuTreeItem[];
  loading: boolean;
  currentItem: MenuTreeItem | null;
  expandedKeys: number[];

  fetchTree: () => Promise<void>;
  fetchDetail: (id: number) => Promise<void>;
  create: (data: MenuFormParams) => Promise<void>;
  update: (data: MenuFormParams) => Promise<void>;
  remove: (id: number) => Promise<void>;
  setCurrentItem: (item: MenuTreeItem | null) => void;
  setExpandedKeys: (keys: number[]) => void;
}

export const useMenuStore = create<MenuState>((set) => ({
  tree: [],
  loading: false,
  currentItem: null,
  expandedKeys: [],

  fetchTree: async () => {
    set({ loading: true });
    try {
      const res = await getMenuTreeApi();
      set({ tree: res.data.data, loading: false });
    } catch {
      set({ loading: false });
    }
  },

  fetchDetail: async (id: number) => {
    const res = await getMenuDetailApi(id);
    set({ currentItem: res.data.data });
  },

  create: async (data: MenuFormParams) => {
    await createMenuApi(data);
  },

  update: async (data: MenuFormParams) => {
    await updateMenuApi(data);
  },

  remove: async (id: number) => {
    await deleteMenuApi(id);
  },

  setCurrentItem: (item: MenuTreeItem | null) => {
    set({ currentItem: item });
  },

  setExpandedKeys: (keys: number[]) => {
    set({ expandedKeys: keys });
  },
}));
