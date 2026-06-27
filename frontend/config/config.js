import { defineConfig } from 'umi';
import routerConfig from './router.config';

export default defineConfig({
  nodeModulesTransform: {
    type: 'none',
  },
  routes: routerConfig,
  fastRefresh: {},
  dva: {
    hmr: true,
    immer: true,
  },
  antd: {},
  layout: false,
  locale: {
    default: 'zh-CN',
    antd: true,
  },
  history: {
    type: 'hash',
  },
  proxy: {
    '/api': {
      target: 'http://localhost:8080',
      changeOrigin: true,
      pathRewrite: { '^/api': '' },
    },
  },
  chainWebpack(config) {
    config.plugin('define').tap((args) => {
      args[0]['process.env.APP_ENV'] = JSON.stringify(process.env.APP_ENV || 'dev');
      return args;
    });
  },
  dynamicImport: {
    loading: '@/components/PageLoading',
  },
  targets: {
    ie: 11,
  },
  theme: {
    'primary-color': '#1890ff',
  },
  ignoreMomentLocale: true,
  manifest: {
    basePath: '/',
  },
});
