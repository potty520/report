import React, { useEffect, useState, useCallback } from 'react';
import { returnTaskByAssignApi } from '@/services/api';
import {
  Table, Button, Space, Tag, Modal, Form, Input, Select, Radio, message, Popconfirm, App, Transfer,
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
  // 分派类型：FILL=填报人、AUDIT=审核人（后端 /task/{id}/assign 支持 assignType）
  const [assignType, setAssignType] = useState<'FILL' | 'AUDIT'>('FILL');

  const doFetch = useCallback((params: Record<string, any> = {}) => {
    fetchAssignList({ current: 1, size: 10, ...params });
  }, [fetchAssignList]);

  const loadTasks = async () => {
    try {
      // 缺陷修复：原先只查 status=1（已发布），但首次分派后任务会变成 2（填报中），
      // 于是再也选不到该任务——补派审核人/追加填报人这条路直接断掉。
      // 这里拉全部任务，前端只保留「已发布(1)/填报中(2)」两种可继续分派的状态。
      const res = await getTaskListApi({ current: 1, size: 1000 });
      const list = (res.data.data.list || []) as TaskItem[];
      setTaskList(list.filter((t) => t.status === 1 || t.status === 2));
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
    setAssignType('FILL');
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
      // 后端 TaskController.assign 的 assignType 是必填 @RequestParam（没有 required=false），
      // 缺参直接 400。本页只有「任务分派」一个入口，语义为填报分派 → FILL。
      // 缺陷修复：原先固定 FILL，界面无法分派审核人，导致两级审核页面永远空
      await assignTask({
        taskId: values.taskId,
        deptIds: targetKeys.map(Number),
        assignType,
      });
      msg.success(assignType === 'AUDIT' ? '审核分派成功' : '任务分派成功');
      setModalOpen(false);
      doFetch(searchParams);
    } catch (error: any) {
      // 表单校验失败由 Form 自己提示，不弹 toast
      if (error?.errorFields) return;
      // 把后端返回的 msg 透出，避免"分派失败"这类无信息提示
      msg.error(error?.response?.data?.msg || error?.message || '分派失败');
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

    const handleReturn = async (record: any) => {
    const reason = window.prompt('请填写退回原因（必填）');
    if (!reason || !reason.trim()) return;
    try {
      const res: any = await returnTaskByAssignApi(record.taskId ?? record.id, reason.trim());
      if (res?.code === 200) {
        message.success('已退回');
        doFetch();
      } else {
        message.error(res?.msg || '退回失败');
      }
    } catch (e: any) {
      message.error(e?.response?.data?.msg || '退回失败');
    }
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
        <Space>
          <Popconfirm title="确定取消分派?" onConfirm={() => handleCancelAssign(record.id)} okText="确定" cancelText="取消">
            <Button type="link" size="small" danger icon={<DeleteOutlined />}>取消</Button>
          </Popconfirm>
          <Button type="link" size="small" onClick={() => handleReturn(record)}>退回</Button>
        </Space>
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
          <Form.Item label="分派类型" required>
            <Radio.Group value={assignType} onChange={(e) => setAssignType(e.target.value)}>
              <Radio.Button value="FILL">填报分派（下达填报任务）</Radio.Button>
              <Radio.Button value="AUDIT">审核分派（指定审核人）</Radio.Button>
            </Radio.Group>
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
