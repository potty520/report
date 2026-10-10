import React, { useCallback, useEffect, useState } from 'react';
import {
  Table, Button, Space, Tag, App, Form, Input, InputNumber, Select, Modal, Popconfirm, Card,
} from 'antd';
import { PlusOutlined, ReloadOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { getDictAllApi, getDictManageApi, addDictApi, updateDictApi, deleteDictApi, DictItem } from '@/services/api';

/** 数据字典管理（P2-G）：按类型查询/新增/修改/删除 */
const DictPage: React.FC = () => {
  const { message: msg } = App.useApp();
  const [form] = Form.useForm();
  const [dictTypes, setDictTypes] = useState<string[]>([]);
  const [activeType, setActiveType] = useState<string | undefined>(undefined);
  const [list, setList] = useState<DictItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<DictItem | null>(null);

  const load = useCallback(async (type?: string) => {
    setLoading(true);
    try {
      const res = await getDictManageApi(type);
      setList(res.data.data || []);
    } catch (e: any) {
      msg.error(e?.message || '查询失败（字典管理仅超级管理员可用）');
      setList([]);
    } finally {
      setLoading(false);
    }
  }, [msg]);

  useEffect(() => {
    (async () => {
      try {
        const res = await getDictAllApi();
        const grouped = res.data.data || {};
        setDictTypes(Object.keys(grouped));
      } catch {
        setDictTypes([]);
      }
      load();
    })();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const openAdd = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ dictType: activeType, sort: 0, status: 1 });
    setModalOpen(true);
  };

  const openEdit = (record: DictItem) => {
    setEditing(record);
    form.setFieldsValue(record);
    setModalOpen(true);
  };

  const submit = async () => {
    try {
      const values = await form.validateFields();
      if (editing) {
        await updateDictApi({ ...editing, ...values });
        msg.success('已更新');
      } else {
        await addDictApi(values);
        msg.success('已新增');
      }
      setModalOpen(false);
      load(activeType);
    } catch (e: any) {
      if (e?.errorFields) return;
      msg.error(e?.message || '保存失败');
    }
  };

  const remove = async (record: DictItem) => {
    try {
      await deleteDictApi(record.id);
      msg.success('已删除');
      load(activeType);
    } catch (e: any) {
      msg.error(e?.message || '删除失败');
    }
  };

  const columns: ColumnsType<DictItem> = [
    { title: 'ID', dataIndex: 'id', width: 70, align: 'center' },
    { title: '字典类型', dataIndex: 'dictType', width: 170, render: (v) => <Tag color="blue">{v}</Tag> },
    { title: '编码', dataIndex: 'dictCode', width: 130 },
    { title: '显示名', dataIndex: 'dictLabel', width: 150 },
    { title: '排序', dataIndex: 'sort', width: 80, align: 'center' },
    {
      title: '状态', dataIndex: 'status', width: 90, align: 'center',
      render: (v: number) => (v === 1 ? <Tag color="green">启用</Tag> : <Tag>停用</Tag>),
    },
    { title: '备注', dataIndex: 'remark', ellipsis: true, render: (v) => v || '-' },
    {
      title: '操作', key: 'action', width: 150, fixed: 'right',
      render: (_, record) => (
        <Space>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => openEdit(record)}>编辑</Button>
          <Popconfirm title="确认删除该字典项？" onConfirm={() => remove(record)} okText="删除" cancelText="取消">
            <Button type="link" size="small" danger icon={<DeleteOutlined />}>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div style={{ padding: 24, background: '#fff', borderRadius: 8 }}>
      <Space style={{ marginBottom: 16 }} wrap>
        <span>字典类型：</span>
        <Select
          placeholder="全部类型"
          allowClear
          style={{ width: 220 }}
          value={activeType}
          onChange={(v) => { setActiveType(v); load(v); }}
          options={dictTypes.map((t) => ({ value: t, label: t }))}
        />
        <Button icon={<ReloadOutlined />} onClick={() => load(activeType)}>刷新</Button>
        <Button type="primary" icon={<PlusOutlined />} onClick={openAdd}>新增字典项</Button>
      </Space>

      <Card size="small" title={`字典项${activeType ? `（${activeType}）` : '（全部）'}`}>
        <Table
          columns={columns}
          dataSource={list}
          rowKey="id"
          loading={loading}
          size="small"
          scroll={{ x: 1000 }}
          pagination={{ pageSize: 20, showSizeChanger: true, showTotal: (t) => `共 ${t} 条` }}
        />
      </Card>

      <Modal
        title={editing ? '编辑字典项' : '新增字典项'}
        open={modalOpen}
        onOk={submit}
        onCancel={() => setModalOpen(false)}
        destroyOnClose
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item name="dictType" label="字典类型" rules={[{ required: true, message: '请填写字典类型' }]}>
            <Input placeholder="如 filling_cycle / task_status" />
          </Form.Item>
          <Form.Item name="dictCode" label="编码" rules={[{ required: true, message: '请填写编码' }]}>
            <Input placeholder="如 1" />
          </Form.Item>
          <Form.Item name="dictLabel" label="显示名" rules={[{ required: true, message: '请填写显示名' }]}>
            <Input placeholder="如 日报" />
          </Form.Item>
          <Space size="large">
            <Form.Item name="sort" label="排序">
              <InputNumber style={{ width: 120 }} />
            </Form.Item>
            <Form.Item name="status" label="状态">
              <Select style={{ width: 120 }}>
                <Select.Option value={1}>启用</Select.Option>
                <Select.Option value={0}>停用</Select.Option>
              </Select>
            </Form.Item>
          </Space>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default DictPage;
