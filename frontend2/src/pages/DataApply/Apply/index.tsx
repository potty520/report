import React, { useEffect, useState, useCallback } from 'react';
import {
  Table, Button, Space, Tag, Modal, Form, Input, message, Popconfirm, App,
} from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, SearchOutlined, ReloadOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { useDataApplyStore } from '@/stores/useDataApplyStore';
import type { DataApplyItem, DataApplyFormParams } from '@/services/api';
import dayjs from 'dayjs';

const DataApplyPage: React.FC = () => {
  const { message: msg } = App.useApp();
  const { applyList, applyTotal, applyLoading, fetchApplyList, createApply, updateApply, removeApply, setCurrentApply } = useDataApplyStore();

  const [searchForm] = Form.useForm();
  const [editForm] = Form.useForm();
  const [modalOpen, setModalOpen] = useState(false);
  const [modalTitle, setModalTitle] = useState('新增申请');
  const [editingId, setEditingId] = useState<number | null>(null);
  const [searchParams, setSearchParams] = useState<Record<string, any>>({});

  const doFetch = useCallback((params: Record<string, any> = {}) => {
    fetchApplyList({ current: 1, size: 10, ...params });
  }, [fetchApplyList]);

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

  const handleAdd = () => {
    setEditingId(null);
    setModalTitle('新增申请');
    editForm.resetFields();
    setModalOpen(true);
  };

  const handleEdit = (record: DataApplyItem) => {
    setEditingId(record.id);
    setModalTitle('编辑申请');
    editForm.setFieldsValue({
      title: record.title,
      reason: record.reason,
      dataScope: record.dataScope,
    });
    setModalOpen(true);
  };

  const handleDelete = async (id: number) => {
    try {
      await removeApply(id);
      msg.success('删除成功');
      doFetch(searchParams);
    } catch {
      msg.error('删除失败');
    }
  };

  const handleModalOk = async () => {
    try {
      const values = await editForm.validateFields();
      const data: DataApplyFormParams = { ...values };
      if (editingId) {
        await updateApply(editingId, data);
        msg.success('更新成功');
      } else {
        await createApply(data);
        msg.success('创建成功');
      }
      setModalOpen(false);
      doFetch(searchParams);
    } catch (error: any) {
      if (error.errorFields) return;
      msg.error(error?.message || '操作失败');
    }
  };

  const statusMap: Record<number, { color: string; text: string }> = {
    0: { color: 'processing', text: '待审批' },
    1: { color: 'green', text: '已通过' },
    2: { color: 'red', text: '已拒绝' },
  };

  const columns: ColumnsType<DataApplyItem> = [
    { title: 'ID', dataIndex: 'id', width: 60, align: 'center' },
    { title: '申请标题', dataIndex: 'title', width: 200 },
    { title: '申请原因', dataIndex: 'reason', width: 200, ellipsis: true },
    { title: '数据范围', dataIndex: 'dataScope', width: 150, render: (v: string) => v || '-' },
    { title: '申请人', dataIndex: 'applicantName', width: 120 },
    {
      title: '状态', dataIndex: 'status', width: 80, align: 'center',
      render: (v: number) => <Tag color={statusMap[v]?.color}>{statusMap[v]?.text}</Tag>,
    },
    {
      title: '申请时间', dataIndex: 'applyTime', width: 170,
      render: (v: string) => v ? dayjs(v).format('YYYY-MM-DD HH:mm:ss') : '-',
    },
    { title: '审批人', dataIndex: 'approverName', width: 120, render: (v: string) => v || '-' },
    {
      title: '审批时间', dataIndex: 'approveTime', width: 170,
      render: (v: string) => v ? dayjs(v).format('YYYY-MM-DD HH:mm:ss') : '-',
    },
    {
      title: '操作', key: 'action', width: 150, fixed: 'right',
      render: (_: any, record: DataApplyItem) => (
        <Space>
          {record.status === 0 && (
            <Button type="link" size="small" icon={<EditOutlined />} onClick={() => handleEdit(record)}>编辑</Button>
          )}
          {record.status !== 0 && (
            <Button type="link" size="small" icon={<EditOutlined />} disabled>编辑</Button>
          )}
          <Popconfirm title="确定删除?" onConfirm={() => handleDelete(record.id)} okText="确定" cancelText="取消">
            <Button type="link" size="small" danger icon={<DeleteOutlined />} disabled={record.status !== 0}>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div style={{ padding: 24, background: '#fff', borderRadius: 8 }}>
      <div className="search-form" style={{ marginBottom: 16 }}>
        <Form form={searchForm} layout="inline">
          <Form.Item name="title" label="标题">
            <Input placeholder="请输入" allowClear />
          </Form.Item>
          <Form.Item name="status" label="状态">
            <Input placeholder="0-待审批 1-通过 2-拒绝" allowClear />
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" icon={<SearchOutlined />} onClick={handleSearch}>搜索</Button>
              <Button icon={<ReloadOutlined />} onClick={handleReset}>重置</Button>
            </Space>
          </Form.Item>
        </Form>
      </div>
      <div className="table-operations">
        <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>新增申请</Button>
      </div>
      <Table
        columns={columns}
        dataSource={applyList}
        rowKey="id"
        loading={applyLoading}
        scroll={{ x: 1300 }}
        onChange={(pagination) => doFetch({ current: pagination.current, size: pagination.pageSize, ...searchParams })}
        pagination={{
          total: applyTotal,
          showSizeChanger: true,
          showTotal: (t) => `共 ${t} 条`,
        }}
      />

      <Modal
        title={modalTitle}
        open={modalOpen}
        onOk={handleModalOk}
        onCancel={() => setModalOpen(false)}
        destroyOnClose
        width={550}
      >
        <Form form={editForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item name="title" label="申请标题" rules={[{ required: true, message: '请输入申请标题' }]}>
            <Input placeholder="请输入申请标题" />
          </Form.Item>
          <Form.Item name="reason" label="申请原因" rules={[{ required: true, message: '请输入申请原因' }]}>
            <Input.TextArea rows={4} placeholder="请描述申请原因" />
          </Form.Item>
          <Form.Item name="dataScope" label="数据范围" rules={[{ required: true, message: '请描述数据范围' }]}>
            <Input.TextArea rows={3} placeholder="请描述所需数据的范围，如：2024年Q1全区GDP数据" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default DataApplyPage;
