import React, { useState, useEffect } from 'react';
import { connect } from 'dva';
import {
  Table, Button, Input, Space, Modal, Form, Select, Popconfirm,
  PageHeader, Tag, message,
} from 'antd';
import {
  PlusOutlined, SearchOutlined, ReloadOutlined,
  EditOutlined, DeleteOutlined, ApiOutlined,
} from '@ant-design/icons';
import { useSelector, useDispatch } from 'umi';

const { Option } = Select;
const { Input: InputPassword } = Input;

const DatasourceList = () => {
  const dispatch = useDispatch();
  const { list, total, loading } = useSelector((state) => state.datasource);
  const [form] = Form.useForm();
  const [searchForm] = Form.useForm();

  const [modalVisible, setModalVisible] = useState(false);
  const [editingRecord, setEditingRecord] = useState(null);
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10 });
  const [params, setParams] = useState({});

  useEffect(() => {
    fetchList();
  }, [pagination.current, pagination.pageSize, params]);

  const fetchList = () => {
    dispatch({
      type: 'datasource/fetchList',
      payload: { ...params, page: pagination.current, size: pagination.pageSize },
    });
  };

  const handleSearch = (values) => {
    setParams(values);
    setPagination((prev) => ({ ...prev, current: 1 }));
  };

  const handleReset = () => {
    searchForm.resetFields();
    setParams({});
    setPagination((prev) => ({ ...prev, current: 1 }));
  };

  const handleAdd = () => {
    setEditingRecord(null);
    form.resetFields();
    setModalVisible(true);
  };

  const handleEdit = (record) => {
    setEditingRecord(record);
    form.setFieldsValue({
      dsName: record.dsName,
      dsType: record.dsType,
      ip: record.ip,
      port: record.port,
      dbName: record.dbName,
      username: record.username,
      password: '', // 不回显密码
    });
    setModalVisible(true);
  };

  const handleDelete = (id) => {
    dispatch({ type: 'datasource/delete', payload: id }).then((success) => {
      if (success) fetchList();
    });
  };

  const handleTestConnection = (record) => {
    dispatch({ type: 'datasource/testConnection', payload: record.id }).then((success) => {
      if (success) {
        message.success(`${record.dsName} 连接测试成功`);
      }
    });
  };

  const handleModalOk = async () => {
    const values = await form.validateFields();
    let success;
    if (editingRecord) {
      const payload = { ...values, id: editingRecord.id };
      // 如果没有输入密码，不更新密码字段
      if (!values.password) delete payload.password;
      success = await dispatch({ type: 'datasource/update', payload });
    } else {
      success = await dispatch({ type: 'datasource/add', payload: values });
    }
    if (success) {
      setModalVisible(false);
      fetchList();
    }
  };

  const dsTypeMap = {
    MYSQL: { color: 'blue', text: 'MySQL' },
    POSTGRESQL: { color: 'green', text: 'PostgreSQL' },
    ORACLE: { color: 'red', text: 'Oracle' },
    SQLSERVER: { color: 'orange', text: 'SQL Server' },
  };

  const columns = [
    { title: '数据源名称', dataIndex: 'dsName', width: 150 },
    {
      title: '类型',
      dataIndex: 'dsType',
      width: 110,
      render: (v) => {
        const t = dsTypeMap[v] || { color: 'default', text: v };
        return <Tag color={t.color}>{t.text}</Tag>;
      },
    },
    { title: 'IP', dataIndex: 'ip', width: 140 },
    { title: '端口', dataIndex: 'port', width: 70 },
    { title: '数据库名', dataIndex: 'dbName', width: 140 },
    { title: '用户名', dataIndex: 'username', width: 110 },
    { title: '创建时间', dataIndex: 'createTime', width: 170 },
    {
      title: '操作',
      width: 240,
      fixed: 'right',
      render: (_, record) => (
        <Space size="small">
          <Button size="small" icon={<EditOutlined />} onClick={() => handleEdit(record)}>
            编辑
          </Button>
          <Button size="small" icon={<ApiOutlined />} onClick={() => handleTestConnection(record)}>
            测试连接
          </Button>
          <Popconfirm title="确定删除该数据源吗?" onConfirm={() => handleDelete(record.id)}>
            <Button size="small" danger icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <PageHeader title="数据源管理" style={{ padding: 0, marginBottom: 16 }} />
      <div style={{ background: '#fff', padding: 16, borderRadius: 4, border: '1px solid #f0f0f0' }}>
        <Form
          form={searchForm}
          layout="inline"
          onFinish={handleSearch}
          style={{ marginBottom: 16 }}
        >
          <Form.Item name="dsName">
            <Input placeholder="数据源名称" style={{ width: 160 }} />
          </Form.Item>
          <Form.Item name="dsType">
            <Select placeholder="数据源类型" allowClear style={{ width: 140 }}>
              <Option value="MYSQL">MySQL</Option>
              <Option value="POSTGRESQL">PostgreSQL</Option>
              <Option value="ORACLE">Oracle</Option>
              <Option value="SQLSERVER">SQL Server</Option>
            </Select>
          </Form.Item>
          <Form.Item name="ip">
            <Input placeholder="IP地址" style={{ width: 140 }} />
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit" icon={<SearchOutlined />}>
                搜索
              </Button>
              <Button onClick={handleReset} icon={<ReloadOutlined />}>
                重置
              </Button>
            </Space>
          </Form.Item>
        </Form>

        <div style={{ marginBottom: 16 }}>
          <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>
            新增数据源
          </Button>
        </div>

        <Table
          rowKey="id"
          columns={columns}
          dataSource={list}
          loading={loading}
          scroll={{ x: 1100 }}
          pagination={{
            current: pagination.current,
            pageSize: pagination.pageSize,
            total,
            showSizeChanger: true,
            showQuickJumper: true,
            showTotal: (t) => `共 ${t} 条`,
            onChange: (page, pageSize) =>
              setPagination({ current: page, pageSize }),
          }}
        />
      </div>

      <Modal
        title={editingRecord ? '编辑数据源' : '新增数据源'}
        open={modalVisible}
        onOk={handleModalOk}
        onCancel={() => setModalVisible(false)}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="dsName"
            label="数据源名称"
            rules={[{ required: true, message: '请输入数据源名称' }]}
          >
            <Input placeholder="请输入数据源名称" />
          </Form.Item>
          <Form.Item
            name="dsType"
            label="数据源类型"
            rules={[{ required: true, message: '请选择数据源类型' }]}
          >
            <Select placeholder="请选择数据源类型">
              <Option value="MYSQL">MySQL</Option>
              <Option value="POSTGRESQL">PostgreSQL</Option>
              <Option value="ORACLE">Oracle</Option>
              <Option value="SQLSERVER">SQL Server</Option>
            </Select>
          </Form.Item>
          <div style={{ display: 'flex', gap: 16 }}>
            <Form.Item
              name="ip"
              label="IP地址"
              rules={[{ required: true, message: '请输入IP地址' }]}
              style={{ flex: 1 }}
            >
              <Input placeholder="IP地址" />
            </Form.Item>
            <Form.Item
              name="port"
              label="端口"
              rules={[{ required: true, message: '请输入端口' }]}
              initialValue={3306}
              style={{ width: 120 }}
            >
              <Input placeholder="端口" />
            </Form.Item>
          </div>
          <Form.Item
            name="dbName"
            label="数据库名"
            rules={[{ required: true, message: '请输入数据库名' }]}
          >
            <Input placeholder="请输入数据库名" />
          </Form.Item>
          <Form.Item
            name="username"
            label="用户名"
            rules={[{ required: true, message: '请输入用户名' }]}
          >
            <Input placeholder="请输入用户名" />
          </Form.Item>
          <Form.Item
            name="password"
            label="密码"
            rules={
              editingRecord
                ? []
                : [{ required: true, message: '请输入密码' }]
            }
          >
            <InputPassword placeholder={editingRecord ? '留空则不修改' : '请输入密码'} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default connect(({ datasource }) => ({ datasource }))(DatasourceList);
