import React, { useEffect, useState, useCallback } from 'react';
import {
  Table, Button, Space, Tag, Modal, Form, Input, InputNumber, Select, DatePicker, message, Popconfirm, App,
} from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, SearchOutlined, ReloadOutlined, SendOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { useTaskStore } from '@/stores/useTaskStore';
import { getTemplateListApi } from '@/services/api';
import type { TaskItem, TaskFormParams, TemplateItem } from '@/services/api';
import dayjs from 'dayjs';

const TaskFormulationPage: React.FC = () => {
  const { message: msg } = App.useApp();
  const { list, total, loading, fetchList, create, update, remove, publish, close } = useTaskStore();

  const [searchForm] = Form.useForm();
  const [editForm] = Form.useForm();
  const [modalOpen, setModalOpen] = useState(false);
  const [modalTitle, setModalTitle] = useState('新增任务');
  const [editingId, setEditingId] = useState<number | null>(null);
  const [searchParams, setSearchParams] = useState<Record<string, any>>({});
  const [templates, setTemplates] = useState<TemplateItem[]>([]);

  const doFetch = useCallback((params: Record<string, any> = {}) => {
    fetchList({ current: 1, size: 10, ...params });
  }, [fetchList]);

  const loadTemplates = async () => {
    try {
      const res = await getTemplateListApi({ current: 1, size: 1000 });
      setTemplates(res.data.data.records || []);
    } catch { /* ignore */ }
  };

  useEffect(() => {
    doFetch();
    loadTemplates();
  }, [doFetch]);

  const handleSearch = () => {
    const values = searchForm.getFieldsValue();
    const params: Record<string, any> = {};
    if (values.name) params.name = values.name;
    if (values.status !== undefined && values.status !== null && values.status !== '') params.status = values.status;
    setSearchParams(params);
    doFetch(params);
  };

  const handleReset = () => {
    searchForm.resetFields();
    setSearchParams({});
    doFetch();
  };

  const handleAdd = () => {
    setEditingId(null);
    setModalTitle('新增任务');
    editForm.resetFields();
    editForm.setFieldsValue({ status: 0, year: dayjs().year() });
    setModalOpen(true);
  };

  const handleEdit = (record: TaskItem) => {
    setEditingId(record.id);
    setModalTitle('编辑任务');
    editForm.setFieldsValue({
      name: record.name,
      templateId: record.templateId,
      year: record.year,
      period: record.period,
      startDate: record.startDate ? dayjs(record.startDate) : null,
      endDate: record.endDate ? dayjs(record.endDate) : null,
      status: record.status,
      description: record.description,
    });
    setModalOpen(true);
  };

  const handleDelete = async (id: number) => {
    try {
      await remove(id);
      msg.success('删除成功');
      doFetch(searchParams);
    } catch {
      msg.error('删除失败');
    }
  };

  const handlePublish = async (id: number) => {
    try {
      await publish(id);
      msg.success('发布成功');
      doFetch(searchParams);
    } catch {
      msg.error('发布失败');
    }
  };

  const handleClose = async (id: number) => {
    try {
      await close(id);
      msg.success('关闭成功');
      doFetch(searchParams);
    } catch {
      msg.error('关闭失败');
    }
  };

  const handleModalOk = async () => {
    try {
      const values = await editForm.validateFields();
      const data: TaskFormParams = {
        ...values,
        startDate: values.startDate ? values.startDate.format('YYYY-MM-DD') : '',
        endDate: values.endDate ? values.endDate.format('YYYY-MM-DD') : '',
      };
      if (editingId) {
        data.id = editingId;
        await update(data);
        msg.success('更新成功');
      } else {
        await create(data);
        msg.success('创建成功');
      }
      setModalOpen(false);
      doFetch(searchParams);
    } catch (error: any) {
      if (error.errorFields) return;
      msg.error(error?.message || '操作失败');
    }
  };

  const statusMap: Record<number, { color: string; text: string }> = {
    0: { color: 'default', text: '草稿' },
    1: { color: 'green', text: '已发布' },
    2: { color: 'red', text: '已关闭' },
  };

  const columns: ColumnsType<TaskItem> = [
    { title: 'ID', dataIndex: 'id', width: 60, align: 'center' },
    { title: '任务名称', dataIndex: 'name', width: 200 },
    { title: '模板', dataIndex: 'templateName', width: 150 },
    { title: '年度', dataIndex: 'year', width: 80, align: 'center' },
    { title: '期数', dataIndex: 'period', width: 100, render: (v: string) => v || '-' },
    {
      title: '开始日期', dataIndex: 'startDate', width: 120,
      render: (v: string) => v || '-',
    },
    {
      title: '结束日期', dataIndex: 'endDate', width: 120,
      render: (v: string) => v || '-',
    },
    {
      title: '状态', dataIndex: 'status', width: 80, align: 'center',
      render: (v: number) => <Tag color={statusMap[v]?.color}>{statusMap[v]?.text}</Tag>,
    },
    { title: '描述', dataIndex: 'description', ellipsis: true, width: 150, render: (v: string) => v || '-' },
    {
      title: '创建时间', dataIndex: 'createTime', width: 170,
      render: (v: string) => v ? dayjs(v).format('YYYY-MM-DD HH:mm:ss') : '-',
    },
    {
      title: '操作', key: 'action', width: 280, fixed: 'right',
      render: (_: any, record: TaskItem) => (
        <Space>
          {record.status === 0 && (
            <>
              <Button type="link" size="small" icon={<EditOutlined />} onClick={() => handleEdit(record)}>编辑</Button>
              <Button type="link" size="small" icon={<SendOutlined />} style={{ color: '#52c41a' }} onClick={() => handlePublish(record.id)}>发布</Button>
            </>
          )}
          {record.status === 1 && (
            <Button type="link" size="small" danger onClick={() => handleClose(record.id)}>关闭</Button>
          )}
          <Popconfirm title="确定删除?" onConfirm={() => handleDelete(record.id)} okText="确定" cancelText="取消">
            <Button type="link" size="small" danger icon={<DeleteOutlined />}>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div style={{ padding: 24, background: '#fff', borderRadius: 8 }}>
      <div className="search-form" style={{ marginBottom: 16 }}>
        <Form form={searchForm} layout="inline">
          <Form.Item name="name" label="任务名称">
            <Input placeholder="请输入" allowClear />
          </Form.Item>
          <Form.Item name="status" label="状态">
            <Select placeholder="请选择" allowClear style={{ width: 120 }}>
              <Select.Option value={0}>草稿</Select.Option>
              <Select.Option value={1}>已发布</Select.Option>
              <Select.Option value={2}>已关闭</Select.Option>
            </Select>
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" icon={<SearchOutlined />} onClick={handleSearch}>搜索</Button>
              <Button icon={<ReloadOutlined />} onClick={handleReset}>重置</Button>
            </Space>
          </Form.Item>
        </Form>
      </div>
      <div className="table-operations">
        <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>新增任务</Button>
      </div>
      <Table
        columns={columns}
        dataSource={list}
        rowKey="id"
        loading={loading}
        scroll={{ x: 1400 }}
        onChange={(pagination) => doFetch({ current: pagination.current, size: pagination.pageSize, ...searchParams })}
        pagination={{
          total,
          showSizeChanger: true,
          showTotal: (t) => `共 ${t} 条`,
        }}
      />

      <Modal
        title={modalTitle}
        open={modalOpen}
        onOk={handleModalOk}
        onCancel={() => setModalOpen(false)}
        destroyOnClose
        width={600}
      >
        <Form form={editForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item name="name" label="任务名称" rules={[{ required: true, message: '请输入任务名称' }]}>
            <Input placeholder="请输入任务名称" />
          </Form.Item>
          <Form.Item name="templateId" label="关联模板" rules={[{ required: true, message: '请选择模板' }]}>
            <Select placeholder="请选择模板" showSearch optionFilterProp="children">
              {templates.map((t) => (
                <Select.Option key={t.id} value={t.id}>{t.name}</Select.Option>
              ))}
            </Select>
          </Form.Item>
          <Space style={{ width: '100%' }} size={16}>
            <Form.Item name="year" label="年度" rules={[{ required: true, message: '请输入年度' }]}>
              <InputNumber placeholder="2024" style={{ width: '100%' }} min={2020} max={2099} />
            </Form.Item>
            <Form.Item name="period" label="期数">
              <Input placeholder="如: Q1" style={{ width: 140 }} />
            </Form.Item>
          </Space>
          <Space style={{ width: '100%' }} size={16}>
            <Form.Item name="startDate" label="开始日期" rules={[{ required: true, message: '请选择开始日期' }]}>
              <DatePicker style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="endDate" label="结束日期" rules={[{ required: true, message: '请选择结束日期' }]}>
              <DatePicker style={{ width: '100%' }} />
            </Form.Item>
          </Space>
          <Form.Item name="status" label="状态">
            <Select>
              <Select.Option value={0}>草稿</Select.Option>
              <Select.Option value={1}>已发布</Select.Option>
            </Select>
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={3} placeholder="任务描述" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default TaskFormulationPage;
