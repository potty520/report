import { create } from 'zustand';
import { getFileListApi, deleteFileApi, FileItem } from '@/services/api';

interface FileState {
  files: FileItem[];
  loading: boolean;
  taskId?: number;
  fetchFiles: (taskId: number) => Promise<void>;
  removeFile: (id: number) => Promise<void>;
  clear: () => void;
}

export const useFileStore = create<FileState>((set, get) => ({
  files: [],
  loading: false,
  taskId: undefined,

  fetchFiles: async (taskId) => {
    set({ loading: true, taskId });
    try {
      const res = await getFileListApi(taskId);
      set({ files: res.data.data || [], loading: false });
    } catch {
      set({ files: [], loading: false });
    }
  },

  removeFile: async (id) => {
    await deleteFileApi(id);
    const taskId = get().taskId;
    if (taskId) await get().fetchFiles(taskId);
  },

  clear: () => set({ files: [], taskId: undefined }),
}));
