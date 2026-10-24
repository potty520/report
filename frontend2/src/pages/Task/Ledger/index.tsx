import React, { useEffect, useMemo, useState } from 'react';
import { Table, Card, Row, Col, Statistic, Tabs, Button, Space, App, Progress } from 'antd';
import { DownloadOutlined, ReloadOutlined, CloudUploadOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { useLedgerStore } from '@/stores/useLedgerStore';
import { exportLedgerApi, commitTaskDataApi } from '@/services/api';
import { handleDownload } from '@/utils/download';

/**
 * 任务台账（P1-E）：6 张视图 + 导出 + 完成率/按时率/超期率。
 *
 * 列由后端返回的 headers（字段→中文表头）动态生成，避免前后端字段漂移；
 * 原先的「主办方/执行方视角」两 Tab 已由后端提供的 6 个标准视图取代。
 */
const LedgerPage: React.FC = () => {
  const { message: msg } = App.useApp();
  const { types, rates, view, loading, activeType, fetchTypes, fetchRates, fetchView } = useLedgerStore();
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [exporting, setExporting] = useState(false);
  const [committing, setCommitting] = useState<number | null>(null);

  /** 入库：把该任务下已审核通过的数据写回目标业务库（原先界面无入口，只能调接口） */
  const doCommit = async (taskId: number) => {
    setCommitting(taskId);
    try {
      const res: any = await commitTaskDataApi(taskId);
      const d = res?.data?.data || {};
      msg.success(`入库完成：写入 ${d.rows ?? 0} 行，影响 ${d.affected ?? 0} 行${d.skippedInbound ? `，跳过已入库 ${d.skippedInbound} 条` : ''}`);
      fetchRates();
      if (activeType) fetchView(activeType, page, pageSize);
    } catch (e: any) {
      msg.error(e?.response?.data?.msg || e?.message || '入库失败');
    } finally {
      setCommitting(null);
    }
  };

  useEffect(() => {
    (async () => {
      const list = await fetchTypes();
      fetchRates();
      const first = list.length > 0 ? list[0].type : undefined;
      if (first) fetchView(first, 1, pageSize);
    })();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const columns: ColumnsType<Record<string, any>> = useMemo(() => {
    const entries = Object.entries(view.headers || {});
    const cols: ColumnsType<Record<string, any>> = entries.map(([key, label]) => ({
      title: label,
      dataIndex: key,
      key,
      ellipsis: true,
      render: (value: any) => {
        if (value === null || value === undefined || value === '') return '-';
        if (typeof value === 'string' && /^\d{4}-\d{2}-\d{2}T/.test(value)) {
          return dayjs(value).format('YYYY-MM-DD HH:mm:ss');
        }
        return String(value);
      },
    }));
    // 任务维度台账增加「入库」操作（只有 hostTaskTotal 的 id 才是任务ID）
    if (activeType === 'hostTaskTotal') {
      cols.push({
        title: '操作', key: 'action', width: 120, fixed: 'right',
        render: (_: any, row: any) => (
          <Button
            type="link" size="small" icon={<CloudUploadOutlined />}
            loading={committing === row.id}
            onClick={() => doCommit(Number(row.id))}
          >
            入库
          </Button>
        ),
      });
    }
    return cols;
  }, [view.headers, activeType, committing]);

  const doExport = async () => {
    if (!activeType) return;
    setExporting(true);
    try {
      const res = await exportLedgerApi(activeType, 20000);
      await handleDownload(res as any, `台账-${activeType}.xlsx`);
      msg.success('导出成功');
    } catch (e: any) {
      msg.error(e?.message || '导出失败');
    } finally {
      setExporting(false);
    }
  };

  const num = (v?: number) => (v === undefined || v === null ? 0 : Number(v));

  return (
    <div style={{ padding: 24, background: '#fff', borderRadius: 8 }}>
      <Row gutter={16} style={{ marginBottom: 16 }}>
        <Col span={4}><Card size="small"><Statistic title="应完成" value={num(rates.dueTask)} /></Card></Col>
        <Col span={4}><Card size="small"><Statistic title="已完成" value={num(rates.doneTask)} /></Card></Col>
        <Col span={4}><Card size="small"><Statistic title="按时完成" value={num(rates.ontimeTask)} valueStyle={{ color: '#3f8600' }} /></Card></Col>
        <Col span={4}><Card size="small"><Statistic title="超期未完成" value={num(rates.overdueTask)} valueStyle={{ color: '#cf1322' }} /></Card></Col>
        <Col span={8}>
          <Card size="small" title="三率">
            <Row gutter={8} align="middle">
              <Col span={8} style={{ textAlign: 'center' }}>
                <Progress type="circle" size={64} percent={num(rates.completeRate)} />
                <div style={{ fontSize: 12, color: '#888', marginTop: 4 }}>完成率</div>
              </Col>
              <Col span={8} style={{ textAlign: 'center' }}>
                <Progress type="circle" size={64} percent={num(rates.ontimeRate)} strokeColor="#52c41a" />
                <div style={{ fontSize: 12, color: '#888', marginTop: 4 }}>按时率</div>
              </Col>
              <Col span={8} style={{ textAlign: 'center' }}>
                <Progress type="circle" size={64} percent={num(rates.overdueRate)} strokeColor="#ff4d4f" />
                <div style={{ fontSize: 12, color: '#888', marginTop: 4 }}>超期率</div>
              </Col>
            </Row>
          </Card>
        </Col>
      </Row>

      <Card
        size="small"
        title="台账视图"
        extra={
          <Space>
            <Button icon={<ReloadOutlined />} disabled={!activeType}
              onClick={() => { fetchRates(); if (activeType) fetchView(activeType, page, pageSize); }}>刷新</Button>
            <Button type="primary" icon={<DownloadOutlined />} loading={exporting} disabled={!activeType}
              onClick={doExport}>导出 Excel</Button>
          </Space>
        }
      >
        <Tabs
          activeKey={activeType}
          onChange={(key) => {
            setPage(1);
            fetchView(key, 1, pageSize);
          }}
          items={types.map((t) => ({
            key: t.type,
            label: t.label,
            children: (
              <Table
                columns={columns}
                dataSource={view.list}
                rowKey={(r) => String(r.id ?? r.taskId ?? r.deptId ?? JSON.stringify(r))}
                loading={loading}
                size="small"
                scroll={{ x: Math.max(900, columns.length * 140) }}
                onChange={(p) => {
                  const nextPage = p.current || 1;
                  const nextSize = p.pageSize || 20;
                  setPage(nextPage);
                  setPageSize(nextSize);
                  fetchView(t.type, nextPage, nextSize);
                }}
                pagination={{
                  current: page,
                  pageSize,
                  total: view.total,
                  showSizeChanger: true,
                  showTotal: (n) => `共 ${n} 条`,
                }}
              />
            ),
          }))}
        />
      </Card>
    </div>
  );
};

export default LedgerPage;
