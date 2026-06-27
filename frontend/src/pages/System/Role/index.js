import React, { useEffect, useState } from 'react';
import {
  Table, Button, Input, Space, Modal, Form, Tag, Popconfirm, Tree, Spin, message,
} from 'antd';
import { PlusOutlined, SearchOutlined, ReloadOutlined, SafetyCertificateOutlined } from '@ant-design/icons';
import { useSelector, useDispatch } from 'umi';

const RoleManagement = () => {
  const dispatch = useDispatch();
  const { list, total, loading } = useSelector((state) => state.role);
  const menuTreeData = useSelector((state) => state.menu.treeData);
  const [form] = Form.useForm();
  const [searchForm] = Form.useForm();
  const [modalVisible, setModalVisible] = useState(false);
  const [permModalVisible, setPermModalVisible] = useState(false);
  const [editingRecord, setEditingRecord] = useState(null);
  const [checkedMenuKeys, setCheckedMenuKeys] = useState([]);
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10 });
  const [params, setParams] = useState({});

  useEffect(() => {
    fetchList();
    dispatch({ type: 'menu/fetchTree' });
  }, [pagination.current, pagination.pageSize, params]);

  const fetchList = () => {
    dispatch({
      type: 'role/fetchList',
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
      name: record.name,
      code: record.code,
      description: record.description,
      sort: record.sort,
      status: record.status,
    });
    setModalVisible(true);
  };

  const handleDelete = (id) => {
    dispatch({ type: 'role/delete', payload: id }).then((success) => {
      if (success) fetchList();
    });
  };

  const handleModalOk = async () => {
    const values = await form.validateFields();
    let success;
    if (editingRecord) {
      success = await dispatch({ type: 'role/update', payload: { ...values, id: editingRecord.id } });
    } else {
      success = await dispatch({ type: 'role/add', payload: values });
    }
    if (success) {
      setModalVisible(false);
      fetchList();
    }
  };

  const handleAssignPerms = async (record) => {
    setEditingRecord(record);
    setPermModalVisible(true);
    const menuIds = await dispatch({ type: 'role/fetchRoleMenus', payload: record.id });
    setCheckedMenuKeys(menuIds || []);
  };

  const handlePermModalOk = async () => {
    const success = await dispatch({
      type: 'role/assignMenus',
      payload: { roleId: editingRecord.id, menuIds: checkedMenuKeys },
    });
    if (success) {
      setPermModalVisible(false);
      fetchList();
    }
  };

  const formatMenuTree = (menus) => {
    if (!menus || !Array.isArray(menus)) return [];
    return menus
      .filter((m) => m.type !== 2) // 过滤按钮
      .map((m) => ({
        key: m.id,
        title: m.name || m.title,
        children: m.children ? formatMenuTree(m.children) : [],
      }));
  };

  const columns = [
    { title: '角色名称', dataIndex: 'name', width: 150 },
    { title: '角色编码', dataIndex: 'code', width: 130 },
    { title: '描述', dataIndex: 'description', width: 200, ellipsis: true },
    { title: '排序', dataIndex: 'sort', width: 80 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 80,
      render: (status) => (
        <Tag color={status === 1 ? 'green' : 'red'}>
          {status === 1 ? '启用' : '禁用'}
        </Tag>
      ),
    },
    { title: '创建时间', dataIndex: 'createTime', width: 170 },
    {
      title: '操作',
      width: 220,
      fixed: 'right',
      render: (_, record) => (
        <Space size="small">
          <Button size="small" onClick={() => handleEdit(record)}>编辑</Button>
          <Button size="small" icon={<SafetyCertificateOutlined />} onClick={() => handleAssignPerms(record)}>
            权限
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
        <Form.Item name="name">
          <Input placeholder="角色名称" style={{ width: 150 }} />
        </Form.Item>
        <Form.Item name="code">
          <Input placeholder="角色编码" style={{ width: 150 }} />
        </Form.Item>
        <Form.Item>
          <Space>
            <Button type="primary" htmlType="submit" icon={<SearchOutlined />}>搜索</Button>
            <Button onClick={handleReset} icon={<ReloadOutlined />}>重置</Button>
          </Space>
        </Form.Item>
      </Form>

      <div style={{ marginBottom: 16 }}>
        <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>新增角色</Button>
      </div>

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
          onChange: (page, pageSize) => setPagination({ current: page, pageSize }),
        }}
      />

      <Modal
        title={editingRecord ? '编辑角色' : '新增角色'}
        open={modalVisible}
        onOk={handleModalOk}
        onCancel={() => setModalVisible(false)}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="角色名称" rules={[{ required: true, message: '请输入角色名称' }]}>
            <Input placeholder="请输入角色名称" />
          </Form.Item>
          <Form.Item name="code" label="角色编码" rules={[{ required: true, message: '请输入角色编码' }]}>
            <Input placeholder="请输入角色编码" disabled={!!editingRecord} />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={3} placeholder="请输入描述" />
          </Form.Item>
          <Form.Item name="sort" label="排序" initialValue={0}>
            <Input type="number" />
          </Form.Item>
          <Form.Item name="status" label="状态" initialValue={1}>
            <Input type="hidden" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={`分配权限 - ${editingRecord?.name || ''}`}
        open={permModalVisible}
        onOk={handlePermModalOk}
        onCancel={() => setPermModalVisible(false)}
        width={500}
        destroyOnClose
      >
        <div style={{ maxHeight: 400, overflow: 'auto' }}>
          {menuTreeData.length > 0 ? (
            <Tree
              checkable
              defaultExpandAll
              checkedKeys={checkedMenuKeys}
              onCheck={(checkedKeys) => setCheckedMenuKeys(checkedKeys)}
              treeData={formatMenuTree(menuTreeData)}
            />
          ) : (
            <div style={{ textAlign: 'center', padding: 20 }}>
              <Spin />
            </div>
          )}
        </div>
      </Modal>
    </div>
  );
};

export default RoleManagement;
