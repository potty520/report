import React, { useCallback, useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Card, Descriptions, Tag, Button, Space, Table, Modal, Form, Input, Select, Radio, message, App, Spin, Row, Col,
} from 'antd';
import { ArrowLeftOutlined, EditOutlined, CheckOutlined, CloseOutlined } from '@ant-design/icons';
import { getDisplayConfigApi, saveDisplayConfigApi, displayLinkTestApi } from '@/services/api';
import { useTemplateStore } from '@/stores/useTemplateStore';
import type { TemplateField } from '@/services/api';
import dayjs from 'dayjs';

const TemplateDetailPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { message: msg } = App.useApp();
  const { currentItem, loading, fetchDetail, audit } = useTemplateStore();

  const [auditModalOpen, setAuditModalOpen] = useState(false);
  const [auditAction, setAuditAction] = useState<number>(1); // 1=pass, 2=reject
  const [auditForm] = Form.useForm();
  const [displayType, setDisplayType] = useState<number>(0);
  const [refTemplateId, setRefTemplateId] = useState<string>('');
  const [refDsId, setRefDsId] = useState<string>('');
  const [refTable, setRefTable] = useState<string>('');

  const loadDisplay = useCallback(() => {
    if (!id) return;
    getDisplayConfigApi(Number(id)).then((res: any) => {
      const d = res?.data || {};
      setDisplayType(d.configType ?? 0);
      setRefTemplateId(d.refTemplateId ? String(d.refTemplateId) : '');
      setRefDsId(d.refDatasourceId ? String(d.refDatasourceId) : '');
      setRefTable(d.refTableName || '');
    }).catch(() => {});
  }, [id]);

  useEffect(() => { loadDisplay(); }, [loadDisplay]);

  const handleSaveDisplay = async () => {
    try {
      const res: any = await saveDisplayConfigApi(Number(id), {
        configType: displayType,
        refTemplateId: displayType === 1 && refTemplateId ? Number(refTemplateId) : null,
        refDatasourceId: displayType === 2 && refDsId ? Number(refDsId) : null,
        refTableName: displayType === 2 ? refTable : null,
        refColumns: '[]',
      });
      if (res?.code === 200) message.success('展示配置已保存');
      else message.error(res?.msg || '保存失败');
    } catch { message.error('保存失败'); }
  };

  const handleLinkTest = async () => {
    if (!refDsId || !refTable) { message.warning('请先填写数据源 ID 与表名'); return; }
    try {
      const res: any = await displayLinkTestApi(Number(refDsId), refTable);
      if (res?.data?.ok) message.success(res.data.message || '链接成功');
      else message.error(res?.data?.message || '链接失败');
    } catch { message.error('链接检测失败'); }
  };


  useEffect(() => {
    if (id) {
      fetchDetail(Number(id));
    }
  }, [id, fetchDetail]);

  const handleAudit = (status: number) => {
    setAuditAction(status);
    auditForm.resetFields();
    setAuditModalOpen(true);
  };

  const handleAuditSubmit = async () => {
    try {
      const values = await auditForm.validateFields();
      if (!currentItem) return;
      await audit(currentItem.id, auditAction, values.remark);
      msg.success(auditAction === 1 ? '审核通过' : '已驳回');
      setAuditModalOpen(false);
      if (id) fetchDetail(Number(id));
    } catch (error: any) {
      if (error.errorFields) return;
      msg.error('审核操作失败');
    }
  };

  const statusMap: Record<number, { color: string; text: string }> = {
    0: { color: 'default', text: '草稿' },
    1: { color: 'green', text: '已发布' },
    2: { color: 'red', text: '已禁用' },
  };

  const fieldColumns = [
    { title: '字段名', dataIndex: 'fieldName', width: 140 },
    { title: '字段标签', dataIndex: 'fieldLabel', width: 140 },
    {
      title: '类型', dataIndex: 'fieldType', width: 100,
      render: (v: string) => {
        const typeMap: Record<string, string> = {
          string: '字符串', number: '数字', date: '日期', datetime: '日期时间',
        };
        return typeMap[v] || v;
      },
    },
    { title: 'Excel列', dataIndex: 'excelColumn', width: 80, align: 'center' as const },
    {
      title: '必填', dataIndex: 'required', width: 70, align: 'center' as const,
      render: (v: number) => <Tag color={v === 1 ? 'blue' : 'default'}>{v === 1 ? '是' : '否'}</Tag>,
    },
    { title: '排序', dataIndex: 'sort', width: 70, align: 'center' as const },
  ];

  if (loading) {
    return <div style={{ display: 'flex', justifyContent: 'center', padding: 100 }}><Spin size="large" /></div>;
  }

  if (!currentItem) {
    return <div style={{ padding: 24, textAlign: 'center' }}>模板不存在</div>;
  }

  return (
    <div style={{ padding: 24, background: '#fff', borderRadius: 8 }}>
      <div style={{ marginBottom: 16 }}>
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/template/list')}>
          返回列表
        </Button>
      </div>

      <Row gutter={16}>
        <Col span={16}>
          <Card title="模板基本信息" style={{ marginBottom: 16 }}>
            <Descriptions column={2} bordered size="small">
              <Descriptions.Item label="模板名称">{currentItem.name}</Descriptions.Item>
              <Descriptions.Item label="模板编码">{currentItem.code}</Descriptions.Item>
              <Descriptions.Item label="版本">v{currentItem.version}</Descriptions.Item>
              <Descriptions.Item label="状态">
                <Tag color={statusMap[currentItem.status]?.color}>{statusMap[currentItem.status]?.text}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="数据源">{currentItem.datasourceName || '-'}</Descriptions.Item>
              <Descriptions.Item label="Excel文件">{currentItem.excelFileName || '-'}</Descriptions.Item>
              <Descriptions.Item label="创建时间">
                {currentItem.createTime ? dayjs(currentItem.createTime).format('YYYY-MM-DD HH:mm:ss') : '-'}
              </Descriptions.Item>
              <Descriptions.Item label="更新时间">
                {currentItem.updateTime ? dayjs(currentItem.updateTime).format('YYYY-MM-DD HH:mm:ss') : '-'}
              </Descriptions.Item>
              <Descriptions.Item label="描述" span={2}>
                {currentItem.description || '-'}
              </Descriptions.Item>
            </Descriptions>
          </Card>

          <Card title={`字段列表 (${(currentItem.fields || []).length}个字段)`}>
            <Table
              columns={fieldColumns}
              dataSource={currentItem.fields || []}
              rowKey={(r: TemplateField) => r.fieldName}
              pagination={false}
              size="small"
            />
          </Card>

          <Card title="展示信息配置" style={{ marginTop: 16 }}>
            <Space direction="vertical" style={{ width: '100%' }}>
              <Radio.Group value={displayType} onChange={(e) => setDisplayType(e.target.value)}>
                <Radio value={0}>无</Radio>
                <Radio value={1}>关联模板</Radio>
                <Radio value={2}>关联表</Radio>
              </Radio.Group>
              {displayType === 1 && (
                <Input placeholder="关联模板 ID" value={refTemplateId} onChange={(e) => setRefTemplateId(e.target.value)} />
              )}
              {displayType === 2 && (
                <>
                  <Input placeholder="数据源 ID" value={refDsId} onChange={(e) => setRefDsId(e.target.value)} />
                  <Input placeholder="表名" value={refTable} onChange={(e) => setRefTable(e.target.value)} />
                </>
              )}
              <Space>
                <Button onClick={handleLinkTest} disabled={displayType !== 2}>链接检测</Button>
                <Button type="primary" onClick={handleSaveDisplay}>保存配置</Button>
              </Space>
              <span style={{ color: '#999', fontSize: 12 }}>
                选择「关联模板」或「关联表」后，填报页会把参考数据只读展示给填报人。
              </span>
            </Space>
          </Card>
        </Col>

        <Col span={8}>
          <Card title="操作">
            <Space direction="vertical" style={{ width: '100%' }}>
              <Button
                type="primary"
                icon={<EditOutlined />}
                block
                onClick={() => navigate(`/template/wizard?id=${id}`)}
                disabled={currentItem.status === 1}
              >
                编辑模板
              </Button>
              {currentItem.status === 0 && (
                <>
                  <Button
                    type="primary"
                    icon={<CheckOutlined />}
                    block
                    style={{ backgroundColor: '#52c41a', borderColor: '#52c41a' }}
                    onClick={() => handleAudit(1)}
                  >
                    审核通过
                  </Button>
                  <Button
                    danger
                    icon={<CloseOutlined />}
                    block
                    onClick={() => handleAudit(2)}
                  >
                    审核驳回
                  </Button>
                </>
              )}
            </Space>

          </Card>

          <Card title="审核记录" size="small" style={{ marginTop: 16 }}>
            <p style={{ color: '#999', fontSize: 13 }}>暂无审核记录</p>
          </Card>
        </Col>
      </Row>

      {/* Audit Modal */}
      <Modal
        title={auditAction === 1 ? '审核通过' : '审核驳回'}
        open={auditModalOpen}
        onOk={handleAuditSubmit}
        onCancel={() => setAuditModalOpen(false)}
        destroyOnClose
      >
        <Form form={auditForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item name="remark" label="审核意见" rules={auditAction === 2 ? [{ required: true, message: '请填写驳回原因' }] : []}>
            <Input.TextArea
              rows={4}
              placeholder={auditAction === 1 ? '审核通过意见(可选)' : '请填写驳回原因'}
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default TemplateDetailPage;
