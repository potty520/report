import { create } from 'zustand';
import {
  getTodoSummaryApi, getTodoListApi, readTodoApi, readAllTodoApi,
  remindTodoApi, scanOverdueApi, TodoSummary, TodoMessage, PageParams,
} from '@/services/api';

interface TodoState {
  summary: TodoSummary;
  list: TodoMessage[];
  total: number;
  loading: boolean;
  fetchSummary: () => Promise<void>;
  fetchList: (params: PageParams & { readFlag?: number; msgType?: string }) => Promise<void>;
  markRead: (id: number) => Promise<void>;
  markAllRead: () => Promise<number>;
  remind: (taskId: number, comment?: string) => Promise<void>;
  scanOverdue: () => Promise<Record<string, any>>;
}

export const useTodoStore = create<TodoState>((set, get) => ({
  summary: {},
  list: [],
  total: 0,
  loading: false,

  fetchSummary: async () => {
    try {
      const res = await getTodoSummaryApi();
      set({ summary: res.data.data || {} });
    } catch {
      set({ summary: {} });
    }
  },

  fetchList: async (params) => {
    set({ loading: true });
    try {
      const res = await getTodoListApi(params);
      const data = res.data.data;
      set({ list: data?.list || [], total: data?.total || 0, loading: false });
    } catch {
      set({ list: [], total: 0, loading: false });
    }
  },

  markRead: async (id) => {
    await readTodoApi(id);
    await get().fetchSummary();
  },

  markAllRead: async () => {
    const res = await readAllTodoApi();
    await get().fetchSummary();
    return res.data.data?.updated || 0;
  },

  remind: async (taskId, comment) => {
    await remindTodoApi({ taskId, comment });
  },

  scanOverdue: async () => {
    const res = await scanOverdueApi();
    await get().fetchSummary();
    return res.data.data || {};
  },
}));
