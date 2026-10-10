import React, { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Timeline, Card, Button, Space, InputNumber, Empty, Tag, App } from 'antd';
import { SearchOutlined, ReloadOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { useAuditStore } from '@/stores/useAuditStore';
import TaskSelector from '@/components/TaskSelector';
import type { TrajectoryNode } from '@/services/api';

const NODE_COLORS: Record<string, string> = {
  SUBMIT: 'blue',
  DEPT_AUDIT: 'orange',
  TASK_AUDIT: 'green',
  CORRECT: 'red',
  INBOUND: 'purple',
};

const actionTag = (action?: string) => {
  if (!action) return null;
  const map: Record<string, { color: string; text: string }> = {
    PASS: { color: 'green', text: '通过' },
    REJECT: { color: 'red', text: '驳回' },
    SUBMIT: { color: 'blue', text: '提交' },
    CORRECT_START: { color: 'volcano', text: '发起补正' },
    CORRECT_CLOSE: { color: 'cyan', text: '关闭补正' },
    INBOUND: { color: 'purple', text: '写回业务库' },
  };
  const hit = map[action];
  return hit ? <Tag color={hit.color}>{hit.text}</Tag> : <Tag>{action}</Tag>;
};

/** 任务流转轨迹（P0-C） */
const TrajectoryPage: React.FC = () => {
  const { message: msg } = App.useApp();
  const [params] = useSearchParams();
  const { trajectory, trajectoryLoading, fetchTrajectory } = useAuditStore();

  const initial = params.get('taskId');
  const [taskId, setTaskId] = useState<number | undefined>(initial ? Number(initial) : undefined);

  useEffect(() => {
    if (initial) {
      const id = Number(initial);
      setTaskId(id);
      fetchTrajectory(id).catch(() => msg.error('获取轨迹失败'));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [initial]);

  const query = async () => {
    if (!taskId) {
      msg.warning('请输入任务ID');
      return;
    }
    try {
      await fetchTrajectory(taskId);
    } catch {
      msg.error('获取轨迹失败');
    }
  };

  return (
    <div style={{ padding: 24, background: '#fff', borderRadius: 8 }}>
      <Space style={{ marginBottom: 16 }} wrap>
        <span>任务：</span>
        <TaskSelector value={taskId} onChange={setTaskId} />
        <Button type="primary" icon={<SearchOutlined />} onClick={query}>查询轨迹</Button>
        <Button icon={<ReloadOutlined />} onClick={query} disabled={!taskId}>刷新</Button>
      </Space>

      <Card size="small" title={`流转轨迹${taskId ? `（任务 ${taskId}）` : ''}`} loading={trajectoryLoading}>
        {trajectory.length === 0 ? (
          <Empty description="暂无轨迹记录" />
        ) : (
          <Timeline
            mode="left"
            items={trajectory.map((n: TrajectoryNode) => ({
              color: NODE_COLORS[n.node_code || ''] || 'gray',
              children: (
                <div>
                  <Space wrap>
                    <strong>{n.node_name || n.node_code}</strong>
                    {actionTag(n.action)}
                    <span style={{ color: '#888' }}>
                      {n.operator_name || '-'}
                    </span>
                    <span style={{ color: '#888' }}>
                      {n.create_time ? dayjs(n.create_time).format('YYYY-MM-DD HH:mm:ss') : '-'}
                    </span>
                  </Space>
                  {n.comment ? (
                    <div style={{ marginTop: 4, color: '#555' }}>{n.comment}</div>
                  ) : null}
                </div>
              ),
            }))}
          />
        )}
      </Card>
    </div>
  );
};

export default TrajectoryPage;
