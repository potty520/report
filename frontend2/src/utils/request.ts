import axios, { AxiosResponse, InternalAxiosRequestConfig } from 'axios';

const request = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Request interceptor - attach token
request.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = localStorage.getItem('token');
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    // 分页参数名对齐后端：后端统一使用 page / pageSize，
    // 前端各页面历史上传的是 current / size，若直传后端会被忽略（翻页与每页条数失效）。
    if (config.params && typeof config.params === 'object') {
      const p: Record<string, any> = { ...config.params };
      if (p.current !== undefined && p.page === undefined) {
        p.page = p.current;
        delete p.current;
      }
      if (p.size !== undefined && p.pageSize === undefined) {
        p.pageSize = p.size;
        delete p.size;
      }
      config.params = p;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// Response interceptor - handle errors
request.interceptors.response.use(
  (response: AxiosResponse) => {
    const res = response.data;
    // Business error
    if (res.code !== 0 && res.code !== 200) {
      // Token expired / not logged in
      if (res.code === 401 || res.code === 403) {
        localStorage.clear();
        window.location.href = '/login';
      }
      return Promise.reject(new Error(res.msg || 'Request failed'));
    }
    return response;
  },
  (error) => {
    if (error.response?.status === 401) {
      localStorage.clear();
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

export default request;
