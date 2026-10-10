import React, { useCallback, useEffect, useState } from 'react';
import { Table, Button, Space, Tag, App, Card, Statistic, Row, Col, Input, Popconfirm, Modal, Form } from 'antd';
import { ReloadOutlined, SearchOutlined, LogoutOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { getOnlineListApi, getOnlineCountApi, kickOnlineApi, kickOnlineUserApi, OnlineUserItem } from '@/services/api';

/**
 * 在线用户（含异地登录可见性 + 踢下线）。
 * 被踢的会话其 token 会在后端 JwtAuthFilter 被拒绝（HTTP 401）。
 */
const OnlineUserPage: React.FC = () => {
  const { message: msg } = App.useApp();
  const [list, setList] = useState<OnlineUserItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [online, setOnline] = useState(0);
  const [kickTarget, setKickTarget] = useState<OnlineUserItem | null>(null);
  const [form] = Form.useForm();

  const load = useCallback(async (kw = keyword) => {
    setLoading(true);
    try {
      const [listRes, countRes] = await Promise.all([
        getOnlineListApi(kw ? { keyword: kw } : undefined),
        getOnlineCountApi(),
      ]);
      setList(listRes.data.data || []);
      setOnline(countRes.data.data?.online ?? 0);
    } catch (e: any) {
      msg.error(e?.message || '查询失败（该接口仅超级管理员可用）');
      setList([]);
    } finally {
      setLoading(false);
    }
  }, [keyword, msg]);

  useEffect(() => {
    load('');
    const timer = window.setInterval(() => load(), 30000);
    return () => window.clearInterval(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const submitKick = async () => {
    if (!kickTarget) return;
    try {
      const values = await form.validateFields();
      await (kickTarget.userId
        ? kickOnlineUserApi({ userId: Number(kickTarget.userId), reason: values.reason } as any)
        : kickOnlineApi({ sessionId: kickTarget.sessionId!, reason: values.reason }));
      msg.success('已踢下线（该会话下次请求将返回 401）');
      setKickTarget(null);
      load();
    } catch (e: any) {
      if (e?.errorFields) return;
      msg.error(e?.message || '操作失败');
    }
  };

  const columns: ColumnsType<OnlineUserItem> = [
    { title: '用户', dataIndex: 'username', width: 130, render: (v, r) => v || r.userId || '-' },
    { title: '姓名', dataIndex: 'realName', width: 110, render: (v) => v || '-' },
    { title: '部门', dataIndex: 'deptName', width: 130, render: (v) => v || '-' },
    { title: 'IP', dataIndex: 'ip', width: 140, render: (v) => (v ? <Tag color="blue">{v}</Tag> : '-') },
    { title: '客户端', dataIndex: 'userAgent', ellipsis: true, render: (v) => v || '-' },
    {
      title: '登录时间', dataIndex: 'loginTime', width: 165,
      render: (v) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm:ss') : '-'),
    },
    {
      title: '最后活跃', dataIndex: 'lastActiveTime', width: 165,
      render: (v) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm:ss') : '-'),
    },
    {
      title: '会话', dataIndex: 'sessionId', width: 120, ellipsis: true,
      render: (v) => (v ? String(v).slice(0, 10) + '…' : '-'),
    },
    {
      title: '操作', key: 'action', width: 110, fixed: 'right',
      render: (_, record) => (
        <Popconfirm title="确认踢下线？" onConfirm={() => { setKickTarget(record); form.resetFields(); }} okText="继续" cancelText="取消">
          <Button type="link" size="small" danger icon={<LogoutOutlined />}>踢下线</Button>
        </Popconfirm>
      ),
    },
  ];

  return (
    <div style={{ padding: 24, background: '#fff', borderRadius: 8 }}>
      <Row gutter={16} style={{ marginBottom: 16 }}>
        <Col span={6}><Card size="small"><Statistic title="当前在线会话" value={online} /></Card></Col>
        <Col span={18} style={{ display: 'flex', alignItems: 'center' }}>
          <Space>
            <Input
              placeholder="按用户名或 IP 搜索"
              allowClear
              style={{ width: 240 }}
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              onPressEnter={() => load()}
            />
            <Button type="primary" icon={<SearchOutlined />} onClick={() => load()}>搜索</Button>
            <Button icon={<ReloadOutlined />} onClick={() => load()}>刷新</Button>
          </Space>
        </Col>
      </Row>

      <Card size="small" title="在线会话（同一账号在不同 IP 登录会同时出现多行）">
        <Table
          columns={columns}
          dataSource={list}
          rowKey="id"
          loading={loading}
          size="small"
          scroll={{ x: 1300 }}
          pagination={{ pageSize: 20, showSizeChanger: true, showTotal: (t) => `共 ${t} 条` }}
        />
      </Card>

      <Modal
        title={`踢下线：${kickTarget?.username || ''} @ ${kickTarget?.ip || ''}`}
        open={!!kickTarget}
        onOk={submitKick}
        onCancel={() => setKickTarget(null)}
        destroyOnClose
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item name="reason" label="下线原因">
            <Input placeholder="如：检测到异地登录，请重新登录" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default OnlineUserPage;
