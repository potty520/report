import React, { useState, useEffect } from 'react';
import { connect } from 'dva';
import {
  Table, Button, Input, Space, Modal, Form, Tag, Select,
  Descriptions, message, PageHeader,
} from 'antd';
import {
  SearchOutlined, ReloadOutlined, EyeOutlined,
  CheckOutlined, CloseOutlined,
} from '@ant-design/icons';
import { useSelector, useDispatch } from 'umi';

const { Option } = Select;
const { TextArea } = Input;

const DataApplyApprove = () => {
  const dispatch = useDispatch();
  const { approveList, approveTotal, loading } = useSelector((state) => state.dataApply);
  const [searchForm] = Form.useForm();
  const [approveForm] = Form.useForm();

  const [detailVisible, setDetailVisible] = useState(false);
  const [approveVisible, setApproveVisible] = useState(false);
  const [currentRecord, setCurrentRecord] = useState(null);
  const [approveAction, setApproveAction] = useState(null); // 'pass' | 'reject'
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10 });
  const [params, setParams] = useState({});

  useEffect(() => {
    fetchList();
  }, [pagination.current, pagination.pageSize, params]);

  const fetchList = () => {
    dispatch({
      type: 'dataApply/fetchApproveList',
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

  const handleViewDetail = async (record) => {
    setCurrentRecord(record);
    const data = await dispatch({ type: 'dataApply/fetchDetail', payload: record.id });
    if (data) setCurrentRecord(data);
    setDetailVisible(true);
  };

  const handleApprove = (record, action) => {
    setCurrentRecord(record);
    setApproveAction(action);
    approveForm.resetFields();
    setApproveVisible(true);
  };

  const handleApproveOk = async () => {
    const values = await approveForm.validateFields();
    const success = await dispatch({
      type: 'dataApply/approve',
      payload: {
        id: currentRecord.id,
        result: approveAction === 'pass' ? 'APPROVED' : 'REJECTED',
        comment: values.comment || '',
      },
    });
    if (success) {
      setApproveVisible(false);
      fetchList();
    }
  };

  const statusMap = {
    PENDING: { color: 'blue', text: '待审批' },
    APPROVED: { color: 'green', text: '已通过' },
    REJECTED: { color: 'red', text: '已驳回' },
  };

  const columns = [
    { title: '申请人', dataIndex: 'applyUserName', width: 100 },
    { title: '申请部门', dataIndex: 'applyDeptName', width: 130 },
    { title: '任务名称', dataIndex: 'taskName', width: 180 },
    { title: '申请原因', dataIndex: 'reason', width: 200, ellipsis: true },
    { title: '申请时间', dataIndex: 'createTime', width: 170 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v) => {
        const s = statusMap[v] || statusMap.PENDING;
        return <Tag color={s.color}>{s.text}</Tag>;
      },
    },
    {
      title: '操作',
      width: 200,
      fixed: 'right',
      render: (_, record) => (
        <Space size="small">
          <Button
            size="small"
            icon={<EyeOutlined />}
            onClick={() => handleViewDetail(record)}
          >
            详情
          </Button>
          {record.status === 'PENDING' && (
            <>
              <Button
                size="small"
                type="primary"
                icon={<CheckOutlined />}
                onClick={() => handleApprove(record, 'pass')}
              >
                通过
              </Button>
              <Button
                size="small"
                danger
                icon={<CloseOutlined />}
                onClick={() => handleApprove(record, 'reject')}
              >
                驳回
              </Button>
            </>
          )}
        </Space>
      ),
    },
  ];

  return (
    <div>
      <PageHeader title="申请审批" style={{ padding: 0, marginBottom: 16 }} />
      <div style={{ background: '#fff', padding: 16, borderRadius: 4, border: '1px solid #f0f0f0' }}>
        <Form
          form={searchForm}
          layout="inline"
          onFinish={handleSearch}
          style={{ marginBottom: 16 }}
        >
          <Form.Item name="applyUserName">
            <Input placeholder="申请人" style={{ width: 140 }} />
          </Form.Item>
          <Form.Item name="taskName">
            <Input placeholder="任务名称" style={{ width: 160 }} />
          </Form.Item>
          <Form.Item name="status">
            <Select placeholder="审批状态" allowClear style={{ width: 130 }}>
              <Option value="PENDING">待审批</Option>
              <Option value="APPROVED">已通过</Option>
              <Option value="REJECTED">已驳回</Option>
            </Select>
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
          columns={columns}
          dataSource={approveList}
          loading={loading}
          scroll={{ x: 1000 }}
          pagination={{
            current: pagination.current,
            pageSize: pagination.pageSize,
            total: approveTotal,
            showSizeChanger: true,
            showQuickJumper: true,
            showTotal: (t) => `共 ${t} 条`,
            onChange: (page, pageSize) =>
              setPagination({ current: page, pageSize }),
          }}
        />
      </div>

      {/* 详情弹窗 */}
      <Modal
        title="申请详情"
        open={detailVisible}
        onCancel={() => setDetailVisible(false)}
        footer={<Button onClick={() => setDetailVisible(false)}>关闭</Button>}
        width={600}
      >
        {currentRecord && (
          <Descriptions bordered column={2} size="small">
            <Descriptions.Item label="申请人">{currentRecord.applyUserName}</Descriptions.Item>
            <Descriptions.Item label="申请部门">{currentRecord.applyDeptName}</Descriptions.Item>
            <Descriptions.Item label="任务名称" span={2}>{currentRecord.taskName}</Descriptions.Item>
            <Descriptions.Item label="模板">{currentRecord.templateName || '-'}</Descriptions.Item>
            <Descriptions.Item label="部门">{currentRecord.deptName || '-'}</Descriptions.Item>
            <Descriptions.Item label="申请原因" span={2}>{currentRecord.reason}</Descriptions.Item>
            <Descriptions.Item label="申请时间">{currentRecord.createTime}</Descriptions.Item>
            <Descriptions.Item label="状态">
              <Tag color={statusMap[currentRecord.status]?.color}>
                {statusMap[currentRecord.status]?.text}
              </Tag>
            </Descriptions.Item>
            {currentRecord.approverName && (
              <>
                <Descriptions.Item label="审批人">{currentRecord.approverName}</Descriptions.Item>
                <Descriptions.Item label="审批时间">{currentRecord.approveTime}</Descriptions.Item>
                <Descriptions.Item label="审批意见" span={2}>
                  {currentRecord.approveComment || '-'}
                </Descriptions.Item>
              </>
            )}
          </Descriptions>
        )}
      </Modal>

      {/* 审批通过/驳回弹窗 */}
      <Modal
        title={approveAction === 'pass' ? '审批通过' : '审批驳回'}
        open={approveVisible}
        onOk={handleApproveOk}
        onCancel={() => setApproveVisible(false)}
        destroyOnClose
        okText={approveAction === 'pass' ? '通过' : '驳回'}
        okButtonProps={{ danger: approveAction === 'reject' }}
      >
        <div style={{ marginBottom: 16 }}>
          申请任务: <strong>{currentRecord?.taskName}</strong>
        </div>
        <Form form={approveForm} layout="vertical">
          <Form.Item name="comment" label="审批意见">
            <TextArea
              rows={4}
              placeholder={approveAction === 'pass' ? '通过意见(可选)' : '请填写驳回原因'}
              rules={approveAction === 'reject' ? [{ required: true, message: '请填写驳回原因' }] : []}
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default connect(({ dataApply }) => ({ dataApply }))(DataApplyApprove);
