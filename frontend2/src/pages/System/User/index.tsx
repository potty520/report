import React, { useEffect, useState, useCallback } from 'react';
import {
  Table, Button, Space, Tag, Modal, Form, Input, Select, Switch, message, Popconfirm, App,
} from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, SearchOutlined, ReloadOutlined, SafetyOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { useUserStore } from '@/stores/useUserStore';
import { useRoleStore } from '@/stores/useRoleStore';
import { useDeptStore } from '@/stores/useDeptStore';
import type { UserItem, UserFormParams } from '@/services/api';
import dayjs from 'dayjs';

const UserPage: React.FC = () => {
  const { message: msg } = App.useApp();
  const {
    list, total, current, size, loading, fetchList, create, update, remove, toggleStatus, assignRoles,
    setCurrentItem,
  } = useUserStore();
  const { allRoles, fetchAllRoles } = useRoleStore();
  const { tree: deptTree, fetchTree: fetchDeptTree } = useDeptStore();

  const [searchForm] = Form.useForm();
  const [editForm] = Form.useForm();
  const [roleForm] = Form.useForm();
  const [modalOpen, setModalOpen] = useState(false);
  const [roleModalOpen, setRoleModalOpen] = useState(false);
  const [modalTitle, setModalTitle] = useState('新增用户');
  const [editingId, setEditingId] = useState<number | null>(null);
  const [searchParams, setSearchParams] = useState<Record<string, any>>({});
  const [selectedUser, setSelectedUser] = useState<UserItem | null>(null);

  // Convert dept tree to cascader options
  const deptOptions = React.useMemo(() => {
    const flatten = (items: any[], prefix = ''): { label: string; value: number }[] => {
      return items.reduce((acc: any[], item: any) => {
        acc.push({ label: prefix + item.name, value: item.id });
        if (item.children && item.children.length > 0) {
          acc.push(...flatten(item.children, prefix + '├ '));
        }
        return acc;
      }, []);
    };
    return flatten(deptTree);
  }, [deptTree]);

  const doFetch = useCallback((params: Record<string, any> = {}) => {
    fetchList({ current: 1, size: 10, ...params });
  }, [fetchList]);

  useEffect(() => {
    doFetch();
    fetchAllRoles();
    fetchDeptTree();
  }, [doFetch, fetchAllRoles, fetchDeptTree]);

  const handleSearch = () => {
    const values = searchForm.getFieldsValue();
    const params = { ...values };
    setSearchParams(params);
    fetchList({ current: 1, size, ...params });
  };

  const handleReset = () => {
    searchForm.resetFields();
    setSearchParams({});
    fetchList({ current: 1, size });
  };

  const handleAdd = () => {
    setEditingId(null);
    setModalTitle('新增用户');
    editForm.resetFields();
    editForm.setFieldsValue({ status: 1, gender: 0 });
    setModalOpen(true);
  };

  const handleEdit = async (record: UserItem) => {
    setEditingId(record.id);
    setModalTitle('编辑用户');
    editForm.setFieldsValue({
      username: record.username,
      nickname: record.nickname,
      email: record.email,
      phone: record.phone,
      gender: record.gender,
      deptId: record.deptId,
      status: record.status,
    });
    setModalOpen(true);
  };

  const handleDelete = async (id: number) => {
    try {
      await remove(id);
      msg.success('删除成功');
      doFetch(searchParams);
    } catch {
      msg.error('删除失败');
    }
  };

  const handleStatusToggle = async (id: number, checked: boolean) => {
    try {
      await toggleStatus(id, checked ? 1 : 0);
      msg.success(checked ? '已启用' : '已禁用');
      doFetch(searchParams);
    } catch {
      msg.error('操作失败');
    }
  };

  const handleModalOk = async () => {
    try {
      const values = await editForm.validateFields();
      const data: UserFormParams = { ...values };
      if (editingId) {
        data.id = editingId;
        await update(data);
        msg.success('更新成功');
      } else {
        await create(data);
        msg.success('创建成功');
      }
      setModalOpen(false);
      doFetch(searchParams);
    } catch (error: any) {
      if (error.errorFields) return; // form validation
      msg.error(error?.message || '操作失败');
    }
  };

  const handleRoleAssign = async (record: UserItem) => {
    setSelectedUser(record);
    roleForm.setFieldsValue({ roleIds: record.roleIds || [] });
    setRoleModalOpen(true);
  };

  const handleRoleModalOk = async () => {
    try {
      const values = await roleForm.validateFields();
      if (selectedUser) {
        await assignRoles(selectedUser.id, values.roleIds || []);
        msg.success('角色分配成功');
        setRoleModalOpen(false);
        doFetch(searchParams);
      }
    } catch {
      // ignore
    }
  };

  const handleTableChange = (pagination: any) => {
    const params = { current: pagination.current, size: pagination.pageSize, ...searchParams };
    fetchList(params);
  };

  const columns: ColumnsType<UserItem> = [
    { title: 'ID', dataIndex: 'id', width: 60, align: 'center' },
    { title: '用户名', dataIndex: 'username', width: 120 },
    { title: '昵称', dataIndex: 'realName', width: 120 },
    { title: '邮箱', dataIndex: 'email', width: 180 },
    { title: '手机号', dataIndex: 'phone', width: 130 },
    {
      title: '性别', dataIndex: 'userType', width: 70, align: 'center',
      render: (v: number) => v === 1 ? '男' : v === 2 ? '女' : '-',
    },
    { title: '部门', dataIndex: 'deptName', width: 130 },
    {
      title: '角色', dataIndex: 'roleIds', width: 180,
      render: (v: string[]) => v?.map((r) => <Tag key={r} color="blue">{r}</Tag>),
    },
    {
      title: '状态', dataIndex: 'status', width: 80, align: 'center',
      render: (v: number, record: UserItem) => (
        <Switch checked={v === 1} onChange={(checked) => handleStatusToggle(record.id, checked)} />
      ),
    },
    {
      title: '创建时间', dataIndex: 'createTime', width: 170,
      render: (v: string) => v ? dayjs(v).format('YYYY-MM-DD HH:mm:ss') : '-',
    },
    {
      title: '操作', key: 'action', width: 200, fixed: 'right',
      render: (_: any, record: UserItem) => (
        <Space>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => handleEdit(record)}>
            编辑
          </Button>
          <Button type="link" size="small" icon={<SafetyOutlined />} onClick={() => handleRoleAssign(record)}>
            角色
          </Button>
          <Popconfirm title="确定删除该用户?" onConfirm={() => handleDelete(record.id)} okText="确定" cancelText="取消">
            <Button type="link" size="small" danger icon={<DeleteOutlined />}>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div style={{ padding: 24, background: '#fff', borderRadius: 8 }}>
      <div className="search-form" style={{ marginBottom: 16 }}>
        <Form form={searchForm} layout="inline">
          <Form.Item name="username" label="用户名">
            <Input placeholder="请输入用户名" allowClear />
          </Form.Item>
          <Form.Item name="nickname" label="昵称">
            <Input placeholder="请输入昵称" allowClear />
          </Form.Item>
          <Form.Item name="status" label="状态">
            <Select placeholder="请选择" allowClear style={{ width: 120 }}>
              <Select.Option value={1}>启用</Select.Option>
              <Select.Option value={0}>禁用</Select.Option>
            </Select>
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" icon={<SearchOutlined />} onClick={handleSearch}>搜索</Button>
              <Button icon={<ReloadOutlined />} onClick={handleReset}>重置</Button>
            </Space>
          </Form.Item>
        </Form>
      </div>
      <div className="table-operations">
        <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>新增用户</Button>
      </div>
      <Table
        columns={columns}
        dataSource={list}
        rowKey="id"
        loading={loading}
        scroll={{ x: 1400 }}
        onChange={handleTableChange}
        pagination={{
          current,
          pageSize: size,
          total,
          showSizeChanger: true,
          showTotal: (t) => `共 ${t} 条`,
        }}
      />

      {/* Add/Edit Modal */}
      <Modal
        title={modalTitle}
        open={modalOpen}
        onOk={handleModalOk}
        onCancel={() => setModalOpen(false)}
        destroyOnClose
        width={600}
      >
        <Form form={editForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item name="username" label="用户名" rules={[{ required: true, message: '请输入用户名' }]}>
            <Input placeholder="请输入用户名" disabled={!!editingId} />
          </Form.Item>
          <Form.Item name="nickname" label="昵称" rules={[{ required: true, message: '请输入昵称' }]}>
            <Input placeholder="请输入昵称" />
          </Form.Item>
          {!editingId && (
            <Form.Item name="password" label="密码" rules={[{ required: true, message: '请输入密码' }]}>
              <Input.Password placeholder="请输入密码" />
            </Form.Item>
          )}
          <Form.Item name="email" label="邮箱" rules={[{ type: 'email', message: '邮箱格式不正确' }]}>
            <Input placeholder="请输入邮箱" />
          </Form.Item>
          <Form.Item name="phone" label="手机号">
            <Input placeholder="请输入手机号" />
          </Form.Item>
          <Form.Item name="gender" label="性别">
            <Select placeholder="请选择">
              <Select.Option value={0}>未知</Select.Option>
              <Select.Option value={1}>男</Select.Option>
              <Select.Option value={2}>女</Select.Option>
            </Select>
          </Form.Item>
          <Form.Item name="deptId" label="部门">
            <Select placeholder="请选择部门" allowClear showSearch optionFilterProp="label" options={deptOptions} />
          </Form.Item>
          <Form.Item name="status" label="状态">
            <Select>
              <Select.Option value={1}>启用</Select.Option>
              <Select.Option value={0}>禁用</Select.Option>
            </Select>
          </Form.Item>
        </Form>
      </Modal>

      {/* Role Assignment Modal */}
      <Modal
        title={`分配角色 - ${selectedUser?.nickname || selectedUser?.username || ''}`}
        open={roleModalOpen}
        onOk={handleRoleModalOk}
        onCancel={() => setRoleModalOpen(false)}
        destroyOnClose
      >
        <Form form={roleForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item name="roleIds" label="角色">
            <Select mode="multiple" placeholder="请选择角色" allowClear style={{ width: '100%' }}>
              {allRoles.map((role) => (
                <Select.Option key={role.id} value={role.id}>{role.name}</Select.Option>
              ))}
            </Select>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default UserPage;
