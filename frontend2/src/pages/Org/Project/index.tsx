import React, { useEffect, useState, useCallback } from 'react';
import {
  Table, Button, Space, Tag, Modal, Form, Input, InputNumber, Tree, TreeSelect, Select, message, Popconfirm, App,
} from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, ReloadOutlined, ProjectOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { useProjectStore } from '@/stores/useProjectStore';
import type { ProjectItem, ProjectFormParams } from '@/services/api';

const ProjectPage: React.FC = () => {
  const { message: msg } = App.useApp();
  const { tree, loading, fetchTree, create, update, remove, setCurrentItem } = useProjectStore();

  const [editForm] = Form.useForm();
  const [modalOpen, setModalOpen] = useState(false);
  const [modalTitle, setModalTitle] = useState('新增项目');
  const [editingId, setEditingId] = useState<number | null>(null);
  const [selectedProject, setSelectedProject] = useState<ProjectItem | null>(null);
  const [selectedKeys, setSelectedKeys] = useState<number[]>([]);

  const doFetch = useCallback(() => {
    fetchTree();
  }, [fetchTree]);

  useEffect(() => {
    doFetch();
  }, [doFetch]);

  // Flatten project tree
  const flattenProjects = (projects: ProjectItem[], level: number = 0): (ProjectItem & { level: number })[] => {
    const result: (ProjectItem & { level: number })[] = [];
    for (const p of projects) {
      result.push({ ...p, level });
      if (p.children && p.children.length > 0) {
        result.push(...flattenProjects(p.children, level + 1));
      }
    }
    return result;
  };

  const flatProjects = flattenProjects(tree);

  const buildTree = (projects: ProjectItem[]): any[] => {
    return projects
      .sort((a, b) => a.sort - b.sort)
      .map((p) => ({
        title: p.name,
        key: p.id,
        children: p.children ? buildTree(p.children) : undefined,
      }));
  };

  const buildTreeSelectData = (projects: ProjectItem[]): any[] => {
    return projects.map((p) => ({
      title: p.name,
      value: p.id,
      children: p.children ? buildTreeSelectData(p.children) : undefined,
    }));
  };

  const handleTreeSelect = (keys: React.Key[]) => {
    if (keys.length > 0) {
      setSelectedKeys(keys as number[]);
      const id = keys[0] as number;
      const findProject = (projects: ProjectItem[], pid: number): ProjectItem | null => {
        for (const p of projects) {
          if (p.id === pid) return p;
          if (p.children) {
            const found = findProject(p.children, pid);
            if (found) return found;
          }
        }
        return null;
      };
      const proj = findProject(tree, id);
      setSelectedProject(proj);
    } else {
      setSelectedKeys([]);
      setSelectedProject(null);
    }
  };

  const handleAdd = (parentId: number | null = null) => {
    setEditingId(null);
    setModalTitle('新增项目');
    editForm.resetFields();
    editForm.setFieldsValue({ parentId: parentId ?? (selectedKeys[0] || 0), sort: 0, status: 1 });
    setModalOpen(true);
  };

  const handleEdit = () => {
    if (!selectedProject) {
      msg.warning('请先选择项目');
      return;
    }
    setEditingId(selectedProject.id);
    setModalTitle('编辑项目');
    editForm.setFieldsValue({
      parentId: selectedProject.parentId,
      name: selectedProject.name,
      code: selectedProject.code,
      sort: selectedProject.sort,
      status: selectedProject.status,
      remark: selectedProject.remark,
    });
    setModalOpen(true);
  };

  const handleDelete = async () => {
    if (!selectedProject) {
      msg.warning('请先选择项目');
      return;
    }
    try {
      await remove(selectedProject.id);
      msg.success('删除成功');
      setSelectedProject(null);
      setSelectedKeys([]);
      doFetch();
    } catch {
      msg.error('删除失败');
    }
  };

  const handleModalOk = async () => {
    try {
      const values = await editForm.validateFields();
      const data: ProjectFormParams = { ...values };
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

  const columns: ColumnsType<ProjectItem & { level: number }> = [
    {
      title: '项目名称', dataIndex: 'name', width: 250,
      render: (text: string, record: any) => (
        <span style={{ paddingLeft: record.level * 24 }}>
          <ProjectOutlined style={{ marginRight: 8, color: '#722ed1' }} />
          {text}
        </span>
      ),
    },
    { title: '项目编码', dataIndex: 'code', width: 150 },
    { title: '排序', dataIndex: 'sort', width: 80, align: 'center' },
    { title: '备注', dataIndex: 'remark', ellipsis: true, render: (v: string) => v || '-' },
    {
      title: '状态', dataIndex: 'status', width: 80, align: 'center',
      render: (v: number) => <Tag color={v === 1 ? 'green' : 'red'}>{v === 1 ? '启用' : '禁用'}</Tag>,
    },
  ];

  return (
    <div style={{ padding: 24, background: '#fff', borderRadius: 8, display: 'flex', gap: 16 }}>
      <div style={{
        width: 260, minWidth: 260, border: '1px solid #f0f0f0', borderRadius: 8, padding: 16,
      }}>
        <div style={{ marginBottom: 12, fontWeight: 500, fontSize: 15 }}>项目树</div>
        <Tree
          treeData={buildTree(tree)}
          onSelect={handleTreeSelect}
          selectedKeys={selectedKeys}
          defaultExpandAll
          blockNode
        />
      </div>

      <div style={{ flex: 1 }}>
        <div className="table-operations">
          <Space>
            <Button type="primary" icon={<PlusOutlined />} onClick={() => handleAdd()}>新增项目</Button>
            <Button icon={<EditOutlined />} onClick={handleEdit} disabled={!selectedProject}>编辑</Button>
            <Popconfirm title="确定删除?" onConfirm={handleDelete} okText="确定" cancelText="取消">
              <Button danger icon={<DeleteOutlined />} disabled={!selectedProject}>删除</Button>
            </Popconfirm>
            <Button icon={<ReloadOutlined />} onClick={doFetch}>刷新</Button>
          </Space>
        </div>

        {selectedProject && (
          <div style={{ marginBottom: 16, padding: '12px 16px', background: '#f6f8fa', borderRadius: 6 }}>
            <strong>当前选中：</strong>{selectedProject.name}
            <span style={{ marginLeft: 16, color: '#999' }}>编码: {selectedProject.code || '-'}</span>
          </div>
        )}

        <Table
          columns={columns}
          dataSource={flatProjects}
          rowKey="id"
          loading={loading}
          pagination={false}
          defaultExpandAllRows
        />
      </div>

      <Modal
        title={modalTitle}
        open={modalOpen}
        onOk={handleModalOk}
        onCancel={() => setModalOpen(false)}
        destroyOnClose
        width={500}
      >
        <Form form={editForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item name="parentId" label="上级项目" rules={[{ required: true, message: '请选择上级项目' }]}>
            <TreeSelect
              treeData={[{ title: '根节点', value: 0, children: buildTreeSelectData(tree) }]}
              placeholder="请选择上级项目"
              treeDefaultExpandAll
            />
          </Form.Item>
          <Form.Item name="name" label="项目名称" rules={[{ required: true, message: '请输入项目名称' }]}>
            <Input placeholder="请输入项目名称" />
          </Form.Item>
          <Form.Item name="code" label="项目编码" rules={[{ required: true, message: '请输入项目编码' }]}>
            <Input placeholder="请输入项目编码" disabled={!!editingId} />
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
    </div>
  );
};

export default ProjectPage;
