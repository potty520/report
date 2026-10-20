import React, { useEffect, useState, useCallback } from 'react';
import {
  Table, Button, Space, Tag, Modal, Form, Input, App, Descriptions,
} from 'antd';
import { ReloadOutlined, CheckOutlined, CloseOutlined, EyeOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { useDataApplyStore } from '@/stores/useDataApplyStore';
import { submitApprovalApi } from '@/services/api';
import type { DataApplyItem } from '@/services/api';
import dayjs from 'dayjs';

const statusMap: Record<number, { color: string; text: string }> = {
  0: { color: 'processing', text: '待审批' },
  1: { color: 'green', text: '已通过' },
  2: { color: 'red', text: '已拒绝' },
};

const ApprovePage: React.FC = () => {
  const { message: msg } = App.useApp();
  const { approveList, approveTotal, approveLoading, fetchApproveList } = useDataApplyStore();

  const [approveForm] = Form.useForm();
  const [detailModalOpen, setDetailModalOpen] = useState(false);
  const [currentItem, setCurrentItem] = useState<DataApplyItem | null>(null);
  const [approveModalOpen, setApproveModalOpen] = useState(false);
  const [approveAction, setApproveAction] = useState<number>(1);
  const [approveId, setApproveId] = useState<number | null>(null);

  const doFetch = useCallback((params: Record<string, any> = {}) => {
    fetchApproveList({ current: 1, size: 10, ...params });
  }, [fetchApproveList]);

  useEffect(() => {
    doFetch();
  }, [doFetch]);

  const handleApprove = (record: DataApplyItem, action: number) => {
    setCurrentItem(record);
    setApproveId(record.id);
    setApproveAction(action);
    approveForm.resetFields();
    setApproveModalOpen(true);
  };

  const handleSingleApprove = async (id: number, status: number, remark?: string) => {
    try {
      await submitApprovalApi({ id, status, remark });
      msg.success(status === 1 ? '审批通过' : '已驳回');
      doFetch();
    } catch (error: any) {
      msg.error(error?.response?.data?.msg || error?.message || '审批操作失败');
      throw error;
    }
  };

  const handleApproveSubmit = async () => {
    try {
      const values = await approveForm.validateFields();
      if (approveId) await handleSingleApprove(approveId, approveAction, values.remark);
      setApproveModalOpen(false);
    } catch (error: any) {
      if (!error?.errorFields) msg.error(error?.response?.data?.msg || '审批操作失败');
    }
  };

  const columns: ColumnsType<DataApplyItem> = [
    { title: 'ID', dataIndex: 'id', width: 60, align: 'center' },
    { title: '申请任务', dataIndex: 'taskName', width: 260, render: (v: string, r) => v || `任务#${r.taskId}` },
    { title: '申请原因', dataIndex: 'reason', width: 260, ellipsis: true },
    { title: '申请人', dataIndex: 'applicantName', width: 120, render: (v) => v || '-' },
    { title: '申请部门', dataIndex: 'applicantDeptName', width: 130, render: (v) => v || '-' },
    {
      title: '状态', dataIndex: 'status', width: 90, align: 'center',
      render: (v: number) => <Tag color={statusMap[v]?.color}>{statusMap[v]?.text || '-'}</Tag>,
    },
    {
      title: '申请时间', dataIndex: 'createTime', width: 170,
      render: (v: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm:ss') : '-'),
    },
    {
      title: '操作', key: 'action', width: 200, fixed: 'right',
      render: (_: any, record: DataApplyItem) => (
        <Space>
          <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => { setCurrentItem(record); setDetailModalOpen(true); }}>详情</Button>
          {record.status === 0 && (
            <>
              <Button type="link" size="small" icon={<CheckOutlined />} style={{ color: '#52c41a' }} onClick={() => handleApprove(record, 1)}>通过</Button>
              <Button type="link" size="small" danger icon={<CloseOutlined />} onClick={() => handleApprove(record, 2)}>拒绝</Button>
            </>
          )}
        </Space>
      ),
    },
  ];

  return (
    <div style={{ padding: 24, background: '#fff', borderRadius: 8 }}>
      <div style={{ marginBottom: 16 }}>
        <h3 style={{ margin: 0 }}>申请审批</h3>
        <p style={{ color: '#999', margin: '4px 0 0' }}>审核数据申请</p>
      </div>
      <Space style={{ marginBottom: 16 }}>
        <Button icon={<ReloadOutlined />} onClick={() => doFetch()}>刷新</Button>
      </Space>
      <Table
        columns={columns}
        dataSource={approveList}
        rowKey="id"
        loading={approveLoading}
        scroll={{ x: 1200 }}
        onChange={(pagination) => doFetch({ current: pagination.current, size: pagination.pageSize })}
        pagination={{ total: approveTotal, showSizeChanger: true, showTotal: (t) => `共 ${t} 条` }}
      />

      <Modal title="申请详情" open={detailModalOpen} onCancel={() => setDetailModalOpen(false)} footer={null} width={650}>
        {currentItem && (
          <Descriptions column={1} bordered size="small">
            <Descriptions.Item label="申请任务">{currentItem.taskName || `任务#${currentItem.taskId}`}</Descriptions.Item>
            <Descriptions.Item label="申请原因">{currentItem.reason || '-'}</Descriptions.Item>
            <Descriptions.Item label="申请人">{currentItem.applicantName || '-'}</Descriptions.Item>
            <Descriptions.Item label="申请部门">{currentItem.applicantDeptName || '-'}</Descriptions.Item>
            <Descriptions.Item label="状态"><Tag color={statusMap[currentItem.status]?.color}>{statusMap[currentItem.status]?.text || '-'}</Tag></Descriptions.Item>
            <Descriptions.Item label="申请时间">{currentItem.createTime ? dayjs(currentItem.createTime).format('YYYY-MM-DD HH:mm:ss') : '-'}</Descriptions.Item>
            <Descriptions.Item label="审批人">{currentItem.auditorName || '-'}</Descriptions.Item>
            <Descriptions.Item label="审批时间">{currentItem.auditTime ? dayjs(currentItem.auditTime).format('YYYY-MM-DD HH:mm:ss') : '-'}</Descriptions.Item>
            <Descriptions.Item label="审批意见">{currentItem.auditComment || '-'}</Descriptions.Item>
          </Descriptions>
        )}
      </Modal>

      <Modal
        title={approveAction === 1 ? '审批通过' : '审批拒绝'}
        open={approveModalOpen}
        onOk={handleApproveSubmit}
        onCancel={() => setApproveModalOpen(false)}
        destroyOnClose
      >
        <div style={{ marginBottom: 16, padding: '8px 12px', background: '#f6f8fa', borderRadius: 6 }}>
          申请任务: <strong>{currentItem?.taskName || `任务#${currentItem?.taskId || ''}`}</strong>
        </div>
        <Form form={approveForm} layout="vertical">
          <Form.Item name="remark" label="审批意见" rules={approveAction === 2 ? [{ required: true, message: '请填写驳回原因' }] : []}>
            <Input.TextArea rows={4} placeholder={approveAction === 1 ? '审批意见(可选)' : '请填写驳回原因'} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default ApprovePage;
