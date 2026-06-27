import React, { useEffect, useState, useRef } from 'react';
import {
  Table, Button, Input, Select, Space, Modal, Form, Tag, Popconfirm, Switch,
  Divider, Row, Col,
} from 'antd';
import { PlusOutlined, SearchOutlined, ReloadOutlined, LockOutlined, SafetyCertificateOutlined } from '@ant-design/icons';
import { useSelector, useDispatch } from 'umi';

const { Option } = Select;

const UserManagement = () => {
  const dispatch = useDispatch();
  const { list, total, loading } = useSelector((state) => state.user);
  const [form] = Form.useForm();
  const [roleForm] = Form.useForm();
  const [passwordForm] = Form.useForm();
  const [searchForm] = Form.useForm();

  const [modalVisible, setModalVisible] = useState(false);
  const [roleModalVisible, setRoleModalVisible] = useState(false);
  const [passwordModalVisible, setPasswordModalVisible] = useState(false);
  const [editingRecord, setEditingRecord] = useState(null);
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10 });
  const [params, setParams] = useState({});

  const mountedRef = useRef(false);

  useEffect(() => {
    mountedRef.current = true;
    fetchList();
    return () => { mountedRef.current = false; };
  }, [pagination.current, pagination.pageSize, params]);

  const fetchList = () => {
    dispatch({
      type: 'user/fetchList',
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
    setEditingRecord(null);
    form.resetFields();
    setModalVisible(true);
  };

  const handleEdit = (record) => {
    setEditingRecord(record);
    form.setFieldsValue({
      username: record.username,
      realName: record.realName,
      email: record.email,
      phone: record.phone,
      deptId: record.deptId,
      status: record.status,
    });
    setModalVisible(true);
  };

  const handleDelete = (id) => {
    dispatch({ type: 'user/delete', payload: id }).then((success) => {
      if (success) fetchList();
    });
  };

  const handleModalOk = async () => {
    const values = await form.validateFields();
    let success;
    if (editingRecord) {
      success = await dispatch({
        type: 'user/update',
        payload: { ...values, id: editingRecord.id },
      });
    } else {
      success = await dispatch({ type: 'user/add', payload: values });
    }
    if (success) {
      setModalVisible(false);
      fetchList();
    }
  };

  const handleStatusChange = (id, checked) => {
    dispatch({
      type: 'user/changeStatus',
      payload: { id, status: checked ? 1 : 0 },
    }).then((success) => {
      if (success) fetchList();
    });
  };

  const handleAssignRoles = (record) => {
    setEditingRecord(record);
    roleForm.resetFields();
    if (record.roleIds) {
      roleForm.setFieldsValue({ roleIds: record.roleIds });
    }
    setRoleModalVisible(true);
  };

  const handleRoleModalOk = async () => {
    const values = await roleForm.validateFields();
    const success = await dispatch({
      type: 'user/assignRoles',
      payload: { userId: editingRecord.id, roleIds: values.roleIds },
    });
    if (success) {
      setRoleModalVisible(false);
      fetchList();
    }
  };

  const handleResetPassword = (record) => {
    setEditingRecord(record);
    passwordForm.resetFields();
    setPasswordModalVisible(true);
  };

  const handlePasswordOk = async () => {
    const values = await passwordForm.validateFields();
    const success = await dispatch({
      type: 'user/resetPassword',
      payload: { id: editingRecord.id, password: values.password },
    });
    if (success) {
      setPasswordModalVisible(false);
    }
  };

  const columns = [
    { title: '用户名', dataIndex: 'username', width: 120 },
    { title: '姓名', dataIndex: 'realName', width: 100 },
    { title: '邮箱', dataIndex: 'email', width: 180 },
    { title: '手机号', dataIndex: 'phone', width: 130 },
    { title: '部门', dataIndex: 'deptName', width: 120 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 80,
      render: (status, record) => (
        <Switch
          checked={status === 1}
          onChange={(checked) => handleStatusChange(record.id, checked)}
          checkedChildren="启用"
          unCheckedChildren="禁用"
        />
      ),
    },
    { title: '创建时间', dataIndex: 'createTime', width: 170 },
    {
      title: '操作',
      width: 260,
      fixed: 'right',
      render: (_, record) => (
        <Space size="small">
          <Button size="small" onClick={() => handleEdit(record)}>编辑</Button>
          <Button size="small" onClick={() => handleAssignRoles(record)}>角色</Button>
          <Button size="small" icon={<LockOutlined />} onClick={() => handleResetPassword(record)}>
            重置密码
          </Button>
          <Popconfirm title="确定删除?" onConfirm={() => handleDelete(record.id)}>
            <Button size="small" danger>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <Form form={searchForm} layout="inline" onFinish={handleSearch} style={{ marginBottom: 16 }}>
        <Form.Item name="username">
          <Input placeholder="用户名" style={{ width: 150 }} />
        </Form.Item>
        <Form.Item name="realName">
          <Input placeholder="姓名" style={{ width: 150 }} />
        </Form.Item>
        <Form.Item name="status">
          <Select placeholder="状态" allowClear style={{ width: 120 }}>
            <Option value={1}>启用</Option>
            <Option value={0}>禁用</Option>
          </Select>
        </Form.Item>
        <Form.Item>
          <Space>
            <Button type="primary" htmlType="submit" icon={<SearchOutlined />}>搜索</Button>
            <Button onClick={handleReset} icon={<ReloadOutlined />}>重置</Button>
          </Space>
        </Form.Item>
      </Form>

      <div style={{ marginBottom: 16 }}>
        <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>新增用户</Button>
      </div>

      <Table
        rowKey="id"
        columns={columns}
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
          onChange: (page, pageSize) => setPagination({ current: page, pageSize }),
        }}
      />

      <Modal
        title={editingRecord ? '编辑用户' : '新增用户'}
        open={modalVisible}
        onOk={handleModalOk}
        onCancel={() => setModalVisible(false)}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item name="username" label="用户名" rules={[{ required: true, message: '请输入用户名' }]}>
            <Input disabled={!!editingRecord} />
          </Form.Item>
          {!editingRecord && (
            <Form.Item name="password" label="密码" rules={[{ required: true, min: 6, message: '请输入至少6位密码' }]}>
              <Input.Password />
            </Form.Item>
          )}
          <Form.Item name="realName" label="姓名" rules={[{ required: true, message: '请输入姓名' }]}>
            <Input />
          </Form.Item>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="email" label="邮箱" rules={[{ type: 'email', message: '请输入有效邮箱' }]}>
                <Input />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="phone" label="手机号">
                <Input />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="deptId" label="所属部门">
            <Select placeholder="请选择部门" allowClear>
              <Option value={1}>技术部</Option>
              <Option value={2}>产品部</Option>
              <Option value={3}>运营部</Option>
              <Option value={4}>市场部</Option>
            </Select>
          </Form.Item>
          <Form.Item name="status" label="状态" initialValue={1}>
            <Select>
              <Option value={1}>启用</Option>
              <Option value={0}>禁用</Option>
            </Select>
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="分配角色"
        open={roleModalVisible}
        onOk={handleRoleModalOk}
        onCancel={() => setRoleModalVisible(false)}
        destroyOnClose
      >
        <Form form={roleForm} layout="vertical">
          <Form.Item name="roleIds" label="角色" rules={[{ required: true, message: '请选择角色' }]}>
            <Select mode="multiple" placeholder="请选择角色">
              <Option value={1}>超级管理员</Option>
              <Option value={2}>普通管理员</Option>
              <Option value={3}>填报人员</Option>
              <Option value={4}>审核人员</Option>
              <Option value={5}>查看人员</Option>
            </Select>
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="重置密码"
        open={passwordModalVisible}
        onOk={handlePasswordOk}
        onCancel={() => setPasswordModalVisible(false)}
        destroyOnClose
      >
        <Form form={passwordForm} layout="vertical">
          <Form.Item name="password" label="新密码" rules={[{ required: true, min: 6, message: '请输入至少6位新密码' }]}>
            <Input.Password />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default UserManagement;
