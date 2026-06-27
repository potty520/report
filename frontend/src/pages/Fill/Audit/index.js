import React, { useState, useEffect } from 'react';
import { connect } from 'dva';
import {
  Table, Button, Input, Space, Modal, Form, Tabs, Tag, Select,
  Descriptions, message, PageHeader,
} from 'antd';
import {
  SearchOutlined, ReloadOutlined, EyeOutlined,
  CheckOutlined, CloseOutlined,
} from '@ant-design/icons';
import { useSelector, useDispatch } from 'umi';

const { Option } = Select;
const { TextArea } = Input;
const { TabPane } = Tabs;

const FillAudit = () => {
  const dispatch = useDispatch();
  const { list, total, loading } = useSelector((state) => state.audit);
  const [searchForm] = Form.useForm();
  const [auditForm] = Form.useForm();

  const [activeTab, setActiveTab] = useState('dept');
  const [detailVisible, setDetailVisible] = useState(false);
  const [auditVisible, setAuditVisible] = useState(false);
  const [currentRecord, setCurrentRecord] = useState(null);
  const [auditAction, setAuditAction] = useState(null); // 'pass' | 'reject'
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10 });
  const [params, setParams] = useState({});

  useEffect(() => {
    fetchList();
  }, [pagination.current, pagination.pageSize, params, activeTab]);

  const fetchList = () => {
    dispatch({
      type: 'audit/fetchList',
      payload: {
        ...params,
        auditType: activeTab,
        page: pagination.current,
        size: pagination.pageSize,
      },
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
    const data = await dispatch({ type: 'audit/fetchDetail', payload: record.id });
    if (data) setCurrentRecord(data);
    setDetailVisible(true);
  };

  const handleAudit = (record, action) => {
    setCurrentRecord(record);
    setAuditAction(action);
    auditForm.resetFields();
    setAuditVisible(true);
  };

  const handleAuditOk = async () => {
    const values = await auditForm.validateFields();
    const success = await dispatch({
      type: 'audit/doAudit',
      payload: {
        auditId: currentRecord.id,
        result: auditAction === 'pass' ? 'PASS' : 'REJECT',
        comment: values.comment || '',
      },
    });
    if (success) {
      setAuditVisible(false);
      fetchList();
    }
  };

  const statusMap = {
    PENDING: { color: 'blue', text: '待审核' },
    PASS: { color: 'green', text: '已通过' },
    REJECT: { color: 'red', text: '已驳回' },
  };

  const columns = [
    { title: '任务名称', dataIndex: 'taskName', width: 180 },
    { title: '模板', dataIndex: 'templateName', width: 160 },
    { title: '填报人', dataIndex: 'fillUserName', width: 100 },
    { title: '填报部门', dataIndex: 'deptName', width: 130 },
    { title: '提交时间', dataIndex: 'submitTime', width: 170 },
    {
      title: '审核状态',
      dataIndex: 'auditStatus',
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
            审核详情
          </Button>
          {record.auditStatus === 'PENDING' && (
            <>
              <Button
                size="small"
                type="primary"
                icon={<CheckOutlined />}
                onClick={() => handleAudit(record, 'pass')}
              >
                通过
              </Button>
              <Button
                size="small"
                danger
                icon={<CloseOutlined />}
                onClick={() => handleAudit(record, 'reject')}
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
      <PageHeader title="审核管理" style={{ padding: 0, marginBottom: 16 }} />
      <div style={{ background: '#fff', padding: 16, borderRadius: 4, border: '1px solid #f0f0f0' }}>
        <Tabs activeKey={activeTab} onChange={setActiveTab}>
          <TabPane tab="部门审核" key="dept">
            <Form
              form={searchForm}
              layout="inline"
              onFinish={handleSearch}
              style={{ marginBottom: 16 }}
            >
              <Form.Item name="taskName">
                <Input placeholder="任务名称" style={{ width: 160 }} />
              </Form.Item>
              <Form.Item name="fillUserName">
                <Input placeholder="填报人" style={{ width: 130 }} />
              </Form.Item>
              <Form.Item name="auditStatus">
                <Select placeholder="审核状态" allowClear style={{ width: 130 }}>
                  <Option value="PENDING">待审核</Option>
                  <Option value="PASS">已通过</Option>
                  <Option value="REJECT">已驳回</Option>
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
              dataSource={list}
              loading={loading}
              scroll={{ x: 1000 }}
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
          </TabPane>

          <TabPane tab="任务方审核" key="task">
            <Form
              layout="inline"
              style={{ marginBottom: 16 }}
            >
              <Form.Item name="taskName">
                <Input placeholder="任务名称" style={{ width: 160 }} />
              </Form.Item>
              <Form.Item name="auditStatus">
                <Select placeholder="审核状态" allowClear style={{ width: 130 }}>
                  <Option value="PENDING">待审核</Option>
                  <Option value="PASS">已通过</Option>
                  <Option value="REJECT">已驳回</Option>
                </Select>
              </Form.Item>
              <Form.Item>
                <Space>
                  <Button type="primary" htmlType="submit" icon={<SearchOutlined />}>
                    搜索
                  </Button>
                  <Button icon={<ReloadOutlined />}>
                    重置
                  </Button>
                </Space>
              </Form.Item>
            </Form>

            <Table
              rowKey="id"
              columns={[
                { title: '任务名称', dataIndex: 'taskName', width: 180 },
                { title: '填报部门', dataIndex: 'deptName', width: 140 },
                { title: '填报人', dataIndex: 'fillUserName', width: 100 },
                { title: '部门审核状态', dataIndex: 'deptAuditStatus', width: 110, render: (v) => {
                  const s = statusMap[v] || { color: 'default', text: v || '未审核' };
                  return <Tag color={s.color}>{s.text}</Tag>;
                }},
                { title: '提交时间', dataIndex: 'submitTime', width: 170 },
                {
                  title: '审核状态',
                  dataIndex: 'auditStatus',
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
                      <Button size="small" icon={<EyeOutlined />} onClick={() => handleViewDetail(record)}>
                        审核详情
                      </Button>
                      {record.auditStatus === 'PENDING' && (
                        <>
                          <Button size="small" type="primary" icon={<CheckOutlined />}
                            onClick={() => handleAudit(record, 'pass')}>通过</Button>
                          <Button size="small" danger icon={<CloseOutlined />}
                            onClick={() => handleAudit(record, 'reject')}>驳回</Button>
                        </>
                      )}
                    </Space>
                  ),
                },
              ]}
              dataSource={list}
              loading={loading}
              scroll={{ x: 1100 }}
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
          </TabPane>
        </Tabs>
      </div>

      {/* 审核详情弹窗 */}
      <Modal
        title="审核详情"
        open={detailVisible}
        onCancel={() => setDetailVisible(false)}
        footer={<Button onClick={() => setDetailVisible(false)}>关闭</Button>}
        width={700}
      >
        {currentRecord && (
          <div>
            <Descriptions bordered column={2} size="small" style={{ marginBottom: 16 }}>
              <Descriptions.Item label="任务名称">{currentRecord.taskName}</Descriptions.Item>
              <Descriptions.Item label="模板">{currentRecord.templateName}</Descriptions.Item>
              <Descriptions.Item label="填报人">{currentRecord.fillUserName}</Descriptions.Item>
              <Descriptions.Item label="部门">{currentRecord.deptName}</Descriptions.Item>
              <Descriptions.Item label="提交时间">{currentRecord.submitTime}</Descriptions.Item>
              <Descriptions.Item label="审核状态">
                <Tag color={statusMap[currentRecord.auditStatus]?.color}>
                  {statusMap[currentRecord.auditStatus]?.text}
                </Tag>
              </Descriptions.Item>
            </Descriptions>
            {currentRecord.fillData && (
              <div>
                <h4>填报数据</h4>
                {Object.entries(currentRecord.fillData).map(([key, value]) => (
                  <p key={key}><strong>{key}:</strong> {String(value)}</p>
                ))}
              </div>
            )}
            {currentRecord.auditHistory && currentRecord.auditHistory.length > 0 && (
              <div style={{ marginTop: 16 }}>
                <h4>审核历史</h4>
                {currentRecord.auditHistory.map((h, i) => (
                  <div key={i} style={{ marginBottom: 8 }}>
                    <Tag color={h.result === 'PASS' ? 'green' : 'red'}>
                      {h.result === 'PASS' ? '通过' : '驳回'}
                    </Tag>
                    {h.auditorName} - {h.auditTime}
                    {h.comment && <p style={{ marginTop: 4, color: '#666' }}>意见: {h.comment}</p>}
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </Modal>

      {/* 审核通过/驳回弹窗 */}
      <Modal
        title={auditAction === 'pass' ? '审核通过' : '审核驳回'}
        open={auditVisible}
        onOk={handleAuditOk}
        onCancel={() => setAuditVisible(false)}
        destroyOnClose
        okText={auditAction === 'pass' ? '通过' : '驳回'}
        okButtonProps={{ danger: auditAction === 'reject' }}
      >
        <Form form={auditForm} layout="vertical">
          <Form.Item name="comment" label="审核意见">
            <TextArea
              rows={4}
              placeholder={auditAction === 'pass' ? '通过意见(可选)' : '请填写驳回原因'}
              rules={auditAction === 'reject' ? [{ required: true, message: '请填写驳回原因' }] : []}
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default connect(({ audit }) => ({ audit }))(FillAudit);
