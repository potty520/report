import { create } from 'zustand';
import {
  getTaskListApi, getTaskDetailApi, createTaskApi, updateTaskApi, deleteTaskApi,
  publishTaskApi, closeTaskApi,
  getTaskAssignListApi, assignTaskApi, cancelAssignApi,
  getTaskLedgerListApi,
  PageParams, TaskItem, TaskFormParams, TaskAssignItem, TaskAssignFormParams, TaskLedgerItem,
} from '@/services/api';

interface TaskState {
  list: TaskItem[];
  total: number;
  loading: boolean;
  currentItem: TaskItem | null;

  assignList: TaskAssignItem[];
  assignTotal: number;
  assignLoading: boolean;

  ledgerList: TaskLedgerItem[];
  ledgerTotal: number;
  ledgerLoading: boolean;

  fetchList: (params: PageParams) => Promise<void>;
  fetchDetail: (id: number) => Promise<void>;
  create: (data: TaskFormParams) => Promise<void>;
  update: (data: TaskFormParams) => Promise<void>;
  remove: (id: number) => Promise<void>;
  publish: (id: number) => Promise<void>;
  close: (id: number) => Promise<void>;
  setCurrentItem: (item: TaskItem | null) => void;

  fetchAssignList: (params: PageParams) => Promise<void>;
  assignTask: (data: TaskAssignFormParams) => Promise<void>;
  cancelAssign: (id: number) => Promise<void>;

  fetchLedgerList: (params: PageParams & { perspective?: 'host' | 'executor' }) => Promise<void>;
}

export const useTaskStore = create<TaskState>((set) => ({
  list: [],
  total: 0,
  loading: false,
  currentItem: null,

  assignList: [],
  assignTotal: 0,
  assignLoading: false,

  ledgerList: [],
  ledgerTotal: 0,
  ledgerLoading: false,

  fetchList: async (params: PageParams) => {
    set({ loading: true });
    try {
      const res = await getTaskListApi(params);
      const { list: records, total } = res.data.data;
      set({ list: records, total, loading: false });
    } catch {
      set({ loading: false });
    }
  },

  fetchDetail: async (id: number) => {
    const res = await getTaskDetailApi(id);
    set({ currentItem: res.data.data });
  },

  create: async (data: TaskFormParams) => {
    await createTaskApi(data);
  },

  update: async (data: TaskFormParams) => {
    await updateTaskApi(data);
  },

  remove: async (id: number) => {
    await deleteTaskApi(id);
  },

  publish: async (id: number) => {
    await publishTaskApi(id);
  },

  close: async (id: number) => {
    await closeTaskApi(id);
  },

  setCurrentItem: (item: TaskItem | null) => {
    set({ currentItem: item });
  },

  fetchAssignList: async (params: PageParams) => {
    set({ assignLoading: true });
    try {
      const res = await getTaskAssignListApi(params);
      const { list: records, total } = res.data.data;
      set({ assignList: records, assignTotal: total, assignLoading: false });
    } catch {
      set({ assignLoading: false });
    }
  },

  assignTask: async (data: TaskAssignFormParams) => {
    await assignTaskApi(data);
  },

  cancelAssign: async (id: number) => {
    await cancelAssignApi(id);
  },

  fetchLedgerList: async (params) => {
    set({ ledgerLoading: true });
    try {
      const res = await getTaskLedgerListApi(params);
      const { list: records, total } = res.data.data;
      set({ ledgerList: records, ledgerTotal: total, ledgerLoading: false });
    } catch {
      set({ ledgerLoading: false });
    }
  },
}));
