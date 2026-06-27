import React, { useEffect, useState, useCallback } from 'react';
import {
  Table, Button, Space, Tag, Form, Input, Select, Drawer, Descriptions, App,
} from 'antd';
import { SearchOutlined, ReloadOutlined, EyeOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { getLogListApi, getLogDetailApi } from '@/services/api';
import type { LogItem } from '@/services/api';
import dayjs from 'dayjs';

const LogPage: React.FC = () => {
  const { message: msg } = App.useApp();
  const [searchForm] = Form.useForm();

  const [list, setList] = useState<LogItem[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [current, setCurrent] = useState(1);
  const [size, setSize] = useState(10);
  const [searchParams, setSearchParams] = useState<Record<string, any>>({});

  const [drawerOpen, setDrawerOpen] = useState(false);
  const [logDetail, setLogDetail] = useState<LogItem | null>(null);

  const doFetch = useCallback((params: Record<string, any> = {}) => {
    setLoading(true);
    getLogListApi({ current: 1, size: 10, ...params })
      .then((res) => {
        const { list: records, total: t, pageNum: c, pageSize: s } = res.data.data;
        setList(records);
        setTotal(t);
        setCurrent(c);
        setSize(s);
      })
      .catch(() => msg.error('获取日志列表失败'))
      .finally(() => setLoading(false));
  }, [msg]);

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

  const handleViewDetail = async (id: number) => {
    try {
      const res = await getLogDetailApi(id);
      setLogDetail(res.data.data);
      setDrawerOpen(true);
    } catch {
      msg.error('获取日志详情失败');
    }
  };

  const columns: ColumnsType<LogItem> = [
    { title: 'ID', dataIndex: 'id', width: 60, align: 'center' },
    { title: '用户名', dataIndex: 'username', width: 120 },
    { title: 'IP地址', dataIndex: 'ip', width: 140 },
    { title: '登录地点', dataIndex: 'location', width: 140, render: (v: string) => v || '-' },
    { title: '浏览器', dataIndex: 'browser', width: 120, render: (v: string) => v || '-' },
    { title: '操作系统', dataIndex: 'os', width: 120, render: (v: string) => v || '-' },
    {
      title: '状态', dataIndex: 'status', width: 80, align: 'center',
      render: (v: number) => <Tag color={v === 1 ? 'green' : 'red'}>{v === 1 ? '成功' : '失败'}</Tag>,
    },
    { title: '消息', dataIndex: 'msg', width: 150, ellipsis: true, render: (v: string) => v || '-' },
    {
      title: '登录时间', dataIndex: 'createTime', width: 170,
      render: (v: string) => v ? dayjs(v).format('YYYY-MM-DD HH:mm:ss') : '-',
    },
    {
      title: '操作', key: 'action', width: 100, fixed: 'right',
      render: (_: any, record: LogItem) => (
        <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => handleViewDetail(record.id)}>详情</Button>
      ),
    },
  ];

  const statusMap: Record<number, { color: string; text: string }> = {
    0: { color: 'red', text: '失败' },
    1: { color: 'green', text: '成功' },
  };

  return (
    <div style={{ padding: 24, background: '#fff', borderRadius: 8 }}>
      <div className="search-form" style={{ marginBottom: 16 }}>
        <Form form={searchForm} layout="inline">
          <Form.Item name="username" label="用户名">
            <Input placeholder="请输入" allowClear />
          </Form.Item>
          <Form.Item name="ip" label="IP地址">
            <Input placeholder="请输入" allowClear />
          </Form.Item>
          <Form.Item name="status" label="状态">
            <Select placeholder="请选择" allowClear style={{ width: 120 }}>
              <Select.Option value={1}>成功</Select.Option>
              <Select.Option value={0}>失败</Select.Option>
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
      <Table
        columns={columns}
        dataSource={list}
        rowKey="id"
        loading={loading}
        scroll={{ x: 1200 }}
        onChange={(pagination) => doFetch({ current: pagination.current, size: pagination.pageSize, ...searchParams })}
        pagination={{
          current,
          pageSize: size,
          total,
          showSizeChanger: true,
          showTotal: (t) => `共 ${t} 条`,
        }}
      />

      <Drawer
        title="登录日志详情"
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        width={500}
      >
        {logDetail && (
          <Descriptions column={1} bordered size="small">
            <Descriptions.Item label="ID">{logDetail.id}</Descriptions.Item>
            <Descriptions.Item label="用户名">{logDetail.username}</Descriptions.Item>
            <Descriptions.Item label="IP地址">{logDetail.ip}</Descriptions.Item>
            <Descriptions.Item label="登录地点">{logDetail.location || '-'}</Descriptions.Item>
            <Descriptions.Item label="浏览器">{logDetail.browser || '-'}</Descriptions.Item>
            <Descriptions.Item label="操作系统">{logDetail.os || '-'}</Descriptions.Item>
            <Descriptions.Item label="登录状态">
              <Tag color={statusMap[logDetail.status]?.color}>{statusMap[logDetail.status]?.text}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="消息">{logDetail.msg || '-'}</Descriptions.Item>
            <Descriptions.Item label="登录时间">
              {logDetail.createTime ? dayjs(logDetail.createTime).format('YYYY-MM-DD HH:mm:ss') : '-'}
            </Descriptions.Item>
          </Descriptions>
        )}
      </Drawer>
    </div>
  );
};

export default LogPage;
