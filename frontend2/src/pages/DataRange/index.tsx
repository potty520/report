import React, { useCallback, useEffect, useState } from 'react';
import {
  Table, Button, Space, Tag, App, Form, Input, InputNumber, Select, Modal, Popconfirm, Card,
  Row, Col, Alert, Empty,
} from 'antd';
import {
  PlusOutlined, ReloadOutlined, EditOutlined, DeleteOutlined, PlayCircleOutlined, EyeOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import {
  getDataRangeListApi, saveDataRangeApi, updateDataRangeApi, deleteDataRangeApi,
  previewDataRangeApi, runDataRangeSqlApi, getDatasourceListApi,
  DataRangeConfig, DataRangeRunResult, DatasourceItem,
} from '@/services/api';

const resultColumns = (columns: string[] = []): ColumnsType<Record<string, any>> =>
  columns.map((c) => ({ title: c, dataIndex: c, key: c, ellipsis: true, render: (v: any) => (v === null || v === undefined ? '-' : String(v)) }));

/** 数据范围 SQL 配置（P2-G）：配置 CRUD + SQL 试跑 + 预览（含 Para_DepID 变量） */
const DataRangePage: React.FC = () => {
  const { message: msg } = App.useApp();
  const [form] = Form.useForm();
  const [list, setList] = useState<DataRangeConfig[]>([]);
  const [loading, setLoading] = useState(false);
  const [datasources, setDatasources] = useState<DatasourceItem[]>([]);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<DataRangeConfig | null>(null);
  const [testResult, setTestResult] = useState<DataRangeRunResult | null>(null);
  const [testing, setTesting] = useState(false);
  const [previewOpen, setPreviewOpen] = useState(false);
  const [previewResult, setPreviewResult] = useState<DataRangeRunResult | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await getDataRangeListApi();
      setList(res.data.data || []);
    } catch (e: any) {
      msg.error(e?.message || '加载失败');
      setList([]);
    } finally {
      setLoading(false);
    }
  }, [msg]);

  useEffect(() => {
    load();
    (async () => {
      try {
        const res = await getDatasourceListApi({ page: 1, pageSize: 100 });
        setDatasources(res.data.data?.list || []);
      } catch {
        setDatasources([]);
      }
    })();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const openAdd = () => {
    setEditing(null);
    setTestResult(null);
    form.resetFields();
    form.setFieldsValue({ status: 1, sqlText: 'SELECT * FROM your_table WHERE dept_id = Para_DepID' });
    setModalOpen(true);
  };

  const openEdit = (record: DataRangeConfig) => {
    setEditing(record);
    setTestResult(null);
    form.setFieldsValue(record);
    setModalOpen(true);
  };

  const submit = async () => {
    try {
      const values = await form.validateFields();
      if (editing?.id) {
        await updateDataRangeApi({ ...editing, ...values });
        msg.success('已更新');
      } else {
        await saveDataRangeApi(values);
        msg.success('已保存');
      }
      setModalOpen(false);
      load();
    } catch (e: any) {
      if (e?.errorFields) return;
      msg.error(e?.message || '保存失败');
    }
  };

  const remove = async (record: DataRangeConfig) => {
    try {
      await deleteDataRangeApi(record.id as number);
      msg.success('已删除');
      load();
    } catch (e: any) {
      msg.error(e?.message || '删除失败');
    }
  };

  const runSql = async () => {
    const values = form.getFieldsValue();
    if (!values.sqlText) {
      msg.warning('请先填写 SQL');
      return;
    }
    setTesting(true);
    try {
      const res = await runDataRangeSqlApi({
        datasourceId: values.datasourceId,
        sql: values.sqlText,
        limit: 20,
      });
      setTestResult(res.data.data || null);
      msg.success('试跑成功');
    } catch (e: any) {
      setTestResult(null);
      msg.error(e?.message || '试跑失败');
    } finally {
      setTesting(false);
    }
  };

  const openPreview = async (record: DataRangeConfig) => {
    try {
      const res = await previewDataRangeApi(record.id as number);
      setPreviewResult(res.data.data || null);
      setPreviewOpen(true);
    } catch (e: any) {
      msg.error(e?.message || '预览失败');
    }
  };

  const columns: ColumnsType<DataRangeConfig> = [
    { title: 'ID', dataIndex: 'id', width: 70, align: 'center' },
    { title: '配置名称', dataIndex: 'configName', width: 200, render: (v) => v || '-' },
    {
      title: '数据源', dataIndex: 'datasourceId', width: 160,
      render: (v) => {
        const hit = datasources.find((d) => d.id === v);
        return hit ? `${hit.name}（${hit.type}）` : (v || '-');
      },
    },
    { title: 'SQL', dataIndex: 'sqlText', ellipsis: true, render: (v) => <code style={{ fontSize: 12 }}>{v || '-'}</code> },
    {
      title: '状态', dataIndex: 'status', width: 90, align: 'center',
      render: (v: number) => (v === 1 ? <Tag color="green">启用</Tag> : <Tag>停用</Tag>),
    },
    { title: '备注', dataIndex: 'remark', width: 140, ellipsis: true, render: (v) => v || '-' },
    {
      title: '操作', key: 'action', width: 210, fixed: 'right',
      render: (_, record) => (
        <Space>
          <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => openPreview(record)}>预览</Button>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => openEdit(record)}>编辑</Button>
          <Popconfirm title="确认删除该配置？" onConfirm={() => remove(record)} okText="删除" cancelText="取消">
            <Button type="link" size="small" danger icon={<DeleteOutlined />}>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const renderResult = (r: DataRangeRunResult | null) => {
    if (!r) return <Empty description="暂无结果" />;
    return (
      <div>
        <Alert
          type="success"
          showIcon
          style={{ marginBottom: 8 }}
          message={`执行 SQL：${r.sql || ''}`}
          description={`返回 ${r.rowCount ?? (r.rows || []).length} 行，耗时 ${r.costMs ?? '-'} ms`}
        />
        <Table
          size="small"
          columns={resultColumns(r.columns || [])}
          dataSource={r.rows || []}
          rowKey={(_, i) => String(i)}
          scroll={{ x: Math.max(600, (r.columns || []).length * 140) }}
          pagination={false}
        />
      </div>
    );
  };

  return (
    <div style={{ padding: 24, background: '#fff', borderRadius: 8 }}>
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="数据范围 SQL 支持变量 Para_DepID / Para_UserId；仅允许 SELECT，且会在外层自动加 limit 保护。"
      />

      <Space style={{ marginBottom: 16 }}>
        <Button icon={<ReloadOutlined />} onClick={load}>刷新</Button>
        <Button type="primary" icon={<PlusOutlined />} onClick={openAdd}>新增配置</Button>
      </Space>

      <Card size="small" title="数据范围配置">
        <Table
          columns={columns}
          dataSource={list}
          rowKey="id"
          loading={loading}
          size="small"
          scroll={{ x: 1200 }}
          pagination={{ pageSize: 10, showSizeChanger: true, showTotal: (t) => `共 ${t} 条` }}
        />
      </Card>

      <Modal
        title={editing ? '编辑数据范围配置' : '新增数据范围配置'}
        open={modalOpen}
        onOk={submit}
        onCancel={() => setModalOpen(false)}
        width={900}
        destroyOnClose
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="configName" label="配置名称" rules={[{ required: true, message: '请填写名称' }]}>
                <Input placeholder="如：部门数据范围" />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="datasourceId" label="数据源" rules={[{ required: true, message: '请选择数据源' }]}>
                <Select placeholder="选择数据源" options={datasources.map((d) => ({ value: d.id, label: `${d.name}（${d.type}）` }))} />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="status" label="状态">
                <Select>
                  <Select.Option value={1}>启用</Select.Option>
                  <Select.Option value={0}>停用</Select.Option>
                </Select>
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="sqlText" label="SQL（支持 Para_DepID / Para_UserId 变量）" rules={[{ required: true, message: '请填写 SQL' }]}>
            <Input.TextArea rows={6} style={{ fontFamily: 'monospace' }} placeholder="SELECT * FROM t WHERE dept_id = Para_DepID" />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input />
          </Form.Item>
          <Space style={{ marginBottom: 8 }}>
            <Button icon={<PlayCircleOutlined />} loading={testing} onClick={runSql}>SQL 试跑（不落库，limit 20）</Button>
          </Space>
          {testResult && renderResult(testResult)}
        </Form>
      </Modal>

      <Modal
        title="预览（按配置执行）"
        open={previewOpen}
        onCancel={() => setPreviewOpen(false)}
        footer={null}
        width={900}
      >
        {renderResult(previewResult)}
      </Modal>
    </div>
  );
};

export default DataRangePage;
