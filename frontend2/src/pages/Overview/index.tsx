import React, { useEffect, useMemo, useState } from 'react';
import { Card, Statistic, Row, Col, Spin, Table } from 'antd';
import {
  FileTextOutlined, CheckCircleOutlined, ClockCircleOutlined,
  ExclamationCircleOutlined, TeamOutlined, RiseOutlined,
} from '@ant-design/icons';
import ReactEChartsCore from 'echarts-for-react';
import { useOverviewStore } from '@/stores/useOverviewStore';
import {
  getTaskMakeStatsApi, getFillTaskStatsApi, getDeptFinishRankApi, getDeptIssueRankApi,
} from '@/services/api';

const OverviewPage: React.FC = () => {
  const { stats, chartData, trendData, loading, fetchStats, fetchChartData, fetchTrend } = useOverviewStore();

  const [makeStats, setMakeStats] = useState<Record<string, number>>({});
  const [fillStats, setFillStats] = useState<Record<string, number>>({});
  const [finishRank, setFinishRank] = useState<any[]>([]);
  const [issueRank, setIssueRank] = useState<any[]>([]);

  useEffect(() => {
    getTaskMakeStatsApi().then((r: any) => setMakeStats(r?.data || {})).catch(() => {});
    getFillTaskStatsApi().then((r: any) => setFillStats(r?.data || {})).catch(() => {});
    getDeptFinishRankApi().then((r: any) => setFinishRank(Array.isArray(r?.data) ? r.data : (r?.data?.list || []))).catch(() => {});
    getDeptIssueRankApi().then((r: any) => setIssueRank(Array.isArray(r?.data) ? r.data : (r?.data?.list || []))).catch(() => {});
  }, []);

  useEffect(() => {
    fetchStats();
    fetchChartData('dept');
    fetchTrend();
  }, [fetchStats, fetchChartData, fetchTrend]);

  const pieOption = useMemo(() => {
    // Mock distribution data if chartData is empty
    const data = chartData.length > 0
      ? chartData.map((d) => ({ name: d.name, value: d.value }))
      : [
          { name: '已填报', value: 65 },
          { name: '未填报', value: 20 },
          { name: '填报中', value: 15 },
        ];

    return {
      title: { text: '填报完成率分布', left: 'center', textStyle: { fontSize: 14 } },
      tooltip: { trigger: 'item' as const },
      legend: { orient: 'vertical' as const, left: 'left' },
      series: [{
        name: '填报统计',
        type: 'pie',
        radius: ['40%', '70%'],
        avoidLabelOverlap: false,
        itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
        label: { show: false, position: 'center' },
        emphasis: { label: { show: true, fontSize: 16, fontWeight: 'bold' } },
        labelLine: { show: false },
        data,
      }],
    };
  }, [chartData]);

  const barOption = useMemo(() => {
    const depts = trendData.length > 0
      ? trendData.map((d) => d.date)
      : ['发改局', '财政局', '统计局', '住建局', '教育局', '卫健局'];
    const fillData = trendData.length > 0
      ? trendData.map((d) => d.fillCount)
      : [120, 200, 150, 80, 70, 90];
    const submitData = trendData.length > 0
      ? trendData.map((d) => d.submitCount)
      : [100, 180, 130, 60, 50, 70];

    return {
      title: { text: '部门填报统计', left: 'center', textStyle: { fontSize: 14 } },
      tooltip: { trigger: 'axis' as const },
      legend: { data: ['填报数', '提交数'], bottom: 0 },
      grid: { left: '3%', right: '4%', bottom: '15%', containLabel: true },
      xAxis: { type: 'category' as const, data: depts, axisLabel: { rotate: 30 } },
      yAxis: { type: 'value' as const },
      series: [
        { name: '填报数', type: 'bar', data: fillData, itemStyle: { color: '#1677ff', borderRadius: [4, 4, 0, 0] } },
        { name: '提交数', type: 'bar', data: submitData, itemStyle: { color: '#52c41a', borderRadius: [4, 4, 0, 0] } },
      ],
    };
  }, [trendData]);

  const trendOption = useMemo(() => {
    const dates = trendData.length > 0
      ? trendData.map((d) => d.date)
      : ['06/20', '06/21', '06/22', '06/23', '06/24', '06/25', '06/26'];
    const audit = trendData.length > 0
      ? trendData.map((d) => d.auditCount)
      : [30, 40, 35, 50, 42, 55, 48];

    return {
      title: { text: '审核趋势 (近7天)', left: 'center', textStyle: { fontSize: 14 } },
      tooltip: { trigger: 'axis' as const },
      grid: { left: '3%', right: '4%', bottom: '4%', containLabel: true },
      xAxis: { type: 'category' as const, data: dates, boundaryGap: false },
      yAxis: { type: 'value' as const },
      series: [{
        name: '审核数',
        type: 'line',
        smooth: true,
        data: audit,
        areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: 'rgba(22,119,255,0.3)' }, { offset: 1, color: 'rgba(22,119,255,0.05)' }] } },
        itemStyle: { color: '#1677ff' },
      }],
    };
  }, [trendData]);

  const statData = stats || {
    totalTasks: 48,
    completedTasks: 32,
    pendingTasks: 12,
    overdueTasks: 4,
    totalFills: 156,
    submittedFills: 130,
    auditPassRate: 85.5,
    totalDepts: 24,
  };

  return (
    <div style={{ padding: '0' }}>
      <Spin spinning={loading}>
        {/* Stat Cards */}
        <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
          <Col xs={24} sm={12} md={6}>
            <Card bordered={false} style={{ background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)' }}>
              <Statistic
                title={<span style={{ color: 'rgba(255,255,255,0.8)' }}>任务总数</span>}
                value={statData.totalTasks}
                prefix={<FileTextOutlined />}
                valueStyle={{ color: '#fff' }}
              />
            </Card>
          </Col>
          <Col xs={24} sm={12} md={6}>
            <Card bordered={false} style={{ background: 'linear-gradient(135deg, #f093fb 0%, #f5576c 100%)' }}>
              <Statistic
                title={<span style={{ color: 'rgba(255,255,255,0.8)' }}>已完成</span>}
                value={statData.completedTasks}
                prefix={<CheckCircleOutlined />}
                valueStyle={{ color: '#fff' }}
              />
            </Card>
          </Col>
          <Col xs={24} sm={12} md={6}>
            <Card bordered={false} style={{ background: 'linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)' }}>
              <Statistic
                title={<span style={{ color: 'rgba(255,255,255,0.8)' }}>待处理</span>}
                value={statData.pendingTasks}
                prefix={<ClockCircleOutlined />}
                valueStyle={{ color: '#fff' }}
              />
            </Card>
          </Col>
          <Col xs={24} sm={12} md={6}>
            <Card bordered={false} style={{ background: 'linear-gradient(135deg, #fa709a 0%, #fee140 100%)' }}>
              <Statistic
                title={<span style={{ color: 'rgba(255,255,255,0.8)' }}>审核通过率</span>}
                value={statData.auditPassRate}
                prefix={<RiseOutlined />}
                suffix="%"
                valueStyle={{ color: '#fff' }}
              />
            </Card>
          </Col>
        </Row>

        {/* Second row of stats */}
        <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
          <Col xs={12} sm={6}>
            <Card>
              <Statistic title="填报总数" value={statData.totalFills} prefix={<FileTextOutlined />} />
            </Card>
          </Col>
          <Col xs={12} sm={6}>
            <Card>
              <Statistic title="已提交" value={statData.submittedFills} prefix={<CheckCircleOutlined />} valueStyle={{ color: '#52c41a' }} />
            </Card>
          </Col>
          <Col xs={12} sm={6}>
            <Card>
              <Statistic title="逾期任务" value={statData.overdueTasks} prefix={<ExclamationCircleOutlined />} valueStyle={{ color: '#ff4d4f' }} />
            </Card>
          </Col>
          <Col xs={12} sm={6}>
            <Card>
              <Statistic title="参与部门" value={statData.totalDepts} prefix={<TeamOutlined />} />
            </Card>
          </Col>
        </Row>

        {/* Charts */}
        <Row gutter={[16, 16]}>
          <Col xs={24} lg={12}>
            <Card>
              <ReactEChartsCore option={pieOption} style={{ height: 350 }} />
            </Card>
          </Col>
          <Col xs={24} lg={12}>
            <Card>
              <ReactEChartsCore option={barOption} style={{ height: 350 }} />
            </Card>
          </Col>
        </Row>

        <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
          <Col span={24}>
            <Card>
              <ReactEChartsCore option={trendOption} style={{ height: 350 }} />
            </Card>
          </Col>
        </Row>

        {/* R-12 手册四类统计 */}
        <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
          <Col xs={24} lg={12}>
            <Card size="small" title="任务制定统计">
              <Row gutter={16}>
                <Col span={8}><Statistic title="制定任务总数" value={makeStats.taskCount ?? 0} /></Col>
                <Col span={8}><Statistic title="生成填报任务" value={makeStats.subTaskCount ?? 0} /></Col>
                <Col span={8}><Statistic title="涉及部门" value={makeStats.deptCount ?? 0} /></Col>
                <Col span={8}><Statistic title="已完成" value={makeStats.doneCount ?? 0} /></Col>
                <Col span={8}><Statistic title="待完成" value={makeStats.todoCount ?? 0} /></Col>
                <Col span={8}><Statistic title="超期" value={makeStats.overdueCount ?? 0} /></Col>
              </Row>
            </Card>
          </Col>
          <Col xs={24} lg={12}>
            <Card size="small" title="填报任务统计">
              <Row gutter={16}>
                <Col span={8}><Statistic title="任务执行总数" value={fillStats.execCount ?? 0} /></Col>
                <Col span={8}><Statistic title="任务下发部门" value={fillStats.deptCount ?? 0} /></Col>
                <Col span={8}><Statistic title="已完成" value={fillStats.doneCount ?? 0} /></Col>
                <Col span={8}><Statistic title="待完成" value={fillStats.todoCount ?? 0} /></Col>
                <Col span={8}><Statistic title="超期" value={fillStats.overdueCount ?? 0} /></Col>
              </Row>
            </Card>
          </Col>
          <Col xs={24} lg={12}>
            <Card size="small" title="部门完成排行">
              <Table
                size="small" rowKey="deptName" pagination={false} dataSource={finishRank}
                columns={[
                  { title: '部门', dataIndex: 'deptName', render: (v: string) => v || '-' },
                  { title: '任务数', dataIndex: 'total', width: 100 },
                  { title: '已完成', dataIndex: 'finished', width: 100 },
                ]}
              />
            </Card>
          </Col>
          <Col xs={24} lg={12}>
            <Card size="small" title="部门下发排行">
              <Table
                size="small" rowKey="deptName" pagination={false} dataSource={issueRank}
                columns={[
                  { title: '部门', dataIndex: 'deptName', render: (v: string) => v || '-' },
                  { title: '下发任务', dataIndex: 'issued', width: 100 },
                  { title: '已完成', dataIndex: 'finished', width: 100 },
                ]}
              />
            </Card>
          </Col>
        </Row>
      </Spin>
    </div>
  );
};

export default OverviewPage;
