import React, { useState, useEffect, useRef } from 'react';
import { connect } from 'dva';
import {
  Card, Row, Col, Statistic, DatePicker, Select, Button, Space,
  PageHeader, Spin,
} from 'antd';
import {
  FileTextOutlined, ScheduleOutlined, FormOutlined,
  CheckCircleOutlined, WarningOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import { useSelector, useDispatch } from 'umi';
import * as echarts from 'echarts';

const { Option } = Select;
const { RangePicker } = DatePicker;

const Overview = () => {
  const dispatch = useDispatch();
  const { stats, chartData, loading } = useSelector((state) => state.overview);

  const pieChartRef = useRef(null);
  const lineChartRef = useRef(null);
  const pieInstance = useRef(null);
  const lineInstance = useRef(null);

  const [dateRange, setDateRange] = useState([]);
  const [deptId, setDeptId] = useState(null);

  useEffect(() => {
    dispatch({ type: 'overview/fetchStats' });
    dispatch({ type: 'overview/fetchCharts', payload: {} });
  }, []);

  useEffect(() => {
    initPieChart();
    initLineChart();
    return () => {
      pieInstance.current?.dispose();
      lineInstance.current?.dispose();
    };
  }, [chartData]);

  const initPieChart = () => {
    if (!pieChartRef.current) return;
    if (!pieInstance.current) {
      pieInstance.current = echarts.init(pieChartRef.current);
    }
    const option = {
      title: { text: '填报完成率', left: 'center', top: 10, textStyle: { fontSize: 14 } },
      tooltip: { trigger: 'item' },
      legend: { bottom: 10, data: ['已完成', '进行中', '未开始'] },
      series: [
        {
          type: 'pie',
          radius: ['40%', '65%'],
          center: ['50%', '55%'],
          avoidLabelOverlap: false,
          label: { show: true, formatter: '{b}: {c}个\n({d}%)' },
          emphasis: {
            label: { show: true, fontSize: 16, fontWeight: 'bold' },
          },
          data: [
            { value: chartData.completed || 156, name: '已完成', itemStyle: { color: '#52c41a' } },
            { value: chartData.inProgress || 38, name: '进行中', itemStyle: { color: '#1890ff' } },
            { value: chartData.notStarted || 45, name: '未开始', itemStyle: { color: '#d9d9d9' } },
          ],
        },
      ],
    };
    pieInstance.current.setOption(option, true);
  };

  const initLineChart = () => {
    if (!lineChartRef.current) return;
    if (!lineInstance.current) {
      lineInstance.current = echarts.init(lineChartRef.current);
    }
    const option = {
      title: { text: '填报趋势', left: 'center', top: 10, textStyle: { fontSize: 14 } },
      tooltip: { trigger: 'axis' },
      legend: { bottom: 10, data: ['任务数', '完成数', '逾期数'] },
      grid: { left: 50, right: 30, top: 50, bottom: 40 },
      xAxis: {
        type: 'category',
        data: chartData.trendLabels || ['1月', '2月', '3月', '4月', '5月', '6月'],
      },
      yAxis: { type: 'value' },
      series: [
        {
          name: '任务数',
          type: 'line',
          data: chartData.taskCounts || [85, 92, 105, 98, 120, 110],
          smooth: true,
          lineStyle: { color: '#1890ff' },
        },
        {
          name: '完成数',
          type: 'line',
          data: chartData.completedCounts || [72, 80, 90, 85, 100, 95],
          smooth: true,
          lineStyle: { color: '#52c41a' },
        },
        {
          name: '逾期数',
          type: 'line',
          data: chartData.overdueCounts || [5, 8, 12, 7, 15, 10],
          smooth: true,
          lineStyle: { color: '#ff4d4f' },
        },
      ],
    };
    lineInstance.current.setOption(option, true);
  };

  const handleDateChange = (dates) => {
    setDateRange(dates || []);
  };

  const handleRefresh = () => {
    dispatch({ type: 'overview/fetchStats' });
    dispatch({
      type: 'overview/fetchCharts',
      payload: { deptId, startDate: dateRange[0], endDate: dateRange[1] },
    });
  };

  const defaultStats = {
    templateCount: stats.templateCount || 15,
    taskCount: stats.taskCount || 128,
    fillRate: stats.fillRate || 78.5,
    auditRate: stats.auditRate || 85.2,
    overdueRate: stats.overdueRate || 6.3,
  };

  return (
    <div>
      <PageHeader title="填报总览" style={{ padding: 0, marginBottom: 16 }}>
        <Space style={{ marginTop: 8 }}>
          <Select
            placeholder="选择部门"
            allowClear
            style={{ width: 150 }}
            value={deptId}
            onChange={setDeptId}
          >
            <Option value={1}>技术部</Option>
            <Option value={2}>产品部</Option>
            <Option value={3}>运营部</Option>
            <Option value={4}>市场部</Option>
            <Option value={5}>财务部</Option>
          </Select>
          <RangePicker
            placeholder={['开始日期', '结束日期']}
            onChange={handleDateChange}
          />
          <Button icon={<ReloadOutlined />} onClick={handleRefresh}>
            刷新
          </Button>
        </Space>
      </PageHeader>

      {loading ? (
        <div style={{ textAlign: 'center', padding: 100 }}><Spin size="large" /></div>
      ) : (
        <>
          {/* 统计卡片 */}
          <Row gutter={16} style={{ marginBottom: 16 }}>
            <Col span={4}>
              <Card size="small">
                <Statistic
                  title="模板总数"
                  value={defaultStats.templateCount}
                  prefix={<FileTextOutlined />}
                  valueStyle={{ color: '#1890ff' }}
                />
              </Card>
            </Col>
            <Col span={4}>
              <Card size="small">
                <Statistic
                  title="任务总数"
                  value={defaultStats.taskCount}
                  prefix={<ScheduleOutlined />}
                  valueStyle={{ color: '#722ed1' }}
                />
              </Card>
            </Col>
            <Col span={5}>
              <Card size="small">
                <Statistic
                  title="填报完成率"
                  value={defaultStats.fillRate}
                  precision={1}
                  suffix="%"
                  prefix={<FormOutlined />}
                  valueStyle={{ color: '#52c41a' }}
                />
              </Card>
            </Col>
            <Col span={5}>
              <Card size="small">
                <Statistic
                  title="审核通过率"
                  value={defaultStats.auditRate}
                  precision={1}
                  suffix="%"
                  prefix={<CheckCircleOutlined />}
                  valueStyle={{ color: '#13c2c2' }}
                />
              </Card>
            </Col>
            <Col span={6}>
              <Card size="small">
                <Statistic
                  title="逾期率"
                  value={defaultStats.overdueRate}
                  precision={1}
                  suffix="%"
                  prefix={<WarningOutlined />}
                  valueStyle={{ color: '#ff4d4f' }}
                />
              </Card>
            </Col>
          </Row>

          {/* 图表区域 */}
          <Row gutter={16}>
            <Col span={11}>
              <Card bodyStyle={{ padding: 8 }}>
                <div ref={pieChartRef} style={{ width: '100%', height: 350 }} />
              </Card>
            </Col>
            <Col span={13}>
              <Card bodyStyle={{ padding: 8 }}>
                <div ref={lineChartRef} style={{ width: '100%', height: 350 }} />
              </Card>
            </Col>
          </Row>
        </>
      )}
    </div>
  );
};

export default connect(({ overview }) => ({ overview }))(Overview);
