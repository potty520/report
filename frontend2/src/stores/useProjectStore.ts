import { create } from 'zustand';
import {
  getProjectTreeApi, getProjectDetailApi, createProjectApi, updateProjectApi, deleteProjectApi,
  ProjectItem, ProjectFormParams, DeptItem,
} from '@/services/api';

interface ProjectState {
  tree: DeptItem[];
  loading: boolean;
  currentItem: ProjectItem | null;

  fetchTree: () => Promise<void>;
  fetchDetail: (id: number) => Promise<void>;
  create: (data: ProjectFormParams) => Promise<void>;
  update: (data: ProjectFormParams) => Promise<void>;
  remove: (id: number) => Promise<void>;
  setCurrentItem: (item: ProjectItem | null) => void;
}

export const useProjectStore = create<ProjectState>((set) => ({
  tree: [],
  loading: false,
  currentItem: null,

  fetchTree: async () => {
    set({ loading: true });
    try {
      const res = await getProjectTreeApi();
      set({ tree: res.data.data, loading: false });
    } catch {
      set({ loading: false });
    }
  },

  fetchDetail: async (id: number) => {
    const res = await getProjectDetailApi(id);
    set({ currentItem: res.data.data });
  },

  create: async (data: ProjectFormParams) => {
    await createProjectApi(data);
  },

  update: async (data: ProjectFormParams) => {
    await updateProjectApi(data);
  },

  remove: async (id: number) => {
    await deleteProjectApi(id);
  },

  setCurrentItem: (item: ProjectItem | null) => {
    set({ currentItem: item });
  },
}));
