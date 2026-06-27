import React, { useState, useEffect } from 'react';
import { connect } from 'dva';
import {
  Table, Button, Select, Space, Form, Row, Col, Card, Tag,
  PageHeader, message, Popconfirm,
} from 'antd';
import {
  PlusOutlined, DeleteOutlined, UserSwitchOutlined,
} from '@ant-design/icons';
import { useSelector, useDispatch } from 'umi';

const { Option } = Select;

const TaskAssign = () => {
  const dispatch = useDispatch();
  const { list, loading } = useSelector((state) => state.task);
  const deptTree = useSelector((state) => state.dept.treeData);
  const [assignForm] = Form.useForm();

  const [selectedTask, setSelectedTask] = useState(null);
  const [assignList, setAssignList] = useState([]);
  const [assignLoading, setAssignLoading] = useState(false);
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10 });

  const [userList, setUserList] = useState([]);

  useEffect(() => {
    dispatch({ type: 'dept/fetchTree' });
    fetchTaskList();
    fetchUsers();
  }, [pagination.current, pagination.pageSize]);

  const fetchTaskList = () => {
    dispatch({
      type: 'task/fetchList',
      payload: { page: pagination.current, size: pagination.pageSize, status: 'PUBLISHED' },
    });
  };

  const fetchUsers = () => {
    setUserList([
      { id: 1, username: 'zhangsan', realName: '张三', deptId: 1, deptName: '技术部' },
      { id: 2, username: 'lisi', realName: '李四', deptId: 1, deptName: '技术部' },
      { id: 3, username: 'wangwu', realName: '王五', deptId: 2, deptName: '产品部' },
      { id: 4, username: 'zhaoliu', realName: '赵六', deptId: 2, deptName: '产品部' },
      { id: 5, username: 'sunqi', realName: '孙七', deptId: 3, deptName: '运营部' },
    ]);
  };

  const handleSelectTask = (record) => {
    setSelectedTask(record);
    setAssignLoading(true);
    setTimeout(() => {
      setAssignList([
        { key: 1, taskId: record.id, userId: 1, userName: '张三', deptName: '技术部', assignType: 'FILL' },
        { key: 2, taskId: record.id, userId: 3, userName: '王五', deptName: '产品部', assignType: 'AUDIT' },
      ]);
      setAssignLoading(false);
    }, 300);
  };

  const handleAddAssign = async () => {
    if (!selectedTask) {
      message.warning('请先选择任务');
      return;
    }
    const values = await assignForm.validateFields();
    const newItem = {
      key: Date.now(),
      taskId: selectedTask.id,
      userId: values.userId,
      userName: userList.find((u) => u.id === values.userId)?.realName || '',
      deptName: userList.find((u) => u.id === values.userId)?.deptName || '',
      assignType: values.assignType,
    };
    const success = await dispatch({
      type: 'task/assignTask',
      payload: {
        id: selectedTask.id,
        userId: values.userId,
        assignType: values.assignType,
      },
    });
    if (success) {
      setAssignList([...assignList, newItem]);
      assignForm.resetFields();
    }
  };

  const handleRemoveAssign = (item) => {
    setAssignList(assignList.filter((a) => a.key !== item.key));
    message.success('已移除分派');
  };

  const assignColumns = [
    { title: '用户', dataIndex: 'userName', width: 100 },
    { title: '部门', dataIndex: 'deptName', width: 120 },
    {
      title: '分派类型',
      dataIndex: 'assignType',
      width: 100,
      render: (v) => (
        <Tag color={v === 'FILL' ? 'blue' : 'orange'}>
          {v === 'FILL' ? '填报' : '审核'}
        </Tag>
      ),
    },
    {
      title: '操作',
      width: 80,
      render: (_, record) => (
        <Popconfirm title="确定移除此分派?" onConfirm={() => handleRemoveAssign(record)}>
          <Button size="small" danger icon={<DeleteOutlined />}>
            移除
          </Button>
        </Popconfirm>
      ),
    },
  ];

  const taskColumns = [
    { title: '任务名称', dataIndex: 'taskName', width: 180 },
    { title: '模板', dataIndex: 'templateName', width: 140 },
    { title: '部门', dataIndex: 'deptName', width: 120 },
    { title: '所属期', dataIndex: 'belongTime', width: 110 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 80,
      render: (v) => {
        const map = {
          PUBLISHED: { color: 'green', text: '已发布' },
          CLOSED: { color: 'red', text: '已关闭' },
        };
        const s = map[v] || { color: 'default', text: v };
        return <Tag color={s.color}>{s.text}</Tag>;
      },
    },
  ];

  return (
    <div>
      <PageHeader title="任务分派" style={{ padding: 0, marginBottom: 16 }} />
      <Row gutter={16}>
        <Col span={10}>
          <Card title="任务列表" size="small" bodyStyle={{ padding: 0 }}>
            <Table
              rowKey="id"
              columns={taskColumns}
              dataSource={list}
              loading={loading}
              size="small"
              scroll={{ x: 600, y: 500 }}
              pagination={{
                current: pagination.current,
                pageSize: pagination.pageSize,
                total,
                size: 'small',
                showSizeChanger: false,
                onChange: (page, pageSize) =>
                  setPagination({ current: page, pageSize }),
              }}
              onRow={(record) => ({
                onClick: () => handleSelectTask(record),
                style: {
                  cursor: 'pointer',
                  background:
                    selectedTask && selectedTask.id === record.id
                      ? '#e6f7ff'
                      : undefined,
                },
              })}
            />
          </Card>
        </Col>
        <Col span={14}>
          <Card
            title={selectedTask ? `分派 - ${selectedTask.taskName}` : '分派详情'}
            size="small"
            bodyStyle={{ padding: 16 }}
          >
            {selectedTask ? (
              <>
                <div style={{ marginBottom: 16, padding: 12, background: '#fafafa', borderRadius: 4 }}>
                  <strong>任务信息：</strong>
                  模板: {selectedTask.templateName} | 部门: {selectedTask.deptName} |
                  截止: {selectedTask.fillDeadline}
                </div>

                <Form form={assignForm} layout="inline" style={{ marginBottom: 16 }}>
                  <Form.Item name="deptId" label="部门">
                    <Select placeholder="选择部门" style={{ width: 140 }} allowClear>
                      {deptTree.map((d) => (
                        <Option key={d.id} value={d.id}>{d.deptName}</Option>
                      ))}
                    </Select>
                  </Form.Item>
                  <Form.Item
                    name="userId"
                    label="用户"
                    rules={[{ required: true, message: '请选择用户' }]}
                  >
                    <Select placeholder="请选择用户" style={{ width: 140 }}>
                      {userList.map((u) => (
                        <Option key={u.id} value={u.id}>{u.realName}</Option>
                      ))}
                    </Select>
                  </Form.Item>
                  <Form.Item
                    name="assignType"
                    label="类型"
                    rules={[{ required: true, message: '请选择分派类型' }]}
                  >
                    <Select placeholder="选择类型" style={{ width: 120 }}>
                      <Option value="FILL">填报</Option>
                      <Option value="AUDIT">审核</Option>
                    </Select>
                  </Form.Item>
                  <Form.Item>
                    <Button
                      type="primary"
                      icon={<PlusOutlined />}
                      onClick={handleAddAssign}
                    >
                      添加分派
                    </Button>
                  </Form.Item>
                </Form>

                <Table
                  rowKey="key"
                  columns={assignColumns}
                  dataSource={assignList}
                  loading={assignLoading}
                  pagination={false}
                  size="small"
                  bordered
                />

                <div style={{ marginTop: 16, textAlign: 'right' }}>
                  <Space>
                    <Button
                      type="primary"
                      icon={<UserSwitchOutlined />}
                      onClick={() => message.success('分派保存成功')}
                    >
                      保存分派
                    </Button>
                  </Space>
                </div>
              </>
            ) : (
              <div style={{ textAlign: 'center', padding: 60, color: '#999' }}>
                <UserSwitchOutlined style={{ fontSize: 48, marginBottom: 12 }} />
                <p>请从左侧选择一个任务进行分派</p>
              </div>
            )}
          </Card>
        </Col>
      </Row>
    </div>
  );
};

export default connect(({ task, dept }) => ({ task, dept }))(TaskAssign);
