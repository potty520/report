import React, { useState, useEffect } from 'react';
import { connect } from 'dva';
import {
  Table, Button, Input, Space, Modal, Form, Select, Tag, message, PageHeader,
} from 'antd';
import {
  SearchOutlined, ReloadOutlined, SwapOutlined,
} from '@ant-design/icons';
import { useSelector, useDispatch } from 'umi';

const { Option } = Select;
const { TextArea } = Input;

const DataApplyApply = () => {
  const dispatch = useDispatch();
  const { list, total, loading } = useSelector((state) => state.dataApply);
  const taskList = useSelector((state) => state.task.list) || [];
  const [searchForm] = Form.useForm();
  const [applyForm] = Form.useForm();

  const [applyVisible, setApplyVisible] = useState(false);
  const [currentTask, setCurrentTask] = useState(null);
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10 });
  const [params, setParams] = useState({});

  useEffect(() => {
    dispatch({ type: 'task/fetchList', payload: { page: 1, size: 1000, status: 'PUBLISHED' } });
    fetchApplyList();
  }, [pagination.current, pagination.pageSize, params]);

  const fetchApplyList = () => {
    dispatch({
      type: 'dataApply/fetchList',
      payload: { ...params, page: pagination.current, size: pagination.pageSize },
    });
  };

  const handleSearch = (values) => {
    setParams(values);
    setPagination((prev) => ({ ...prev, current: 1 }));
  };

  const handleReset = () => {
    searchForm.resetFields();
    setParams({});
    setPagination((prev) => ({ ...prev, current: 1 }));
  };

  const handleApply = (record) => {
    setCurrentTask(record);
    applyForm.resetFields();
    setApplyVisible(true);
  };

  const handleSubmitApply = async () => {
    const values = await applyForm.validateFields();
    const success = await dispatch({
      type: 'dataApply/add',
      payload: {
        taskId: currentTask.id,
        reason: values.reason,
      },
    });
    if (success) {
      setApplyVisible(false);
      fetchApplyList();
    }
  };

  const statusMap = {
    PENDING: { color: 'blue', text: '待审批' },
    APPROVED: { color: 'green', text: '已通过' },
    REJECTED: { color: 'red', text: '已驳回' },
  };

  // 可申请的任务表格列
  const taskColumns = [
    { title: '任务名称', dataIndex: 'taskName', width: 180 },
    { title: '模板', dataIndex: 'templateName', width: 160 },
    { title: '部门', dataIndex: 'deptName', width: 130 },
    { title: '所属期', dataIndex: 'belongTime', width: 110 },
    { title: '创建时间', dataIndex: 'createTime', width: 170 },
    {
      title: '操作',
      width: 100,
      fixed: 'right',
      render: (_, record) => (
        <Button
          size="small"
          type="primary"
          icon={<SwapOutlined />}
          onClick={() => handleApply(record)}
        >
          申请
        </Button>
      ),
    },
  ];

  // 已申请记录表格列
  const applyColumns = [
    { title: '任务名称', dataIndex: 'taskName', width: 180 },
    { title: '申请原因', dataIndex: 'reason', width: 200, ellipsis: true },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v) => {
        const s = statusMap[v] || statusMap.PENDING;
        return <Tag color={s.color}>{s.text}</Tag>;
      },
    },
    { title: '审批人', dataIndex: 'approverName', width: 100 },
    { title: '审批意见', dataIndex: 'approveComment', width: 150, ellipsis: true },
    { title: '申请时间', dataIndex: 'createTime', width: 170 },
  ];

  return (
    <div>
      <PageHeader title="数据申请" style={{ padding: 0, marginBottom: 16 }} />
      <div style={{ background: '#fff', padding: 16, borderRadius: 4, border: '1px solid #f0f0f0', marginBottom: 16 }}>
        <h4 style={{ marginBottom: 12 }}>可申请的任务</h4>
        <Form
          form={searchForm}
          layout="inline"
          onFinish={handleSearch}
          style={{ marginBottom: 16 }}
        >
          <Form.Item name="taskName">
            <Input placeholder="任务名称" style={{ width: 160 }} />
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit" icon={<SearchOutlined />}>
                搜索
              </Button>
              <Button onClick={handleReset} icon={<ReloadOutlined />}>
                重置
              </Button>
            </Space>
          </Form.Item>
        </Form>

        <Table
          rowKey="id"
          columns={taskColumns}
          dataSource={taskList}
          loading={loading}
          scroll={{ x: 800 }}
          pagination={{
            current: pagination.current,
            pageSize: pagination.pageSize,
            total,
            showSizeChanger: true,
            showQuickJumper: true,
            showTotal: (t) => `共 ${t} 条`,
            onChange: (page, pageSize) =>
              setPagination({ current: page, pageSize }),
          }}
        />
      </div>

      <div style={{ background: '#fff', padding: 16, borderRadius: 4, border: '1px solid #f0f0f0' }}>
        <h4 style={{ marginBottom: 12 }}>我的申请记录</h4>
        <Table
          rowKey="id"
          columns={applyColumns}
          dataSource={list}
          loading={loading}
          scroll={{ x: 950 }}
          pagination={{
            current: pagination.current,
            pageSize: pagination.pageSize,
            total,
            showSizeChanger: true,
            showQuickJumper: true,
            showTotal: (t) => `共 ${t} 条`,
            onChange: (page, pageSize) =>
              setPagination({ current: page, pageSize }),
          }}
        />
      </div>

      {/* 申请弹窗 */}
      <Modal
        title={`数据申请 - ${currentTask?.taskName || ''}`}
        open={applyVisible}
        onOk={handleSubmitApply}
        onCancel={() => setApplyVisible(false)}
        destroyOnClose
      >
        <div style={{ marginBottom: 16, padding: 12, background: '#fafafa', borderRadius: 4 }}>
          任务: {currentTask?.taskName} | 模板: {currentTask?.templateName} | 部门: {currentTask?.deptName}
        </div>
        <Form form={applyForm} layout="vertical">
          <Form.Item name="taskId" label="任务" hidden>
            <Input />
          </Form.Item>
          <Form.Item
            name="reason"
            label="申请原因"
            rules={[{ required: true, message: '请输入申请原因' }]}
          >
            <TextArea rows={4} placeholder="请输入申请原因" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default connect(({ dataApply, task }) => ({ dataApply, task }))(DataApplyApply);
