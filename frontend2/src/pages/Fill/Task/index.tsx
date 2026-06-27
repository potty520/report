import React, { useEffect, useState, useCallback } from 'react';
import {
  Table, Button, Space, Tag, Modal, Form, Input, Upload, message, App,
} from 'antd';
import { UploadOutlined, EditOutlined, SendOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { useFillStore } from '@/stores/useFillStore';
import type { FillTaskItem, FillDataForm } from '@/services/api';
import dayjs from 'dayjs';

const FillTaskPage: React.FC = () => {
  const { message: msg } = App.useApp();
  const { taskList, taskTotal, taskLoading, currentFillData, fetchTaskList, fetchFillDetail, submitFill, saveDraft } = useFillStore();

  const [fillModalOpen, setFillModalOpen] = useState(false);
  const [currentAssignId, setCurrentAssignId] = useState<number | null>(null);
  const [fillForm] = Form.useForm();
  const [submitting, setSubmitting] = useState(false);

  const doFetch = useCallback(() => {
    fetchTaskList({ current: 1, size: 10 });
  }, [fetchTaskList]);

  useEffect(() => {
    doFetch();
  }, [doFetch]);

  const handleFill = async (record: FillTaskItem) => {
    setCurrentAssignId(record.assignId);
    try {
      await fetchFillDetail(record.assignId);
      setFillModalOpen(true);
    } catch {
      msg.error('获取填报详情失败');
    }
  };

  const handleFillModalOk = async (type: 'draft' | 'submit') => {
    if (!currentAssignId) return;
    try {
      const values = await fillForm.validateFields();
      const data: FillDataForm = {
        assignId: currentAssignId,
        fields: Object.entries(values).map(([fieldName, fieldValue]) => ({
          fieldName,
          fieldValue: String(fieldValue || ''),
        })),
      };
      setSubmitting(true);
      if (type === 'draft') {
        await saveDraft(data);
        msg.success('草稿保存成功');
      } else {
        await submitFill(data);
        msg.success('提交成功');
        setFillModalOpen(false);
      }
      doFetch();
    } catch (error: any) {
      if (error.errorFields) return;
      msg.error('操作失败');
    } finally {
      setSubmitting(false);
    }
  };

  const statusMap: Record<number, { color: string; text: string }> = {
    0: { color: 'default', text: '未填报' },
    1: { color: 'processing', text: '已填报' },
    2: { color: 'green', text: '已提交' },
  };

  const columns: ColumnsType<FillTaskItem> = [
    { title: 'ID', dataIndex: 'id', width: 60, align: 'center' },
    { title: '任务名称', dataIndex: 'taskName', width: 200 },
    { title: '模板', dataIndex: 'templateName', width: 150 },
    {
      title: '状态', dataIndex: 'status', width: 80, align: 'center',
      render: (v: number) => <Tag color={statusMap[v]?.color}>{statusMap[v]?.text}</Tag>,
    },
    {
      title: '开始日期', dataIndex: 'startDate', width: 120,
      render: (v: string) => v || '-',
    },
    {
      title: '截止日期', dataIndex: 'endDate', width: 120,
      render: (v: string) => v || '-',
    },
    {
      title: '分派时间', dataIndex: 'assignTime', width: 170,
      render: (v: string) => v ? dayjs(v).format('YYYY-MM-DD HH:mm:ss') : '-',
    },
    {
      title: '操作', key: 'action', width: 200, fixed: 'right',
      render: (_: any, record: FillTaskItem) => (
        <Space>
          {record.status === 0 && (
            <Button type="primary" size="small" icon={<EditOutlined />} onClick={() => handleFill(record)}>
              填报
            </Button>
          )}
          {record.status === 1 && (
            <Button type="primary" size="small" icon={<SendOutlined />} onClick={() => handleFill(record)}>
              提交
            </Button>
          )}
          {record.status === 2 && (
            <Button size="small" icon={<EditOutlined />} onClick={() => handleFill(record)}>
              查看
            </Button>
          )}
        </Space>
      ),
    },
  ];

  return (
    <div style={{ padding: 24, background: '#fff', borderRadius: 8 }}>
      <div style={{ marginBottom: 16 }}>
        <h3 style={{ margin: 0 }}>数据填报</h3>
        <p style={{ color: '#999', margin: '4px 0 0' }}>填写和提交各项报表数据</p>
      </div>
      <Table
        columns={columns}
        dataSource={taskList}
        rowKey="id"
        loading={taskLoading}
        scroll={{ x: 900 }}
        onChange={(pagination) => fetchTaskList({ current: pagination.current, size: pagination.pageSize })}
        pagination={{
          total: taskTotal,
          showSizeChanger: true,
          showTotal: (t) => `共 ${t} 条`,
        }}
      />

      <Modal
        title="数据填报"
        open={fillModalOpen}
        onCancel={() => setFillModalOpen(false)}
        width={700}
        footer={[
          <Button key="draft" onClick={() => handleFillModalOk('draft')} loading={submitting}>保存草稿</Button>,
          <Button key="submit" type="primary" onClick={() => handleFillModalOk('submit')} loading={submitting} icon={<SendOutlined />}>
            提交数据
          </Button>,
        ]}
        destroyOnClose
      >
        <div style={{ marginBottom: 16 }}>
          <Upload accept=".xlsx,.xls" maxCount={1}>
            <Button icon={<UploadOutlined />}>上传Excel填报</Button>
          </Upload>
          <span style={{ marginLeft: 12, color: '#999', fontSize: 13 }}>也可直接在下表中填写</span>
        </div>
        <Form form={fillForm} layout="vertical">
          {currentFillData?.fields?.map((field) => (
            <Form.Item
              key={field.fieldName}
              name={field.fieldName}
              label={`${field.fieldName}`}
            >
              <Input placeholder={`请输入${field.fieldName}`} />
            </Form.Item>
          ))}
          {(!currentFillData?.fields || currentFillData.fields.length === 0) && (
            <div style={{ textAlign: 'center', padding: 40, color: '#999' }}>
              暂无填报字段，请先上传Excel或联系管理员配置模板字段
            </div>
          )}
        </Form>
      </Modal>
    </div>
  );
};

export default FillTaskPage;
