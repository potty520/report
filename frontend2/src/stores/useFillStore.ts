import { create } from 'zustand';
import {
  getFillTaskListApi, getFillTaskDetailApi, submitFillDataApi, saveFillDraftApi,
  getAuditListApi, getAuditDetailApi, submitAuditApi,
  PageParams, FillTaskItem, FillDetail, FillSubmitParams, AuditItem, AuditFormParams,
} from '@/services/api';

interface FillState {
  taskList: FillTaskItem[];
  taskTotal: number;
  taskLoading: boolean;
  currentFillData: FillDetail | null;

  auditList: AuditItem[];
  auditTotal: number;
  auditLoading: boolean;
  auditDetail: any;

  fetchTaskList: (params: PageParams) => Promise<void>;
  /** 按 taskId 拉取填报详情（后端 GET /fill/task/{taskId}） */
  fetchFillDetail: (taskId: number) => Promise<FillDetail | null>;
  submitFill: (data: FillSubmitParams) => Promise<void>;
  saveDraft: (data: FillSubmitParams) => Promise<void>;

  fetchAuditList: (params: PageParams & { auditType?: 'dept' | 'task' }) => Promise<void>;
  fetchAuditDetail: (id: number) => Promise<void>;
  submitAudit: (data: AuditFormParams) => Promise<void>;
  setCurrentFillData: (data: FillDetail | null) => void;
}

export const useFillStore = create<FillState>((set) => ({
  taskList: [],
  taskTotal: 0,
  taskLoading: false,
  currentFillData: null,

  auditList: [],
  auditTotal: 0,
  auditLoading: false,
  auditDetail: null,

  fetchTaskList: async (params: PageParams) => {
    set({ taskLoading: true });
    try {
      const res = await getFillTaskListApi(params);
      const { list: records, total } = res.data.data;
      set({ taskList: records, taskTotal: total, taskLoading: false });
    } catch {
      set({ taskLoading: false });
    }
  },

  fetchFillDetail: async (taskId: number) => {
    const res = await getFillTaskDetailApi(taskId);
    const detail = res.data.data ?? null;
    set({ currentFillData: detail });
    return detail;
  },

  submitFill: async (data: FillSubmitParams) => {
    await submitFillDataApi(data);
  },

  saveDraft: async (data: FillSubmitParams) => {
    await saveFillDraftApi(data);
  },

  fetchAuditList: async (params) => {
    set({ auditLoading: true });
    try {
      const res = await getAuditListApi(params);
      const { list: records, total } = res.data.data;
      set({ auditList: records, auditTotal: total, auditLoading: false });
    } catch {
      set({ auditLoading: false });
    }
  },

  fetchAuditDetail: async (id: number) => {
    const res = await getAuditDetailApi(id);
    set({ auditDetail: res.data.data });
  },

  submitAudit: async (data: AuditFormParams) => {
    await submitAuditApi(data);
  },

  setCurrentFillData: (data: FillDetail | null) => {
    set({ currentFillData: data });
  },
}));
