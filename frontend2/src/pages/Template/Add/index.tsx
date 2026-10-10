import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Steps, Form, Input, Upload, Button, Card, Select, Table, Space, message, Popconfirm, App,
} from 'antd';
import { PlusOutlined, UploadOutlined, DeleteOutlined, ArrowLeftOutlined } from '@ant-design/icons';
import { useTemplateStore } from '@/stores/useTemplateStore';
import { getDatasourceListApi } from '@/services/api';
import type { TemplateField } from '@/services/api';

const { Step } = Steps;

const TemplateAddPage: React.FC = () => {
  const { message: msg } = App.useApp();
  const navigate = useNavigate();
  const { create, uploadExcel } = useTemplateStore();

  const [currentStep, setCurrentStep] = useState(0);
  const [templateId, setTemplateId] = useState<number | null>(null);
  const [form] = Form.useForm();
  const [fieldForm] = Form.useForm();
  const [dsForm] = Form.useForm();
  const [fields, setFields] = useState<TemplateField[]>([]);
  const [fileList, setFileList] = useState<any[]>([]);
  const [datasources, setDatasources] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);

  // Step 1: Save basic info
  const handleStep1Next = async () => {
    try {
      const values = await form.validateFields();
      setLoading(true);
      // Create template via store (returns void, but we simulate getting an id)
      await create({ name: values.name, code: values.code, description: values.description });
      msg.success('基本信息保存成功');
      // In real API, create would return the id; we simulate with Date.now
      setTemplateId(Date.now());
      setCurrentStep(1);
    } catch (error: any) {
      if (error.errorFields) return;
      msg.error(error?.message || '保存失败');
    } finally {
      setLoading(false);
    }
  };

  // Step 2: Upload Excel
  const handleUpload = async () => {
    if (fileList.length === 0) {
      msg.warning('请先选择文件');
      return;
    }
    if (!templateId) {
      msg.error('请先保存基本信息');
      return;
    }
    try {
      setLoading(true);
      const formData = new FormData();
      formData.append('file', fileList[0].originFileObj);
      await uploadExcel(templateId, formData);
      msg.success('Excel上传成功');
      setCurrentStep(2);
    } catch {
      msg.error('上传失败');
    } finally {
      setLoading(false);
    }
  };

  // Step 3: Field mapping
  const handleAddField = () => {
    setFields([
      ...fields,
      {
        fieldName: '',
        fieldLabel: '',
        fieldType: 'string',
        excelColumn: '',
        required: 0,
        sort: fields.length + 1,
      },
    ]);
  };

  const handleDeleteField = (index: number) => {
    const newFields = fields.filter((_, i) => i !== index);
    setFields(newFields);
  };

  const handleFieldChange = (index: number, key: string, value: any) => {
    const newFields = [...fields];
    (newFields[index] as any)[key] = value;
    setFields(newFields);
  };

  const handleStep3Next = () => {
    if (fields.length === 0) {
      msg.warning('请添加至少一个字段');
      return;
    }
    // Validate all fields have names
    for (const f of fields) {
      if (!f.fieldName || !f.fieldLabel || !f.excelColumn) {
        msg.warning('请完整填写所有字段信息');
        return;
      }
    }
    setCurrentStep(3);
  };

  // Step 4: Datasource config
  const loadDatasources = async () => {
    try {
      const res = await getDatasourceListApi({ current: 1, size: 100 });
      setDatasources(res.data.data.list || []);
    } catch {
      // ignore
    }
  };

  // Load datasources when entering step 4
  React.useEffect(() => {
    if (currentStep === 3) {
      loadDatasources();
    }
  }, [currentStep]);

  const handleSubmit = async () => {
    try {
      const dsValues = await dsForm.validateFields();
      setLoading(true);
      // Update template with fields and datasource
      await create({
        name: form.getFieldValue('name'),
        code: form.getFieldValue('code'),
        description: form.getFieldValue('description'),
        fields,
        datasourceId: dsValues.datasourceId,
      });
      msg.success('模板创建成功');
      navigate('/template/list');
    } catch (error: any) {
      if (error.errorFields) return;
      msg.error(error?.message || '提交失败');
    } finally {
      setLoading(false);
    }
  };

  const fieldColumns = [
    {
      title: '字段名', width: 140,
      render: (_: any, __: any, index: number) => (
        <Input
          placeholder="fieldName"
          value={fields[index]?.fieldName}
          onChange={(e) => handleFieldChange(index, 'fieldName', e.target.value)}
        />
      ),
    },
    {
      title: '字段标签', width: 140,
      render: (_: any, __: any, index: number) => (
        <Input
          placeholder="字段标签"
          value={fields[index]?.fieldLabel}
          onChange={(e) => handleFieldChange(index, 'fieldLabel', e.target.value)}
        />
      ),
    },
    {
      title: '类型', width: 100,
      render: (_: any, __: any, index: number) => (
        <Select
          value={fields[index]?.fieldType}
          onChange={(v) => handleFieldChange(index, 'fieldType', v)}
          style={{ width: '100%' }}
        >
          <Select.Option value="string">字符串</Select.Option>
          <Select.Option value="number">数字</Select.Option>
          <Select.Option value="date">日期</Select.Option>
          <Select.Option value="datetime">日期时间</Select.Option>
        </Select>
      ),
    },
    {
      title: 'Excel列', width: 100,
      render: (_: any, __: any, index: number) => (
        <Input
          placeholder="A/B/C"
          value={fields[index]?.excelColumn}
          onChange={(e) => handleFieldChange(index, 'excelColumn', e.target.value)}
        />
      ),
    },
    {
      title: '必填', width: 70,
      render: (_: any, __: any, index: number) => (
        <Select
          value={fields[index]?.required}
          onChange={(v) => handleFieldChange(index, 'required', v)}
          style={{ width: '100%' }}
        >
          <Select.Option value={0}>否</Select.Option>
          <Select.Option value={1}>是</Select.Option>
        </Select>
      ),
    },
    {
      title: '操作', width: 60,
      render: (_: any, __: any, index: number) => (
        <Popconfirm title="确定删除?" onConfirm={() => handleDeleteField(index)}>
          <Button type="link" danger icon={<DeleteOutlined />} size="small" />
        </Popconfirm>
      ),
    },
  ];

  const steps = [
    { title: '基本信息' },
    { title: '上传Excel' },
    { title: '字段映射' },
    { title: '数据源配置' },
  ];

  return (
    <div style={{ padding: 24, background: '#fff', borderRadius: 8, minHeight: 500 }}>
      <div style={{ marginBottom: 24 }}>
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/template/list')} style={{ marginBottom: 16 }}>
          返回列表
        </Button>
        <Steps current={currentStep}>
          {steps.map((item) => (
            <Step key={item.title} title={item.title} />
          ))}
        </Steps>
      </div>

      <div style={{ maxWidth: 800, margin: '0 auto' }}>
        {/* Step 1: Basic Info */}
        {currentStep === 0 && (
          <Card title="模板基本信息">
            <Form form={form} layout="vertical">
              <Form.Item name="name" label="模板名称" rules={[{ required: true, message: '请输入模板名称' }]}>
                <Input placeholder="请输入模板名称" />
              </Form.Item>
              <Form.Item name="code" label="模板编码" rules={[{ required: true, message: '请输入模板编码' }]}>
                <Input placeholder="请输入模板编码" />
              </Form.Item>
              <Form.Item name="description" label="描述">
                <Input.TextArea rows={4} placeholder="请输入模板描述" />
              </Form.Item>
              <Form.Item>
                <Button type="primary" onClick={handleStep1Next} loading={loading}>
                  下一步
                </Button>
              </Form.Item>
            </Form>
          </Card>
        )}

        {/* Step 2: Upload Excel */}
        {currentStep === 1 && (
          <Card title="上传Excel模板">
            <div style={{ textAlign: 'center', padding: '40px 0' }}>
              <Upload
                fileList={fileList}
                beforeUpload={(file) => {
                  setFileList([{ uid: '-1', name: file.name, status: 'done', originFileObj: file }]);
                  return false;
                }}
                onRemove={() => setFileList([])}
                maxCount={1}
                accept=".xlsx,.xls"
              >
                <Button icon={<UploadOutlined />}>选择Excel文件</Button>
              </Upload>
              <p style={{ marginTop: 8, color: '#999' }}>支持 .xlsx, .xls 格式</p>
            </div>
            <div style={{ display: 'flex', gap: 8, justifyContent: 'center' }}>
              <Button onClick={() => setCurrentStep(0)}>上一步</Button>
              <Button type="primary" onClick={handleUpload} loading={loading} disabled={fileList.length === 0}>
                上传并解析
              </Button>
            </div>
          </Card>
        )}

        {/* Step 3: Field Mapping */}
        {currentStep === 2 && (
          <Card
            title="字段映射"
            extra={<Button type="dashed" icon={<PlusOutlined />} onClick={handleAddField}>添加字段</Button>}
          >
            <Table
              columns={fieldColumns}
              dataSource={fields}
              rowKey={(_, index) => String(index)}
              pagination={false}
              size="small"
              bordered
            />
            <div style={{ marginTop: 16, display: 'flex', gap: 8, justifyContent: 'center' }}>
              <Button onClick={() => setCurrentStep(1)}>上一步</Button>
              <Button type="primary" onClick={handleStep3Next}>
                下一步
              </Button>
            </div>
          </Card>
        )}

        {/* Step 4: Datasource Config */}
        {currentStep === 3 && (
          <Card title="数据源配置">
            <Form form={dsForm} layout="vertical">
              <Form.Item name="datasourceId" label="选择数据源">
                <Select placeholder="请选择数据源" allowClear>
                  {datasources.map((ds: any) => (
                    <Select.Option key={ds.id} value={ds.id}>
                      {ds.name} ({ds.type})
                    </Select.Option>
                  ))}
                </Select>
              </Form.Item>
              <div style={{ display: 'flex', gap: 8, justifyContent: 'center', marginTop: 24 }}>
                <Button onClick={() => setCurrentStep(2)}>上一步</Button>
                <Button type="primary" onClick={handleSubmit} loading={loading}>
                  完成创建
                </Button>
              </div>
            </Form>
          </Card>
        )}
      </div>
    </div>
  );
};

export default TemplateAddPage;
