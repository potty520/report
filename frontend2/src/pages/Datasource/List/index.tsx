import React, { useEffect, useState, useCallback } from 'react';
import {
  Table, Button, Space, Tag, Modal, Form, Input, InputNumber, Select, message, Popconfirm, App, Alert,
} from 'antd';
import {
  PlusOutlined, EditOutlined, DeleteOutlined, SearchOutlined, ReloadOutlined, ApiOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { useDatasourceStore } from '@/stores/useDatasourceStore';
import type { DatasourceItem, DatasourceFormParams } from '@/services/api';
import dayjs from 'dayjs';

const DatasourceListPage: React.FC = () => {
  const { message: msg } = App.useApp();
  const {
    list, total, loading, fetchList, create, update, remove, testConnection, testResult, clearTestResult, setCurrentItem,
  } = useDatasourceStore();

  const [searchForm] = Form.useForm();
  const [editForm] = Form.useForm();
  const [modalOpen, setModalOpen] = useState(false);
  const [modalTitle, setModalTitle] = useState('新增数据源');
  const [editingId, setEditingId] = useState<number | null>(null);
  const [searchParams, setSearchParams] = useState<Record<string, any>>({});
  const [testing, setTesting] = useState(false);

  const doFetch = useCallback((params: Record<string, any> = {}) => {
    fetchList({ current: 1, size: 10, ...params });
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

  const handleAdd = () => {
    setEditingId(null);
    setModalTitle('新增数据源');
    editForm.resetFields();
    editForm.setFieldsValue({ type: 'mysql', port: 3306, status: 1 });
    clearTestResult();
    setModalOpen(true);
  };

  const handleEdit = (record: DatasourceItem) => {
    setEditingId(record.id);
    setModalTitle('编辑数据源');
    editForm.setFieldsValue({
      name: record.name,
      type: record.type,
      host: record.host,
      port: record.port,
      databaseName: record.databaseName,
      username: record.username,
      status: record.status,
      remark: record.remark,
    });
    clearTestResult();
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

  const handleModalOk = async () => {
    try {
      const values = await editForm.validateFields();
      const data: DatasourceFormParams = { ...values };
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

  const handleTestConnection = async () => {
    try {
      const values = await editForm.validateFields();
      setTesting(true);
      await testConnection(values);
    } catch (error: any) {
      if (error.errorFields) return;
      msg.error('验证表单失败');
    } finally {
      setTesting(false);
    }
  };

  const typeMap: Record<string, { color: string; text: string }> = {
    mysql: { color: 'blue', text: 'MySQL' },
    postgresql: { color: 'cyan', text: 'PostgreSQL' },
    sqlserver: { color: 'purple', text: 'SQL Server' },
  };

  const columns: ColumnsType<DatasourceItem> = [
    { title: 'ID', dataIndex: 'id', width: 60, align: 'center' },
    { title: '名称', dataIndex: 'dsName', width: 160 },
    {
      title: '类型', dataIndex: 'dsType', width: 120,
      render: (v: string) => <Tag color={typeMap[v]?.color}>{typeMap[v]?.text || v}</Tag>,
    },
    { title: '主机', dataIndex: 'ip', width: 150 },
    { title: '端口', dataIndex: 'port', width: 80, align: 'center' },
    { title: '数据库', dataIndex: 'dbName', width: 140 },
    { title: '用户名', dataIndex: 'username', width: 120 },
    {
      title: '状态', dataIndex: 'status', width: 80, align: 'center',
      render: (v: number) => <Tag color={v === 1 ? 'green' : 'red'}>{v === 1 ? '启用' : '禁用'}</Tag>,
    },
    { title: '备注', dataIndex: 'description', ellipsis: true, width: 150, render: (v: string) => v || '-' },
    {
      title: '创建时间', dataIndex: 'createTime', width: 170,
      render: (v: string) => v ? dayjs(v).format('YYYY-MM-DD HH:mm:ss') : '-',
    },
    {
      title: '操作', key: 'action', width: 200, fixed: 'right',
      render: (_: any, record: DatasourceItem) => (
        <Space>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => handleEdit(record)}>编辑</Button>
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
          <Form.Item name="name" label="名称">
            <Input placeholder="请输入" allowClear />
          </Form.Item>
          <Form.Item name="type" label="类型">
            <Select placeholder="请选择" allowClear style={{ width: 130 }}>
              <Select.Option value="mysql">MySQL</Select.Option>
              <Select.Option value="postgresql">PostgreSQL</Select.Option>
              <Select.Option value="sqlserver">SQL Server</Select.Option>
            </Select>
          </Form.Item>
          <Form.Item name="host" label="主机">
            <Input placeholder="请输入" allowClear />
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
        <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>新增数据源</Button>
      </div>
      <Table
        columns={columns}
        dataSource={list}
        rowKey="id"
        loading={loading}
        scroll={{ x: 1300 }}
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
        onCancel={() => { setModalOpen(false); clearTestResult(); }}
        destroyOnClose
        width={560}
      >
        <Form form={editForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item name="name" label="数据源名称" rules={[{ required: true, message: '请输入数据源名称' }]}>
            <Input placeholder="请输入数据源名称" />
          </Form.Item>
          <Form.Item name="type" label="数据库类型" rules={[{ required: true, message: '请选择数据库类型' }]}>
            <Select>
              <Select.Option value="mysql">MySQL</Select.Option>
              <Select.Option value="postgresql">PostgreSQL</Select.Option>
              <Select.Option value="sqlserver">SQL Server</Select.Option>
            </Select>
          </Form.Item>
          <Space style={{ width: '100%' }} size={16}>
            <Form.Item name="host" label="主机地址" rules={[{ required: true, message: '请输入主机地址' }]} style={{ flex: 1 }}>
              <Input placeholder="如: localhost" />
            </Form.Item>
            <Form.Item name="port" label="端口" rules={[{ required: true, message: '请输入端口' }]} style={{ width: 130 }}>
              <InputNumber placeholder="3306" style={{ width: '100%' }} min={1} max={65535} />
            </Form.Item>
          </Space>
          <Form.Item name="databaseName" label="数据库名" rules={[{ required: true, message: '请输入数据库名' }]}>
            <Input placeholder="请输入数据库名" />
          </Form.Item>
          <Form.Item name="username" label="用户名" rules={[{ required: true, message: '请输入用户名' }]}>
            <Input placeholder="请输入用户名" />
          </Form.Item>
          <Form.Item name="password" label="密码">
            <Input.Password placeholder={editingId ? '留空则不修改' : '请输入密码'} />
          </Form.Item>
          <Form.Item name="status" label="状态">
            <Select>
              <Select.Option value={1}>启用</Select.Option>
              <Select.Option value={0}>禁用</Select.Option>
            </Select>
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={3} placeholder="备注信息" />
          </Form.Item>
          <Form.Item>
            <Button icon={<ApiOutlined />} onClick={handleTestConnection} loading={testing}>
              测试连接
            </Button>
          </Form.Item>
        </Form>
        {testResult && (
          <Alert
            type={testResult.success ? 'success' : 'error'}
            message={testResult.success ? '连接成功' : '连接失败'}
            description={testResult.message}
            closable
            onClose={clearTestResult}
            style={{ marginTop: 8 }}
          />
        )}
      </Modal>
    </div>
  );
};

export default DatasourceListPage;
