import React, { useEffect, useState } from 'react';
import {
  Table, Button, Space, Modal, Form, Input, Select, InputNumber, Popconfirm, Tag,
} from 'antd';
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import { useSelector, useDispatch } from 'umi';
import IconSelector from '@/components/IconSelector';

const { Option } = Select;

const MenuManagement = () => {
  const dispatch = useDispatch();
  const { treeData, loading } = useSelector((state) => state.menu);
  const [form] = Form.useForm();
  const [modalVisible, setModalVisible] = useState(false);
  const [editingRecord, setEditingRecord] = useState(null);
  const [parentId, setParentId] = useState(0);
  const [expandedRowKeys, setExpandedRowKeys] = useState([]);

  useEffect(() => {
    dispatch({ type: 'menu/fetchTree' });
  }, []);

  const getFlatList = (tree, level = 0) => {
    const result = [];
    tree.forEach((item) => {
      result.push({ ...item, level, key: item.id });
      if (item.children && item.children.length > 0) {
        result.push(...getFlatList(item.children, level + 1));
      }
    });
    return result;
  };

  const flatData = getFlatList(treeData);

  const handleAdd = (pid) => {
    setEditingRecord(null);
    setParentId(pid || 0);
    form.resetFields();
    form.setFieldsValue({ parentId: pid || 0, type: 0, hidden: 0, sort: 0 });
    setModalVisible(true);
  };

  const handleEdit = (record) => {
    setEditingRecord(record);
    setParentId(record.parentId || 0);
    form.setFieldsValue({
      parentId: record.parentId,
      name: record.name,
      type: record.type,
      path: record.path,
      icon: record.icon,
      component: record.component,
      sort: record.sort,
      perms: record.perms,
      hidden: record.hidden,
    });
    setModalVisible(true);
  };

  const handleDelete = (id) => {
    dispatch({ type: 'menu/delete', payload: id }).then((success) => {
      if (success) dispatch({ type: 'menu/fetchTree' });
    });
  };

  const handleModalOk = async () => {
    const values = await form.validateFields();
    let success;
    if (editingRecord) {
      success = await dispatch({ type: 'menu/update', payload: { ...values, id: editingRecord.id } });
    } else {
      success = await dispatch({ type: 'menu/add', payload: values });
    }
    if (success) {
      setModalVisible(false);
      dispatch({ type: 'menu/fetchTree' });
    }
  };

  const columns = [
    {
      title: '菜单名称',
      dataIndex: 'name',
      width: 200,
      render: (text, record) => (
        <span style={{ paddingLeft: record.level * 20 }}>
          {record.icon && <span style={{ marginRight: 8 }}>{record.icon}</span>}
          {text}
        </span>
      ),
    },
    {
      title: '类型',
      dataIndex: 'type',
      width: 80,
      render: (type) => {
        const map = { 0: '目录', 1: '菜单', 2: '按钮' };
        const color = { 0: 'blue', 1: 'green', 2: 'orange' };
        return <Tag color={color[type]}>{map[type]}</Tag>;
      },
    },
    { title: '路径', dataIndex: 'path', width: 150 },
    { title: '组件', dataIndex: 'component', width: 180, ellipsis: true },
    { title: '权限标识', dataIndex: 'perms', width: 150, ellipsis: true },
    { title: '排序', dataIndex: 'sort', width: 70 },
    {
      title: '可见',
      dataIndex: 'hidden',
      width: 70,
      render: (hidden) => <Tag color={hidden === 0 ? 'green' : 'red'}>{hidden === 0 ? '是' : '否'}</Tag>,
    },
    { title: '创建时间', dataIndex: 'createTime', width: 170 },
    {
      title: '操作',
      width: 200,
      fixed: 'right',
      render: (_, record) => (
        <Space size="small">
          {record.type !== 2 && (
            <Button size="small" onClick={() => handleAdd(record.id)}>新增子菜单</Button>
          )}
          <Button size="small" onClick={() => handleEdit(record)}>编辑</Button>
          <Popconfirm title="确定删除? 子菜单将一并删除" onConfirm={() => handleDelete(record.id)}>
            <Button size="small" danger>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <div style={{ marginBottom: 16 }}>
        <Space>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => handleAdd(0)}>新增菜单</Button>
          <Button icon={<ReloadOutlined />} onClick={() => dispatch({ type: 'menu/fetchTree' })}>刷新</Button>
        </Space>
      </div>

      <Table
        rowKey="id"
        columns={columns}
        dataSource={flatData}
        loading={loading}
        scroll={{ x: 1300 }}
        pagination={false}
        defaultExpandAllRows
      />

      <Modal
        title={editingRecord ? '编辑菜单' : '新增菜单'}
        open={modalVisible}
        onOk={handleModalOk}
        onCancel={() => setModalVisible(false)}
        destroyOnClose
        width={600}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="parentId" label="上级菜单">
            <Select disabled>
              <Option value={0}>根目录</Option>
              {treeData.map((item) => (
                <Option key={item.id} value={item.id}>{item.name}</Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item name="type" label="菜单类型" rules={[{ required: true }]}>
            <Select>
              <Option value={0}>目录</Option>
              <Option value={1}>菜单</Option>
              <Option value={2}>按钮</Option>
            </Select>
          </Form.Item>
          <Form.Item name="name" label="菜单名称" rules={[{ required: true, message: '请输入菜单名称' }]}>
            <Input placeholder="请输入菜单名称" />
          </Form.Item>
          <Form.Item name="icon" label="图标">
            <Input placeholder="选择图标" addonAfter="可选" />
          </Form.Item>
          <Form.Item name="path" label="路由地址">
            <Input placeholder="请输入路由地址" />
          </Form.Item>
          <Form.Item name="component" label="组件路径">
            <Input placeholder="请输入组件路径" />
          </Form.Item>
          <Form.Item name="perms" label="权限标识">
            <Input placeholder="请输入权限标识" />
          </Form.Item>
          <Form.Item name="sort" label="排序">
            <InputNumber style={{ width: '100%' }} min={0} />
          </Form.Item>
          <Form.Item name="hidden" label="是否隐藏" initialValue={0}>
            <Select>
              <Option value={0}>显示</Option>
              <Option value={1}>隐藏</Option>
            </Select>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default MenuManagement;
