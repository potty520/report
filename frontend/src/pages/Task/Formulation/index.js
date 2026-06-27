import React, { useState, useEffect } from 'react';
import { connect } from 'dva';
import {
  Table, Button, Input, Space, Modal, Form, Select, DatePicker, Popconfirm,
  Tag, PageHeader, message,
} from 'antd';
import {
  PlusOutlined, SearchOutlined, ReloadOutlined,
  SendOutlined, StopOutlined, DeleteOutlined,
} from '@ant-design/icons';
import { useSelector, useDispatch } from 'umi';

const { Option } = Select;
const { TextArea } = Input;

const TaskFormulation = () => {
  const dispatch = useDispatch();
  const { list, total, loading } = useSelector((state) => state.task);
  const deptTree = useSelector((state) => state.dept.treeData);
  const [form] = Form.useForm();
  const [searchForm] = Form.useForm();

  const [modalVisible, setModalVisible] = useState(false);
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10 });
  const [params, setParams] = useState({});

  useEffect(() => {
    dispatch({ type: 'dept/fetchTree' });
    dispatch({ type: 'template/fetchList', payload: { page: 1, size: 1000, status: 1 } });
    dispatch({ type: 'project/fetchList', payload: { page: 1, size: 1000 } });
  }, []);

  useEffect(() => {
    fetchList();
  }, [pagination.current, pagination.pageSize, params]);

  const templateList = useSelector((state) => state.template.list) || [];
  const projectList = useSelector((state) => state.project.list) || [];

  const fetchList = () => {
    dispatch({
      type: 'task/fetchList',
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

  const handleAdd = () => {
    form.resetFields();
    setModalVisible(true);
  };

  const handleModalOk = async () => {
    const values = await form.validateFields();
    const payload = {
      ...values,
      belongTime: values.belongTime ? values.belongTime.format('YYYY-MM-DD HH:mm:ss') : undefined,
      fillDeadline: values.fillDeadline ? values.fillDeadline.format('YYYY-MM-DD HH:mm:ss') : undefined,
      auditDeadline: values.auditDeadline ? values.auditDeadline.format('YYYY-MM-DD HH:mm:ss') : undefined,
    };
    const success = await dispatch({ type: 'task/add', payload });
    if (success) {
      setModalVisible(false);
      fetchList();
    }
  };

  const handlePublish = (record) => {
    dispatch({
      type: 'task/update',
      payload: { id: record.id, status: 'PUBLISHED' },
    }).then((success) => {
      if (success) {
        message.success('任务已发布');
        fetchList();
      }
    });
  };

  const handleClose = (record) => {
    dispatch({
      type: 'task/update',
      payload: { id: record.id, status: 'CLOSED' },
    }).then((success) => {
      if (success) {
        message.success('任务已关闭');
        fetchList();
      }
    });
  };

  const handleDelete = (id) => {
    dispatch({ type: 'task/delete', payload: id }).then((success) => {
      if (success) fetchList();
    });
  };

  const statusMap = {
    DRAFT: { color: 'default', text: '草稿' },
    PUBLISHED: { color: 'green', text: '已发布' },
    CLOSED: { color: 'red', text: '已关闭' },
    IN_PROGRESS: { color: 'blue', text: '进行中' },
    FINISHED: { color: 'purple', text: '已完成' },
  };

  const columns = [
    { title: '任务名称', dataIndex: 'taskName', width: 180 },
    { title: '模板', dataIndex: 'templateName', width: 160 },
    { title: '部门', dataIndex: 'deptName', width: 130 },
    { title: '项目', dataIndex: 'projectName', width: 140 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v) => {
        const s = statusMap[v] || statusMap.DRAFT;
        return <Tag color={s.color}>{s.text}</Tag>;
      },
    },
    { title: '所属期', dataIndex: 'belongTime', width: 120 },
    { title: '填报截止', dataIndex: 'fillDeadline', width: 120 },
    { title: '审核截止', dataIndex: 'auditDeadline', width: 120 },
    { title: '创建时间', dataIndex: 'createTime', width: 170 },
    {
      title: '操作',
      width: 220,
      fixed: 'right',
      render: (_, record) => (
        <Space size="small">
          {(record.status === 'DRAFT' || !record.status) && (
            <Button
              size="small"
              type="primary"
              icon={<SendOutlined />}
              onClick={() => handlePublish(record)}
            >
              发布
            </Button>
          )}
          {record.status === 'PUBLISHED' && (
            <Button
              size="small"
              icon={<StopOutlined />}
              onClick={() => handleClose(record)}
            >
              关闭
            </Button>
          )}
          {record.status === 'CLOSED' && (
            <Popconfirm title="确定删除?" onConfirm={() => handleDelete(record.id)}>
              <Button size="small" danger icon={<DeleteOutlined />}>
                删除
              </Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  return (
    <div>
      <PageHeader title="填报任务制定" style={{ padding: 0, marginBottom: 16 }} />
      <div style={{ background: '#fff', padding: 16, borderRadius: 4, border: '1px solid #f0f0f0' }}>
        <Form
          form={searchForm}
          layout="inline"
          onFinish={handleSearch}
          style={{ marginBottom: 16 }}
        >
          <Form.Item name="templateId">
            <Select placeholder="选择模板" allowClear style={{ width: 160 }}>
              {templateList.map((t) => (
                <Option key={t.id} value={t.id}>{t.templateName}</Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item name="deptId">
            <Select placeholder="选择部门" allowClear style={{ width: 160 }}>
              {deptTree.map((d) => (
                <Option key={d.id} value={d.id}>{d.deptName}</Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item name="status">
            <Select placeholder="状态" allowClear style={{ width: 120 }}>
              <Option value="DRAFT">草稿</Option>
              <Option value="PUBLISHED">已发布</Option>
              <Option value="CLOSED">已关闭</Option>
              <Option value="IN_PROGRESS">进行中</Option>
              <Option value="FINISHED">已完成</Option>
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
            新增任务
          </Button>
        </div>

        <Table
          rowKey="id"
          columns={columns}
          dataSource={list}
          loading={loading}
          scroll={{ x: 1400 }}
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

      <Modal
        title="新增任务"
        open={modalVisible}
        onOk={handleModalOk}
        onCancel={() => setModalVisible(false)}
        destroyOnClose
        width={600}
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="taskName"
            label="任务名称"
            rules={[{ required: true, message: '请输入任务名称' }]}
          >
            <Input placeholder="请输入任务名称" />
          </Form.Item>
          <Form.Item
            name="templateId"
            label="关联模板"
            rules={[{ required: true, message: '请选择模板' }]}
          >
            <Select placeholder="请选择模板" showSearch optionFilterProp="children">
              {templateList.map((t) => (
                <Option key={t.id} value={t.id}>{t.templateName}</Option>
              ))}
            </Select>
          </Form.Item>
          <div style={{ display: 'flex', gap: 16 }}>
            <Form.Item
              name="deptId"
              label="部门"
              style={{ flex: 1 }}
              rules={[{ required: true, message: '请选择部门' }]}
            >
              <Select placeholder="请选择部门">
                {deptTree.map((d) => (
                  <Option key={d.id} value={d.id}>{d.deptName}</Option>
                ))}
              </Select>
            </Form.Item>
            <Form.Item
              name="projectId"
              label="项目"
              style={{ flex: 1 }}
            >
              <Select placeholder="请选择项目" allowClear>
                {projectList.map((p) => (
                  <Option key={p.id} value={p.id}>{p.projectName}</Option>
                ))}
              </Select>
            </Form.Item>
          </div>
          <Form.Item
            name="belongTime"
            label="数据所属期"
            rules={[{ required: true, message: '请选择所属期' }]}
          >
            <DatePicker style={{ width: '100%' }} placeholder="请选择数据所属期" />
          </Form.Item>
          <div style={{ display: 'flex', gap: 16 }}>
            <Form.Item
              name="fillDeadline"
              label="填报截止时间"
              style={{ flex: 1 }}
              rules={[{ required: true, message: '请选择填报截止时间' }]}
            >
              <DatePicker showTime style={{ width: '100%' }} placeholder="填报截止" />
            </Form.Item>
            <Form.Item
              name="auditDeadline"
              label="审核截止时间"
              style={{ flex: 1 }}
              rules={[{ required: true, message: '请选择审核截止时间' }]}
            >
              <DatePicker showTime style={{ width: '100%' }} placeholder="审核截止" />
            </Form.Item>
          </div>
        </Form>
      </Modal>
    </div>
  );
};

export default connect(({ task, dept, template, project }) => ({
  task,
  dept,
  template,
  project,
}))(TaskFormulation);
