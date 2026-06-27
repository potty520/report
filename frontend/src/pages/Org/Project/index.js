import React, { useState, useEffect } from 'react';
import { connect } from 'dva';
import {
  Table, Button, Input, Space, Modal, Form, Tree, Popconfirm,
  Row, Col, PageHeader,
} from 'antd';
import {
  PlusOutlined, SearchOutlined, ReloadOutlined,
  EditOutlined, DeleteOutlined,
} from '@ant-design/icons';
import { useSelector, useDispatch } from 'umi';

const { TextArea } = Input;

const ProjectManagement = () => {
  const dispatch = useDispatch();
  const { list, total, loading } = useSelector((state) => state.project);
  const deptTree = useSelector((state) => state.dept.treeData);
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
      type: 'project/fetchList',
      payload: {
        ...params,
        deptId: selectedDeptId,
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
      form.setFieldsValue({ deptId: selectedDeptId });
    }
    setModalVisible(true);
  };

  const handleEdit = (record) => {
    setEditingRecord(record);
    form.setFieldsValue({
      projectName: record.projectName,
      projectCode: record.projectCode,
      deptId: record.deptId,
      description: record.description,
    });
    setModalVisible(true);
  };

  const handleDelete = (id) => {
    dispatch({ type: 'project/delete', payload: id }).then((success) => {
      if (success) fetchList();
    });
  };

  const handleModalOk = async () => {
    const values = await form.validateFields();
    let success;
    if (editingRecord) {
      success = await dispatch({
        type: 'project/update',
        payload: { ...values, id: editingRecord.id },
      });
    } else {
      success = await dispatch({ type: 'project/add', payload: values });
    }
    if (success) {
      setModalVisible(false);
      fetchList();
    }
  };

  const columns = [
    { title: '项目名称', dataIndex: 'projectName', width: 160 },
    { title: '项目编码', dataIndex: 'projectCode', width: 140 },
    { title: '所属部门', dataIndex: 'deptName', width: 140 },
    { title: '描述', dataIndex: 'description', width: 220, ellipsis: true },
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
          <Popconfirm title="确定删除该项目吗?" onConfirm={() => handleDelete(record.id)}>
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
      <PageHeader title="项目管理" style={{ padding: 0, marginBottom: 16 }} />
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
            {deptTree.length > 0 ? (
              <Tree
                treeData={deptTree}
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
              <Form.Item name="projectName">
                <Input placeholder="项目名称" style={{ width: 150 }} />
              </Form.Item>
              <Form.Item name="projectCode">
                <Input placeholder="项目编码" style={{ width: 150 }} />
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
                新增项目
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
        title={editingRecord ? '编辑项目' : '新增项目'}
        open={modalVisible}
        onOk={handleModalOk}
        onCancel={() => setModalVisible(false)}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="projectName"
                label="项目名称"
                rules={[{ required: true, message: '请输入项目名称' }]}
              >
                <Input placeholder="请输入项目名称" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="projectCode"
                label="项目编码"
                rules={[{ required: true, message: '请输入项目编码' }]}
              >
                <Input placeholder="请输入项目编码" disabled={!!editingRecord} />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item
            name="deptId"
            label="所属部门"
            rules={[{ required: true, message: '请选择所属部门' }]}
          >
            <Input placeholder="部门ID" />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <TextArea rows={3} placeholder="请输入项目描述" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default connect(({ project, dept }) => ({ project, dept }))(ProjectManagement);
