import React, { useState, useEffect } from 'react';
import { connect } from 'dva';
import {
  Table, Button, Input, Space, Modal, Form, Select, Tag,
  Descriptions, message, Upload, PageHeader, Row, Col, DatePicker,
} from 'antd';
import {
  SearchOutlined, ReloadOutlined, FormOutlined,
  EyeOutlined, UploadOutlined, SendOutlined,
} from '@ant-design/icons';
import { useSelector, useDispatch } from 'umi';

const { Option } = Select;
const { TextArea } = Input;

const FillTask = () => {
  const dispatch = useDispatch();
  const { list, total, loading } = useSelector((state) => state.fill);
  const [searchForm] = Form.useForm();
  const [fillForm] = Form.useForm();

  const [fillVisible, setFillVisible] = useState(false);
  const [detailVisible, setDetailVisible] = useState(false);
  const [currentTask, setCurrentTask] = useState(null);
  const [fieldSchema, setFieldSchema] = useState([]); // 动态字段
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10 });
  const [params, setParams] = useState({});

  useEffect(() => {
    fetchList();
  }, [pagination.current, pagination.pageSize, params]);

  const fetchList = () => {
    dispatch({
      type: 'fill/fetchList',
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

  const handleFill = async (record) => {
    setCurrentTask(record);
    const data = await dispatch({ type: 'fill/fetchDetail', payload: record.id });
    if (data && data.fieldSchema) {
      setFieldSchema(data.fieldSchema);
      // 预填已有数据
      const formValues = {};
      data.fillData && Object.keys(data.fillData).forEach((key) => {
        formValues[key] = data.fillData[key];
      });
      fillForm.setFieldsValue(formValues);
    } else {
      // 模拟字段 schema
      const mockSchema = [
        { fieldName: 'reportNum', fieldLabel: '报表编号', fieldType: 'VARCHAR', required: true },
        { fieldName: 'projectName', fieldLabel: '项目名称', fieldType: 'VARCHAR', required: true },
        { fieldName: 'amount', fieldLabel: '金额(万元)', fieldType: 'DECIMAL', required: true },
        { fieldName: 'count', fieldLabel: '数量', fieldType: 'INTEGER', required: false },
        { fieldName: 'reportDate', fieldLabel: '填报日期', fieldType: 'DATE', required: true },
        { fieldName: 'remark', fieldLabel: '备注', fieldType: 'TEXT', required: false },
      ];
      setFieldSchema(mockSchema);
      fillForm.resetFields();
    }
    setFillVisible(true);
  };

  const handleSubmitFill = async () => {
    const values = await fillForm.validateFields();
    const payload = { ...values };
    // 格式化日期字段
    fieldSchema.forEach((f) => {
      if (f.fieldType === 'DATE' && payload[f.fieldName] && payload[f.fieldName].format) {
        payload[f.fieldName] = payload[f.fieldName].format('YYYY-MM-DD');
      }
    });
    const success = await dispatch({
      type: 'fill/submit',
      payload: { id: currentTask.id, data: payload },
    });
    if (success) {
      setFillVisible(false);
      fetchList();
    }
  };

  const handleViewDetail = async (record) => {
    setCurrentTask(record);
    const detail = await dispatch({ type: 'fill/fetchDetail', payload: record.id });
    if (detail) setCurrentTask(detail.task || record);
    setDetailVisible(true);
  };

  const statusMap = {
    PENDING: { color: 'blue', text: '待填报' },
    IN_PROGRESS: { color: 'orange', text: '填报中' },
    SUBMITTED: { color: 'purple', text: '已提交' },
    COMPLETED: { color: 'green', text: '已完成' },
    OVERDUE: { color: 'red', text: '已逾期' },
  };

  const columns = [
    { title: '任务名称', dataIndex: 'taskName', width: 180 },
    { title: '模板', dataIndex: 'templateName', width: 160 },
    { title: '部门', dataIndex: 'deptName', width: 130 },
    { title: '所属期', dataIndex: 'belongTime', width: 110 },
    { title: '填报截止', dataIndex: 'deadline', width: 170 },
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
      width: 180,
      fixed: 'right',
      render: (_, record) => (
        <Space size="small">
          {(record.status === 'PENDING' || record.status === 'IN_PROGRESS') && (
            <Button
              size="small"
              type="primary"
              icon={<FormOutlined />}
              onClick={() => handleFill(record)}
            >
              填报
            </Button>
          )}
          <Button
            size="small"
            icon={<EyeOutlined />}
            onClick={() => handleViewDetail(record)}
          >
            详情
          </Button>
        </Space>
      ),
    },
  ];

  const renderField = (field) => {
    const commonProps = {
      key: field.fieldName,
      name: field.fieldName,
      label: field.fieldLabel,
      rules: field.required
        ? [{ required: true, message: `请输入${field.fieldLabel}` }]
        : [],
    };

    switch (field.fieldType) {
      case 'DATE':
        return (
          <Col span={12} key={field.fieldName}>
            <Form.Item {...commonProps}>
              <DatePicker style={{ width: '100%' }} placeholder={`请选择${field.fieldLabel}`} />
            </Form.Item>
          </Col>
        );
      case 'TEXT':
        return (
          <Col span={24} key={field.fieldName}>
            <Form.Item {...commonProps}>
              <TextArea rows={3} placeholder={`请输入${field.fieldLabel}`} />
            </Form.Item>
          </Col>
        );
      default:
        return (
          <Col span={12} key={field.fieldName}>
            <Form.Item {...commonProps}>
              <Input placeholder={`请输入${field.fieldLabel}`} />
            </Form.Item>
          </Col>
        );
    }
  };

  return (
    <div>
      <PageHeader title="数据填报" style={{ padding: 0, marginBottom: 16 }} />
      <div style={{ background: '#fff', padding: 16, borderRadius: 4, border: '1px solid #f0f0f0' }}>
        <Form
          form={searchForm}
          layout="inline"
          onFinish={handleSearch}
          style={{ marginBottom: 16 }}
        >
          <Form.Item name="taskName">
            <Input placeholder="任务名称" style={{ width: 160 }} />
          </Form.Item>
          <Form.Item name="templateName">
            <Input placeholder="模板名称" style={{ width: 160 }} />
          </Form.Item>
          <Form.Item name="status">
            <Select placeholder="状态" allowClear style={{ width: 120 }}>
              <Option value="PENDING">待填报</Option>
              <Option value="IN_PROGRESS">填报中</Option>
              <Option value="SUBMITTED">已提交</Option>
              <Option value="COMPLETED">已完成</Option>
              <Option value="OVERDUE">已逾期</Option>
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
      </div>

      {/* 填报弹窗 */}
      <Modal
        title={`数据填报 - ${currentTask?.taskName || ''}`}
        open={fillVisible}
        onOk={handleSubmitFill}
        onCancel={() => setFillVisible(false)}
        width={700}
        destroyOnClose
        okText="提交"
      >
        <div style={{ marginBottom: 16, padding: 12, background: '#fafafa', borderRadius: 4 }}>
          模板: {currentTask?.templateName} | 截止: {currentTask?.deadline}
        </div>
        <Form form={fillForm} layout="vertical">
          <Row gutter={16}>
            {fieldSchema.map(renderField)}
          </Row>
        </Form>
      </Modal>

      {/* 详情弹窗 */}
      <Modal
        title={`任务详情 - ${currentTask?.taskName || ''}`}
        open={detailVisible}
        onCancel={() => setDetailVisible(false)}
        footer={<Button onClick={() => setDetailVisible(false)}>关闭</Button>}
        width={600}
      >
        {currentTask && (
          <Descriptions bordered column={1} size="small">
            <Descriptions.Item label="任务名称">{currentTask.taskName}</Descriptions.Item>
            <Descriptions.Item label="模板">{currentTask.templateName}</Descriptions.Item>
            <Descriptions.Item label="部门">{currentTask.deptName}</Descriptions.Item>
            <Descriptions.Item label="所属期">{currentTask.belongTime}</Descriptions.Item>
            <Descriptions.Item label="填报截止">{currentTask.deadline}</Descriptions.Item>
            <Descriptions.Item label="状态">
              <Tag color={statusMap[currentTask.status]?.color}>
                {statusMap[currentTask.status]?.text}
              </Tag>
            </Descriptions.Item>
          </Descriptions>
        )}
      </Modal>
    </div>
  );
};

export default connect(({ fill }) => ({ fill }))(FillTask);
