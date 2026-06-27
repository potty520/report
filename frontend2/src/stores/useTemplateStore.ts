import { create } from 'zustand';
import {
  getTemplateListApi, getTemplateDetailApi, createTemplateApi, updateTemplateApi,
  deleteTemplateApi, updateTemplateStatusApi, uploadTemplateExcelApi,
  submitTemplateApi, auditTemplateApi,
  PageParams, TemplateItem, TemplateFormParams,
} from '@/services/api';

interface TemplateState {
  list: TemplateItem[];
  total: number;
  loading: boolean;
  currentItem: TemplateItem | null;

  fetchList: (params: PageParams) => Promise<void>;
  fetchDetail: (id: number) => Promise<void>;
  create: (data: TemplateFormParams) => Promise<void>;
  update: (data: TemplateFormParams) => Promise<void>;
  remove: (id: number) => Promise<void>;
  toggleStatus: (id: number, status: number) => Promise<void>;
  uploadExcel: (templateId: number, formData: FormData) => Promise<void>;
  submit: (id: number) => Promise<void>;
  audit: (id: number, status: number, remark?: string) => Promise<void>;
  setCurrentItem: (item: TemplateItem | null) => void;
}

export const useTemplateStore = create<TemplateState>((set) => ({
  list: [],
  total: 0,
  loading: false,
  currentItem: null,

  fetchList: async (params: PageParams) => {
    set({ loading: true });
    try {
      const res = await getTemplateListApi(params);
      const { list: records, total } = res.data.data;
      set({ list: records, total, loading: false });
    } catch {
      set({ loading: false });
    }
  },

  fetchDetail: async (id: number) => {
    set({ loading: true });
    try {
      const res = await getTemplateDetailApi(id);
      set({ currentItem: res.data.data, loading: false });
    } catch {
      set({ loading: false });
    }
  },

  create: async (data: TemplateFormParams) => {
    await createTemplateApi(data);
  },

  update: async (data: TemplateFormParams) => {
    await updateTemplateApi(data);
  },

  remove: async (id: number) => {
    await deleteTemplateApi(id);
  },

  toggleStatus: async (id: number, status: number) => {
    await updateTemplateStatusApi(id, status);
  },

  uploadExcel: async (templateId: number, formData: FormData) => {
    await uploadTemplateExcelApi(templateId, formData);
  },

  submit: async (id: number) => {
    await submitTemplateApi(id);
  },

  audit: async (id: number, status: number, remark?: string) => {
    await auditTemplateApi(id, status, remark);
  },

  setCurrentItem: (item: TemplateItem | null) => {
    set({ currentItem: item });
  },
}));
