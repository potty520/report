import React, { useEffect, useState } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { Form, Input, Button, Card, message, Row, Col, Image } from 'antd';
import { UserOutlined, LockOutlined, SafetyOutlined } from '@ant-design/icons';
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

  return (
    <div style={{
      display: 'flex',
      justifyContent: 'center',
      alignItems: 'center',
      minHeight: '100vh',
      background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
    }}>
      <Card
        style={{ width: 420, boxShadow: '0 8px 24px rgba(0,0,0,0.15)' }}
        bordered={false}
      >
        <div style={{ textAlign: 'center', marginBottom: 32 }}>
          <h1 style={{ fontSize: 28, color: '#1677ff', margin: 0 }}>数据填报系统</h1>
          <p style={{ color: '#999', marginTop: 8 }}>Report Management System</p>
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
            <Input prefix={<UserOutlined />} placeholder="用户名" />
          </Form.Item>
          <Form.Item
            name="password"
            rules={[{ required: true, message: '请输入密码' }]}
          >
            <Input.Password prefix={<LockOutlined />} placeholder="密码" />
          </Form.Item>
          <Form.Item
            name="captchaCode"
            rules={[{ required: true, message: '请输入验证码' }]}
          >
            <Row gutter={8}>
              <Col span={14}>
                <Input prefix={<SafetyOutlined />} placeholder="验证码" />
              </Col>
              <Col span={10}>
                {captcha?.captchaImage ? (
                  <Image
                    src={captcha.captchaImage}
                    alt="验证码"
                    preview={false}
                    onClick={handleRefreshCaptcha}
                    style={{ height: 40, cursor: 'pointer', width: '100%', objectFit: 'cover', borderRadius: 6 }}
                  />
                ) : (
                  <Button onClick={handleRefreshCaptcha} block style={{ height: 40 }}>
                    获取验证码
                  </Button>
                )}
              </Col>
            </Row>
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" loading={loading} block>
              登录
            </Button>
          </Form.Item>
        </Form>
      </Card>
    </div>
  );
};

export default LoginPage;
