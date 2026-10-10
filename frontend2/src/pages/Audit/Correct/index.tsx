import React, { useEffect, useState } from 'react';
import {
  Table, Button, Space, Tag, Modal, Form, Input, App, InputNumber, Card, Alert,
} from 'antd';
import { SearchOutlined, ReloadOutlined, EditOutlined, StopOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { useAuditStore } from '@/stores/useAuditStore';
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
