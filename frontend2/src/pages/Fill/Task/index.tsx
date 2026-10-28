import React, { useEffect, useMemo, useState, useCallback } from 'react';
import {
  Table, Button, Space, Tag, Modal, Form, Input, Upload, Alert, Typography, message, App, Card,
} from 'antd';
import {
  UploadOutlined, EditOutlined, SendOutlined, PaperClipOutlined, PlusOutlined, DeleteOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { useFillStore } from '@/stores/useFillStore';
import { getTemplateFieldsApi, uploadFillFileApi, getMyAssignedTasksApi,
  noUpdateSubmitApi, returnFillTaskApi, getTaskReferenceApi, verifyFillApi } from '@/services/api';
import type { FillTaskItem, TemplateField, AssignedTaskItem } from '@/services/api';
import dayjs from 'dayjs';

/**
 * 解析后端 dataJson（可能是单个对象，也可能是 Excel 导入产生的对象数组）。
 * 取第一条用于表单回填，避免数组场景直接把表单值写成数组。
 */
function firstFillRecord(dataJson?: string): Record<string, any> {
  if (!dataJson) return {};
  try {
    const parsed = JSON.parse(dataJson);
    if (Array.isArray(parsed)) {
      return parsed[0] && typeof parsed[0] === 'object' ? parsed[0] : {};
    }
    return parsed && typeof parsed === 'object' ? parsed : {};
  } catch {
    return {};
  }
}

/** 用模板字段把后端 dataJson 映射成表单初始值（兼容 fieldCode / fieldName 两种 key） */
function buildFormValues(fields: TemplateField[], data: Record<string, any>): Record<string, any> {
  const values: Record<string, any> = {};
  fields.forEach((f) => {
    const key = f.fieldName;
    if (!key) return;
    const raw = data[f.fieldCode || ''] ?? data[key] ?? data[f.fieldLabel || ''] ?? '';
    values[key] = raw === null || raw === undefined ? '' : String(raw);
  });
  return values;
}

interface ManualRow {
  key: string;
  value: string;
}

const FillTaskPage: React.FC = () => {
  const { message: msg } = App.useApp();
  const { taskList, taskTotal, taskLoading, fetchTaskList, fetchFillDetail, submitFill, saveDraft } = useFillStore();

  const [fillModalOpen, setFillModalOpen] = useState(false);
  const [currentTaskId, setCurrentTaskId] = useState<number | null>(null);
  const [currentStatus, setCurrentStatus] = useState<number>(0);
  const [templateFields, setTemplateFields] = useState<TemplateField[]>([]);
  const [fieldsLoading, setFieldsLoading] = useState(false);
  // 填报人角色没有 /template/{id}/fields 权限（403），退化为「字段名-值」手工录入
  const [manualMode, setManualMode] = useState(false);
  const [manualRows, setManualRows] = useState<ManualRow[]>([]);
  const [fileUrl, setFileUrl] = useState<string | undefined>(undefined);
  const [fileName, setFileName] = useState<string>('');
  const [uploading, setUploading] = useState(false);
  const [fillForm] = Form.useForm();
  const [refData, setRefData] = useState<any>(null);
  const [verifyErrors, setVerifyErrors] = useState<any[]>([]);
  const hasAttachField = (templateFields as any[]).some((f) => Number(f?.fieldKind) === 1);
  const [submitting, setSubmitting] = useState(false);
  // 已分派但还没有填报记录的任务（用于补全「首次填报」入口）
  const [assignedTasks, setAssignedTasks] = useState<AssignedTaskItem[]>([]);

  const readOnly = currentStatus === 1;

  const doFetch = useCallback(() => {
    fetchTaskList({ current: 1, size: 10 });
    getMyAssignedTasksApi({ current: 1, size: 200 })
      .then((res) => setAssignedTasks(res.data.data.list || []))
      .catch(() => setAssignedTasks([]));
  }, [fetchTaskList]);

  /** 已有填报记录的任务 + 仅分派未填报的任务，合并成一张列表 */
  const rows = useMemo<FillTaskItem[]>(() => {
    const records = (taskList || []) as FillTaskItem[];
    const recorded = new Set(records.map((r) => r.taskId));
    const pending = assignedTasks
      .filter((t) => !recorded.has(t.id))
      .map((t) => ({
        id: 0,
        taskId: t.id,
        templateId: t.templateId,
        taskName: t.taskName,
        templateName: t.templateName,
        status: 0,
        pending: true,
        createTime: undefined,
        submitTime: undefined,
      } as FillTaskItem));
    return [...records, ...pending];
  }, [taskList, assignedTasks]);

  useEffect(() => {
    doFetch();
  }, [doFetch]);

  const patchManualRow = (index: number, patch: Partial<ManualRow>) => {
    setManualRows((prev) => prev.map((row, i) => (i === index ? { ...row, ...patch } : row)));
  };

  const handleFill = async (record: FillTaskItem) => {
    // 后端 RptTaskData 没有分配记录主键字段，任务标识只能用 taskId
    if (!record.taskId) {
      msg.error('该填报记录缺少任务ID，无法打开填报页');
      return;
    }
    setCurrentTaskId(record.taskId);
    setCurrentStatus(record.status ?? 0);
    setFileUrl(record.fileUrl);
    setFileName(record.fileName || '');
    setTemplateFields([]);
    setManualRows([]);
    setManualMode(false);
    setRefData(null);
    getTaskReferenceApi(record.taskId).then((res: any) => setRefData(res?.data || null)).catch(() => setRefData(null));
    fillForm.resetFields();
    setFillModalOpen(true);
    setFieldsLoading(true);
    try {
      // 详情接口：GET /fill/task/{taskId}
      // 首次填报时后端还没有该任务的数据记录，会返回「未找到填报数据」，
      // 这里不能当成失败——按空表单继续，模板字段仍可从任务上取到。
      let detail: any = null;
      try {
        detail = await fetchFillDetail(record.taskId);
      } catch {
        detail = null;
      }
      setCurrentStatus(detail?.status ?? record.status ?? 0);
      setFileUrl(detail?.fileUrl ?? record.fileUrl);
      setFileName(detail?.fileName || record.fileName || '');
      const initial = firstFillRecord(detail?.dataJson ?? record.dataJson);
      const templateId = detail?.templateId ?? record.templateId;
      if (!templateId) {
        setManualMode(true);
        setManualRows(Object.entries(initial).map(([key, value]) => ({ key, value: value == null ? '' : String(value) })));
        msg.warning('该任务未关联模板，请按已知字段名手工填写');
        return;
      }
      try {
        const res = await getTemplateFieldsApi(templateId);
        const list = (res.data.data || []) as TemplateField[];
        if (list.length === 0) {
          setManualMode(true);
          setManualRows(Object.entries(initial).map(([key, value]) => ({ key, value: value == null ? '' : String(value) })));
        } else {
          setTemplateFields(list);
          fillForm.setFieldsValue(buildFormValues(list, initial));
        }
      } catch {
        // 纯填报人访问模板字段接口会 403（该接口仅超管/部门管理员可读）。
        // 这里不阻断填报，退化为按字段名手工录入，提交格式与后端一致。
        setManualMode(true);
        setManualRows(Object.entries(initial).map(([key, value]) => ({ key, value: value == null ? '' : String(value) })));
      }
    } catch (error: any) {
      msg.error(error?.response?.data?.msg || error?.message || '获取填报详情失败');
      setFillModalOpen(false);
    } finally {
      setFieldsLoading(false);
    }
  };

  /** 上传填报附件：POST /fill/upload，成功后把 fileUrl 随提交一起发给后端 */
  const handleUploadFile = async (file: File) => {
    if (!currentTaskId) return;
    try {
      setUploading(true);
      const res = await uploadFillFileApi(file, currentTaskId);
      const data = res.data.data;
      setFileUrl(data?.fileUrl);
      setFileName(data?.fileName || file.name);
      msg.success('附件上传成功');
    } catch (error: any) {
      msg.error(error?.response?.data?.msg || error?.message || '附件上传失败');
    } finally {
      setUploading(false);
    }
  };

  const handleFillModalOk = async (type: 'draft' | 'submit') => {
    if (!currentTaskId) return;
    try {
      // dataJson 的 key 与后端解析口径一致：模板字段用 fieldCode（缺省用 fieldName）
      const payload: Record<string, string> = {};
      if (manualMode) {
        manualRows.forEach((row) => {
          const key = row.key.trim();
          if (key) payload[key] = row.value ?? '';
        });
        if (Object.keys(payload).length === 0) {
          msg.error('请至少填写一个字段');
          return;
        }
      } else {
        const values = await fillForm.validateFields();
        templateFields.forEach((f) => {
          const key = f.fieldCode || f.fieldName;
          if (!key) return;
          const raw = values[f.fieldName];
          payload[key] = raw === undefined || raw === null ? '' : String(raw);
        });
        if (Object.keys(payload).length === 0) {
          msg.error('该任务没有可填写的模板字段');
          return;
        }
      }
      if (type === 'submit') {
        try {
          const vr: any = await verifyFillApi(currentTaskId, [payload]);
          const errs = [...((vr?.data?.stage1?.errors) || []), ...((vr?.data?.stage2?.errors) || [])];
          if (!vr?.data?.passed && errs.length) {
            setVerifyErrors(errs);
            Modal.error({
              title: '数据校验未通过',
              width: 640,
              content: (
                <div>
                  <p>以下问题需要先修正，修正后才能提交：</p>
                  <ul style={{ paddingLeft: 18 }}>
                    {errs.slice(0, 20).map((e: any, i: number) => (
                      <li key={i}>第 {e.row} 行 · {e.column}：{e.reason}</li>
                    ))}
                  </ul>
                </div>
              ),
            });
            return;
          }
        } catch { /* 校验接口异常不阻断 */ }
      }
      const data = {
        taskId: currentTaskId,
        dataJson: JSON.stringify(payload),
        fileUrl: fileUrl || undefined,
      };
      setSubmitting(true);
      if (type === 'draft') {
        await saveDraft(data);
        msg.success('草稿保存成功');
      } else {
        await submitFill(data);
        msg.success('提交成功');
        setFillModalOpen(false);
      }
      doFetch();
    } catch (error: any) {
      if (error?.errorFields) return;
      msg.error(error?.response?.data?.msg || error?.message || '操作失败');
    } finally {
      setSubmitting(false);
    }
  };

  const statusMap: Record<number, { color: string; text: string }> = {
    0: { color: 'default', text: '草稿' },
    1: { color: 'green', text: '已提交' },
  };

  const [reasonModal, setReasonModal] = useState<{ open: boolean; type: 'noUpdate' | 'return'; record?: FillTaskItem }>(
    { open: false, type: 'noUpdate' },
  );
  const [reasonText, setReasonText] = useState('');

  const openReason = (type: 'noUpdate' | 'return', record: FillTaskItem) => {
    setReasonText('');
    setReasonModal({ open: true, type, record });
  };

  const submitReason = async () => {
    const record = reasonModal.record;
    if (!record) return;
    if (!reasonText.trim()) {
      message.warning('请填写说明');
      return;
    }
    try {
      if (reasonModal.type === 'noUpdate') {
        await noUpdateSubmitApi(record.taskId as number, reasonText.trim());
        message.success('已提交无更新说明');
      } else {
        await returnFillTaskApi(record.taskId as number, reasonText.trim());
        message.success('已退回');
      }
      setReasonModal({ open: false, type: 'noUpdate' });
      doFetch();
    } catch (e: any) {
      message.error(e?.response?.data?.msg || e?.message || '操作失败');
    }
  };

  const columns: ColumnsType<FillTaskItem> = [
    { title: 'ID', dataIndex: 'id', width: 70, align: 'center', render: (v: number) => (v ? v : '-') },
    { title: '任务名称', dataIndex: 'taskName', width: 200, render: (v: string) => v || '-' },
    { title: '模板', dataIndex: 'templateName', width: 150, render: (v: string) => v || '-' },
    {
      title: '状态', dataIndex: 'status', width: 90, align: 'center',
      render: (v: number, record: FillTaskItem) => (record.pending
        ? <Tag color="orange">未填报</Tag>
        : <Tag color={statusMap[v]?.color}>{statusMap[v]?.text || '-'}</Tag>),
    },
    {
      title: '填报时间', dataIndex: 'createTime', width: 170,
      render: (v: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm:ss') : '-'),
    },
    {
      title: '提交时间', dataIndex: 'submitTime', width: 170,
      render: (v: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm:ss') : '-'),
    },
    {
      title: '操作', key: 'action', width: 300, fixed: 'right',
      render: (_: any, record: FillTaskItem) => (
        <Space>
          {record.status === 1 ? (
            <Button size="small" icon={<EditOutlined />} onClick={() => handleFill(record)}>
              查看
            </Button>
          ) : (
            <Button type="primary" size="small" icon={<EditOutlined />} onClick={() => handleFill(record)}>
              填报
            </Button>
          )}
          {record.status !== 1 && (
            <Button size="small" onClick={() => openReason('noUpdate', record)}>无更新提交</Button>
          )}
          {record.status !== 1 && (
            <Button size="small" danger onClick={() => openReason('return', record)}>退回</Button>
          )}
        </Space>
      ),
    },
  ];

  return (
    <div style={{ padding: 24, background: '#fff', borderRadius: 8 }}>
      <div style={{ marginBottom: 16 }}>
        <h3 style={{ margin: 0 }}>数据填报</h3>
        <p style={{ color: '#999', margin: '4px 0 0' }}>填写和提交各项报表数据</p>
      </div>
      <Table
        columns={columns}
        dataSource={rows}
        rowKey={(r) => (r.id ? `d-${r.id}` : `t-${r.taskId}`)}
        loading={taskLoading}
        scroll={{ x: 900 }}
        onChange={(pagination) => fetchTaskList({ current: pagination.current, size: pagination.pageSize })}
        pagination={{
          total: taskTotal,
          showSizeChanger: true,
          showTotal: (t) => `共 ${t} 条`,
        }}
      />

      <Modal
        title={readOnly ? '查看填报数据' : '数据填报'}
        open={fillModalOpen}
        onCancel={() => setFillModalOpen(false)}
        width={700}
        confirmLoading={submitting}
        footer={readOnly ? [
          <Button key="close" onClick={() => setFillModalOpen(false)}>关闭</Button>,
        ] : [
          <Button key="draft" onClick={() => handleFillModalOk('draft')} loading={submitting}>保存草稿</Button>,
          <Button key="submit" type="primary" onClick={() => handleFillModalOk('submit')} loading={submitting} icon={<SendOutlined />}>
            提交数据
          </Button>,
        ]}
        destroyOnClose
      >
        {refData && refData.configType ? (
          <Card size="small" title="参考数据（只读）" style={{ marginBottom: 16 }}>
            <div style={{ color: '#999', fontSize: 12, marginBottom: 8 }}>
              {refData.configType === 1 ? '关联模板' : '关联表'}：以下为系统带出的历史数据，仅供填报参考，不会写入本次提交。
            </div>
            <Table
              size="small"
              rowKey={(_, i) => String(i)}
              pagination={{ pageSize: 5, size: 'small' }}
              dataSource={(refData.rows || []) as any[]}
              columns={
                (refData.rows && refData.rows.length
                  ? Object.keys(refData.rows[0] as any).map((k) => ({ title: k, dataIndex: k, ellipsis: true }))
                  : [])
              }
              locale={{ emptyText: '暂无参考数据' }}
            />
          </Card>
        ) : null}
        <div style={{ marginBottom: 16, display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
          {!readOnly && (
            <Upload
              accept=".xlsx,.xls,.pdf,.doc,.docx,.png,.jpg"
              maxCount={1}
              showUploadList={false}
              beforeUpload={(file) => {
                handleUploadFile(file as unknown as File);
                return false;
              }}
            >
              <Button icon={<UploadOutlined />} loading={uploading}>上传附件</Button>
            </Upload>
          )}
          {!readOnly && hasAttachField && (
            <Upload
              accept=".zip,.rar"
              maxCount={1}
              showUploadList={false}
              beforeUpload={(file) => {
                handleUploadFile(file as unknown as File);
                return false;
              }}
            >
              <Button icon={<UploadOutlined />} loading={uploading}>非分卷上传附件</Button>
            </Upload>
          )}
          {fileName ? (
            <Typography.Text type="secondary">
              <PaperClipOutlined /> {fileName}
            </Typography.Text>
          ) : (
            <Typography.Text type="secondary">可选：上传附件（Excel/PDF 等）</Typography.Text>
          )}
          {!readOnly && fileUrl && (
            <Button type="link" size="small" onClick={() => { setFileUrl(undefined); setFileName(''); }}>
              移除附件
            </Button>
          )}
        </div>

        {manualMode && (
          <Alert
            type="info"
            showIcon
            style={{ marginBottom: 12 }}
            message="未读取到模板字段定义，请按模板的字段名（或其字段编码）逐项填写"
          />
        )}

        {manualMode ? (
          <div>
            {manualRows.map((row, index) => (
              <div key={index} style={{ display: 'flex', gap: 8, marginBottom: 8 }}>
                <Input
                  placeholder="字段名 / 字段编码"
                  value={row.key}
                  disabled={readOnly}
                  onChange={(e) => patchManualRow(index, { key: e.target.value })}
                  style={{ width: 220 }}
                />
                <Input
                  placeholder="字段值"
                  value={row.value}
                  disabled={readOnly}
                  onChange={(e) => patchManualRow(index, { value: e.target.value })}
                />
                {!readOnly && (
                  <Button
                    danger
                    icon={<DeleteOutlined />}
                    onClick={() => setManualRows((prev) => prev.filter((_, i) => i !== index))}
                  />
                )}
              </div>
            ))}
            {!readOnly && (
              <Button
                type="dashed"
                icon={<PlusOutlined />}
                onClick={() => setManualRows((prev) => [...prev, { key: '', value: '' }])}
                block
              >
                添加字段
              </Button>
            )}
            {!readOnly && manualRows.length === 0 && (
              <div style={{ textAlign: 'center', padding: 24, color: '#999' }}>
                暂无填报字段，点「添加字段」按模板字段名录入
              </div>
            )}
          </div>
        ) : (
          <Form form={fillForm} layout="vertical" disabled={readOnly}>
            {templateFields.map((field) => (
              <Form.Item
                key={field.fieldName}
                name={field.fieldName}
                label={field.fieldLabel || field.fieldName}
                rules={(field.required === 1 || field.isRequired === 1)
                  ? [{ required: true, message: `请填写${field.fieldLabel || field.fieldName}` }]
                  : undefined}
              >
                <Input placeholder={`请输入${field.fieldLabel || field.fieldName}`} />
              </Form.Item>
            ))}
            {!fieldsLoading && templateFields.length === 0 && (
              <div style={{ textAlign: 'center', padding: 40, color: '#999' }}>
                暂无填报字段，请联系管理员检查任务关联的模板字段
              </div>
            )}
          </Form>
        )}
      </Modal>
      <Modal
        title={reasonModal.type === 'noUpdate' ? '无更新提交' : '退回任务'}
        open={reasonModal.open}
        onOk={submitReason}
        onCancel={() => setReasonModal({ open: false, type: 'noUpdate' })}
        okText="确定"
        cancelText="取消"
      >
        <p style={{ color: '#999' }}>
          {reasonModal.type === 'noUpdate'
            ? '本期数据无变化时，可提交「无更新」说明，任务计为已填报，不产生入库数据。'
            : '退回后该任务将回到分派人处，可重新分派。'}
        </p>
        <Input.TextArea
          rows={4}
          maxLength={200}
          showCount
          value={reasonText}
          onChange={(e) => setReasonText(e.target.value)}
          placeholder={reasonModal.type === 'noUpdate' ? '请填写无更新说明（必填，≤200 字）' : '请填写退回原因（必填，≤200 字）'}
        />
      </Modal>

    </div>
  );
};

export default FillTaskPage;
