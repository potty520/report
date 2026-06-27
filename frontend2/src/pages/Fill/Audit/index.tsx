import React, { useEffect, useState, useCallback } from 'react';
import {
  Table, Button, Space, Tag, Modal, Form, Input, Select, Tabs, message, App, Descriptions,
} from 'antd';
import { SearchOutlined, ReloadOutlined, CheckOutlined, CloseOutlined, EyeOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { useFillStore } from '@/stores/useFillStore';
import type { AuditItem } from '@/services/api';
import dayjs from 'dayjs';

const FillAuditPage: React.FC = () => {
  const { message: msg } = App.useApp();
  const { auditList, auditTotal, auditLoading, auditDetail, fetchAuditList, fetchAuditDetail, submitAudit } = useFillStore();

  const [searchForm] = Form.useForm();
  const [auditForm] = Form.useForm();
  const [auditType, setAuditType] = useState<'dept' | 'task'>('dept');
  const [searchParams, setSearchParams] = useState<Record<string, any>>({});
  const [selectedRowKeys, setSelectedRowKeys] = useState<number[]>([]);
  const [detailModalOpen, setDetailModalOpen] = useState(false);
  const [auditModalOpen, setAuditModalOpen] = useState(false);
  const [auditAction, setAuditAction] = useState<number>(1); // 1=pass, 2=reject

  const doFetch = useCallback((params: Record<string, any> = {}) => {
    fetchAuditList({ current: 1, size: 10, auditType, ...params });
  }, [fetchAuditList, auditType]);

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
    setAuditType(key as 'dept' | 'task');
    setSearchParams({});
    setSelectedRowKeys([]);
    searchForm.resetFields();
  };

  const handleViewDetail = async (id: number) => {
    try {
      await fetchAuditDetail(id);
      setDetailModalOpen(true);
    } catch {
      msg.error('获取审核详情失败');
    }
  };

  const handleBatchAudit = (action: number) => {
    if (selectedRowKeys.length === 0) {
      msg.warning('请先选择要审核的记录');
      return;
    }
    setAuditAction(action);
    auditForm.resetFields();
    setAuditModalOpen(true);
  };

  const handleAuditSubmit = async () => {
    try {
      const values = await auditForm.validateFields();
      await submitAudit({
        ids: selectedRowKeys,
        status: auditAction,
        remark: values.remark,
      });
      msg.success(auditAction === 1 ? '审核通过' : '已驳回');
      setAuditModalOpen(false);
      setSelectedRowKeys([]);
      doFetch(searchParams);
    } catch (error: any) {
      if (error.errorFields) return;
      msg.error('审核操作失败');
    }
  };

  const auditStatusMap: Record<number, { color: string; text: string }> = {
    0: { color: 'processing', text: '待审核' },
    1: { color: 'green', text: '审核通过' },
    2: { color: 'red', text: '审核拒绝' },
  };

  const columns: ColumnsType<AuditItem> = [
    { title: 'ID', dataIndex: 'id', width: 60, align: 'center' },
    { title: '任务名称', dataIndex: 'taskName', width: 200 },
    { title: '模板', dataIndex: 'templateName', width: 150 },
    { title: '填报部门', dataIndex: 'deptName', width: 150 },
    { title: '项目', dataIndex: 'projectName', width: 120, render: (v: string) => v || '-' },
    {
      title: '审核状态', dataIndex: 'status', width: 90, align: 'center',
      render: (v: number) => <Tag color={auditStatusMap[v]?.color}>{auditStatusMap[v]?.text}</Tag>,
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
      render: (_: any, record: AuditItem) => (
        <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => handleViewDetail(record.id)}>详情</Button>
      ),
    },
  ];

  const tabItems = [
    {
      key: 'dept',
      label: '部门审核',
      children: (
        <>
          <div className="table-operations">
            <Space>
              <Button
                type="primary"
                icon={<CheckOutlined />}
                style={{ backgroundColor: '#52c41a', borderColor: '#52c41a' }}
                onClick={() => handleBatchAudit(1)}
                disabled={selectedRowKeys.length === 0}
              >
                批量通过
              </Button>
              <Button
                danger
                icon={<CloseOutlined />}
                onClick={() => handleBatchAudit(2)}
                disabled={selectedRowKeys.length === 0}
              >
                批量驳回
              </Button>
            </Space>
          </div>
          <Table
            columns={columns}
            dataSource={auditList}
            rowKey="id"
            loading={auditLoading}
            scroll={{ x: 1100 }}
            rowSelection={{
              selectedRowKeys,
              onChange: (keys) => setSelectedRowKeys(keys as number[]),
              getCheckboxProps: (record: AuditItem) => ({
                disabled: record.status !== 0,
              }),
            }}
            onChange={(pagination) => doFetch({ current: pagination.current, size: pagination.pageSize, ...searchParams })}
            pagination={{
              total: auditTotal,
              showSizeChanger: true,
              showTotal: (t) => `共 ${t} 条`,
            }}
          />
        </>
      ),
    },
    {
      key: 'task',
      label: '任务方审核',
      children: (
        <>
          <div className="table-operations">
            <Space>
              <Button
                type="primary"
                icon={<CheckOutlined />}
                style={{ backgroundColor: '#52c41a', borderColor: '#52c41a' }}
                onClick={() => handleBatchAudit(1)}
                disabled={selectedRowKeys.length === 0}
              >
                批量通过
              </Button>
              <Button
                danger
                icon={<CloseOutlined />}
                onClick={() => handleBatchAudit(2)}
                disabled={selectedRowKeys.length === 0}
              >
                批量驳回
              </Button>
            </Space>
          </div>
          <Table
            columns={columns}
            dataSource={auditList}
            rowKey="id"
            loading={auditLoading}
            scroll={{ x: 1100 }}
            rowSelection={{
              selectedRowKeys,
              onChange: (keys) => setSelectedRowKeys(keys as number[]),
              getCheckboxProps: (record: AuditItem) => ({
                disabled: record.status !== 0,
              }),
            }}
            onChange={(pagination) => doFetch({ current: pagination.current, size: pagination.pageSize, ...searchParams })}
            pagination={{
              total: auditTotal,
              showSizeChanger: true,
              showTotal: (t) => `共 ${t} 条`,
            }}
          />
        </>
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
          <Form.Item name="status" label="状态">
            <Select placeholder="请选择" allowClear style={{ width: 130 }}>
              <Select.Option value={0}>待审核</Select.Option>
              <Select.Option value={1}>审核通过</Select.Option>
              <Select.Option value={2}>审核拒绝</Select.Option>
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

      <Tabs items={tabItems} onChange={handleTabChange} activeKey={auditType} />

      {/* Detail Drawer-like Modal */}
      <Modal
        title="审核详情"
        open={detailModalOpen}
        onCancel={() => setDetailModalOpen(false)}
        footer={null}
        width={600}
      >
        {auditDetail ? (
          <Descriptions column={1} bordered size="small">
            <Descriptions.Item label="任务名称">{auditDetail.taskName}</Descriptions.Item>
            <Descriptions.Item label="模板">{auditDetail.templateName}</Descriptions.Item>
            <Descriptions.Item label="填报部门">{auditDetail.deptName}</Descriptions.Item>
            <Descriptions.Item label="项目">{auditDetail.projectName || '-'}</Descriptions.Item>
            <Descriptions.Item label="审核状态">
              <Tag color={auditStatusMap[auditDetail.status]?.color}>{auditStatusMap[auditDetail.status]?.text}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="填报数据">
              {auditDetail.fields ? (
                <div style={{ maxHeight: 200, overflow: 'auto' }}>
                  {(auditDetail.fields as any[]).map((f: any, i: number) => (
                    <div key={i} style={{ marginBottom: 4 }}>
                      <strong>{f.fieldLabel || f.fieldName}:</strong> {f.fieldValue}
                    </div>
                  ))}
                </div>
              ) : '-'}
            </Descriptions.Item>
          </Descriptions>
        ) : (
          <div style={{ textAlign: 'center', padding: 40 }}>加载中...</div>
        )}
      </Modal>

      {/* Audit Action Modal */}
      <Modal
        title={auditAction === 1 ? '审核通过' : '审核驳回'}
        open={auditModalOpen}
        onOk={handleAuditSubmit}
        onCancel={() => setAuditModalOpen(false)}
        destroyOnClose
      >
        <Form form={auditForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item label="已选择">{selectedRowKeys.length} 条记录</Form.Item>
          <Form.Item name="remark" label="审核意见" rules={auditAction === 2 ? [{ required: true, message: '请填写驳回原因' }] : []}>
            <Input.TextArea
              rows={4}
              placeholder={auditAction === 1 ? '审核意见(可选)' : '请填写驳回原因'}
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default FillAuditPage;
