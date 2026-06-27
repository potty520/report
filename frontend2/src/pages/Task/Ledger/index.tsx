import React, { useEffect, useState, useCallback } from 'react';
import {
  Table, Button, Space, Tag, Form, Input, Select, Tabs, App,
} from 'antd';
import { SearchOutlined, ReloadOutlined, EyeOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { useTaskStore } from '@/stores/useTaskStore';
import type { TaskLedgerItem } from '@/services/api';
import dayjs from 'dayjs';

const TaskLedgerPage: React.FC = () => {
  const { message: msg } = App.useApp();
  const { ledgerList, ledgerTotal, ledgerLoading, fetchLedgerList } = useTaskStore();
  const [searchForm] = Form.useForm();
  const [perspective, setPerspective] = useState<'host' | 'executor'>('host');
  const [searchParams, setSearchParams] = useState<Record<string, any>>({});

  const doFetch = useCallback((params: Record<string, any> = {}) => {
    fetchLedgerList({ current: 1, size: 10, perspective, ...params });
  }, [fetchLedgerList, perspective]);

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

  const handleTabChange = (key: string) => {
    setPerspective(key as 'host' | 'executor');
    setSearchParams({});
    searchForm.resetFields();
  };

  const fillStatusMap: Record<number, { color: string; text: string }> = {
    0: { color: 'default', text: '未填报' },
    1: { color: 'processing', text: '已填报' },
    2: { color: 'green', text: '已提交' },
  };

  const statusMap: Record<number, { color: string; text: string }> = {
    0: { color: 'default', text: '未开始' },
    1: { color: 'processing', text: '进行中' },
    2: { color: 'green', text: '已完成' },
  };

  const columns: ColumnsType<TaskLedgerItem> = [
    { title: 'ID', dataIndex: 'id', width: 60, align: 'center' },
    { title: '任务名称', dataIndex: 'taskName', width: 200 },
    { title: '模板', dataIndex: 'templateName', width: 150 },
    { title: '部门', dataIndex: 'deptName', width: 150 },
    { title: '项目', dataIndex: 'projectName', width: 120, render: (v: string) => v || '-' },
    {
      title: '任务状态', dataIndex: 'status', width: 80, align: 'center',
      render: (v: number) => <Tag color={statusMap[v]?.color}>{statusMap[v]?.text}</Tag>,
    },
    {
      title: '填报状态', dataIndex: 'fillStatus', width: 80, align: 'center',
      render: (v: number) => <Tag color={fillStatusMap[v]?.color}>{fillStatusMap[v]?.text}</Tag>,
    },
    {
      title: '分派时间', dataIndex: 'assignTime', width: 170,
      render: (v: string) => v ? dayjs(v).format('YYYY-MM-DD HH:mm:ss') : '-',
    },
    {
      title: '填报时间', dataIndex: 'fillTime', width: 170,
      render: (v: string) => v ? dayjs(v).format('YYYY-MM-DD HH:mm:ss') : '-',
    },
    {
      title: '提交时间', dataIndex: 'submitTime', width: 170,
      render: (v: string) => v ? dayjs(v).format('YYYY-MM-DD HH:mm:ss') : '-',
    },
    {
      title: '操作', key: 'action', width: 100, fixed: 'right',
      render: () => (
        <Button type="link" size="small" icon={<EyeOutlined />}>详情</Button>
      ),
    },
  ];

  const tabItems = [
    {
      key: 'host',
      label: '主办方视角',
      children: (
        <Table
          columns={columns}
          dataSource={ledgerList}
          rowKey="id"
          loading={ledgerLoading}
          scroll={{ x: 1300 }}
          onChange={(pagination) => doFetch({ current: pagination.current, size: pagination.pageSize, ...searchParams })}
          pagination={{
            total: ledgerTotal,
            showSizeChanger: true,
            showTotal: (t) => `共 ${t} 条`,
          }}
        />
      ),
    },
    {
      key: 'executor',
      label: '执行方视角',
      children: (
        <Table
          columns={columns}
          dataSource={ledgerList}
          rowKey="id"
          loading={ledgerLoading}
          scroll={{ x: 1300 }}
          onChange={(pagination) => doFetch({ current: pagination.current, size: pagination.pageSize, ...searchParams })}
          pagination={{
            total: ledgerTotal,
            showSizeChanger: true,
            showTotal: (t) => `共 ${t} 条`,
          }}
        />
      ),
    },
  ];

  return (
    <div style={{ padding: 24, background: '#fff', borderRadius: 8 }}>
      <div className="search-form" style={{ marginBottom: 16 }}>
        <Form form={searchForm} layout="inline">
          <Form.Item name="taskName" label="任务名称">
            <Input placeholder="请输入" allowClear />
          </Form.Item>
          <Form.Item name="deptName" label="部门">
            <Input placeholder="请输入" allowClear />
          </Form.Item>
          <Form.Item name="fillStatus" label="填报状态">
            <Select placeholder="请选择" allowClear style={{ width: 130 }}>
              <Select.Option value={0}>未填报</Select.Option>
              <Select.Option value={1}>已填报</Select.Option>
              <Select.Option value={2}>已提交</Select.Option>
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

      <Tabs items={tabItems} onChange={handleTabChange} activeKey={perspective} />
    </div>
  );
};

export default TaskLedgerPage;
