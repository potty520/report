import axios, { AxiosResponse, InternalAxiosRequestConfig } from 'axios';

const request = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
});

// ---------------------------------------------------------------------------
// 全局在途请求登记：页面切换 / 刷新时统一 abort，避免请求被硬切断。
//
// 背景：顶栏消息角标每 60s 轮询 /todo/summary，若用户此刻正在切换路由，
// 浏览器会在响应传输中途断开连接。某些浏览器/中间件组合下这会以
// `net::ERR_INVALID_CHUNKED_ENCODING` 的形式冒到控制台，看起来像服务端故障，
// 实际是「客户端主动弃用响应」。这里在 fetch 层做一次显式 abort，
// 让请求以标准取消语义（AbortError）结束，不再产生该误报。
// ---------------------------------------------------------------------------
const inflight = new Set<AbortController>();

/** 取消所有在途请求（路由切换、卸载时调用） */
export function abortAllRequests(): void {
  inflight.forEach((c) => {
    try {
      c.abort();
    } catch {
      /* 已结束，忽略 */
    }
  });
  inflight.clear();
}

// 浏览器前进/后退（bfcache 恢复）与页面卸载时统一清理在途请求
if (typeof window !== 'undefined') {
  window.addEventListener('pagehide', abortAllRequests);
}

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
    // 登记 AbortController，便于页面切换时统一取消
    const controller = new AbortController();
    config.signal = controller.signal;
    inflight.add(controller);
    (config as any).__abortController = controller;
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// 请求结束后从登记表移除
function releaseController(config?: InternalAxiosRequestConfig): void {
  const controller = (config as any)?.__abortController as AbortController | undefined;
  if (controller) inflight.delete(controller);
}

/** 判断是否为「主动取消」引发的错误（路由切换等），用于静默处理 */
export function isCanceled(error: any): boolean {
  return (
    axios.isCancel?.(error) === true ||
    error?.code === 'ERR_CANCELED' ||
    error?.name === 'CanceledError' ||
    error?.name === 'AbortError'
  );
}

// Response interceptor - handle errors
request.interceptors.response.use(
  (response: AxiosResponse) => {
    releaseController(response.config as InternalAxiosRequestConfig);
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
    releaseController(error?.config);
    // 主动取消（页面切换/卸载）不算错误，静默返回，避免污染控制台与全局提示
    if (isCanceled(error)) {
      return Promise.reject(error);
    }
    if (error.response?.status === 401) {
      localStorage.clear();
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

export default request;
