import React, { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Table, Button, Space, Tag, Modal, Form, Input, App, Alert, InputNumber,
} from 'antd';
import {
  ReloadOutlined, CheckOutlined, CloseOutlined, NodeIndexOutlined, SearchOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { useAuditStore } from '@/stores/useAuditStore';
import TaskSelector from '@/components/TaskSelector';
import type { AuditDataItem } from '@/services/api';

const auditTag = (v?: number) => {
  if (v === 1) return <Tag color="green">通过</Tag>;
  if (v === 2) return <Tag color="red">驳回</Tag>;
  if (v === 0) return <Tag color="processing">待审核</Tag>;
  return <Tag>未开始</Tag>;
};

/**
 * 任务方审核（双通道第二级）：后端要求部门审核已通过才允许操作。
 */
const TaskAuditPage: React.FC = () => {
  const { message: msg } = App.useApp();
  const navigate = useNavigate();
  const { list, total, loading, taskData, taskDataLoading, fetchList, fetchTaskData, doTaskAudit } = useAuditStore();

  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [taskId, setTaskId] = useState<number | undefined>(undefined);
  const [modalOpen, setModalOpen] = useState(false);
  const [pass, setPass] = useState(true);
  const [current, setCurrent] = useState<AuditDataItem | null>(null);
  const [form] = Form.useForm();

  const load = useCallback((p = page, s = pageSize) => {
    fetchList({ current: p, size: s });
  }, [fetchList, page, pageSize]);

  useEffect(() => {
    load(1, pageSize);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const openAudit = (record: AuditDataItem, isPass: boolean) => {
    setCurrent(record);
    setPass(isPass);
    form.resetFields();
    setModalOpen(true);
  };

  const submit = async () => {
    if (!current) return;
    try {
      const values = await form.validateFields();
      await doTaskAudit({ taskDataId: current.id, pass, comment: values.comment });
      msg.success(pass ? '任务方审核通过' : '已驳回');
      setModalOpen(false);
      if (taskId) fetchTaskData(taskId); else load();
    } catch (e: any) {
      if (e?.errorFields) return;
      msg.error(e?.message || '审核失败');
    }
  };

  const handleDownload = (url?: any) => {
    if (!url) {
      (window as any).alert('暂无可下载的文件');
      return;
    }
    window.open(url, '_blank');
  };

  const columns: ColumnsType<AuditDataItem> = [
    { title: 'ID', dataIndex: 'id', width: 70, align: 'center' },
    { title: '任务名称', dataIndex: 'taskName', width: 200, render: (v) => v || '-' },
    { title: '模板', dataIndex: 'templateName', width: 150, render: (v) => v || '-' },
    { title: '填报部门', dataIndex: 'deptName', width: 140, render: (v) => v || '-' },
    { title: '填报人', dataIndex: 'fillUserName', width: 110, render: (v, r) => v || r.fillRealName || '-' },
    {
      title: '提交时间', dataIndex: 'submitTime', width: 165,
      render: (v) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm:ss') : '-'),
    },
    { title: '部门审核', dataIndex: 'deptAuditStatus', width: 100, align: 'center', render: auditTag },
    { title: '任务方审核', dataIndex: 'taskAuditStatus', width: 110, align: 'center', render: auditTag },
    {
      title: '操作', key: 'action', width: 220, fixed: 'right',
      render: (_, record) => {
        const notDeptPassed = record.deptAuditStatus !== 1;
        const done = record.taskAuditStatus === 1 || record.taskAuditStatus === 2;
        return (
          <Space>
            <Button type="link" size="small" icon={<CheckOutlined />} disabled={notDeptPassed || done}
              title={notDeptPassed ? '需部门审核通过后才能进行任务方审核' : undefined}
              onClick={() => openAudit(record, true)}>通过</Button>
            <Button type="link" size="small" danger icon={<CloseOutlined />} disabled={notDeptPassed || done}
              title={notDeptPassed ? '需部门审核通过后才能进行任务方审核' : undefined}
              onClick={() => openAudit(record, false)}>驳回</Button>
            <Button type="link" size="small" onClick={() => handleDownload((record as any).fileUrl)} disabled={!(record as any).fileUrl}>报表</Button>
            <Button type="link" size="small" icon={<NodeIndexOutlined />}
              onClick={() => navigate(`/audit/trajectory?taskId=${record.taskId}`)}>轨迹</Button>
          </Space>
        );
      },
    },
  ];

  const dataSource = taskId ? taskData : list;

  return (
    <div style={{ padding: 24, background: '#fff', borderRadius: 8 }}>
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="双通道审核：必须先通过「部门审核」，才能进行任务方审核；且不能审核自己提交的数据。"
      />

      <Space style={{ marginBottom: 16 }} wrap>
        <span>按任务查看：</span>
        <TaskSelector value={taskId} onChange={setTaskId} />
        <Button type="primary" icon={<SearchOutlined />} disabled={!taskId}
          onClick={() => taskId && fetchTaskData(taskId)}>查询</Button>
        <Button icon={<ReloadOutlined />} onClick={() => { setTaskId(undefined); load(1, pageSize); }}>
          我的审核任务
        </Button>
      </Space>

      <Table
        columns={columns}
        dataSource={dataSource}
        rowKey="id"
        loading={taskId ? taskDataLoading : loading}
        scroll={{ x: 1400 }}
        onChange={(p) => {
          if (taskId) return;
          setPage(p.current || 1);
          setPageSize(p.pageSize || 10);
          load(p.current || 1, p.pageSize || 10);
        }}
        pagination={taskId
          ? { pageSize: 10, showSizeChanger: false }
          : { current: page, pageSize, total, showSizeChanger: true, showTotal: (t) => `共 ${t} 条` }}
      />

      <Modal
        title={pass ? '任务方审核通过' : '任务方审核驳回'}
        open={modalOpen}
        onOk={submit}
        onCancel={() => setModalOpen(false)}
        destroyOnClose
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item label="数据ID">{current?.id}</Form.Item>
          <Form.Item label="任务">{current?.taskName}</Form.Item>
          <Form.Item name="comment" label="审核意见"
            rules={pass ? [] : [{ required: true, message: '请填写驳回原因' }]}>
            <Input.TextArea rows={4} placeholder={pass ? '审核意见（可选）' : '请填写驳回原因'} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default TaskAuditPage;
