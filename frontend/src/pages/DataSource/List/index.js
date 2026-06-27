import React, { useState, useEffect } from 'react';
import { connect, history } from 'umi';
import { Table, Button, Card, Input, Select, Space, Modal, Form, message, Row, Col } from 'antd';
import { PlusOutlined, SearchOutlined, ApiOutlined } from '@ant-design/icons';

const { Option } = Select;

const DatasourceList = ({ dispatch, datasource }) => {
  const { list = [], total = 0 } = datasource;
  const [modalVisible, setModalVisible] = useState(false);
  const [editingRecord, setEditingRecord] = useState(null);
  const [form] = Form.useForm();
  const [searchParams, setSearchParams] = useState({});
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10 });
  const [loading, setLoading] = useState(false);

  useEffect(() => { fetchList(); }, [pagination]);

  const fetchList = (params = {}) => {
    setLoading(true);
    dispatch({ type: 'datasource/fetchList', payload: { pageNum: pagination.current, pageSize: pagination.pageSize, ...searchParams, ...params } }).then(() => setLoading(false));
  };

  const handleAdd = () => { setEditingRecord(null); form.resetFields(); setModalVisible(true); };
  const handleEdit = (r) => { setEditingRecord(r); form.setFieldsValue(r); setModalVisible(true); };
  const handleDelete = (id) => { dispatch({ type: 'datasource/remove', payload: id }).then(() => fetchList()); };

  const handleOk = () => {
    form.validateFields().then(v => {
      dispatch({ type: editingRecord ? 'datasource/update' : 'datasource/add', payload: editingRecord ? { ...v, id: editingRecord.id } : v });
      setModalVisible(false); form.resetFields(); fetchList();
    });
  };

  const handleTest = () => {
    form.validateFields().then(v => {
      dispatch({ type: 'datasource/testConnection', payload: v }).then(res => message.success(res ? '连接成功' : '连接失败'));
    });
  };

  const columns = [
    { title: '名称', dataIndex: 'dsName' }, { title: '类型', dataIndex: 'dsType' },
    { title: 'IP', dataIndex: 'ip' }, { title: '端口', dataIndex: 'port' },
    { title: '数据库', dataIndex: 'dbName' },
    { title: '操作', render: (_, r) => (<Space><Button size="small" onClick={() => handleEdit(r)}>编辑</Button><Button size="small" danger onClick={() => handleDelete(r.id)}>删除</Button></Space>) },
  ];

  return (
    <Card title="数据源管理" extra={<Button type="primary" icon={<PlusOutlined/>} onClick={handleAdd}>新增</Button>}>
      <Row gutter={16} style={{ marginBottom: 16 }}><Col span={6}><Input placeholder="名称" value={searchParams.dsName} onChange={e => setSearchParams({ ...searchParams, dsName: e.target.value })} /></Col><Col span={4}><Space><Button type="primary" icon={<SearchOutlined />} onClick={() => fetchList()}>查询</Button><Button onClick={() => { setSearchParams({}); fetchList({}); }}>重置</Button></Space></Col></Row>
      <Table columns={columns} dataSource={list} rowKey="id" loading={loading} pagination={{ ...pagination, total, onChange: (p, s) => setPagination({ current: p, pageSize: s }) }} />
      <Modal title={editingRecord ? '编辑' : '新增'} open={modalVisible} onOk={handleOk} onCancel={() => setModalVisible(false)}>
        <Form form={form} labelCol={{ span: 6 }}>
          <Form.Item name="dsName" label="名称" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="dsType" label="类型"><Select><Option value="MYSQL">MySQL</Option><Option value="ORACLE">Oracle</Option></Select></Form.Item>
          <Form.Item name="ip" label="IP" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="port" label="端口"><Input /></Form.Item>
          <Form.Item name="dbName" label="数据库" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="username" label="用户名"><Input /></Form.Item>
          <Form.Item name="password" label="密码"><Input.Password /></Form.Item>
          <Form.Item label=" " colon={false}><Button icon={<ApiOutlined />} onClick={handleTest}>测试连接</Button></Form.Item>
        </Form>
      </Modal>
    </Card>
  );
};
export default connect(({ datasource }) => ({ datasource }))(DatasourceList);
