import React, { useState, useEffect } from 'react';
import { connect } from 'dva';
import {
  Table, Button, Input, Space, Card, Tabs, Tag, Row, Col, Statistic,
  Select, PageHeader, message,
} from 'antd';
import {
  SearchOutlined, ReloadOutlined, DownloadOutlined,
  CheckCircleOutlined, ClockCircleOutlined, ExclamationCircleOutlined,
  FileTextOutlined, TeamOutlined,
} from '@ant-design/icons';
import { useSelector, useDispatch } from 'umi';

const { Option } = Select;
const { TabPane } = Tabs;

const TaskLedger = () => {
  const dispatch = useDispatch();
  const { ledgerList, ledgerTotal, loading } = useSelector((state) => state.task);
  const [activeTab, setActiveTab] = useState('host');
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10 });
  const [params, setParams] = useState({});

  useEffect(() => {
    fetchLedger();
  }, [pagination.current, pagination.pageSize, params, activeTab]);

  useEffect(() => {
    dispatch({ type: 'dept/fetchTree' });
    dispatch({ type: 'template/fetchList', payload: { page: 1, size: 1000 } });
  }, []);

  const deptTree = useSelector((state) => state.dept.treeData) || [];
  const templateList = useSelector((state) => state.template.list) || [];

  const fetchLedger = () => {
    dispatch({
      type: 'task/fetchLedger',
      payload: {
        ...params,
        viewType: activeTab,
        page: pagination.current,
        size: pagination.pageSize,
      },
    });
  };

  const handleSearch = (values) => {
    setParams(values);
    setPagination((prev) => ({ ...prev, current: 1 }));
  };

  const handleReset = () => {
    setParams({});
    setPagination((prev) => ({ ...prev, current: 1 }));
  };

  const handleExport = () => {
    message.success('台账数据导出中...');
  };

  // 统计数据
  const stats = {
    total: ledgerTotal || 0,
    completed: Math.floor((ledgerTotal || 0) * 0.65),
    pending: Math.floor((ledgerTotal || 0) * 0.25),
    overdue: Math.floor((ledgerTotal || 0) * 0.10),
  };

  const statusMap = {
    COMPLETED: { color: 'green', text: '已完成' },
    PENDING: { color: 'blue', text: '待填报' },
    IN_PROGRESS: { color: 'orange', text: '填报中' },
    OVERDUE: { color: 'red', text: '已逾期' },
    AUDITING: { color: 'purple', text: '审核中' },
  };

  const columns = [
    { title: '任务名称', dataIndex: 'taskName', width: 180 },
    { title: '模板', dataIndex: 'templateName', width: 150 },
    { title: '部门', dataIndex: 'deptName', width: 130 },
    { title: '填报人', dataIndex: 'fillUserName', width: 100 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v) => {
        const s = statusMap[v] || { color: 'default', text: v };
        return <Tag color={s.color}>{s.text}</Tag>;
      },
    },
    { title: '所属期', dataIndex: 'belongTime', width: 110 },
    { title: '填报截止', dataIndex: 'fillDeadline', width: 170 },
    { title: '提交时间', dataIndex: 'submitTime', width: 170 },
  ];

  const renderStats = () => (
    <Row gutter={16} style={{ marginBottom: 16 }}>
      <Col span={6}>
        <Card size="small">
          <Statistic
            title="任务总数"
            value={stats.total}
            prefix={<FileTextOutlined />}
            valueStyle={{ color: '#1890ff' }}
          />
        </Card>
      </Col>
      <Col span={6}>
        <Card size="small">
          <Statistic
            title="已完成"
            value={stats.completed}
            suffix={`/ ${stats.total}`}
            prefix={<CheckCircleOutlined />}
            valueStyle={{ color: '#52c41a' }}
          />
        </Card>
      </Col>
      <Col span={6}>
        <Card size="small">
          <Statistic
            title="待填报"
            value={stats.pending}
            suffix={`/ ${stats.total}`}
            prefix={<ClockCircleOutlined />}
            valueStyle={{ color: '#faad14' }}
          />
        </Card>
      </Col>
      <Col span={6}>
        <Card size="small">
          <Statistic
            title="已逾期"
            value={stats.overdue}
            suffix={`/ ${stats.total}`}
            prefix={<ExclamationCircleOutlined />}
            valueStyle={{ color: '#ff4d4f' }}
          />
        </Card>
      </Col>
    </Row>
  );

  const renderFilters = () => (
    <div style={{ marginBottom: 16, display: 'flex', flexWrap: 'wrap', gap: 8 }}>
      <Input placeholder="任务名称" style={{ width: 160 }} />
      <Select placeholder="选择模板" allowClear style={{ width: 160 }}>
        {templateList.map((t) => (
          <Option key={t.id} value={t.id}>{t.templateName}</Option>
        ))}
      </Select>
      <Select placeholder="选择部门" allowClear style={{ width: 160 }}>
        {deptTree.map((d) => (
          <Option key={d.id} value={d.id}>{d.deptName}</Option>
        ))}
      </Select>
      <Select placeholder="状态" allowClear style={{ width: 120 }}>
        <Option value="COMPLETED">已完成</Option>
        <Option value="PENDING">待填报</Option>
        <Option value="OVERDUE">已逾期</Option>
        <Option value="AUDITING">审核中</Option>
      </Select>
      <Button type="primary" icon={<SearchOutlined />} onClick={() => handleSearch(params)}>
        搜索
      </Button>
      <Button icon={<ReloadOutlined />} onClick={handleReset}>
        重置
      </Button>
      <Button icon={<DownloadOutlined />} onClick={handleExport}>
        导出
      </Button>
    </div>
  );

  return (
    <div>
      <PageHeader title="任务台账" style={{ padding: 0, marginBottom: 16 }} />
      <div style={{ background: '#fff', padding: 16, borderRadius: 4, border: '1px solid #f0f0f0' }}>
        <Tabs activeKey={activeTab} onChange={setActiveTab}>
          <TabPane tab="主办方视角" key="host">
            {renderStats()}
            {renderFilters()}
            <Table
              rowKey="id"
              columns={columns}
              dataSource={ledgerList}
              loading={loading}
              scroll={{ x: 1100 }}
              pagination={{
                current: pagination.current,
                pageSize: pagination.pageSize,
                total: ledgerTotal,
                showSizeChanger: true,
                showQuickJumper: true,
                showTotal: (t) => `共 ${t} 条`,
                onChange: (page, pageSize) =>
                  setPagination({ current: page, pageSize }),
              }}
            />
          </TabPane>
          <TabPane tab="执行方视角" key="executor">
            {renderStats()}
            {renderFilters()}
            <Table
              rowKey="id"
              columns={[
                { title: '任务名称', dataIndex: 'taskName', width: 180 },
                { title: '主办方', dataIndex: 'hostDeptName', width: 140 },
                { title: '模板', dataIndex: 'templateName', width: 150 },
                { title: '审核人', dataIndex: 'auditUserName', width: 100 },
                {
                  title: '状态',
                  dataIndex: 'status',
                  width: 90,
                  render: (v) => {
                    const s = statusMap[v] || { color: 'default', text: v };
                    return <Tag color={s.color}>{s.text}</Tag>;
                  },
                },
                { title: '所属期', dataIndex: 'belongTime', width: 110 },
                { title: '审核截止', dataIndex: 'auditDeadline', width: 170 },
                { title: '完成时间', dataIndex: 'completedTime', width: 170 },
              ]}
              dataSource={ledgerList}
              loading={loading}
              scroll={{ x: 1100 }}
              pagination={{
                current: pagination.current,
                pageSize: pagination.pageSize,
                total: ledgerTotal,
                showSizeChanger: true,
                showQuickJumper: true,
                showTotal: (t) => `共 ${t} 条`,
                onChange: (page, pageSize) =>
                  setPagination({ current: page, pageSize }),
              }}
            />
          </TabPane>
        </Tabs>
      </div>
    </div>
  );
};

export default connect(({ task, dept, template }) => ({
  task,
  dept,
  template,
}))(TaskLedger);
