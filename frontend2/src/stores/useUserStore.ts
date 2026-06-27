import { create } from 'zustand';
import {
  getUserListApi, getUserDetailApi, createUserApi, updateUserApi,
  deleteUserApi, updateUserStatusApi, assignUserRolesApi,
  PageParams, UserItem, UserFormParams,
} from '@/services/api';

interface UserState {
  list: UserItem[];
  total: number;
  current: number;
  size: number;
  loading: boolean;
  currentItem: UserItem | null;

  fetchList: (params: PageParams) => Promise<void>;
  fetchDetail: (id: number) => Promise<void>;
  create: (data: UserFormParams) => Promise<void>;
  update: (data: UserFormParams) => Promise<void>;
  remove: (id: number) => Promise<void>;
  toggleStatus: (id: number, status: number) => Promise<void>;
  assignRoles: (userId: number, roleIds: number[]) => Promise<void>;
  setCurrentItem: (item: UserItem | null) => void;
}

export const useUserStore = create<UserState>((set) => ({
  list: [],
  total: 0,
  current: 1,
  size: 10,
  loading: false,
  currentItem: null,

  fetchList: async (params: PageParams) => {
    set({ loading: true });
    try {
      const res = await getUserListApi(params);
      const { list: records, total, pageNum: current, pageSize: size } = res.data.data;
      set({ list: records, total, current, size, loading: false });
    } catch {
      set({ loading: false });
    }
  },

  fetchDetail: async (id: number) => {
    const res = await getUserDetailApi(id);
    set({ currentItem: res.data.data });
  },

  create: async (data: UserFormParams) => {
    await createUserApi(data);
  },

  update: async (data: UserFormParams) => {
    await updateUserApi(data);
  },

  remove: async (id: number) => {
    await deleteUserApi(id);
  },

  toggleStatus: async (id: number, status: number) => {
    await updateUserStatusApi(id, status);
  },

  assignRoles: async (userId: number, roleIds: number[]) => {
    await assignUserRolesApi(userId, roleIds);
  },

  setCurrentItem: (item: UserItem | null) => {
    set({ currentItem: item });
  },
}));
