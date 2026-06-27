import React, { useEffect, useState } from 'react';
import {
  Table, Button, Input, Space, Modal, Descriptions, Tag, DatePicker,
} from 'antd';
import { SearchOutlined, ReloadOutlined, EyeOutlined } from '@ant-design/icons';
import { useSelector, useDispatch } from 'umi';
import moment from 'moment';

const { RangePicker } = DatePicker;

const LogManagement = () => {
  const dispatch = useDispatch();
  const [detailVisible, setDetailVisible] = useState(false);
  const [currentLog, setCurrentLog] = useState(null);
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10 });
  const [filters, setFilters] = useState({});

  // 模拟日志数据
  const [logData, setLogData] = useState([]);
  const [logTotal, setLogTotal] = useState(0);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    fetchLogs();
  }, [pagination.current, pagination.pageSize, filters]);

  const fetchLogs = () => {
    setLoading(true);
    // 模拟API调用
    setTimeout(() => {
      const mockData = Array.from({ length: 25 }, (_, i) => ({
        id: i + 1,
        username: ['admin', 'zhangsan', 'lisi', 'wangwu'][i % 4],
        realName: ['管理员', '张三', '李四', '王五'][i % 4],
        ip: `192.168.1.${100 + i}`,
        browser: ['Chrome 120', 'Firefox 121', 'Edge 120', 'Safari 17'][i % 4],
        os: ['Windows 10', 'macOS 14', 'Windows 11', 'Ubuntu 22'][i % 4],
        loginTime: moment().subtract(i, 'hours').format('YYYY-MM-DD HH:mm:ss'),
        status: i % 5 === 0 ? 0 : 1,
        msg: i % 5 === 0 ? '密码错误' : '登录成功',
        location: ['北京', '上海', '广州', '深圳'][i % 4],
      }));
      setLogData(mockData.slice(
        (pagination.current - 1) * pagination.pageSize,
        pagination.current * pagination.pageSize,
      ));
      setLogTotal(25);
      setLoading(false);
    }, 300);
  };

  const handleSearch = (values) => {
    setFilters(values);
    setPagination((prev) => ({ ...prev, current: 1 }));
  };

  const handleShowDetail = (record) => {
    setCurrentLog(record);
    setDetailVisible(true);
  };

  const columns = [
    { title: '用户名', dataIndex: 'username', width: 100 },
    { title: '姓名', dataIndex: 'realName', width: 100 },
    { title: 'IP地址', dataIndex: 'ip', width: 130 },
    { title: '浏览器', dataIndex: 'browser', width: 130 },
    { title: '操作系统', dataIndex: 'os', width: 130 },
    { title: '登录时间', dataIndex: 'loginTime', width: 170 },
    { title: '登录地点', dataIndex: 'location', width: 100 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 80,
      render: (status) => (
        <Tag color={status === 1 ? 'green' : 'red'}>
          {status === 1 ? '成功' : '失败'}
        </Tag>
      ),
    },
    { title: '说明', dataIndex: 'msg', width: 100 },
    {
      title: '操作',
      width: 80,
      fixed: 'right',
      render: (_, record) => (
        <Button size="small" icon={<EyeOutlined />} onClick={() => handleShowDetail(record)}>
          详情
        </Button>
      ),
    },
  ];

  return (
    <div>
      <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between', flexWrap: 'wrap', gap: 8 }}>
        <Space wrap>
          <Input placeholder="用户名" style={{ width: 130 }} />
          <Input placeholder="IP地址" style={{ width: 130 }} />
          <RangePicker
            showTime
            placeholder={['开始时间', '结束时间']}
          />
          <Button type="primary" icon={<SearchOutlined />}>搜索</Button>
          <Button icon={<ReloadOutlined />} onClick={() => setFilters({})}>重置</Button>
        </Space>
      </div>

      <Table
        rowKey="id"
        columns={columns}
        dataSource={logData}
        loading={loading}
        scroll={{ x: 1300 }}
        pagination={{
          current: pagination.current,
          pageSize: pagination.pageSize,
          total: logTotal,
          showSizeChanger: true,
          showQuickJumper: true,
          showTotal: (t) => `共 ${t} 条`,
          onChange: (page, pageSize) => setPagination({ current: page, pageSize }),
        }}
      />

      <Modal
        title="登录日志详情"
        open={detailVisible}
        onCancel={() => setDetailVisible(false)}
        footer={null}
        width={600}
      >
        {currentLog && (
          <Descriptions bordered column={2} size="small">
            <Descriptions.Item label="用户名">{currentLog.username}</Descriptions.Item>
            <Descriptions.Item label="姓名">{currentLog.realName}</Descriptions.Item>
            <Descriptions.Item label="IP地址">{currentLog.ip}</Descriptions.Item>
            <Descriptions.Item label="登录地点">{currentLog.location}</Descriptions.Item>
            <Descriptions.Item label="浏览器">{currentLog.browser}</Descriptions.Item>
            <Descriptions.Item label="操作系统">{currentLog.os}</Descriptions.Item>
            <Descriptions.Item label="登录时间">{currentLog.loginTime}</Descriptions.Item>
            <Descriptions.Item label="登录状态">
              <Tag color={currentLog.status === 1 ? 'green' : 'red'}>
                {currentLog.status === 1 ? '成功' : '失败'}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="说明" span={2}>{currentLog.msg}</Descriptions.Item>
          </Descriptions>
        )}
      </Modal>
    </div>
  );
};

export default LogManagement;
