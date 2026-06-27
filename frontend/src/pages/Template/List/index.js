import React, { useState, useEffect } from 'react';
import { connect } from 'dva';
import {
  Table, Button, Input, Space, Modal, Form, Select, Tag, Popconfirm,
  PageHeader,
} from 'antd';
import {
  PlusOutlined, SearchOutlined, ReloadOutlined, EyeOutlined,
  CheckOutlined, CloseOutlined, SwapOutlined,
} from '@ant-design/icons';
import { useSelector, useDispatch, history } from 'umi';

const { Option } = Select;
const { TextArea } = Input;

const TemplateList = () => {
  const dispatch = useDispatch();
  const { list, total, loading } = useSelector((state) => state.template);
  const [searchForm] = Form.useForm();
  const [auditForm] = Form.useForm();

  const [auditVisible, setAuditVisible] = useState(false);
  const [auditRecord, setAuditRecord] = useState(null);
  const [auditType, setAuditType] = useState(null); // 'pass' | 'reject'
  const [detailVisible, setDetailVisible] = useState(false);
  const [currentRecord, setCurrentRecord] = useState(null);
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10 });
  const [params, setParams] = useState({});

  useEffect(() => {
    fetchList();
  }, [pagination.current, pagination.pageSize, params]);

  const fetchList = () => {
    dispatch({
      type: 'template/fetchList',
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
    const data = await dispatch({ type: 'template/fetchDetail', payload: record.id });
    if (data) {
      setCurrentRecord(data);
    }
    setDetailVisible(true);
  };

  const handleAudit = (record, type) => {
    setAuditRecord(record);
    setAuditType(type);
    auditForm.resetFields();
    setAuditVisible(true);
  };

  const handleAuditOk = async () => {
    const values = await auditForm.validateFields();
    const success = await dispatch({
      type: 'template/audit',
      payload: {
        id: auditRecord.id,
        result: auditType === 'pass' ? 'PASS' : 'REJECT',
        comment: values.comment || '',
      },
    });
    if (success) {
      setAuditVisible(false);
      fetchList();
    }
  };

  const handleToggleStatus = (record) => {
    dispatch({
      type: 'template/update',
      payload: { ...record, status: record.status === 1 ? 0 : 1 },
    }).then((success) => {
      if (success) fetchList();
    });
  };

  const handleDelete = (id) => {
    dispatch({ type: 'template/delete', payload: id }).then((success) => {
      if (success) fetchList();
    });
  };

  const handleAdd = () => {
    history.push('/template/add');
  };

  const statusMap = {
    0: { color: 'default', text: '停用' },
    1: { color: 'green', text: '启用' },
    2: { color: 'orange', text: '草稿' },
  };

  const auditStatusMap = {
    PENDING: { color: 'blue', text: '待审核' },
    PASS: { color: 'green', text: '已通过' },
    REJECT: { color: 'red', text: '已驳回' },
    NOT_SUBMIT: { color: 'default', text: '未提交' },
  };

  const fillingCycleMap = {
    DAILY: '日',
    WEEKLY: '周',
    MONTHLY: '月',
    QUARTERLY: '季',
    YEARLY: '年',
  };

  const columns = [
    { title: '模板名称', dataIndex: 'templateName', width: 160 },
    { title: '模板编码', dataIndex: 'templateCode', width: 140 },
    {
      title: '填报周期',
      dataIndex: 'fillingCycle',
      width: 90,
      render: (v) => fillingCycleMap[v] || v,
    },
    { title: '所属部门', dataIndex: 'deptName', width: 130 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 80,
      render: (status) => {
        const s = statusMap[status] || statusMap[0];
        return <Tag color={s.color}>{s.text}</Tag>;
      },
    },
    {
      title: '审核状态',
      dataIndex: 'auditStatus',
      width: 100,
      render: (v) => {
        const a = auditStatusMap[v] || auditStatusMap.NOT_SUBMIT;
        return <Tag color={a.color}>{a.text}</Tag>;
      },
    },
    { title: '创建时间', dataIndex: 'createTime', width: 170 },
    {
      title: '操作',
      width: 280,
      fixed: 'right',
      render: (_, record) => (
        <Space size="small">
          <Button size="small" icon={<EyeOutlined />} onClick={() => handleViewDetail(record)}>
            详情
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
          <Button size="small" icon={<SwapOutlined />} onClick={() => handleToggleStatus(record)}>
            {record.status === 1 ? '停用' : '启用'}
          </Button>
          <Popconfirm title="确定删除该模板吗?" onConfirm={() => handleDelete(record.id)}>
            <Button size="small" danger>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <PageHeader title="模板列表" style={{ padding: 0, marginBottom: 16 }} />

      <div style={{ background: '#fff', padding: 16, borderRadius: 4, border: '1px solid #f0f0f0' }}>
        <Form
          form={searchForm}
          layout="inline"
          onFinish={handleSearch}
          style={{ marginBottom: 16 }}
        >
          <Form.Item name="templateName">
            <Input placeholder="模板名称" style={{ width: 150 }} />
          </Form.Item>
          <Form.Item name="fillingCycle">
            <Select placeholder="填报周期" allowClear style={{ width: 130 }}>
              <Option value="DAILY">日</Option>
              <Option value="WEEKLY">周</Option>
              <Option value="MONTHLY">月</Option>
              <Option value="QUARTERLY">季</Option>
              <Option value="YEARLY">年</Option>
            </Select>
          </Form.Item>
          <Form.Item name="status">
            <Select placeholder="状态" allowClear style={{ width: 110 }}>
              <Option value={1}>启用</Option>
              <Option value={0}>停用</Option>
              <Option value={2}>草稿</Option>
            </Select>
          </Form.Item>
          <Form.Item name="auditStatus">
            <Select placeholder="审核状态" allowClear style={{ width: 130 }}>
              <Option value="PENDING">待审核</Option>
              <Option value="PASS">已通过</Option>
              <Option value="REJECT">已驳回</Option>
              <Option value="NOT_SUBMIT">未提交</Option>
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

        <div style={{ marginBottom: 16 }}>
          <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>
            新建模板
          </Button>
        </div>

        <Table
          rowKey="id"
          columns={columns}
          dataSource={list}
          loading={loading}
          scroll={{ x: 1300 }}
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

      {/* 审核弹窗 */}
      <Modal
        title={auditType === 'pass' ? '审核通过' : '审核驳回'}
        open={auditVisible}
        onOk={handleAuditOk}
        onCancel={() => setAuditVisible(false)}
        destroyOnClose
        okText={auditType === 'pass' ? '通过' : '驳回'}
        okButtonProps={{ danger: auditType === 'reject' }}
      >
        <Form form={auditForm} layout="vertical">
          <Form.Item name="comment" label="审核意见">
            <TextArea
              rows={4}
              placeholder={auditType === 'pass' ? '请输入通过意见(可选)' : '请输入驳回原因'}
            />
          </Form.Item>
        </Form>
      </Modal>

      {/* 详情弹窗 */}
      <Modal
        title={`模板详情 - ${currentRecord?.templateName || ''}`}
        open={detailVisible}
        onCancel={() => setDetailVisible(false)}
        footer={<Button onClick={() => setDetailVisible(false)}>关闭</Button>}
        width={700}
      >
        {currentRecord && (
          <div>
            <p><strong>模板名称：</strong>{currentRecord.templateName}</p>
            <p><strong>模板编码：</strong>{currentRecord.templateCode}</p>
            <p><strong>填报周期：</strong>{fillingCycleMap[currentRecord.fillingCycle] || currentRecord.fillingCycle}</p>
            <p><strong>所属部门：</strong>{currentRecord.deptName}</p>
            <p><strong>状态：</strong>
              <Tag color={statusMap[currentRecord.status]?.color}>
                {statusMap[currentRecord.status]?.text}
              </Tag>
            </p>
            <p><strong>审核状态：</strong>
              <Tag color={auditStatusMap[currentRecord.auditStatus]?.color}>
                {auditStatusMap[currentRecord.auditStatus]?.text}
              </Tag>
            </p>
            <p><strong>描述：</strong>{currentRecord.description || '无'}</p>
            <p><strong>创建时间：</strong>{currentRecord.createTime}</p>
          </div>
        )}
      </Modal>
    </div>
  );
};

export default connect(({ template }) => ({ template }))(TemplateList);
