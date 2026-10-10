import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Alert, Button, Card, Descriptions, Divider, Form, Input, InputNumber, Result,
  Select, Space, Steps, Switch, Table, Tag, Upload, message,
} from 'antd';
import {
  ArrowDownOutlined, ArrowUpOutlined, CheckCircleOutlined, DeleteOutlined,
  InboxOutlined, PlusOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import {
  createTemplateApi, getDatasourceListApi, materializeTemplateApi, parseTemplateExcelApi,
  updateTemplateStatusApi,
  type TemplateSaveField,
} from '@/services/api';

/** 向导内维护的字段行 */
interface WizardField {
  key: string;
  fieldName: string;
  fieldCode: string;
  dbColumn: string;
  fieldType: string;
  fieldLength: number;
  isRequired: number;
  isKey: number;
  excelColumn: string;
  sort: number;
}

const FIELD_TYPES = ['VARCHAR', 'CHAR', 'INT', 'BIGINT', 'DECIMAL', 'DATE', 'DATETIME', 'TEXT'];
const CYCLE_OPTIONS = [
  { value: 1, label: '月度' },
  { value: 2, label: '季度' },
  { value: 3, label: '半年' },
  { value: 4, label: '年度' },
  { value: 9, label: '一次性' },
];

let seq = 0;
const nextKey = () => `wf-${Date.now()}-${seq++}`;

function emptyField(index: number): WizardField {
  return {
    key: nextKey(),
    fieldName: '',
    fieldCode: `f${index + 1}`,
    dbColumn: `f${index + 1}`,
    fieldType: 'VARCHAR',
    fieldLength: 255,
    isRequired: 0,
    isKey: 0,
    excelColumn: '',
    sort: index,
  };
}

export default function TemplateWizard() {
  const navigate = useNavigate();
  const [current, setCurrent] = useState(0);
  const [fields, setFields] = useState<WizardField[]>([]);
  const [headers, setHeaders] = useState<string[]>([]);
  const [info, setInfo] = useState({
    templateName: '',
    templateCode: '',
    fillingCycle: 1,
    description: '',
  });
  const [materialize, setMaterialize] = useState({ datasourceId: undefined as number | undefined, tableName: '', doIt: false, recreate: false });
  const [datasourceList, setDatasourceList] = useState<Array<Record<string, any>>>([]);
  const [parsing, setParsing] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [done, setDone] = useState<{ id: number; tableName: string; published?: boolean } | null>(null);
  const [publish, setPublish] = useState(true);

  useEffect(() => {
    getDatasourceListApi({ page: 1, pageSize: 100 })
      .then((res: any) => {
        const page = res?.data?.data || {};
        setDatasourceList(page.list || []);
      })
      .catch(() => setDatasourceList([]));
  }, []);

  const patchField = useCallback((key: string, patch: Partial<WizardField>) => {
    setFields((prev) => prev.map((f) => (f.key === key ? { ...f, ...patch } : f)));
  }, []);

  const addField = useCallback(() => {
    setFields((prev) => [...prev, emptyField(prev.length)]);
  }, []);

  const removeField = useCallback((key: string) => {
    setFields((prev) => prev.filter((f) => f.key !== key).map((f, i) => ({ ...f, sort: i })));
  }, []);

  const moveField = useCallback((index: number, dir: -1 | 1) => {
    setFields((prev) => {
      const next = [...prev];
      const target = index + dir;
      if (target < 0 || target >= next.length) return prev;
      [next[index], next[target]] = [next[target], next[index]];
      return next.map((f, i) => ({ ...f, sort: i }));
    });
  }, []);

  /** 第 1 步：解析 Excel 表头 */
  const handleParse = async (file: File, headerRowIndex = 0) => {
    setParsing(true);
    try {
      const res: any = await parseTemplateExcelApi(file, headerRowIndex);
      const data = res?.data?.data || {};
      const parsed: Array<Record<string, any>> = data.fields || [];
      setHeaders(data.headers || []);
      if (parsed.length === 0) {
        message.warning('未从该 Excel 解析到表头，请检查文件或手动添加字段');
      } else {
        setFields(
          parsed.map((f, i) => ({
            key: nextKey(),
            fieldName: String(f.fieldName ?? ''),
            fieldCode: String(f.fieldCode ?? `f${i + 1}`),
            dbColumn: String(f.dbColumn ?? f.fieldCode ?? `f${i + 1}`),
            fieldType: String(f.fieldType ?? 'VARCHAR'),
            fieldLength: Number(f.fieldLength ?? 255),
            isRequired: Number(f.isRequired ?? 0),
            isKey: Number(f.isKey ?? 0),
            excelColumn: String(f.excelColumn ?? ''),
            sort: i,
          })),
        );
        message.success(`解析成功：识别 ${parsed.length} 个字段`);
      }
    } catch (e: any) {
      message.error(e?.message || 'Excel 解析失败');
    } finally {
      setParsing(false);
    }
    return false; // 阻止 antd 自动上传
  };

  const validateStep = (step: number): string | null => {
    if (step === 0) {
      if (fields.length === 0) return '请先上传 Excel 解析字段，或手动添加至少一个字段';
      return null;
    }
    if (step === 1) {
      for (let i = 0; i < fields.length; i++) {
        const f = fields[i];
        if (!f.fieldName.trim()) return `第 ${i + 1} 行：字段名（中文）不能为空`;
        if (!f.fieldCode.trim()) return `第 ${i + 1} 行：字段编码不能为空`;
        if (!/^[A-Za-z][A-Za-z0-9_]*$/.test(f.fieldCode.trim())) return `第 ${i + 1} 行：字段编码只能是字母/数字/下划线且以字母开头`;
        if (!f.dbColumn.trim()) return `第 ${i + 1} 行：库列名不能为空`;
      }
      const codes = fields.map((f) => f.fieldCode.trim().toLowerCase());
      if (new Set(codes).size !== codes.length) return '字段编码有重复，请修改';
      return null;
    }
    if (step === 2) {
      if (!info.templateName.trim()) return '请填写模板名称';
      if (!info.templateCode.trim()) return '请填写模板编码';
      if (!/^[A-Za-z][A-Za-z0-9_]*$/.test(info.templateCode.trim())) return '模板编码只能是字母/数字/下划线且以字母开头';
      return null;
    }
    if (step === 3) {
      if (materialize.doIt && !materialize.datasourceId) return '勾选了立即物化建表，必须选择目标数据源';
      return null;
    }
    return null;
  };

  const goNext = () => {
    const err = validateStep(current);
    if (err) {
      message.error(err);
      return;
    }
    if (current === 2 && !materialize.tableName) {
      setMaterialize((m) => ({ ...m, tableName: `t_${info.templateCode.trim().toLowerCase()}` }));
    }
    setCurrent((c) => Math.min(c + 1, 4));
  };

  const handleSubmit = async () => {
    for (const step of [0, 1, 2, 3]) {
      const err = validateStep(step);
      if (err) {
        message.error(err);
        setCurrent(step);
        return;
      }
    }
    setSubmitting(true);
    try {
      const payload = {
        templateName: info.templateName.trim(),
        templateCode: info.templateCode.trim(),
        fillingCycle: info.fillingCycle,
        description: info.description,
        datasourceId: materialize.datasourceId,
        tableName: materialize.tableName || undefined,
        fields: fields.map((f) => ({
          fieldName: f.fieldName.trim(),
          fieldCode: f.fieldCode.trim(),
          dbColumn: f.dbColumn.trim(),
          fieldType: f.fieldType,
          fieldLength: f.fieldLength,
          isRequired: f.isRequired,
          isKey: f.isKey,
          excelColumn: f.excelColumn,
          sort: f.sort,
        })) as TemplateSaveField[],
      };
      const res: any = await createTemplateApi(payload);
      const newId = Number(res?.data?.data ?? 0);
      if (!newId) {
        throw new Error(res?.data?.msg || '模板创建未返回主键');
      }
      let tableName = '';
      if (materialize.doIt) {
        const m: any = await materializeTemplateApi(newId, materialize.recreate);
        tableName = m?.data?.data?.tableName || '';
      }
      let published = false;
      if (publish) {
        // 缺陷修复：向导产物默认草稿，列表页没有发布入口会卡住"模板未启用"，这里直接发布
        try {
          await updateTemplateStatusApi(newId, 1);
          published = true;
        } catch (e) {
          message.warning('模板已创建，但自动发布失败，请在列表页点「发布」');
        }
      }
      setDone({ id: newId, tableName, published });
      message.success(materialize.doIt ? '模板创建并物化成功' : '模板创建成功');
    } catch (e: any) {
      message.error(e?.message || '创建失败');
    } finally {
      setSubmitting(false);
    }
  };

  const fieldColumns = useMemo<ColumnsType<WizardField>>(
    () => [
      {
        title: '序号', width: 60, align: 'center',
        render: (_v, _r, i) => i + 1,
      },
      {
        title: '字段名（中文）', width: 160, dataIndex: 'fieldName',
        render: (v: string, r) => (
          <Input value={v} placeholder="如 企业名称" onChange={(e) => patchField(r.key, { fieldName: e.target.value })} />
        ),
      },
      {
        title: '字段编码', width: 140, dataIndex: 'fieldCode',
        render: (v: string, r) => (
          <Input value={v} placeholder="如 qymc" onChange={(e) => patchField(r.key, { fieldCode: e.target.value })} />
        ),
      },
      {
        title: '库列名', width: 140, dataIndex: 'dbColumn',
        render: (v: string, r) => (
          <Input value={v} placeholder="目标表列名" onChange={(e) => patchField(r.key, { dbColumn: e.target.value })} />
        ),
      },
      {
        title: '类型', width: 118, dataIndex: 'fieldType',
        render: (v: string, r) => (
          <Select value={v} options={FIELD_TYPES.map((t) => ({ value: t, label: t }))} style={{ width: '100%' }}
            onChange={(val) => patchField(r.key, { fieldType: val })} />
        ),
      },
      {
        title: '长度', width: 92, dataIndex: 'fieldLength',
        render: (v: number, r) => (
          <InputNumber min={1} max={8000} value={v} style={{ width: '100%' }}
            onChange={(val) => patchField(r.key, { fieldLength: Number(val || 255) })} />
        ),
      },
      {
        title: '必填', width: 74, align: 'center', dataIndex: 'isRequired',
        render: (v: number, r) => (
          <Switch size="small" checked={v === 1} onChange={(c) => patchField(r.key, { isRequired: c ? 1 : 0 })} />
        ),
      },
      {
        title: '主键', width: 74, align: 'center', dataIndex: 'isKey',
        render: (v: number, r) => (
          <Switch size="small" checked={v === 1} onChange={(c) => patchField(r.key, { isKey: c ? 1 : 0 })} />
        ),
      },
      {
        title: 'Excel 列', width: 100, dataIndex: 'excelColumn',
        render: (v: string, r) => (
          <Input value={v} placeholder="A" onChange={(e) => patchField(r.key, { excelColumn: e.target.value })} />
        ),
      },
      {
        title: '操作', width: 116, fixed: 'right', align: 'center',
        render: (_v, _r, i) => (
          <Space size={2}>
            <Button type="text" size="small" icon={<ArrowUpOutlined />} disabled={i === 0} onClick={() => moveField(i, -1)} />
            <Button type="text" size="small" icon={<ArrowDownOutlined />} disabled={i === fields.length - 1} onClick={() => moveField(i, 1)} />
            <Button type="text" size="small" danger icon={<DeleteOutlined />} onClick={() => removeField(fields[i].key)} />
          </Space>
        ),
      },
    ],
    [fields, moveField, patchField, removeField],
  );

  if (done) {
    return (
      <Card title="模板创建完成">
        <Result
          status="success"
          title={`模板已创建（ID ${done.id}）`}
          subTitle={`${done.tableName ? `已物化到目标库，表名：${done.tableName}` : '未物化（可在模板列表中随时物化）'}${done.published ? ' · 已发布' : ' · 仍为草稿（未发布）'}`}
          extra={[
            <Button type="primary" key="list" onClick={() => navigate('/template/list')}>返回模板列表</Button>,
            <Button key="detail" onClick={() => navigate(`/template/detail/${done.id}`)}>查看模板详情</Button>,
            <Button key="again" onClick={() => {
              setDone(null); setCurrent(0); setFields([]); setHeaders([]);
              setInfo({ templateName: '', templateCode: '', fillingCycle: 1, description: '' });
              setMaterialize({ datasourceId: undefined, tableName: '', doIt: false, recreate: false });
            }}>再建一个</Button>,
          ]}
        />
      </Card>
    );
  }

  const steps = [
    {
      title: '上传并解析 Excel',
      content: (
        <div>
          <Alert
            type="info" showIcon style={{ marginBottom: 16 }}
            message="上传原版制度表/上报表（.xlsx/.xls），系统自动识别表头并生成字段建议"
            description="也可以点「手动添加字段」跳过上传，直接进入下一步逐个录入字段。"
          />
          <Upload.Dragger
            accept=".xlsx,.xls"
            maxCount={1}
            showUploadList={false}
            beforeUpload={(file) => handleParse(file as unknown as File)}
            disabled={parsing}
          >
            <p className="ant-upload-drag-icon"><InboxOutlined /></p>
            <p className="ant-upload-text">{parsing ? '正在解析…' : '点击或拖拽 Excel 文件到此处解析'}</p>
            <p className="ant-upload-hint">解析结果会带着推荐类型自动填到第 2 步，可逐项修改</p>
          </Upload.Dragger>
          <Space style={{ marginTop: 16 }}>
            <Button onClick={addField} icon={<PlusOutlined />}>手动添加字段</Button>
            {headers.length > 0 && <Tag color="blue">识别到 {headers.length} 个表头：{headers.slice(0, 8).join('、')}{headers.length > 8 ? ' …' : ''}</Tag>}
            {fields.length > 0 && <Tag color="green">当前字段 {fields.length} 个</Tag>}
          </Space>
        </div>
      ),
    },
    {
      title: '字段规则',
      content: (
        <div>
          <Alert
            type="info" showIcon style={{ marginBottom: 12 }}
            message="逐项确认字段名（中文）、字段编码、目标库列名、类型与长度；主键与必填按业务勾选"
            description="编码规则：字母开头，只含字母/数字/下划线（会作为目标表的列名与接口字段名）。"
          />
          <Space style={{ marginBottom: 12 }}>
            <Button icon={<PlusOutlined />} onClick={addField}>添加字段</Button>
            <span style={{ color: '#888' }}>共 {fields.length} 个字段</span>
          </Space>
          <Table<WizardField>
            rowKey="key"
            size="small"
            columns={fieldColumns}
            dataSource={fields}
            pagination={false}
            scroll={{ x: 1200 }}
          />
        </div>
      ),
    },
    {
      title: '模板信息与展示',
      content: (
        <Form layout="vertical" style={{ maxWidth: 720 }}>
          <Form.Item label="模板名称" required>
            <Input value={info.templateName} placeholder="如 2026 年企业经营情况上报表"
              onChange={(e) => setInfo((s) => ({ ...s, templateName: e.target.value }))} />
          </Form.Item>
          <Form.Item label="模板编码" required extra="字母开头，只含字母/数字/下划线；会用于生成目标表名">
            <Input value={info.templateCode} placeholder="如 ent_business_2026"
              onChange={(e) => setInfo((s) => ({ ...s, templateCode: e.target.value }))} />
          </Form.Item>
          <Form.Item label="填报周期">
            <Select value={info.fillingCycle} options={CYCLE_OPTIONS}
              onChange={(v) => setInfo((s) => ({ ...s, fillingCycle: v }))} style={{ width: 200 }} />
          </Form.Item>
          <Form.Item label="说明">
            <Input.TextArea rows={3} value={info.description} placeholder="模板用途、填报口径等"
              onChange={(e) => setInfo((s) => ({ ...s, description: e.target.value }))} />
          </Form.Item>
          <Divider orientation="left" plain>字段展示顺序</Divider>
          <Table<WizardField>
            rowKey="key"
            size="small"
            pagination={false}
            dataSource={fields}
            columns={[
              { title: '顺序', width: 70, align: 'center', render: (_v, _r, i) => i + 1 },
              { title: '字段名（中文）', dataIndex: 'fieldName' },
              { title: '字段编码', dataIndex: 'fieldCode', width: 180 },
              { title: '必填', width: 80, align: 'center', dataIndex: 'isRequired', render: (v: number) => (v === 1 ? <Tag color="red">必填</Tag> : <Tag>选填</Tag>) },
              {
                title: '排序', width: 120, align: 'center',
                render: (_v, _r, i) => (
                  <Space size={2}>
                    <Button type="text" size="small" icon={<ArrowUpOutlined />} disabled={i === 0} onClick={() => moveField(i, -1)} />
                    <Button type="text" size="small" icon={<ArrowDownOutlined />} disabled={i === fields.length - 1} onClick={() => moveField(i, 1)} />
                  </Space>
                ),
              },
            ]}
          />
        </Form>
      ),
    },
    {
      title: '物化配置',
      content: (
        <Form layout="vertical" style={{ maxWidth: 720 }}>
          <Alert
            type="warning" showIcon style={{ marginBottom: 16 }}
            message="物化 = 按字段定义在目标业务库里建表（原版「模型发布」的等价动作）"
            description="选择数据源后，系统会按上面的库列名/类型/长度建表；不勾选也可以先只建模板，之后再物化。"
          />
          <Form.Item label="目标数据源" required={materialize.doIt}>
            <Select
              allowClear
              placeholder="选择目标业务库（MySQL / PostgreSQL 等）"
              style={{ width: 420 }}
              value={materialize.datasourceId}
              onChange={(v) => setMaterialize((m) => ({ ...m, datasourceId: v }))}
              options={datasourceList.map((d) => ({
                value: d.id,
                label: `${d.name || d.dsName || d.datasourceName || d.id}（${d.type || d.dbType || '-'}）`,
              }))}
            />
          </Form.Item>
          <Form.Item label="目标表名" extra="留空则按模板编码自动生成（t_模板编码）">
            <Input style={{ width: 420 }} value={materialize.tableName} placeholder="如 t_ent_business_2026"
              onChange={(e) => setMaterialize((m) => ({ ...m, tableName: e.target.value }))} />
          </Form.Item>
          <Form.Item label="立即物化建表">
            <Switch checked={materialize.doIt} onChange={(c) => setMaterialize((m) => ({ ...m, doIt: c }))} />
            {materialize.doIt && (
              <span style={{ marginLeft: 16 }}>
                重建已有表 <Switch size="small" checked={materialize.recreate}
                  onChange={(c) => setMaterialize((m) => ({ ...m, recreate: c }))} />
                <span style={{ color: '#d46b08', marginLeft: 8 }}>（勾选会先 DROP 再建，慎用）</span>
              </span>
            )}
          </Form.Item>
        </Form>
      ),
    },
    {
      title: '确认提交',
      content: (
        <div>
          <Descriptions bordered column={2} size="small" style={{ marginBottom: 16 }}>
            <Descriptions.Item label="模板名称">{info.templateName}</Descriptions.Item>
            <Descriptions.Item label="模板编码">{info.templateCode}</Descriptions.Item>
            <Descriptions.Item label="填报周期">
              {CYCLE_OPTIONS.find((c) => c.value === info.fillingCycle)?.label || info.fillingCycle}
            </Descriptions.Item>
            <Descriptions.Item label="字段数">{fields.length}</Descriptions.Item>
            <Descriptions.Item label="目标数据源">
              {materialize.datasourceId
                ? (datasourceList.find((d) => d.id === materialize.datasourceId)?.name
                  || datasourceList.find((d) => d.id === materialize.datasourceId)?.dsName
                  || materialize.datasourceId)
                : '未选择'}
            </Descriptions.Item>
            <Descriptions.Item label="目标表名">{materialize.tableName || `t_${info.templateCode.toLowerCase()}`}</Descriptions.Item>
            <Descriptions.Item label="立即物化">{materialize.doIt ? (materialize.recreate ? '是（先删后建）' : '是') : '否'}</Descriptions.Item>
            <Descriptions.Item label="创建后立即发布">
              <Switch size="small" checked={publish} onChange={setPublish} />
              <span style={{ color: '#888', marginLeft: 8 }}>不发布则为草稿，建任务时会提示"模板未启用"</span>
            </Descriptions.Item>
            <Descriptions.Item label="说明" span={2}>{info.description || '-'}</Descriptions.Item>
          </Descriptions>
          <Table<WizardField>
            rowKey="key"
            size="small"
            pagination={false}
            dataSource={fields}
            columns={[
              { title: '字段名（中文）', dataIndex: 'fieldName' },
              { title: '编码', dataIndex: 'fieldCode', width: 140 },
              { title: '库列名', dataIndex: 'dbColumn', width: 140 },
              { title: '类型', dataIndex: 'fieldType', width: 110 },
              { title: '长度', dataIndex: 'fieldLength', width: 80, align: 'center' },
              { title: '必填', dataIndex: 'isRequired', width: 70, align: 'center', render: (v: number) => (v === 1 ? '是' : '否') },
            ]}
          />
          <Alert
            style={{ marginTop: 16 }} type="success" showIcon icon={<CheckCircleOutlined />}
            message="确认无误后点下方「创建模板」；创建成功后会自动跳转到结果页"
          />
        </div>
      ),
    },
  ];

  return (
    <Card title="模板向导（5 步）" extra={<Button onClick={() => navigate('/template/list')}>返回列表</Button>}>
      <Steps current={current} items={steps.map((s) => ({ title: s.title }))} style={{ marginBottom: 24 }} />
      <div style={{ minHeight: 320 }}>{steps[current].content}</div>
      <Divider />
      <Space>
        <Button disabled={current === 0} onClick={() => setCurrent((c) => Math.max(0, c - 1))}>上一步</Button>
        {current < 4 && <Button type="primary" onClick={goNext}>下一步</Button>}
        {current === 4 && (
          <Button type="primary" loading={submitting} onClick={handleSubmit}>创建模板</Button>
        )}
        <Button type="link" onClick={() => navigate('/template/list')}>取消</Button>
      </Space>
    </Card>
  );
}
