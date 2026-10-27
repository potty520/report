import React, { useEffect, useState, useCallback } from 'react';
import {
  Table, Button, Space, Tag, Modal, Form, Input, InputNumber, Tree, TreeSelect, Switch, Select, message, Popconfirm, App,
} from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, ReloadOutlined, FolderOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { useDeptStore } from '@/stores/useDeptStore';
import { getFillDeptApi, setFillDeptApi } from '@/services/api';
import type { DeptItem, DeptFormParams } from '@/services/api';

const DeptPage: React.FC = () => {
  const { message: msg } = App.useApp();
  const { tree, loading, fetchTree, create, update, remove, selectedKey, setSelectedKey } = useDeptStore();

  const [editForm] = Form.useForm();
  const [modalOpen, setModalOpen] = useState(false);
  const [modalTitle, setModalTitle] = useState('新增部门');
  const [editingId, setEditingId] = useState<number | null>(null);
  const [selectedDept, setSelectedDept] = useState<DeptItem | null>(null);
  const [fillDeptIds, setFillDeptIds] = useState<number[]>([]);

  const loadFillDepts = useCallback(() => {
    getFillDeptApi()
      .then((res: any) => setFillDeptIds((res?.data || []).map((d: any) => d.id)))
      .catch(() => setFillDeptIds([]));
  }, []);

  const saveFillDepts = async () => {
    try {
      const res: any = await setFillDeptApi(fillDeptIds);
      if (res?.code === 200) msg.success('已保存填报部门');
      else msg.error(res?.msg || '保存失败');
    } catch (e: any) {
      msg.error(e?.response?.data?.msg || '保存失败');
    }
  };

  const doFetch = useCallback(() => {
    fetchTree();
  }, [fetchTree]);

  useEffect(() => {
    doFetch();
    loadFillDepts();
  }, [doFetch, loadFillDepts]);

  // Flatten dept tree
  const flattenDepts = (depts: DeptItem[], level: number = 0): (DeptItem & { level: number })[] => {
    const result: (DeptItem & { level: number })[] = [];
    for (const d of depts) {
      result.push({ ...d, level });
      if (d.children && d.children.length > 0) {
        result.push(...flattenDepts(d.children, level + 1));
      }
    }
    return result;
  };

  const flatDepts = flattenDepts(tree);

  // Build tree data for left panel
  const buildTree = (depts: DeptItem[]): any[] => {
    return depts
      .sort((a, b) => a.sort - b.sort)
      .map((d) => ({
        title: d.deptName,
        key: d.id,
        children: d.children ? buildTree(d.children) : undefined,
      }));
  };

  // Build tree select data
  const buildTreeSelectData = (depts: DeptItem[]): any[] => {
    return depts.map((d) => ({
      title: d.deptName,
      value: d.id,
      children: d.children ? buildTreeSelectData(d.children) : undefined,
    }));
  };

  const handleTreeSelect = (keys: React.Key[]) => {
    if (keys.length > 0) {
      setSelectedKey(keys[0] as number);
      // Find the selected item
      const findDept = (depts: DeptItem[], id: number): DeptItem | null => {
        for (const d of depts) {
          if (d.id === id) return d;
          if (d.children) {
            const found = findDept(d.children, id);
            if (found) return found;
          }
        }
        return null;
      };
      const dept = findDept(tree, keys[0] as number);
      setSelectedDept(dept);
    } else {
      setSelectedKey(null);
      setSelectedDept(null);
    }
  };

  const handleAdd = (parentId: number | null = null) => {
    setEditingId(null);
    setModalTitle('新增部门');
    editForm.resetFields();
    editForm.setFieldsValue({ parentId: parentId ?? selectedKey ?? 0, sort: 0, status: 1 });
    setModalOpen(true);
  };

  const handleEdit = () => {
    if (!selectedDept) {
      msg.warning('请先选择部门');
      return;
    }
    setEditingId(selectedDept.id);
    setModalTitle('编辑部门');
    editForm.setFieldsValue({
      parentId: selectedDept.parentId,
      deptName: selectedDept.deptName,
      sort: selectedDept.sort,
      leader: selectedDept.leader,
      phone: selectedDept.phone,
      email: selectedDept.email,
      status: selectedDept.status,
    });
    setModalOpen(true);
  };

  const handleDelete = async () => {
    if (!selectedDept) {
      msg.warning('请先选择部门');
      return;
    }
    try {
      await remove(selectedDept.id);
      msg.success('删除成功');
      setSelectedDept(null);
      setSelectedKey(null);
      doFetch();
    } catch {
      msg.error('删除失败(可能包含子部门)');
    }
  };

  const handleModalOk = async () => {
    try {
      const values = await editForm.validateFields();
      const data: DeptFormParams = { ...values };
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

  const columns: ColumnsType<DeptItem & { level: number }> = [
    {
      title: '部门名称', dataIndex: 'deptName', width: 250,
      render: (text: string, record: any) => (
        <span style={{ paddingLeft: record.level * 24 }}>
          <FolderOutlined style={{ marginRight: 8, color: '#1890ff' }} />
          {text}
        </span>
      ),
    },
    { title: '排序', dataIndex: 'sort', width: 80, align: 'center' },
    { title: '负责人', dataIndex: 'leader', width: 120, render: (v: string) => v || '-' },
    { title: '电话', dataIndex: 'phone', width: 130, render: (v: string) => v || '-' },
    { title: '邮箱', dataIndex: 'email', width: 180, render: (v: string) => v || '-' },
    {
      title: '填报部门', dataIndex: 'isFillDept', width: 110, align: 'center',
      render: (_: any, record: any) => (
        <Switch
          size="small"
          checked={fillDeptIds.includes(record.id)}
          onChange={(checked) =>
            setFillDeptIds((prev: number[]) =>
              checked ? Array.from(new Set([...prev, record.id])) : prev.filter((id: number) => id !== record.id))
          }
        />
      ),
    },
    {
      title: '状态', dataIndex: 'status', width: 80, align: 'center',
      render: (v: number) => <Tag color={v === 1 ? 'green' : 'red'}>{v === 1 ? '启用' : '禁用'}</Tag>,
    },
  ];

  return (
    <div style={{ padding: 24, background: '#fff', borderRadius: 8, display: 'flex', gap: 16 }}>
      {/* Left Tree Panel */}
      <div style={{
        width: 260, minWidth: 260, border: '1px solid #f0f0f0', borderRadius: 8, padding: 16,
      }}>
        <div style={{ marginBottom: 12, fontWeight: 500, fontSize: 15 }}>部门树</div>
        <Tree
          treeData={buildTree(tree)}
          onSelect={handleTreeSelect}
          selectedKeys={selectedKey ? [selectedKey] : []}
          defaultExpandAll
          blockNode
        />
      </div>

      {/* Right Content */}
      <div style={{ flex: 1 }}>
        <div className="table-operations">
          <Space>
            <Button type="primary" icon={<PlusOutlined />} onClick={() => handleAdd()}>新增部门</Button>
            <Button icon={<EditOutlined />} onClick={handleEdit} disabled={!selectedDept}>编辑</Button>
            <Popconfirm title="确定删除该部门及子部门?" onConfirm={handleDelete} okText="确定" cancelText="取消">
              <Button danger icon={<DeleteOutlined />} disabled={!selectedDept}>删除</Button>
            </Popconfirm>
            <Button onClick={saveFillDepts}>保存填报部门</Button>
            <Button icon={<ReloadOutlined />} onClick={doFetch}>刷新</Button>
          </Space>
        </div>

        {/* Selected dept info */}
        {selectedDept && (
          <div style={{ marginBottom: 16, padding: '12px 16px', background: '#f6f8fa', borderRadius: 6 }}>
            <strong>当前选中：</strong>{selectedDept.deptName}
            <span style={{ marginLeft: 16, color: '#999' }}>
              负责人: {selectedDept.leader || '-'} | 电话: {selectedDept.phone || '-'}
            </span>
          </div>
        )}

        <Table
          columns={columns}
          dataSource={flatDepts}
          rowKey="id"
          loading={loading}
          pagination={false}
          defaultExpandAllRows
        />
      </div>

      {/* Modal */}
      <Modal
        title={modalTitle}
        open={modalOpen}
        onOk={handleModalOk}
        onCancel={() => setModalOpen(false)}
        destroyOnClose
        width={500}
      >
        <Form form={editForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item name="parentId" label="上级部门" rules={[{ required: true, message: '请选择上级部门' }]}>
            <TreeSelect
              treeData={[{ title: '根节点', value: 0, children: buildTreeSelectData(tree) }]}
              placeholder="请选择上级部门"
              treeDefaultExpandAll
            />
          </Form.Item>
          <Form.Item name="deptName" label="部门名称" rules={[{ required: true, message: '请输入部门名称' }]}>
            <Input placeholder="请输入部门名称" />
          </Form.Item>
            <Form.Item name="deptCode" label="部门编码" extra="可留空，系统自动生成（如 D000001）">
              <Input placeholder="如 D000001" allowClear style={{ width: 260 }} />
            </Form.Item>
          <Form.Item name="sort" label="排序">
            <InputNumber placeholder="排序号" style={{ width: '100%' }} min={0} />
          </Form.Item>
          <Form.Item name="leader" label="负责人">
            <Input placeholder="请输入负责人" />
          </Form.Item>
          <Form.Item name="phone" label="电话">
            <Input placeholder="请输入电话" />
          </Form.Item>
          <Form.Item name="email" label="邮箱">
            <Input placeholder="请输入邮箱" />
          </Form.Item>
          <Form.Item name="status" label="状态">
            <Select>
              <Select.Option value={1}>启用</Select.Option>
              <Select.Option value={0}>禁用</Select.Option>
            </Select>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default DeptPage;
