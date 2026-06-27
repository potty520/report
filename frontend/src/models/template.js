import { getTemplateList, addTemplate, updateTemplate, deleteTemplate, submitTemplate, auditTemplate, uploadExcel, getFieldMapping, saveFieldMapping, getTemplateDetail } from '@/services/api';
import { message } from 'antd';

export default {
  namespace: 'template',

  state: {
    list: [],
    total: 0,
    loading: false,
    current: {},
    fieldMappings: [],
  },

  effects: {
    *fetchList({ payload }, { call, put }) {
      yield put({ type: 'updateState', payload: { loading: true } });
      const response = yield call(getTemplateList, payload);
      if (response && response.code === 0) {
        yield put({
          type: 'updateState',
          payload: {
            list: response.data.records || response.data.list || [],
            total: response.data.total || 0,
          },
        });
      }
      yield put({ type: 'updateState', payload: { loading: false } });
    },

    *fetchDetail({ payload }, { call, put }) {
      const response = yield call(getTemplateDetail, payload);
      if (response && response.code === 0) {
        yield put({
          type: 'updateState',
          payload: { current: response.data },
        });
        return response.data;
      }
      return null;
    },

    *add({ payload }, { call }) {
      const response = yield call(addTemplate, payload);
      if (response && response.code === 0) {
        message.success('新增模板成功');
        return true;
      }
      return false;
    },

    *update({ payload }, { call }) {
      const response = yield call(updateTemplate, payload);
      if (response && response.code === 0) {
        message.success('更新模板成功');
        return true;
      }
      return false;
    },

    *delete({ payload }, { call }) {
      const response = yield call(deleteTemplate, payload);
      if (response && response.code === 0) {
        message.success('删除模板成功');
        return true;
      }
      return false;
    },

    *submit({ payload }, { call }) {
      const response = yield call(submitTemplate, payload);
      if (response && response.code === 0) {
        message.success('提交审核成功');
        return true;
      }
      return false;
    },

    *audit({ payload }, { call }) {
      const { id, ...data } = payload;
      const response = yield call(auditTemplate, id, data);
      if (response && response.code === 0) {
        message.success('审核完成');
        return true;
      }
      return false;
    },

    *uploadExcel({ payload }, { call }) {
      const response = yield call(uploadExcel, payload);
      if (response && response.code === 0) {
        message.success('Excel上传成功');
        return response.data;
      }
      return null;
    },

    *fetchFieldMapping({ payload }, { call, put }) {
      const response = yield call(getFieldMapping, payload);
      if (response && response.code === 0) {
        yield put({
          type: 'updateState',
          payload: { fieldMappings: response.data || [] },
        });
        return response.data;
      }
      return [];
    },

    *saveFieldMapping({ payload }, { call }) {
      const { templateId, mappings } = payload;
      const response = yield call(saveFieldMapping, templateId, mappings);
      if (response && response.code === 0) {
        message.success('字段映射保存成功');
        return true;
      }
      return false;
    },
  },

  reducers: {
    updateState(state, { payload }) {
      return { ...state, ...payload };
    },
  },
};
