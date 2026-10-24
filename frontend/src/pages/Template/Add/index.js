import React, { useState, useEffect } from 'react';
import { connect } from 'dva';
import {
  Steps, Form, Input, Select, Button, Upload, Table, Card, message,
  PageHeader, Row, Col, Space,
} from 'antd';
import {
  UploadOutlined, InboxOutlined, ArrowLeftOutlined,
  ArrowRightOutlined, CheckOutlined,
} from '@ant-design/icons';
import { useSelector, useDispatch, history } from 'umi';

const { Option } = Select;
const { TextArea } = Input;
const { Dragger } = Upload;

const TemplateAdd = () => {
  const dispatch = useDispatch();
  const { fieldMappings } = useSelector((state) => state.template);
  const deptTree = useSelector((state) => state.dept.treeData);

  const [currentStep, setCurrentStep] = useState(0);
  const [form1] = Form.useForm();
  const [form4] = Form.useForm();
  const [fileList, setFileList] = useState([]);
  const [excelPreview, setExcelPreview] = useState(null);
  const [mappings, setMappings] = useState([]);
  const [savedTemplateId, setSavedTemplateId] = useState(null);

  useEffect(() => {
    dispatch({ type: 'dept/fetchTree' });
    dispatch({ type: 'project/fetchList', payload: { page: 1, size: 1000 } });
  }, []);

  const projectList = useSelector((state) => state.project.list) || [];

  const steps = [
    { title: '基本信息' },
    { title: '上传Excel' },
    { title: '字段映射' },
    { title: '数据库配置' },
  ];

  const handleSaveStep1 = async () => {
    const values = await form1.validateFields();
    const success = await dispatch({ type: 'template/add', payload: values });
    if (success) {
      setSavedTemplateId(Date.now());
      setCurrentStep(1);
    }
  };

  const handleUploadChange = (info) => {
    let fl = [...info.fileList];
    fl = fl.slice(-1);
    setFileList(fl);
    if (info.file.status === 'done') {
      message.success(`${info.file.name} 上传成功`);
      setExcelPreview(['序号', '项目名称', '金额', '日期', '备注']);
      setMappings([
        { key: 1, excelColumn: '序号', fieldName: '', dbColumn: '', fieldType: '' },
        { key: 2, excelColumn: '项目名称', fieldName: '', dbColumn: '', fieldType: '' },
        { key: 3, excelColumn: '金额', fieldName: '', dbColumn: '', fieldType: '' },
        { key: 4, excelColumn: '日期', fieldName: '', dbColumn: '', fieldType: '' },
        { key: 5, excelColumn: '备注', fieldName: '', dbColumn: '', fieldType: '' },
      ]);
    }
  };

  const uploadProps = {
    name: 'file',
    action: '/api/template/uploadExcel',
    fileList,
    onChange: handleUploadChange,
    beforeUpload: () => true,
  };

  const handleMappingChange = (index, field, value) => {
    const newMappings = [...mappings];
    newMappings[index] = { ...newMappings[index], [field]: value };
    setMappings(newMappings);
  };

  const mappingColumns = [
    { title: 'Excel列名', dataIndex: 'excelColumn', width: 130 },
    {
      title: '字段名称',
      dataIndex: 'fieldName',
      width: 150,
      render: (text, record, index) => (
        <Input
          value={text}
          size="small"
          onChange={(e) => handleMappingChange(index, 'fieldName', e.target.value)}
          placeholder="字段名称"
        />
      ),
    },
    {
      title: '数据库字段',
      dataIndex: 'dbColumn',
      width: 150,
      render: (text, record, index) => (
        <Input
          value={text}
          size="small"
          onChange={(e) => handleMappingChange(index, 'dbColumn', e.target.value)}
          placeholder="数据库字段"
        />
      ),
    },
    {
      title: '字段类型',
      dataIndex: 'fieldType',
      width: 130,
      render: (text, record, index) => (
        <Select
          value={text || undefined}
          size="small"
          style={{ width: '100%' }}
          onChange={(v) => handleMappingChange(index, 'fieldType', v)}
          placeholder="类型"
        >
          <Option value="VARCHAR">VARCHAR</Option>
          <Option value="INTEGER">INTEGER</Option>
          <Option value="DECIMAL">DECIMAL</Option>
          <Option value="DATE">DATE</Option>
          <Option value="DATETIME">DATETIME</Option>
          <Option value="TEXT">TEXT</Option>
        </Select>
      ),
    },
  ];

  const handleSaveMapping = async () => {
    const hasUnmapped = mappings.some((m) => !m.fieldName || !m.dbColumn);
    if (hasUnmapped) {
      message.warning('请完成所有字段的映射配置');
      return;
    }
    const success = await dispatch({
      type: 'template/saveFieldMapping',
      payload: { templateId: savedTemplateId, mappings },
    });
    if (success) {
      setCurrentStep(3);
    }
  };

  const handleFinish = async () => {
    const values = await form4.validateFields();
    message.success('模板创建完成');
    history.push('/template/list');
  };

  const renderStepContent = () => {
    switch (currentStep) {
      case 0:
        return (
          <Form form={form1} layout="vertical" style={{ maxWidth: 600 }}>
            <Row gutter={16}>
              <Col span={12}>
                <Form.Item
                  name="templateName"
                  label="模板名称"
                  rules={[{ required: true, message: '请输入模板名称' }]}
                >
                  <Input placeholder="请输入模板名称" />
                </Form.Item>
              </Col>
              <Col span={12}>
                <Form.Item
                  name="templateCode"
                  label="模板编码"
                  rules={[{ required: true, message: '请输入模板编码' }]}
                >
                  <Input placeholder="请输入模板编码" />
                </Form.Item>
              </Col>
            </Row>
            <Form.Item
              name="fillingCycle"
              label="填报周期"
              rules={[{ required: true, message: '请选择填报周期' }]}
            >
              <Select placeholder="请选择填报周期">
                <Option value={1}>每日</Option>
                <Option value={2}>每周</Option>
                <Option value={3}>每月</Option>
                <Option value={4}>每季</Option>
                <Option value={5}>每年</Option>
                <Option value={6}>一次性</Option>
              </Select>
            </Form.Item>
            <Row gutter={16}>
              <Col span={12}>
                <Form.Item
                  name="deptId"
                  label="所属部门"
                  rules={[{ required: true, message: '请选择部门' }]}
                >
                  <Select placeholder="请选择部门" showSearch optionFilterProp="children">
                    {deptTree.map((d) => (
                      <Option key={d.id} value={d.id}>{d.deptName}</Option>
                    ))}
                  </Select>
                </Form.Item>
              </Col>
              <Col span={12}>
                <Form.Item name="projectId" label="关联项目">
                  <Select placeholder="请选择项目" allowClear showSearch>
                    {projectList.map((p) => (
                      <Option key={p.id} value={p.id}>{p.projectName}</Option>
                    ))}
                  </Select>
                </Form.Item>
              </Col>
            </Row>
            <Form.Item name="description" label="描述">
              <TextArea rows={3} placeholder="请输入模板描述" />
            </Form.Item>
          </Form>
        );
      case 1:
        return (
          <div>
            <Dragger {...uploadProps} style={{ padding: 24 }}>
              <p className="ant-upload-drag-icon">
                <InboxOutlined />
              </p>
              <p className="ant-upload-text">点击或拖拽Excel文件到此区域上传</p>
              <p className="ant-upload-hint">支持 .xlsx .xls 格式</p>
            </Dragger>
            {excelPreview && (
              <Card title="上传预览" style={{ marginTop: 16 }}>
                <p>Excel 列: {excelPreview.join('、')}</p>
              </Card>
            )}
          </div>
        );
      case 2:
        return (
          <div>
            <h4 style={{ marginBottom: 16 }}>字段映射配置</h4>
            <Table
              rowKey="key"
              columns={mappingColumns}
              dataSource={mappings}
              pagination={false}
              bordered
              size="small"
            />
          </div>
        );
      case 3:
        return (
          <Form form={form4} layout="vertical" style={{ maxWidth: 500 }}>
            <Form.Item
              name="datasourceId"
              label="数据源"
              rules={[{ required: true, message: '请选择数据源' }]}
            >
              <Select placeholder="请选择数据源">
                <Option value={1}>主数据库(MySQL-192.168.1.100)</Option>
                <Option value={2}>备份数据库(MySQL-192.168.1.101)</Option>
                <Option value={3}>报表数据库(PostgreSQL-10.0.0.50)</Option>
              </Select>
            </Form.Item>
            <Form.Item
              name="tableName"
              label="目标表名"
              rules={[{ required: true, message: '请输入目标表名' }]}
            >
              <Input placeholder="请输入目标表名，如 report_data" />
            </Form.Item>
          </Form>
        );
      default:
        return null;
    }
  };

  return (
    <div>
      <PageHeader
        title="新增模板"
        onBack={() => history.push('/template/list')}
        style={{ padding: 0, marginBottom: 16 }}
      />
      <div style={{ background: '#fff', padding: 24, borderRadius: 4, border: '1px solid #f0f0f0' }}>
        <Steps current={currentStep} style={{ marginBottom: 32 }}>
          {steps.map((s) => (
            <Steps.Step key={s.title} title={s.title} />
          ))}
        </Steps>

        <div style={{ minHeight: 300 }}>
          {renderStepContent()}
        </div>

        <div style={{ marginTop: 24, textAlign: 'right' }}>
          <Space>
            {currentStep > 0 && (
              <Button onClick={() => setCurrentStep((s) => s - 1)} icon={<ArrowLeftOutlined />}>
                上一步
              </Button>
            )}
            {currentStep < steps.length - 1 && (
              <Button
                type="primary"
                onClick={() => {
                  if (currentStep === 0) handleSaveStep1();
                  else if (currentStep === 1) {
                    if (fileList.length === 0) {
                      message.warning('请先上传Excel文件');
                      return;
                    }
                    setCurrentStep(2);
                  } else if (currentStep === 2) handleSaveMapping();
                }}
                icon={<ArrowRightOutlined />}
              >
                下一步
              </Button>
            )}
            {currentStep === steps.length - 1 && (
              <Button type="primary" onClick={handleFinish} icon={<CheckOutlined />}>
                完成
              </Button>
            )}
          </Space>
        </div>
      </div>
    </div>
  );
};

export default connect(({ template, dept, project }) => ({
  template,
  dept,
  project,
}))(TemplateAdd);
