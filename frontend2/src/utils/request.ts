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
