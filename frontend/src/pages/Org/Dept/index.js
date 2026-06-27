import React, { useState, useEffect } from 'react';
import { connect } from 'dva';
import {
  Table, Button, Input, Space, Modal, Form, Tree, Popconfirm,
  Row, Col, InputNumber, PageHeader,
} from 'antd';
import {
  PlusOutlined, SearchOutlined, ReloadOutlined,
  EditOutlined, DeleteOutlined,
} from '@ant-design/icons';
import { useSelector, useDispatch } from 'umi';

const DeptManagement = () => {
  const dispatch = useDispatch();
  const { treeData, list, total, loading } = useSelector((state) => state.dept);
  const [form] = Form.useForm();
  const [searchForm] = Form.useForm();

  const [modalVisible, setModalVisible] = useState(false);
  const [editingRecord, setEditingRecord] = useState(null);
  const [selectedDeptId, setSelectedDeptId] = useState(null);
  const [expandedKeys, setExpandedKeys] = useState([]);
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10 });
  const [params, setParams] = useState({});

  useEffect(() => {
    dispatch({ type: 'dept/fetchTree' });
  }, []);

  useEffect(() => {
    fetchList();
  }, [pagination.current, pagination.pageSize, params, selectedDeptId]);

  const fetchList = () => {
    dispatch({
      type: 'dept/fetchList',
      payload: {
        ...params,
        parentId: selectedDeptId,
        page: pagination.current,
        size: pagination.pageSize,
      },
    });
  };

  const handleTreeSelect = (selectedKeys) => {
    setSelectedDeptId(selectedKeys.length > 0 ? selectedKeys[0] : null);
    setPagination((prev) => ({ ...prev, current: 1 }));
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
    if (selectedDeptId) {
      form.setFieldsValue({ parentId: selectedDeptId });
    }
    setModalVisible(true);
  };

  const handleEdit = (record) => {
    setEditingRecord(record);
    form.setFieldsValue({
      deptName: record.deptName,
      deptCode: record.deptCode,
      leader: record.leader,
      phone: record.phone,
      parentId: record.parentId,
      sort: record.sort,
    });
    setModalVisible(true);
  };

  const handleDelete = (id) => {
    dispatch({ type: 'dept/delete', payload: id }).then((success) => {
      if (success) {
        dispatch({ type: 'dept/fetchTree' });
        fetchList();
      }
    });
  };

  const handleModalOk = async () => {
    const values = await form.validateFields();
    let success;
    if (editingRecord) {
      success = await dispatch({
        type: 'dept/update',
        payload: { ...values, id: editingRecord.id },
      });
    } else {
      success = await dispatch({ type: 'dept/add', payload: values });
    }
    if (success) {
      setModalVisible(false);
      dispatch({ type: 'dept/fetchTree' });
      fetchList();
    }
  };

  const columns = [
    { title: '部门名称', dataIndex: 'deptName', width: 160 },
    { title: '部门编码', dataIndex: 'deptCode', width: 130 },
    { title: '负责人', dataIndex: 'leader', width: 110 },
    { title: '联系电话', dataIndex: 'phone', width: 140 },
    { title: '排序', dataIndex: 'sort', width: 80, align: 'center' },
    { title: '创建时间', dataIndex: 'createTime', width: 170 },
    {
      title: '操作',
      width: 160,
      fixed: 'right',
      render: (_, record) => (
        <Space size="small">
          <Button size="small" icon={<EditOutlined />} onClick={() => handleEdit(record)}>
            编辑
          </Button>
          <Popconfirm title="确定删除该部门吗?" onConfirm={() => handleDelete(record.id)}>
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
      <PageHeader title="部门管理" style={{ padding: 0, marginBottom: 16 }} />
      <Row gutter={16}>
        <Col span={5}>
          <div
            style={{
              background: '#fff',
              padding: 12,
              borderRadius: 4,
              minHeight: 400,
              border: '1px solid #f0f0f0',
            }}
          >
            <h4 style={{ marginBottom: 12 }}>部门树</h4>
            {treeData.length > 0 ? (
              <Tree
                treeData={treeData}
                defaultExpandAll
                expandedKeys={expandedKeys}
                onExpand={(keys) => setExpandedKeys(keys)}
                onSelect={handleTreeSelect}
                selectedKeys={selectedDeptId ? [selectedDeptId] : []}
                fieldNames={{ title: 'deptName', key: 'id', children: 'children' }}
                showLine
              />
            ) : (
              <div style={{ textAlign: 'center', padding: 40, color: '#999' }}>
                暂无部门数据
              </div>
            )}
          </div>
        </Col>
        <Col span={19}>
          <div style={{ background: '#fff', padding: 16, borderRadius: 4, border: '1px solid #f0f0f0' }}>
            <Form
              form={searchForm}
              layout="inline"
              onFinish={handleSearch}
              style={{ marginBottom: 16 }}
            >
              <Form.Item name="deptName">
                <Input placeholder="部门名称" style={{ width: 150 }} />
              </Form.Item>
              <Form.Item name="deptCode">
                <Input placeholder="部门编码" style={{ width: 150 }} />
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
                新增部门
              </Button>
            </div>

            <Table
              rowKey="id"
              columns={columns}
              dataSource={list}
              loading={loading}
              scroll={{ x: 900 }}
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
        </Col>
      </Row>

      <Modal
        title={editingRecord ? '编辑部门' : '新增部门'}
        open={modalVisible}
        onOk={handleModalOk}
        onCancel={() => setModalVisible(false)}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="deptName"
                label="部门名称"
                rules={[{ required: true, message: '请输入部门名称' }]}
              >
                <Input placeholder="请输入部门名称" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="deptCode"
                label="部门编码"
                rules={[{ required: true, message: '请输入部门编码' }]}
              >
                <Input placeholder="请输入部门编码" disabled={!!editingRecord} />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="leader" label="负责人">
                <Input placeholder="请输入负责人" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="phone" label="联系电话">
                <Input placeholder="请输入联系电话" />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="parentId" label="上级部门">
                <Input placeholder="上级部门ID" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="sort" label="排序" initialValue={0}>
                <InputNumber style={{ width: '100%' }} min={0} placeholder="排序号" />
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Modal>
    </div>
  );
};

export default connect(({ dept }) => ({ dept }))(DeptManagement);
