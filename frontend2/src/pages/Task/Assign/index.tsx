import React, { useEffect, useState, useCallback } from 'react';
import {
  Table, Button, Space, Tag, Modal, Form, Input, Select, message, Popconfirm, App, Transfer,
} from 'antd';
import { PlusOutlined, DeleteOutlined, SearchOutlined, ReloadOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { useTaskStore } from '@/stores/useTaskStore';
import { useDeptStore } from '@/stores/useDeptStore';
import { getTaskListApi } from '@/services/api';
import type { TaskAssignItem, TaskItem, DeptItem } from '@/services/api';
import dayjs from 'dayjs';

const TaskAssignPage: React.FC = () => {
  const { message: msg } = App.useApp();
  const { assignList, assignTotal, assignLoading, fetchAssignList, assignTask, cancelAssign } = useTaskStore();
  const { tree: deptTree, fetchTree: fetchDeptTree } = useDeptStore();

  const [searchForm] = Form.useForm();
  const [editForm] = Form.useForm();
  const [modalOpen, setModalOpen] = useState(false);
  const [searchParams, setSearchParams] = useState<Record<string, any>>({});
  const [taskList, setTaskList] = useState<TaskItem[]>([]);
  const [targetKeys, setTargetKeys] = useState<string[]>([]);

  const doFetch = useCallback((params: Record<string, any> = {}) => {
    fetchAssignList({ current: 1, size: 10, ...params });
  }, [fetchAssignList]);

  const loadTasks = async () => {
    try {
      const res = await getTaskListApi({ current: 1, size: 1000, status: 1 });
      setTaskList(res.data.data.list || []);
    } catch { /* ignore */ }
  };

  useEffect(() => {
    doFetch();
    fetchDeptTree();
    loadTasks();
  }, [doFetch, fetchDeptTree]);

  const handleSearch = () => {
    const values = searchForm.getFieldsValue();
    setSearchParams(values);
    doFetch(values);
  };

  const handleReset = () => {
    searchForm.resetFields();
    setSearchParams({});
    doFetch();
  };

  const handleAdd = () => {
    editForm.resetFields();
    setTargetKeys([]);
    setModalOpen(true);
  };

  const flattenDept = (depts: DeptItem[], prefix = ''): { key: string; title: string }[] => {
    return depts.reduce((acc: { key: string; title: string }[], d) => {
      acc.push({ key: String(d.id), title: prefix + d.deptName });
      if (d.children && d.children.length > 0) {
        acc.push(...flattenDept(d.children, prefix + '  '));
      }
      return acc;
    }, []);
  };

  const deptData = flattenDept(deptTree);

  const handleAssignSubmit = async () => {
    try {
      const values = await editForm.validateFields();
      if (targetKeys.length === 0) {
        msg.warning('请选择至少一个部门');
        return;
      }
      await assignTask({
        taskId: values.taskId,
        deptIds: targetKeys.map(Number),
      });
      msg.success('任务分派成功');
      setModalOpen(false);
      doFetch(searchParams);
    } catch (error: any) {
      if (error.errorFields) return;
      msg.error('分派失败');
    }
  };

  const handleCancelAssign = async (id: number) => {
    try {
      await cancelAssign(id);
      msg.success('已取消分派');
      doFetch(searchParams);
    } catch {
      msg.error('取消失败');
    }
  };

  const statusMap: Record<number, { color: string; text: string }> = {
    0: { color: 'default', text: '未开始' },
    1: { color: 'processing', text: '进行中' },
    2: { color: 'green', text: '已完成' },
  };

  const columns: ColumnsType<TaskAssignItem> = [
    { title: 'ID', dataIndex: 'id', width: 60, align: 'center' },
    { title: '任务名称', dataIndex: 'taskName', width: 200 },
    { title: '分派部门', dataIndex: 'deptName', width: 180 },
    { title: '关联项目', dataIndex: 'projectName', width: 150, render: (v: string) => v || '-' },
    {
      title: '状态', dataIndex: 'status', width: 80, align: 'center',
      render: (v: number) => <Tag color={statusMap[v]?.color}>{statusMap[v]?.text}</Tag>,
    },
    {
      title: '分派时间', dataIndex: 'assignTime', width: 170,
      render: (v: string) => v ? dayjs(v).format('YYYY-MM-DD HH:mm:ss') : '-',
    },
    {
      title: '操作', key: 'action', width: 100, fixed: 'right',
      render: (_: any, record: TaskAssignItem) => (
        <Popconfirm title="确定取消分派?" onConfirm={() => handleCancelAssign(record.id)} okText="确定" cancelText="取消">
          <Button type="link" size="small" danger icon={<DeleteOutlined />}>取消</Button>
        </Popconfirm>
      ),
    },
  ];

  return (
    <div style={{ padding: 24, background: '#fff', borderRadius: 8 }}>
      <div className="search-form" style={{ marginBottom: 16 }}>
        <Form form={searchForm} layout="inline">
          <Form.Item name="taskName" label="任务名称">
            <Input placeholder="请输入" allowClear />
          </Form.Item>
          <Form.Item name="deptName" label="部门">
            <Input placeholder="请输入" allowClear />
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" icon={<SearchOutlined />} onClick={handleSearch}>搜索</Button>
              <Button icon={<ReloadOutlined />} onClick={handleReset}>重置</Button>
            </Space>
          </Form.Item>
        </Form>
      </div>
      <div className="table-operations">
        <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>任务分派</Button>
      </div>
      <Table
        columns={columns}
        dataSource={assignList}
        rowKey="id"
        loading={assignLoading}
        scroll={{ x: 1000 }}
        onChange={(pagination) => doFetch({ current: pagination.current, size: pagination.pageSize, ...searchParams })}
        pagination={{
          total: assignTotal,
          showSizeChanger: true,
          showTotal: (t) => `共 ${t} 条`,
        }}
      />

      <Modal
        title="任务分派"
        open={modalOpen}
        onOk={handleAssignSubmit}
        onCancel={() => setModalOpen(false)}
        width={650}
        destroyOnClose
      >
        <Form form={editForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item name="taskId" label="选择任务" rules={[{ required: true, message: '请选择任务' }]}>
            <Select placeholder="请选择已发布的任务" showSearch optionFilterProp="children">
              {taskList.map((t) => (
                <Select.Option key={t.id} value={t.id}>{t.name}</Select.Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item label="选择部门">
            <Transfer
              dataSource={deptData}
              targetKeys={targetKeys}
              onChange={(nextTargetKeys) => setTargetKeys(nextTargetKeys as string[])}
              render={(item) => item.title}
              listStyle={{ width: 260, height: 300 }}
              titles={['可选部门', '已选部门']}
              showSearch
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default TaskAssignPage;
