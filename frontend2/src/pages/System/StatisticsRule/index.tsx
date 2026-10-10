import React, { useCallback, useEffect, useState } from 'react';
import {
  Table, Button, Space, Tag, App, Card, Form, Input, InputNumber, Select, Modal, Popconfirm, Row, Col, Statistic, Alert,
} from 'antd';
import { PlusOutlined, ReloadOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import {
  getStatisticsRuleListApi, getStatisticsRuleEffectiveApi,
  saveStatisticsRuleApi, deleteStatisticsRuleApi, StatisticsRuleItem,
} from '@/services/api';

/**
 * 统计规则配置：超期宽限天数 / 提前提醒天数（默认 0，与配置前口径一致）。
 */
const StatisticsRulePage: React.FC = () => {
  const { message: msg } = App.useApp();
  const [form] = Form.useForm();
  const [list, setList] = useState<StatisticsRuleItem[]>([]);
  const [effective, setEffective] = useState<Record<string, any>>({});
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<StatisticsRuleItem | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [listRes, effRes] = await Promise.all([
        getStatisticsRuleListApi(),
        getStatisticsRuleEffectiveApi(),
      ]);
      setList(listRes.data.data || []);
      setEffective(effRes.data.data || {});
    } catch (e: any) {
      msg.error(e?.message || '查询失败（规则列表仅超级管理员可用）');
      setList([]);
    } finally {
      setLoading(false);
    }
  }, [msg]);

  useEffect(() => { load(); }, [load]);

  const openAdd = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ graceDays: 0, remindDays: 0, enabled: 1 });
    setModalOpen(true);
  };

  const openEdit = (record: StatisticsRuleItem) => {
    setEditing(record);
    form.setFieldsValue(record);
    setModalOpen(true);
  };

  const submit = async () => {
    try {
      const values = await form.validateFields();
      await saveStatisticsRuleApi({ ...(editing || {}), ...values });
      msg.success('已保存（规则按 ruleCode 幂等更新）');
      setModalOpen(false);
      load();
    } catch (e: any) {
      if (e?.errorFields) return;
      msg.error(e?.message || '保存失败');
    }
  };

  const remove = async (record: StatisticsRuleItem) => {
    if (!record.id) return;
    try {
      await deleteStatisticsRuleApi(record.id);
      msg.success('已删除');
      load();
    } catch (e: any) {
      msg.error(e?.message || '删除失败');
    }
  };

  const columns: ColumnsType<StatisticsRuleItem> = [
    { title: '规则编码', dataIndex: 'ruleCode', width: 180, render: (v) => <Tag color="blue">{v}</Tag> },
    { title: '规则名称', dataIndex: 'ruleName', width: 150, render: (v) => v || '-' },
    { title: '超期宽限(天)', dataIndex: 'graceDays', width: 130, align: 'center', render: (v) => v ?? 0 },
    { title: '提前提醒(天)', dataIndex: 'remindDays', width: 130, align: 'center', render: (v) => v ?? 0 },
    {
      title: '启用', dataIndex: 'enabled', width: 90, align: 'center',
      render: (v: number) => (v === 1 ? <Tag color="green">启用</Tag> : <Tag>停用</Tag>),
    },
    { title: '备注', dataIndex: 'remark', ellipsis: true, render: (v) => v || '-' },
    {
      title: '操作', key: 'action', width: 150, fixed: 'right',
      render: (_, record) => (
        <Space>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => openEdit(record)}>编辑</Button>
          <Popconfirm title="确认删除该规则？" onConfirm={() => remove(record)} okText="删除" cancelText="取消">
            <Button type="link" size="small" danger icon={<DeleteOutlined />} disabled={!record.id}>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div style={{ padding: 24, background: '#fff', borderRadius: 8 }}>
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="超期判定 = 填报截止时间 + 宽限天数；默认 0 天，与系统原有口径完全一致。规则未启用或缺行时按默认值处理。"
      />

      <Row gutter={16} style={{ marginBottom: 16 }}>
        <Col span={6}><Card size="small"><Statistic title="生效：超期宽限(天)" value={effective.overdueGraceDays ?? 0} /></Card></Col>
        <Col span={6}><Card size="small"><Statistic title="生效：超期提前提醒(天)" value={effective.overdueRemindDaysBefore ?? 0} /></Card></Col>
        <Col span={6}><Card size="small"><Statistic title="待办提前提醒(天)" value={effective.todoRemindDaysBefore ?? 0} /></Card></Col>
        <Col span={6}><Card size="small"><Statistic title="有截止时间的任务数" value={effective.taskCount ?? 0} /></Card></Col>
      </Row>

      <Space style={{ marginBottom: 16 }}>
        <Button icon={<ReloadOutlined />} onClick={load}>刷新</Button>
        <Button type="primary" icon={<PlusOutlined />} onClick={openAdd}>新增规则</Button>
      </Space>

      <Card size="small" title="统计/提醒规则">
        <Table
          columns={columns}
          dataSource={list}
          rowKey={(r) => String(r.id ?? r.ruleCode)}
          loading={loading}
          size="small"
          scroll={{ x: 1000 }}
          pagination={{ pageSize: 20, showSizeChanger: false }}
        />
      </Card>

      <Modal
        title={editing ? `编辑规则：${editing.ruleCode}` : '新增规则'}
        open={modalOpen}
        onOk={submit}
        onCancel={() => setModalOpen(false)}
        destroyOnClose
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item name="ruleCode" label="规则编码" rules={[{ required: true, message: '请填写规则编码' }]}>
            <Input placeholder="如 OVERDUE_REMIND / TODO_REMIND / CORRECT_REMIND" disabled={!!editing} />
          </Form.Item>
          <Form.Item name="ruleName" label="规则名称">
            <Input placeholder="如 超期提醒" />
          </Form.Item>
          <Space size="large">
            <Form.Item name="graceDays" label="超期宽限天数">
              <InputNumber min={0} style={{ width: 140 }} />
            </Form.Item>
            <Form.Item name="remindDays" label="提前提醒天数">
              <InputNumber min={0} style={{ width: 140 }} />
            </Form.Item>
            <Form.Item name="enabled" label="启用">
              <Select style={{ width: 110 }}>
                <Select.Option value={1}>启用</Select.Option>
                <Select.Option value={0}>停用</Select.Option>
              </Select>
            </Form.Item>
          </Space>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default StatisticsRulePage;
