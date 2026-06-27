import React, { useEffect, useState } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { Form, Input, Button, message, Row, Col, Image } from 'antd';
import { UserOutlined, LockOutlined, SafetyOutlined, DatabaseOutlined, BarChartOutlined, FileTextOutlined, ScheduleOutlined } from '@ant-design/icons';
import { useAuthStore } from '@/stores/useAuthStore';

const LoginPage: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { login, fetchCaptcha, captcha, loading } = useAuthStore();
  const [form] = Form.useForm();

  const from = (location.state as any)?.from?.pathname || '/overview';

  useEffect(() => {
    fetchCaptcha();
  }, [fetchCaptcha]);

  const handleSubmit = async (values: { username: string; password: string; captchaCode: string }) => {
    try {
      await login({
        username: values.username,
        password: values.password,
        uuid: captcha?.uuid || '',
        captcha: values.captchaCode,
      });
      message.success('登录成功');
      navigate(from, { replace: true });
    } catch (error: any) {
      message.error(error?.response?.data?.msg || error?.message || '登录失败');
      fetchCaptcha();
      form.setFieldValue('captchaCode', '');
    }
  };

  const handleRefreshCaptcha = () => {
    fetchCaptcha();
    form.setFieldValue('captchaCode', '');
  };

  const features = [
    { icon: <BarChartOutlined />, label: '数据总览' },
    { icon: <FileTextOutlined />, label: '模板管理' },
    { icon: <ScheduleOutlined />, label: '任务调度' },
    { icon: <DatabaseOutlined />, label: '数据填报' },
  ];

  return (
    <div style={{ display: 'flex', height: '100vh', overflow: 'hidden' }}>
      {/* Left panel - branding */}
      <div style={{
        flex: '0 0 480px',
        background: 'linear-gradient(135deg, #0c1a32 0%, #132347 30%, #1a3263 60%, #224488 100%)',
        display: 'flex',
        flexDirection: 'column',
        justifyContent: 'center',
        alignItems: 'center',
        padding: 60,
        position: 'relative',
        overflow: 'hidden',
      }}>
        {/* Decorative circles */}
        <div style={{
          position: 'absolute',
          top: -120,
          right: -120,
          width: 400,
          height: 400,
          borderRadius: '50%',
          background: 'rgba(255,255,255,0.03)',
          pointerEvents: 'none',
        }} />
        <div style={{
          position: 'absolute',
          top: '40%',
          left: -60,
          width: 200,
          height: 200,
          borderRadius: '50%',
          background: 'rgba(255,255,255,0.03)',
          pointerEvents: 'none',
        }} />
        <div style={{
          position: 'absolute',
          bottom: -80,
          right: '30%',
          width: 300,
          height: 300,
          borderRadius: '50%',
          background: 'rgba(255,255,255,0.02)',
          pointerEvents: 'none',
        }} />

        {/* Grid pattern overlay */}
        <div style={{
          position: 'absolute',
          inset: 0,
          backgroundImage: `linear-gradient(rgba(255,255,255,0.03) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,0.03) 1px, transparent 1px)`,
          backgroundSize: '60px 60px',
          pointerEvents: 'none',
        }} />

        {/* Content */}
        <div style={{ position: 'relative', zIndex: 1, textAlign: 'center' }}>
          <div style={{
            width: 80,
            height: 80,
            borderRadius: 20,
            background: 'linear-gradient(135deg, #1677ff 0%, #4096ff 100%)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            margin: '0 auto 32px',
            boxShadow: '0 8px 32px rgba(22,119,255,0.3)',
          }}>
            <DatabaseOutlined style={{ fontSize: 40, color: '#fff' }} />
          </div>
          <h1 style={{
            color: '#fff',
            fontSize: 32,
            fontWeight: 700,
            margin: '0 0 8px',
            letterSpacing: 4,
          }}>
            数据填报系统
          </h1>
          <p style={{
            color: 'rgba(255,255,255,0.55)',
            fontSize: 15,
            margin: '0 0 48px',
            letterSpacing: 2,
          }}>
            Report Management System
          </p>

          {/* Feature icons */}
          <div style={{
            display: 'grid',
            gridTemplateColumns: '1fr 1fr',
            gap: 20,
            maxWidth: 320,
            margin: '0 auto',
          }}>
            {features.map((f) => (
              <div key={f.label} style={{
                display: 'flex',
                alignItems: 'center',
                gap: 10,
                color: 'rgba(255,255,255,0.6)',
                fontSize: 14,
              }}>
                <span style={{ fontSize: 18, opacity: 0.7 }}>{f.icon}</span>
                <span>{f.label}</span>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Right panel - login form */}
      <div style={{
        flex: 1,
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        background: '#f5f7fa',
        padding: 40,
      }}>
        <div style={{ width: 380, maxWidth: '100%' }}>
          <div style={{ marginBottom: 40 }}>
            <h2 style={{ fontSize: 26, fontWeight: 600, margin: 0, color: '#1a1a2e' }}>欢迎登录</h2>
            <p style={{ color: '#999', marginTop: 8, fontSize: 14 }}>请输入账号密码进入系统</p>
          </div>

          <Form
            form={form}
            onFinish={handleSubmit}
            size="large"
            initialValues={{ username: 'admin' }}
          >
            <Form.Item
              name="username"
              rules={[{ required: true, message: '请输入用户名' }]}
            >
              <Input
                prefix={<UserOutlined style={{ color: '#bfbfbf' }} />}
                placeholder="用户名"
                style={{ height: 48 }}
              />
            </Form.Item>
            <Form.Item
              name="password"
              rules={[{ required: true, message: '请输入密码' }]}
            >
              <Input.Password
                prefix={<LockOutlined style={{ color: '#bfbfbf' }} />}
                placeholder="密码"
                style={{ height: 48 }}
              />
            </Form.Item>
            <Form.Item
              name="captchaCode"
              rules={[{ required: true, message: '请输入验证码' }]}
            >
              <Row gutter={8}>
                <Col span={14}>
                  <Input
                    prefix={<SafetyOutlined style={{ color: '#bfbfbf' }} />}
                    placeholder="验证码"
                    style={{ height: 48 }}
                  />
                </Col>
                <Col span={10}>
                  {captcha?.captchaImage ? (
                    <Image
                      src={captcha.captchaImage}
                      alt="验证码"
                      preview={false}
                      onClick={handleRefreshCaptcha}
                      style={{
                        height: 48,
                        cursor: 'pointer',
                        width: '100%',
                        objectFit: 'cover',
                        borderRadius: 6,
                        border: '1px solid #d9d9d9',
                      }}
                    />
                  ) : (
                    <Button onClick={handleRefreshCaptcha} block style={{ height: 48 }}>
                      获取验证码
                    </Button>
                  )}
                </Col>
              </Row>
            </Form.Item>
            <Form.Item style={{ marginTop: 8 }}>
              <Button
                type="primary"
                htmlType="submit"
                loading={loading}
                block
                style={{ height: 48, fontSize: 16, borderRadius: 6 }}
              >
                登 录
              </Button>
            </Form.Item>
          </Form>
        </div>
      </div>
    </div>
  );
};

export default LoginPage;
