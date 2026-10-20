import React, { useEffect, useState, useCallback } from 'react';
import {
  Table, Button, Space, Tag, Modal, Form, Input, InputNumber, Select, Tree, message, Popconfirm, App,
} from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, SearchOutlined, ReloadOutlined, SafetyOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { useRoleStore } from '@/stores/useRoleStore';
import { useMenuStore } from '@/stores/useMenuStore';
import type { RoleItem, RoleFormParams, MenuTreeItem } from '@/services/api';
import dayjs from 'dayjs';

const RolePage: React.FC = () => {
  const { message: msg } = App.useApp();
  const {
    list, total, loading, fetchList, create, update, remove, setCurrentItem,
  } = useRoleStore();
  const { tree: menuTree, fetchTree: fetchMenuTree } = useMenuStore();

  const [searchForm] = Form.useForm();
  const [editForm] = Form.useForm();
  const [modalOpen, setModalOpen] = useState(false);
  const [permModalOpen, setPermModalOpen] = useState(false);
  const [modalTitle, setModalTitle] = useState('新增角色');
  const [editingId, setEditingId] = useState<number | null>(null);
  const [searchParams, setSearchParams] = useState<Record<string, any>>({});
  const [selectedRole, setSelectedRole] = useState<RoleItem | null>(null);
  const [checkedMenuKeys, setCheckedMenuKeys] = useState<number[]>([]);

  const doFetch = useCallback((params: Record<string, any> = {}) => {
    fetchList({ current: 1, size: 10, ...params });
  }, [fetchList]);

  useEffect(() => {
    doFetch();
    fetchMenuTree();
  }, [doFetch, fetchMenuTree]);

  const handleSearch = () => {
    const values = searchForm.getFieldsValue();
    setSearchParams(values);
    fetchList({ current: 1, size: 10, ...values });
  };

  const handleReset = () => {
    searchForm.resetFields();
    setSearchParams({});
    doFetch();
  };

  const handleAdd = () => {
    setEditingId(null);
    setModalTitle('新增角色');
    editForm.resetFields();
    editForm.setFieldsValue({ status: 1, sort: 0 });
    setModalOpen(true);
  };

  const handleEdit = (record: RoleItem) => {
    setEditingId(record.id);
    setModalTitle('编辑角色');
    editForm.setFieldsValue({
      name: record.name,
      code: record.code,
      sort: record.sort,
      status: record.status,
      remark: record.remark,
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

  const handleModalOk = async () => {
    try {
      const values = await editForm.validateFields();
      const data: RoleFormParams = { ...values };
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
      if (error.errorFields) return;
      msg.error(error?.message || '操作失败');
    }
  };

  const handlePermAssign = async (record: RoleItem) => {
    setSelectedRole(record);
    try {
      const res = await import('@/services/api').then((m) => m.getRoleMenuIdsApi(record.id));
      setCheckedMenuKeys(res.data.data || []);
      setPermModalOpen(true);
    } catch {
      msg.error('加载权限失败');
    }
  };

  const handlePermModalOk = async () => {
    if (!selectedRole) return;
    try {
      await update({ id: selectedRole.id, name: selectedRole.name, code: selectedRole.code, menuIds: checkedMenuKeys });
      msg.success('权限分配成功');
      setPermModalOpen(false);
      doFetch(searchParams);
    } catch {
      msg.error('权限分配失败');
    }
  };

  // Build tree data for Tree component
  const buildTreeData = (menus: MenuTreeItem[]): any[] => {
    return menus
      .filter((m) => m.menuType !== 'F') // filter buttons
      .sort((a, b) => a.sort - b.sort)
      .map((menu) => ({
        title: menu.menuName,
        key: menu.id,
        children: menu.children ? buildTreeData(menu.children) : undefined,
      }));
  };

  const treeData = buildTreeData(menuTree);

  const columns: ColumnsType<RoleItem> = [
    { title: 'ID', dataIndex: 'id', width: 60, align: 'center' },
    { title: '角色名称', dataIndex: 'roleName', width: 150 },
    { title: '角色编码', dataIndex: 'roleCode', width: 150 },
    { title: '排序', dataIndex: 'sort', width: 80, align: 'center' },
    {
      title: '状态', dataIndex: 'status', width: 80, align: 'center',
      render: (v: number) => <Tag color={v === 1 ? 'green' : 'red'}>{v === 1 ? '启用' : '禁用'}</Tag>,
    },
    { title: '备注', dataIndex: 'description', ellipsis: true },
    {
      title: '创建时间', dataIndex: 'createTime', width: 170,
      render: (v: string) => v ? dayjs(v).format('YYYY-MM-DD HH:mm:ss') : '-',
    },
    {
      title: '操作', key: 'action', width: 200, fixed: 'right',
      render: (_: any, record: RoleItem) => (
        <Space>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => handleEdit(record)}>编辑</Button>
          <Button type="link" size="small" icon={<SafetyOutlined />} onClick={() => handlePermAssign(record)}>权限</Button>
          <Popconfirm title="确定删除该角色?" onConfirm={() => handleDelete(record.id)} okText="确定" cancelText="取消">
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
          <Form.Item name="name" label="角色名称">
            <Input placeholder="请输入" allowClear />
          </Form.Item>
          <Form.Item name="code" label="角色编码">
            <Input placeholder="请输入" allowClear />
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
        <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>新增角色</Button>
      </div>
      <Table
        columns={columns}
        dataSource={list}
        rowKey="id"
        loading={loading}
        scroll={{ x: 1100 }}
        pagination={{
          total,
          showSizeChanger: true,
          showTotal: (t) => `共 ${t} 条`,
        }}
        onChange={(pagination) => fetchList({ current: pagination.current, size: pagination.pageSize, ...searchParams })}
      />

      {/* Add/Edit Modal */}
      <Modal
        title={modalTitle}
        open={modalOpen}
        onOk={handleModalOk}
        onCancel={() => setModalOpen(false)}
        destroyOnClose
        width={500}
      >
        <Form form={editForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item name="name" label="角色名称" rules={[{ required: true, message: '请输入角色名称' }]}>
            <Input placeholder="请输入角色名称" />
          </Form.Item>
          <Form.Item name="code" label="角色编码" rules={[{ required: true, message: '请输入角色编码' }]}>
            <Input placeholder="请输入角色编码" disabled={!!editingId} />
          </Form.Item>
          <Form.Item name="sort" label="排序">
            <InputNumber placeholder="排序号" style={{ width: '100%' }} min={0} />
          </Form.Item>
          <Form.Item name="status" label="状态">
            <Select>
              <Select.Option value={1}>启用</Select.Option>
              <Select.Option value={0}>禁用</Select.Option>
            </Select>
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={3} placeholder="备注信息" />
          </Form.Item>
        </Form>
      </Modal>

      {/* Permission Assignment Modal */}
      <Modal
        title={`菜单权限 - ${selectedRole?.name || ''}`}
        open={permModalOpen}
        onOk={handlePermModalOk}
        onCancel={() => setPermModalOpen(false)}
        width={500}
      >
        <div style={{ maxHeight: 400, overflow: 'auto', marginTop: 16 }}>
          <Tree
            checkable
            defaultExpandAll
            checkedKeys={checkedMenuKeys}
            onCheck={(checkedKeysValue: any) => {
              setCheckedMenuKeys(checkedKeysValue as number[]);
            }}
            treeData={treeData}
          />
        </div>
      </Modal>
    </div>
  );
};

export default RolePage;
