import React, { useEffect, useState } from 'react';
import { Select, InputNumber } from 'antd';
import { getTaskListApi, TaskItem } from '@/services/api';

interface TaskSelectorProps {
  value?: number;
  onChange: (v?: number) => void;
  width?: number;
  placeholder?: string;
}

/**
 * 任务选择器。
 *
 * 任务列表接口只允许 SUPER_ADMIN / DEPT_ADMIN，审核人（AUDIT_USER）会拿到 403，
 * 因此这里先尝试拉取任务下拉，失败则自动退化为「任务ID 输入框」，保证各角色都能用。
 */
const TaskSelector: React.FC<TaskSelectorProps> = ({
  value, onChange, width = 280, placeholder = '选择任务',
}) => {
  const [tasks, setTasks] = useState<TaskItem[]>([]);
  const [mode, setMode] = useState<'loading' | 'select' | 'input'>('loading');

  useEffect(() => {
    let alive = true;
    (async () => {
      try {
        const res = await getTaskListApi({ page: 1, pageSize: 200 });
        if (!alive) return;
        setTasks(res.data.data?.list || []);
        setMode('select');
      } catch {
        if (alive) setMode('input');
      }
    })();
    return () => { alive = false; };
  }, []);

  if (mode === 'loading') {
    return <Select loading style={{ width }} placeholder="加载任务列表..." />;
  }
  if (mode === 'input') {
    return (
      <InputNumber
        style={{ width }}
        placeholder="任务ID"
        value={value}
        onChange={(v) => onChange(v === null ? undefined : Number(v))}
      />
    );
  }
  return (
    <Select
      showSearch
      allowClear
      style={{ width }}
      placeholder={placeholder}
      value={value}
      optionFilterProp="label"
      onChange={(v) => onChange(v)}
      options={tasks.map((t) => ({ value: t.id, label: `#${t.id} ${t.name}` }))}
    />
  );
};

export default TaskSelector;
