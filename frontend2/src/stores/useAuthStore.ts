import { create } from 'zustand';
import { loginApi, logoutApi, getCurrentUserApi, getCaptchaApi, LoginParams, UserInfo, MenuItem, CaptchaResult } from '@/services/api';
import { clearAuth, getToken } from '@/utils/auth';

interface AuthState {
  token: string | null;
  userInfo: UserInfo | null;
  menus: MenuItem[];
  permissions: string[];
  captcha: CaptchaResult | null;
  loading: boolean;

  // Actions
  login: (params: LoginParams) => Promise<void>;
  logout: () => Promise<void>;
  fetchCaptcha: () => Promise<void>;
  fetchCurrentUser: () => Promise<void>;
  setUserInfo: (info: UserInfo) => void;
  initialize: () => void;
}

export const useAuthStore = create<AuthState>((set, get) => ({
  token: getToken(),
  userInfo: null,
  menus: [],
  permissions: [],
  captcha: null,
  loading: false,

  login: async (params: LoginParams) => {
    set({ loading: true });
    try {
      const res = await loginApi(params);
      const { token, userInfo, permissions, menus } = res.data.data;
      localStorage.setItem('token', token);
      localStorage.setItem('userInfo', JSON.stringify(userInfo));
      localStorage.setItem('permissions', JSON.stringify(permissions));
      localStorage.setItem('menus', JSON.stringify(menus));
      set({ token, userInfo, permissions, menus, loading: false });
    } catch (error) {
      set({ loading: false });
      throw error;
    }
  },

  logout: async () => {
    try {
      await logoutApi();
    } finally {
      clearAuth();
      set({ token: null, userInfo: null, menus: [], permissions: [], captcha: null });
    }
  },

  fetchCaptcha: async () => {
    const res = await getCaptchaApi();
    set({ captcha: res.data.data });
  },

  fetchCurrentUser: async () => {
    try {
      const res = await getCurrentUserApi();
      set({ userInfo: res.data.data });
      localStorage.setItem('userInfo', JSON.stringify(res.data.data));
    } catch {
      clearAuth();
      set({ token: null, userInfo: null, menus: [], permissions: [] });
    }
  },

  setUserInfo: (info: UserInfo) => {
    set({ userInfo: info });
    localStorage.setItem('userInfo', JSON.stringify(info));
  },

  initialize: () => {
    const token = getToken();
    if (token) {
      const stored = localStorage.getItem('userInfo');
      const perms = localStorage.getItem('permissions');
      const menus = localStorage.getItem('menus');
      set({
        token,
        userInfo: stored ? JSON.parse(stored) : null,
        permissions: perms ? JSON.parse(perms) : [],
        menus: menus ? JSON.parse(menus) : [],
      });
    }
  },
}));
