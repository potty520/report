import { create } from 'zustand';
import {
  getAuditMyTasksApi, getAuditTaskDataApi, getAuditStatisticsApi,
  deptAuditApi, taskAuditApi, getTrajectoryApi,
  startCorrectApi, closeCorrectApi, getCorrectListApi,
  PageParams, AuditDataItem, TrajectoryNode, CorrectBatch,
} from '@/services/api';

interface AuditState {
  list: AuditDataItem[];
  total: number;
  loading: boolean;
  stats: Record<string, number>;

  taskData: AuditDataItem[];
  taskDataLoading: boolean;

  trajectory: TrajectoryNode[];
  trajectoryLoading: boolean;

  correctBatches: CorrectBatch[];

  fetchList: (params: PageParams & { auditStatus?: number }) => Promise<void>;
  fetchStats: () => Promise<void>;
  fetchTaskData: (taskId: number) => Promise<void>;
  fetchTrajectory: (taskId: number) => Promise<void>;
  fetchCorrectBatches: (taskId: number) => Promise<void>;
  doDeptAudit: (data: { taskDataId: number; pass: boolean; comment?: string }) => Promise<void>;
  doTaskAudit: (data: { taskDataId: number; pass: boolean; comment?: string }) => Promise<void>;
  doStartCorrect: (data: { taskId: number; dataIds?: string; comment?: string }) => Promise<number>;
  doCloseCorrect: (data: { taskId: number; comment?: string }) => Promise<void>;
}

export const useAuditStore = create<AuditState>((set) => ({
  list: [],
  total: 0,
  loading: false,
  stats: {},

  taskData: [],
  taskDataLoading: false,

  trajectory: [],
  trajectoryLoading: false,

  correctBatches: [],

  fetchList: async (params) => {
    set({ loading: true });
    try {
      const res = await getAuditMyTasksApi(params);
      const page = res.data.data;
      set({ list: page?.list || [], total: page?.total || 0, loading: false });
    } catch {
      set({ list: [], total: 0, loading: false });
    }
  },

  fetchStats: async () => {
    try {
      const res = await getAuditStatisticsApi();
      set({ stats: res.data.data || {} });
    } catch {
      set({ stats: {} });
    }
  },

  fetchTaskData: async (taskId) => {
    set({ taskDataLoading: true });
    try {
      const res = await getAuditTaskDataApi(taskId);
      const page = res.data.data;
      set({ taskData: page?.list || [], taskDataLoading: false });
    } catch {
      set({ taskData: [], taskDataLoading: false });
    }
  },

  fetchTrajectory: async (taskId) => {
    set({ trajectoryLoading: true });
    try {
      const res = await getTrajectoryApi(taskId);
      set({ trajectory: res.data.data || [], trajectoryLoading: false });
    } catch {
      set({ trajectory: [], trajectoryLoading: false });
    }
  },

  fetchCorrectBatches: async (taskId) => {
    try {
      const res = await getCorrectListApi(taskId);
      set({ correctBatches: res.data.data || [] });
    } catch {
      set({ correctBatches: [] });
    }
  },

  doDeptAudit: async (data) => {
    await deptAuditApi(data);
  },

  doTaskAudit: async (data) => {
    await taskAuditApi(data);
  },

  doStartCorrect: async (data) => {
    const res = await startCorrectApi(data);
    return res.data.data;
  },

  doCloseCorrect: async (data) => {
    await closeCorrectApi(data);
  },
}));
