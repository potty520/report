import { create } from 'zustand';
import {
  getDataApplyListApi, getDataApplyDetailApi, createDataApplyApi,
  updateDataApplyApi, deleteDataApplyApi,
  getApproveListApi, submitApprovalApi,
  PageParams, DataApplyItem, DataApplyFormParams, ApproveFormParams,
} from '@/services/api';

interface DataApplyState {
  applyList: DataApplyItem[];
  applyTotal: number;
  applyLoading: boolean;
  currentApply: DataApplyItem | null;

  approveList: DataApplyItem[];
  approveTotal: number;
  approveLoading: boolean;

  fetchApplyList: (params: PageParams) => Promise<void>;
  fetchApplyDetail: (id: number) => Promise<void>;
  createApply: (data: DataApplyFormParams) => Promise<void>;
  updateApply: (id: number, data: DataApplyFormParams) => Promise<void>;
  removeApply: (id: number) => Promise<void>;

  fetchApproveList: (params: PageParams) => Promise<void>;
  submitApproval: (data: ApproveFormParams) => Promise<void>;
  setCurrentApply: (item: DataApplyItem | null) => void;
}

export const useDataApplyStore = create<DataApplyState>((set) => ({
  applyList: [],
  applyTotal: 0,
  applyLoading: false,
  currentApply: null,

  approveList: [],
  approveTotal: 0,
  approveLoading: false,

  fetchApplyList: async (params: PageParams) => {
    set({ applyLoading: true });
    try {
      const res = await getDataApplyListApi(params);
      const { list: records, total } = res.data.data;
      set({ applyList: records, applyTotal: total, applyLoading: false });
    } catch {
      set({ applyLoading: false });
    }
  },

  fetchApplyDetail: async (id: number) => {
    const res = await getDataApplyDetailApi(id);
    set({ currentApply: res.data.data });
  },

  createApply: async (data: DataApplyFormParams) => {
    await createDataApplyApi(data);
  },

  updateApply: async (id: number, data: DataApplyFormParams) => {
    await updateDataApplyApi(id, data);
  },

  removeApply: async (id: number) => {
    await deleteDataApplyApi(id);
  },

  fetchApproveList: async (params: PageParams) => {
    set({ approveLoading: true });
    try {
      const res = await getApproveListApi(params);
      const { list: records, total } = res.data.data;
      set({ approveList: records, approveTotal: total, approveLoading: false });
    } catch {
      set({ approveLoading: false });
    }
  },

  submitApproval: async (data: ApproveFormParams) => {
    await submitApprovalApi(data);
  },

  setCurrentApply: (item: DataApplyItem | null) => {
    set({ currentApply: item });
  },
}));
