import React, { useEffect, useState } from 'react';
import {
  Modal, Tabs, Table, Button, Space, Tag, App, Form, Select, TreeSelect, Popconfirm, Empty,
} from 'antd';
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import {
  getTemplateAuthsApi, grantTemplateAuthApi, revokeTemplateAuthApi,
  getDeptTreeApi, getUserListApi,
  TemplateAuthItem, DeptItem, UserItem,
} from '@/services/api';

interface TemplateAuthModalProps {
  open: boolean;
  templateId?: number;
  templateName?: string;
  onClose: () => void;
}

interface TreeNode {
  value: number;
  title: string;
  children?: TreeNode[];
}

const toTreeData = (depts: DeptItem[]): TreeNode[] =>
  (depts || []).map((d) => ({
    value: d.id,
    title: d.deptName,
    children: d.children && d.children.length > 0 ? toTreeData(d.children) : undefined,
  }));

/**
 * 模板两级授权（部门 / 用户）。
 *
 * 语义：一旦存在授权记录，该模板变为「仅授权可见/可用」；无授权记录则对所有具备模板权限的角色开放。
 */
const TemplateAuthModal: React.FC<TemplateAuthModalProps> = ({ open, templateId, templateName, onClose }) => {
  const { message: msg } = App.useApp();
  const [form] = Form.useForm();
  const [auths, setAuths] = useState<TemplateAuthItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [authType, setAuthType] = useState<'DEPT' | 'USER'>('DEPT');
  const [deptTree, setDeptTree] = useState<TreeNode[]>([]);
  const [users, setUsers] = useState<UserItem[]>([]);

  const loadAuths = async (id: number) => {
    setLoading(true);
    try {
      const res = await getTemplateAuthsApi(id);
      setAuths(res.data.data || []);
    } catch (e: any) {
      msg.error(e?.message || '加载授权失败');
      setAuths([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (!open || !templateId) return;
    loadAuths(templateId);
    form.resetFields();
    (async () => {
      try {
        const res = await getDeptTreeApi();
        setDeptTree(toTreeData(res.data.data || []));
      } catch {
        setDeptTree([]);
      }
      try {
        const res = await getUserListApi({ page: 1, pageSize: 200 });
        setUsers(res.data.data?.list || []);
      } catch {
        setUsers([]);
      }
    })();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, templateId]);

  const grant = async () => {
    if (!templateId) return;
    try {
      const values = await form.validateFields();
      const ids: number[] = Array.isArray(values.targetIds) ? values.targetIds : [values.targetIds];
      const added = await grantTemplateAuthApi(templateId, {
        authType: values.authType,
        targetIds: ids.filter((v) => v !== undefined && v !== null),
      });
      msg.success(`已授权（新增 ${added.data.data ?? 0} 条）`);
      form.resetFields(['targetIds']);
      loadAuths(templateId);
    } catch (e: any) {
      if (e?.errorFields) return;
      msg.error(e?.message || '授权失败');
    }
  };

  const revoke = async (record: TemplateAuthItem) => {
    if (!templateId) return;
    try {
      await revokeTemplateAuthApi(templateId, record.id);
      msg.success('已撤销');
      loadAuths(templateId);
    } catch (e: any) {
      msg.error(e?.message || '撤销失败');
    }
  };

  const columns: ColumnsType<TemplateAuthItem> = [
    {
      title: '授权类型', dataIndex: 'authType', width: 110,
      render: (v: string) => (v === 'DEPT' ? <Tag color="blue">部门</Tag> : <Tag color="purple">用户</Tag>),
    },
    { title: '授权对象', dataIndex: 'targetName', ellipsis: true, render: (v, r) => v || r.targetId || '-' },
    { title: '对象ID', dataIndex: 'targetId', width: 90, align: 'center' },
    {
      title: '授权时间', dataIndex: 'createTime', width: 165,
      render: (v) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm:ss') : '-'),
    },
    {
      title: '操作', key: 'action', width: 100,
      render: (_, record) => (
        <Popconfirm title="撤销该授权？" onConfirm={() => revoke(record)} okText="撤销" cancelText="取消">
          <Button type="link" size="small" danger icon={<DeleteOutlined />}>撤销</Button>
        </Popconfirm>
      ),
    },
  ];

  return (
    <Modal
      title={`模板授权${templateName ? `：${templateName}` : ''}（#${templateId ?? ''}）`}
      open={open}
      onCancel={onClose}
      footer={null}
      width={760}
      destroyOnClose
    >
      <Tabs
        activeKey={authType}
        onChange={(k) => setAuthType(k as 'DEPT' | 'USER')}
        items={[
          {
            key: 'DEPT',
            label: '授权给部门',
            children: (
              <Form form={form} layout="inline" initialValues={{ authType: 'DEPT' }}>
                <Form.Item name="authType" hidden initialValue="DEPT"><input type="hidden" /></Form.Item>
                <Form.Item name="targetIds" label="部门" rules={[{ required: true, message: '请选择部门' }]}>
                  <TreeSelect
                    treeData={deptTree}
                    treeCheckable
                    showCheckedStrategy={TreeSelect.SHOW_ALL}
                    placeholder="选择部门（可多选）"
                    style={{ minWidth: 320 }}
                    treeDefaultExpandAll
                  />
                </Form.Item>
                <Form.Item>
                  <Button type="primary" icon={<PlusOutlined />} onClick={grant}>授权</Button>
                </Form.Item>
              </Form>
            ),
          },
          {
            key: 'USER',
            label: '授权给用户',
            children: (
              <Form form={form} layout="inline">
                <Form.Item name="authType" hidden initialValue="USER"><input type="hidden" /></Form.Item>
                <Form.Item name="targetIds" label="用户" rules={[{ required: true, message: '请选择用户' }]}>
                  <Select
                    mode="multiple"
                    showSearch
                    optionFilterProp="label"
                    placeholder="选择用户（可多选）"
                    style={{ minWidth: 320 }}
                    options={users.map((u) => {
                      const anyUser = u as unknown as Record<string, any>;
                      const label = anyUser.realName || u.nickname || u.username || u.id;
                      return {
                        value: u.id,
                        label: `${label}${u.deptName ? '（' + u.deptName + '）' : ''}`,
                      };
                    })}
                  />
                </Form.Item>
                <Form.Item>
                  <Button type="primary" icon={<PlusOutlined />} onClick={grant}>授权</Button>
                </Form.Item>
              </Form>
            ),
          },
        ]}
      />

      <Table
        columns={columns}
        dataSource={auths}
        rowKey="id"
        loading={loading}
        size="small"
        locale={{ emptyText: <Empty description="暂无授权（未设置授权的模板对所有模板角色开放）" /> }}
        pagination={{ pageSize: 10, showSizeChanger: false }}
        style={{ marginTop: 8 }}
      />
    </Modal>
  );
};

export default TemplateAuthModal;
