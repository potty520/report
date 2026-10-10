import React, { useCallback, useEffect, useState } from 'react';
import { Table, Button, Space, Tag, App, Form, Input, Select, Card } from 'antd';
import { SearchOutlined, ReloadOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { getOperLogPageApi, OperLogItem } from '@/services/api';

/** 操作审计日志（P2-G）：module/action/userId/status 过滤 + 分页 */
const OperLogPage: React.FC = () => {
  const { message: msg } = App.useApp();
  const [form] = Form.useForm();
  const [list, setList] = useState<OperLogItem[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [params, setParams] = useState<Record<string, any>>({});

  const load = useCallback(async (p = page, s = pageSize, extra: Record<string, any> = params) => {
    setLoading(true);
    try {
      const res = await getOperLogPageApi({ page: p, pageSize: s, ...extra });
      const data = res.data.data;
      setList(data?.list || []);
      setTotal(data?.total || 0);
    } catch (e: any) {
      msg.error(e?.message || '查询失败');
      setList([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, params, msg]);

  useEffect(() => {
    load(1, pageSize, {});
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const columns: ColumnsType<OperLogItem> = [
    { title: 'ID', dataIndex: 'id', width: 70, align: 'center' },
    { title: '操作人', dataIndex: 'username', width: 110, render: (v, r) => v || r.userId || '-' },
    { title: '模块', dataIndex: 'module', width: 120, render: (v) => (v ? <Tag color="blue">{v}</Tag> : '-') },
    { title: '动作', dataIndex: 'action', width: 130, render: (v) => (v ? <Tag color="geekblue">{v}</Tag> : '-') },
    { title: '目标/入参', dataIndex: 'target', ellipsis: true, render: (v) => v || '-' },
    { title: 'IP', dataIndex: 'ip', width: 130, render: (v) => v || '-' },
    { title: '耗时(ms)', dataIndex: 'costMs', width: 100, align: 'right', render: (v) => (v === undefined || v === null ? '-' : v) },
    {
      title: '结果', dataIndex: 'status', width: 90, align: 'center',
      render: (v: number) => (v === 1 ? <Tag color="green">成功</Tag> : <Tag color="red">失败</Tag>),
    },
    { title: '错误信息', dataIndex: 'errorMsg', ellipsis: true, render: (v) => v || '-' },
    {
      title: '时间', dataIndex: 'createTime', width: 165,
      render: (v) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm:ss') : '-'),
    },
  ];

  return (
    <div style={{ padding: 24, background: '#fff', borderRadius: 8 }}>
      <div style={{ marginBottom: 16 }}>
        <Form form={form} layout="inline">
          <Form.Item name="module" label="模块">
            <Input placeholder="如：数据范围" allowClear />
          </Form.Item>
          <Form.Item name="action" label="动作">
            <Input placeholder="如：SQL试跑" allowClear />
          </Form.Item>
          <Form.Item name="userId" label="用户ID">
            <Input placeholder="数字" allowClear />
          </Form.Item>
          <Form.Item name="status" label="结果">
            <Select placeholder="全部" allowClear style={{ width: 110 }}>
              <Select.Option value={1}>成功</Select.Option>
              <Select.Option value={0}>失败</Select.Option>
            </Select>
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" icon={<SearchOutlined />} onClick={() => {
                const values = form.getFieldsValue();
                const clean: Record<string, any> = {};
                Object.entries(values).forEach(([k, v]) => {
                  if (v !== undefined && v !== null && v !== '') clean[k] = v;
                });
                setParams(clean);
                setPage(1);
                load(1, pageSize, clean);
              }}>搜索</Button>
              <Button icon={<ReloadOutlined />} onClick={() => {
                form.resetFields();
                setParams({});
                setPage(1);
                load(1, pageSize, {});
              }}>重置</Button>
            </Space>
          </Form.Item>
        </Form>
      </div>

      <Card size="small" title="操作审计日志">
        <Table
          columns={columns}
          dataSource={list}
          rowKey="id"
          loading={loading}
          size="small"
          scroll={{ x: 1400 }}
          onChange={(p) => {
            setPage(p.current || 1);
            setPageSize(p.pageSize || 10);
            load(p.current || 1, p.pageSize || 10);
          }}
          pagination={{ current: page, pageSize, total, showSizeChanger: true, showTotal: (n) => `共 ${n} 条` }}
        />
      </Card>
    </div>
  );
};

export default OperLogPage;
