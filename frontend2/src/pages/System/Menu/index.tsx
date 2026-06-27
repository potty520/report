import React, { useEffect, useState, useCallback } from 'react';
import {
  Table, Button, Space, Tag, Modal, Form, Input, InputNumber, Select, TreeSelect, message, Popconfirm, App,
} from 'antd';
import {
  PlusOutlined, EditOutlined, DeleteOutlined, ReloadOutlined,
  FolderOutlined, FileOutlined, MenuOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { useMenuStore } from '@/stores/useMenuStore';
import type { MenuTreeItem, MenuFormParams } from '@/services/api';

const MenuPage: React.FC = () => {
  const { message: msg } = App.useApp();
  const { tree, loading, fetchTree, create, update, remove } = useMenuStore();

  const [editForm] = Form.useForm();
  const [modalOpen, setModalOpen] = useState(false);
  const [modalTitle, setModalTitle] = useState('新增菜单');
  const [editingId, setEditingId] = useState<number | null>(null);

  const doFetch = useCallback(() => {
    fetchTree();
  }, [fetchTree]);

  useEffect(() => {
    doFetch();
  }, [doFetch]);

  const handleAdd = (parentId: number = 0) => {
    setEditingId(null);
    setModalTitle('新增菜单');
    editForm.resetFields();
    editForm.setFieldsValue({ parentId, menuType: 'C', sort: 0, visible: 1, status: 1 });
    setModalOpen(true);
  };

  const handleEdit = (record: MenuTreeItem) => {
    setEditingId(record.id);
    setModalTitle('编辑菜单');
    editForm.setFieldsValue({
      parentId: record.parentId,
      menuName: record.menuName,
      menuType: record.menuType,
      path: record.path,
      component: record.component,
      icon: record.icon,
      sort: record.sort,
      permission: record.permission,
      visible: record.visible,
    });
    setModalOpen(true);
  };

  const handleDelete = async (id: number) => {
    try {
      await remove(id);
      msg.success('删除成功');
      doFetch();
    } catch {
      msg.error('删除失败(可能包含子菜单)');
    }
  };

  const handleModalOk = async () => {
    try {
      const values = await editForm.validateFields();
      const data: MenuFormParams = { ...values };
      if (editingId) {
        data.id = editingId;
        await update(data);
        msg.success('更新成功');
      } else {
        await create(data);
        msg.success('创建成功');
      }
      setModalOpen(false);
      doFetch();
    } catch (error: any) {
      if (error.errorFields) return;
      msg.error(error?.message || '操作失败');
    }
  };

  // Flatten tree to list for table display
  const flattenTree = (menus: MenuTreeItem[], level: number = 0): (MenuTreeItem & { level: number })[] => {
    const result: (MenuTreeItem & { level: number })[] = [];
    for (const menu of menus) {
      result.push({ ...menu, level });
      if (menu.children && menu.children.length > 0) {
        result.push(...flattenTree(menu.children, level + 1));
      }
    }
    return result;
  };

  const flatData = flattenTree(tree);

  // Build tree select data
  const buildTreeSelectData = (menus: MenuTreeItem[]): any[] => {
    return menus
      .sort((a, b) => a.sort - b.sort)
      .map((menu) => ({
        title: menu.menuName,
        value: menu.id,
        children: menu.children ? buildTreeSelectData(menu.children) : undefined,
      }));
  };

  const treeSelectData = buildTreeSelectData(tree);

  const columns: ColumnsType<MenuTreeItem & { level: number }> = [
    {
      title: '菜单名称', dataIndex: 'menuName', width: 220,
      render: (text: string, record: any) => (
        <span style={{ paddingLeft: record.level * 24 }}>
          {record.menuType === "M" ? <FolderOutlined style={{ marginRight: 8, color: '#faad14' }} /> :
           record.menuType === "C" ? <FileOutlined style={{ marginRight: 8, color: '#1890ff' }} /> :
           <MenuOutlined style={{ marginRight: 8, color: '#52c41a' }} />}
          {text}
        </span>
      ),
    },
    {
      title: '图标', dataIndex: 'icon', width: 100, align: 'center',
      render: (v: string) => v || '-',
    },
    {
      title: '类型', dataIndex: 'menuType', width: 80, align: 'center',
      render: (v: string) => {
        const map: Record<string, { color: string; text: string }> = {
          M: { color: 'gold', text: '目录' },
          C: { color: 'blue', text: '菜单' },
          F: { color: 'green', text: '按钮' },
        };
        return <Tag color={map[v]?.color}>{map[v]?.text || v}</Tag>;
      },
    },
    { title: '路由路径', dataIndex: 'path', width: 180, render: (v: string) => v || '-' },
    { title: '组件路径', dataIndex: 'component', width: 200, render: (v: string) => v || '-' },
    { title: '权限标识', dataIndex: 'permission', width: 160, render: (v: string) => v || '-' },
    { title: '排序', dataIndex: 'sort', width: 70, align: 'center' },
    {
      title: '可见', dataIndex: 'visible', width: 70, align: 'center',
      render: (v: number) => <Tag color={v === 1 ? 'green' : 'red'}>{v === 1 ? '是' : '否'}</Tag>,
    },
    {
      title: '操作', key: 'action', width: 200, fixed: 'right',
      render: (_: any, record: MenuTreeItem & { level: number }) => (
        <Space>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => handleEdit(record)}>编辑</Button>
          {record.menuType !== 2 && (
            <Button type="link" size="small" icon={<PlusOutlined />} onClick={() => handleAdd(record.id)}>添加</Button>
          )}
          <Popconfirm title="确定删除?" onConfirm={() => handleDelete(record.id)} okText="确定" cancelText="取消">
            <Button type="link" size="small" danger icon={<DeleteOutlined />}>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  // Common icon options
  const iconOptions = [
    'DashboardOutlined', 'SettingOutlined', 'UserOutlined', 'TeamOutlined', 'ApartmentOutlined',
    'MenuOutlined', 'FileTextOutlined', 'FileAddOutlined', 'DatabaseOutlined', 'ScheduleOutlined',
    'SendOutlined', 'BookOutlined', 'FormOutlined', 'AuditOutlined', 'CheckCircleOutlined',
    'SafetyOutlined', 'ProjectOutlined', 'PieChartOutlined', 'BarChartOutlined', 'KeyOutlined',
    'HistoryOutlined', 'FileProtectOutlined', 'FolderOutlined', 'CloudUploadOutlined',
    'EditOutlined', 'ProfileOutlined', 'HomeOutlined', 'AppstoreOutlined',
    'FileSearchOutlined', 'UnlockOutlined',
  ];

  return (
    <div style={{ padding: 24, background: '#fff', borderRadius: 8 }}>
      <div className="table-operations">
        <Button type="primary" icon={<PlusOutlined />} onClick={() => handleAdd(0)}>新增根菜单</Button>
        <Button icon={<ReloadOutlined />} onClick={doFetch}>刷新</Button>
      </div>
      <Table
        columns={columns}
        dataSource={flatData}
        rowKey="id"
        loading={loading}
        scroll={{ x: 1500 }}
        pagination={false}
        defaultExpandAllRows
      />

      <Modal
        title={modalTitle}
        open={modalOpen}
        onOk={handleModalOk}
        onCancel={() => setModalOpen(false)}
        destroyOnClose
        width={600}
      >
        <Form form={editForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item name="parentId" label="上级菜单" rules={[{ required: true, message: '请选择上级菜单' }]}>
            <TreeSelect
              treeData={[{ title: '根节点', value: 0, children: treeSelectData }]}
              placeholder="请选择上级菜单"
              treeDefaultExpandAll
            />
          </Form.Item>
          <Form.Item name="menuType" label="菜单类型" rules={[{ required: true, message: '请选择类型' }]}>
            <Select>
              <Select.Option value="M">目录</Select.Option>
              <Select.Option value="C">菜单</Select.Option>
              <Select.Option value="F">按钮</Select.Option>
            </Select>
          </Form.Item>
          <Form.Item name="menuName" label="菜单名称" rules={[{ required: true, message: '请输入菜单名称' }]}>
            <Input placeholder="请输入菜单名称" />
          </Form.Item>
          <Form.Item name="path" label="路由路径">
            <Input placeholder="如: /system/user" />
          </Form.Item>
          <Form.Item name="component" label="组件路径">
            <Input placeholder="如: System/User" />
          </Form.Item>
          <Form.Item name="icon" label="图标">
            <Select placeholder="请选择图标" allowClear showSearch>
              {iconOptions.map((icon) => (
                <Select.Option key={icon} value={icon}>{icon}</Select.Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item name="permission" label="权限标识">
            <Input placeholder="如: system:user:add" />
          </Form.Item>
          <Form.Item name="sort" label="排序">
            <InputNumber placeholder="排序号" style={{ width: '100%' }} min={0} />
          </Form.Item>
          <Form.Item name="visible" label="是否可见">
            <Select>
              <Select.Option value={1}>可见</Select.Option>
              <Select.Option value={0}>隐藏</Select.Option>
            </Select>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default MenuPage;
