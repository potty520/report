import React, { useEffect, useState } from 'react';
import {
  Table, Button, Space, Tag, Modal, Form, Input, App, InputNumber, Card, Alert,
} from 'antd';
import { SearchOutlined, ReloadOutlined, EditOutlined, StopOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { useAuditStore } from '@/stores/useAuditStore';
import { getCorrectItemsApi, addCorrectItemApi, deleteCorrectItemApi } from '@/services/api';
import TaskSelector from '@/components/TaskSelector';
import type { AuditDataItem, CorrectBatch } from '@/services/api';

const correctTag = (v?: number) => {
  if (v === 1) return <Tag color="volcano">进行中</Tag>;
  if (v === 2) return <Tag color="green">已完成</Tag>;
  return <Tag>无</Tag>;
};

/** 补正管理（发起 / 关闭 / 批次查询，P0-C） */
const CorrectPage: React.FC = () => {
  const { message: msg } = App.useApp();
  const {
    taskData, taskDataLoading, correctBatches,
    fetchTaskData, fetchCorrectBatches, doStartCorrect, doCloseCorrect,
  } = useAuditStore();

  const [taskId, setTaskId] = useState<number | undefined>(undefined);
  const [selectedKeys, setSelectedKeys] = useState<number[]>([]);
  const [startOpen, setStartOpen] = useState(false);
  const [closeOpen, setCloseOpen] = useState(false);
  const [startForm] = Form.useForm();
  const [closeForm] = Form.useForm();
  const [itemsOpen, setItemsOpen] = useState(false);
  const [items, setItems] = useState<any[]>([]);
  const [currentBatchId, setCurrentBatchId] = useState<number | null>(null);

  const openItems = async (batchId: number) => {
    try {
      setCurrentBatchId(batchId);
      const res: any = await getCorrectItemsApi(batchId);
      setItems(res?.data || []);
      setItemsOpen(true);
    } catch {
      msg.error('加载补正明细失败');
    }
  };

  const addItem = async (opType: number) => {
    if (!currentBatchId) return;
    const rowKey = window.prompt('明细行标识（目标表主键值）') || '';
    const after = window.prompt('新值 JSON，例如 {"amount": 100}') || '{}';
    const before = window.prompt('原值 JSON（可为空）') || '';
    try {
      const res: any = await addCorrectItemApi(currentBatchId, { opType, rowKey, afterJson: after, beforeJson: before });
      if (res?.code === 200) {
        msg.success('已添加');
        openItems(currentBatchId);
      } else {
        msg.error(res?.msg || '添加失败');
      }
    } catch (e: any) {
      msg.error(e?.response?.data?.msg || '添加失败');
    }
  };

  const removeItem = async (itemId: number) => {
    try {
      await deleteCorrectItemApi(itemId);
      if (currentBatchId) openItems(currentBatchId);
    } catch {
      msg.error('删除失败');
    }
  };

  const loadTask = async (id: number) => {
    try {
      await Promise.all([fetchTaskData(id), fetchCorrectBatches(id)]);
    } catch {
      msg.error('加载失败');
    }
  };

  useEffect(() => {
    // 初始化不自动加载，等用户输入任务ID
  }, []);

  const startCorrect = async () => {
    if (!taskId) return;
    try {
      const values = await startForm.validateFields();
      await doStartCorrect({
        taskId,
        dataIds: selectedKeys.length ? selectedKeys.join(',') : undefined,
        comment: values.comment,
      });
      msg.success('已发起补正');
      setStartOpen(false);
      setSelectedKeys([]);
      loadTask(taskId);
    } catch (e: any) {
      if (e?.errorFields) return;
      msg.error(e?.message || '发起补正失败');
    }
  };

  const closeCorrect = async () => {
    if (!taskId) return;
    try {
      const values = await closeForm.validateFields();
      await doCloseCorrect({ taskId, comment: values.comment });
      msg.success('已关闭补正');
      setCloseOpen(false);
      loadTask(taskId);
    } catch (e: any) {
      if (e?.errorFields) return;
      msg.error(e?.message || '关闭补正失败');
    }
  };

  const dataColumns: ColumnsType<AuditDataItem> = [
    { title: 'ID', dataIndex: 'id', width: 70, align: 'center' },
    { title: '填报部门', dataIndex: 'deptName', width: 140, render: (v) => v || '-' },
    { title: '填报人', dataIndex: 'fillUserName', width: 110, render: (v, r) => v || r.fillRealName || '-' },
    {
      title: '提交时间', dataIndex: 'submitTime', width: 165,
      render: (v) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm:ss') : '-'),
    },
    { title: '补正状态', dataIndex: 'correctStatus', width: 100, align: 'center', render: correctTag },
    { title: '补正说明', dataIndex: 'correctComment', ellipsis: true, render: (v) => v || '-' },
  ];

  const batchColumns: ColumnsType<CorrectBatch> = [
    { title: '批次', dataIndex: 'id', width: 70, align: 'center' },
    { title: '涉及数据ID', dataIndex: 'data_ids', ellipsis: true, render: (v) => v || '-' },
    { title: '状态', dataIndex: 'status', width: 100, align: 'center', render: correctTag },
    { title: '发起人', dataIndex: 'start_user_name', width: 110, render: (v) => v || '-' },
    {
      title: '发起时间', dataIndex: 'start_time', width: 165,
      render: (v) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm:ss') : '-'),
    },
    {
      title: '关闭时间', dataIndex: 'close_time', width: 165,
      render: (v) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm:ss') : '-'),
    },
    { title: '说明', dataIndex: 'comment', ellipsis: true, render: (v) => v || '-' },
    {
      title: '操作', key: 'action', width: 90,
      render: (_: any, r: any) => (
        <Button size="small" type="link" onClick={() => openItems(r.id)}>明细</Button>
      ),
    },
  ];

  const hasOngoing = correctBatches.some((b) => b.status === 1);

  return (
    <div style={{ padding: 24, background: '#fff', borderRadius: 8 }}>
      <Alert
        type="warning"
        showIcon
        style={{ marginBottom: 16 }}
        message="发起补正会把所选数据退回为未提交状态，要求填报人重新填报；补正批次需要手动关闭。"
      />

      <Space style={{ marginBottom: 16 }} wrap>
        <span>任务：</span>
        <TaskSelector value={taskId} onChange={setTaskId} />
        <Button type="primary" icon={<SearchOutlined />} disabled={!taskId}
          onClick={() => taskId && loadTask(taskId)}>加载</Button>
        <Button icon={<ReloadOutlined />} disabled={!taskId}
          onClick={() => taskId && loadTask(taskId)}>刷新</Button>
        <Button type="primary" danger icon={<EditOutlined />} disabled={!taskId}
          onClick={() => { startForm.resetFields(); setStartOpen(true); }}>发起补正</Button>
        <Button icon={<StopOutlined />} disabled={!taskId || !hasOngoing}
          onClick={() => { closeForm.resetFields(); setCloseOpen(true); }}>关闭补正</Button>
      </Space>

      <Card size="small" title="任务填报数据" style={{ marginBottom: 16 }}>
        <Table
          columns={dataColumns}
          dataSource={taskData}
          rowKey="id"
          loading={taskDataLoading}
          size="small"
          rowSelection={{
            selectedRowKeys: selectedKeys,
            onChange: (keys) => setSelectedKeys(keys as number[]),
          }}
          pagination={{ pageSize: 10, showSizeChanger: false }}
        />
      </Card>

      <Card size="small" title="补正批次">
        <Table
          columns={batchColumns}
          dataSource={correctBatches}
          rowKey="id"
          size="small"
          pagination={{ pageSize: 10, showSizeChanger: false }}
        />
      </Card>

      <Modal title="发起补正" open={startOpen} onOk={startCorrect} onCancel={() => setStartOpen(false)} destroyOnClose>
        <Form form={startForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item label="已选数据">
            {selectedKeys.length ? selectedKeys.join(', ') : '未选择（将对整个任务发起补正）'}
          </Form.Item>
          <Form.Item name="comment" label="补正说明" rules={[{ required: true, message: '请填写补正说明' }]}>
            <Input.TextArea rows={4} placeholder="例如：金额与凭证不一致，请核对后重新提交" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal title="补正明细" open={itemsOpen} onCancel={() => setItemsOpen(false)} footer={null} width={860}>
        <Space style={{ marginBottom: 12 }}>
          <Button size="small" onClick={() => addItem(1)}>新增行</Button>
          <Button size="small" onClick={() => addItem(2)}>修改行</Button>
          <Button size="small" danger onClick={() => addItem(3)}>删除行</Button>
        </Space>
        <Table
          rowKey="id"
          size="small"
          dataSource={items}
          pagination={false}
          columns={[
            { title: '类型', dataIndex: 'opType', width: 90, render: (v: number) => ({ 1: '新增', 2: '修改', 3: '删除' } as Record<number, string>)[v] || v },
            { title: '行标识', dataIndex: 'rowKey', width: 140 },
            { title: '原值', dataIndex: 'beforeJson', ellipsis: true },
            { title: '新值', dataIndex: 'afterJson', ellipsis: true },
            { title: '操作', width: 80, render: (_: any, r: any) => <Button size="small" danger onClick={() => removeItem(r.id)}>删除</Button> },
          ]}
        />
      </Modal>

      <Modal title="关闭补正" open={closeOpen} onOk={closeCorrect} onCancel={() => setCloseOpen(false)} destroyOnClose>
        <Form form={closeForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item name="comment" label="关闭说明">
            <Input.TextArea rows={3} placeholder="补正完成说明（可选）" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default CorrectPage;
