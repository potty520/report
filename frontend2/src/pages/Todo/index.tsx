import React, { useCallback, useEffect, useState } from 'react';
import {
  Table, Button, Space, Tag, App, Card, Row, Col, Statistic, Modal, Form, Input, InputNumber, Tabs, Badge,
} from 'antd';
import { ReloadOutlined, CheckOutlined, NotificationOutlined, ClockCircleOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { useTodoStore } from '@/stores/useTodoStore';
import type { TodoMessage } from '@/services/api';

const MSG_TYPES: Record<string, { color: string; text: string }> = {
  TODO: { color: 'blue', text: '待办' },
  REMIND: { color: 'orange', text: '催办' },
  OVERDUE: { color: 'red', text: '超期' },
  CORRECT: { color: 'volcano', text: '补正' },
  SYSTEM: { color: 'default', text: '系统' },
};

/** 消息中心（P1-F）：待办汇总 + 我的消息 + 催办/超期扫描 */
const TodoPage: React.FC = () => {
  const { message: msg } = App.useApp();
  const { summary, list, total, loading, fetchSummary, fetchList, markRead, markAllRead, remind, scanOverdue } = useTodoStore();
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [readFlag, setReadFlag] = useState<number | undefined>(undefined);
  const [remindOpen, setRemindOpen] = useState(false);
  const [form] = Form.useForm();

  const load = useCallback((p = page, s = pageSize, flag = readFlag) => {
    fetchList({ current: p, size: s, readFlag: flag });
  }, [fetchList, page, pageSize, readFlag]);

  useEffect(() => {
    fetchSummary();
    load(1, pageSize, undefined);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleRead = async (record: TodoMessage) => {
    try {
      await markRead(record.id);
      msg.success('已标记为已读');
      load();
    } catch (e: any) {
      msg.error(e?.message || '操作失败');
    }
  };

  const handleReadAll = async () => {
    try {
      const n = await markAllRead();
      msg.success(`已全部标记为已读（${n} 条）`);
      load();
    } catch (e: any) {
      msg.error(e?.message || '操作失败');
    }
  };

  const handleScan = async () => {
    try {
      const r = await scanOverdue();
      msg.success(`超期扫描完成：发现超期任务 ${r?.overdueTasks ?? 0} 个，新发通知 ${r?.notified ?? 0} 条`);
      load();
    } catch (e: any) {
      msg.error(e?.message || '扫描失败');
    }
  };

  const submitRemind = async () => {
    try {
      const values = await form.validateFields();
      await remind(values.taskId, values.comment);
      msg.success('催办已发送');
      setRemindOpen(false);
      load();
    } catch (e: any) {
      if (e?.errorFields) return;
      msg.error(e?.message || '催办失败');
    }
  };

  const columns: ColumnsType<TodoMessage> = [
    {
      title: '状态', dataIndex: 'readFlag', width: 80, align: 'center',
      render: (v: number) => (v === 0 ? <Badge status="processing" text="未读" /> : <Badge status="default" text="已读" />),
    },
    {
      title: '类型', dataIndex: 'msgType', width: 90, align: 'center',
      render: (v: string) => {
        const hit = MSG_TYPES[v] || { color: 'default', text: v || '-' };
        return <Tag color={hit.color}>{hit.text}</Tag>;
      },
    },
    { title: '标题', dataIndex: 'title', ellipsis: true },
    { title: '内容', dataIndex: 'content', ellipsis: true, render: (v) => v || '-' },
    { title: '关联任务', dataIndex: 'bizTaskId', width: 100, align: 'center', render: (v) => v || '-' },
    {
      title: '时间', dataIndex: 'createTime', width: 165,
      render: (v) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm:ss') : '-'),
    },
    {
      title: '操作', key: 'action', width: 110, fixed: 'right',
      render: (_, record) => (
        <Button type="link" size="small" icon={<CheckOutlined />} disabled={record.readFlag !== 0}
          onClick={() => handleRead(record)}>标记已读</Button>
      ),
    },
  ];

  const tabItems = [
    { key: 'all', label: '全部', children: null },
    { key: 'unread', label: '未读', children: null },
    { key: 'read', label: '已读', children: null },
  ];

  return (
    <div style={{ padding: 24, background: '#fff', borderRadius: 8 }}>
      <Row gutter={16} style={{ marginBottom: 16 }}>
        <Col span={6}><Card size="small"><Statistic title="待办" value={summary.todoCount ?? 0} valueStyle={{ color: '#1677ff' }} /></Card></Col>
        <Col span={6}><Card size="small"><Statistic title="已办" value={summary.doneCount ?? 0} valueStyle={{ color: '#3f8600' }} /></Card></Col>
        <Col span={6}><Card size="small"><Statistic title="今日" value={summary.todayCount ?? 0} /></Card></Col>
        <Col span={6}><Card size="small"><Statistic title="超期" value={summary.overdueCount ?? 0} valueStyle={{ color: '#cf1322' }} /></Card></Col>
      </Row>

      <Space style={{ marginBottom: 12 }} wrap>
        <Button icon={<ReloadOutlined />} onClick={() => { fetchSummary(); load(); }}>刷新</Button>
        <Button type="primary" icon={<CheckOutlined />} onClick={handleReadAll}>全部已读</Button>
        <Button icon={<NotificationOutlined />} onClick={() => { form.resetFields(); setRemindOpen(true); }}>发起催办</Button>
        <Button icon={<ClockCircleOutlined />} onClick={handleScan}>超期扫描</Button>
      </Space>

      <Tabs
        items={tabItems.map((t) => ({ key: t.key, label: t.label }))}
        onChange={(key) => {
          const flag = key === 'unread' ? 0 : key === 'read' ? 1 : undefined;
          setReadFlag(flag);
          setPage(1);
          load(1, pageSize, flag);
        }}
      />

      <Table
        columns={columns}
        dataSource={list}
        rowKey="id"
        loading={loading}
        size="small"
        scroll={{ x: 1000 }}
        onChange={(p) => {
          setPage(p.current || 1);
          setPageSize(p.pageSize || 10);
          load(p.current || 1, p.pageSize || 10);
        }}
        pagination={{ current: page, pageSize, total, showSizeChanger: true, showTotal: (n) => `共 ${n} 条` }}
      />

      <Modal title="发起催办" open={remindOpen} onOk={submitRemind} onCancel={() => setRemindOpen(false)} destroyOnClose>
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item name="taskId" label="任务ID" rules={[{ required: true, message: '请填写任务ID' }]}>
            <InputNumber style={{ width: '100%' }} placeholder="需要催办的任务ID" />
          </Form.Item>
          <Form.Item name="comment" label="催办说明">
            <Input.TextArea rows={3} placeholder="例如：请于今日下班前提交" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default TodoPage;
