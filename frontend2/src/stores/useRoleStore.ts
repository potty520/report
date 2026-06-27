import { create } from 'zustand';
import {
  getRoleListApi, getAllRolesApi, getRoleDetailApi, createRoleApi, updateRoleApi,
  deleteRoleApi, getRoleMenuIdsApi,
  PageParams, RoleItem, RoleFormParams,
} from '@/services/api';

interface RoleState {
  list: RoleItem[];
  allRoles: RoleItem[];
  total: number;
  loading: boolean;
  currentItem: RoleItem | null;
  roleMenuIds: number[];

  fetchList: (params: PageParams) => Promise<void>;
  fetchAllRoles: () => Promise<void>;
  fetchDetail: (id: number) => Promise<void>;
  fetchRoleMenuIds: (roleId: number) => Promise<void>;
  create: (data: RoleFormParams) => Promise<void>;
  update: (data: RoleFormParams) => Promise<void>;
  remove: (id: number) => Promise<void>;
  setCurrentItem: (item: RoleItem | null) => void;
}

export const useRoleStore = create<RoleState>((set) => ({
  list: [],
  allRoles: [],
  total: 0,
  loading: false,
  currentItem: null,
  roleMenuIds: [],

  fetchList: async (params: PageParams) => {
    set({ loading: true });
    try {
      const res = await getRoleListApi(params);
      const { list: records, total } = res.data.data;
      set({ list: records, total, loading: false });
    } catch {
      set({ loading: false });
    }
  },

  fetchAllRoles: async () => {
    const res = await getAllRolesApi();
    set({ allRoles: res.data.data });
  },

  fetchDetail: async (id: number) => {
    const res = await getRoleDetailApi(id);
    set({ currentItem: res.data.data });
  },

  fetchRoleMenuIds: async (roleId: number) => {
    const res = await getRoleMenuIdsApi(roleId);
    set({ roleMenuIds: res.data.data });
  },

  create: async (data: RoleFormParams) => {
    await createRoleApi(data);
  },

  update: async (data: RoleFormParams) => {
    await updateRoleApi(data);
  },

  remove: async (id: number) => {
    await deleteRoleApi(id);
  },

  setCurrentItem: (item: RoleItem | null) => {
    set({ currentItem: item, roleMenuIds: [] });
  },
}));
