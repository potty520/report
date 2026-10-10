import React, { useEffect, useState, useCallback } from 'react';
import TemplateAuthModal from '@/components/TemplateAuthModal';
import {
  Table, Button, Space, Tag, Form, Input, Select, Switch, message, Popconfirm, App,
} from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, SearchOutlined, ReloadOutlined, EyeOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { useNavigate } from 'react-router-dom';
import { useTemplateStore } from '@/stores/useTemplateStore';
import type { TemplateItem } from '@/services/api';
import dayjs from 'dayjs';

const TemplateListPage: React.FC = () => {
  const { message: msg } = App.useApp();
  const navigate = useNavigate();
  const { list, total, loading, fetchList, remove, toggleStatus } = useTemplateStore();
  const [searchForm] = Form.useForm();
  const [searchParams, setSearchParams] = useState<Record<string, any>>({});
  const [authModal, setAuthModal] = useState<{ open: boolean; id?: number; name?: string }>({ open: false });

  const doFetch = useCallback((params: Record<string, any> = {}) => {
    // 后端按 templateName/templateCode 过滤；这里把旧前端的 name/code 一并映射过去
    const mapped: Record<string, any> = { ...params };
    if (params.name) mapped.templateName = params.name;
    if (params.code) mapped.templateCode = params.code;
    fetchList({ current: 1, size: 10, ...mapped });
  }, [fetchList]);

  useEffect(() => {
    doFetch();
  }, [doFetch]);

  const handleSearch = () => {
    const values = searchForm.getFieldsValue();
    setSearchParams(values);
    doFetch(values);
  };

  const handleReset = () => {
    searchForm.resetFields();
    setSearchParams({});
    doFetch();
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

  const handleStatusToggle = async (id: number, checked: boolean) => {
    try {
      await toggleStatus(id, checked ? 1 : 0);
      msg.success('状态更新成功');
      doFetch(searchParams);
    } catch {
      msg.error('操作失败');
    }
  };

  const statusMap: Record<number, { color: string; text: string }> = {
    0: { color: 'default', text: '草稿' },
    1: { color: 'green', text: '已发布' },
    2: { color: 'red', text: '已禁用' },
  };

  const columns: ColumnsType<TemplateItem> = [
    { title: 'ID', dataIndex: 'id', width: 60, align: 'center' },
    { title: '模板名称', dataIndex: 'name', width: 200 },
    { title: '模板编码', dataIndex: 'code', width: 150 },
    { title: '物化', dataIndex: 'materialized', width: 90, align: 'center', render: (v: number) => (v === 1 ? <Tag color="green">已物化</Tag> : <Tag>未物化</Tag>) },
    { title: '描述', dataIndex: 'description', ellipsis: true, width: 200, render: (v: string) => v || '-' },
    {
      title: '状态', dataIndex: 'status', width: 80, align: 'center',
      render: (v: number) => <Tag color={statusMap[v]?.color}>{statusMap[v]?.text}</Tag>,
    },
    { title: '数据源', dataIndex: 'datasourceName', width: 120, render: (v: string) => v || '-' },
    {
      title: '创建时间', dataIndex: 'createTime', width: 170,
      render: (v: string) => v ? dayjs(v).format('YYYY-MM-DD HH:mm:ss') : '-',
    },
    {
      title: '更新时间', dataIndex: 'updateTime', width: 170,
      render: (v: string) => v ? dayjs(v).format('YYYY-MM-DD HH:mm:ss') : '-',
    },
    {
      title: '操作', key: 'action', width: 240, fixed: 'right',
      render: (_: any, record: TemplateItem) => (
        <Space>
          <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => navigate(`/template/detail/${record.id}`)}>详情</Button>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => navigate(`/template/detail/${record.id}`)}>编辑</Button>
          <Button type="link" size="small" onClick={() => setAuthModal({ open: true, id: record.id, name: record.name })}>授权</Button>
          {record.status !== 1 && (
            <Popconfirm title="发布后即可用于建任务，确定发布?" onConfirm={() => handleStatusToggle(record.id, true)} okText="发布" cancelText="取消">
              <Button type="link" size="small">发布</Button>
            </Popconfirm>
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
          <Form.Item name="name" label="模板名称">
            <Input placeholder="请输入" allowClear />
          </Form.Item>
          <Form.Item name="code" label="模板编码">
            <Input placeholder="请输入" allowClear />
          </Form.Item>
          <Form.Item name="status" label="状态">
            <Select placeholder="请选择" allowClear style={{ width: 120 }}>
              <Select.Option value={0}>草稿</Select.Option>
              <Select.Option value={1}>已发布</Select.Option>
              <Select.Option value={2}>已禁用</Select.Option>
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
        <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/template/wizard')}>向导新建</Button>
        <Button style={{ marginLeft: 8 }} onClick={() => navigate('/template/add')}>基础新建</Button>
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

      <TemplateAuthModal
        open={authModal.open}
        templateId={authModal.id}
        templateName={authModal.name}
        onClose={() => setAuthModal({ open: false })}
      />
    </div>
  );
};

export default TemplateListPage;
