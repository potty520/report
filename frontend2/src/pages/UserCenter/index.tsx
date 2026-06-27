import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Card, Form, Input, Button, message, App } from 'antd';
import { LockOutlined, ArrowLeftOutlined } from '@ant-design/icons';
import { changePasswordApi } from '@/services/api';
import JSEncrypt from 'jsencrypt';

const PUBLIC_KEY = `-----BEGIN PUBLIC KEY-----
MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCDEje+xU53iVN4wPqIcBB+42s1
L6VxN7H6DZG1e5UY0w6V0tNxJ2pN08kXTTUQTvIfx0NlF5EUJ8kPTVxYViHx0IHP
lcM09W0RQ8jYPqULm5fT4JLXZwNjYJHyGpMNUyJwURUwCFLNqGP5yBGEkTyjhrX2
s6AlKqkS8KVdXhOwIDAQAB
-----END PUBLIC KEY-----`;

const UserCenterPage: React.FC = () => {
  const { message: msg } = App.useApp();
  const navigate = useNavigate();
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (values: { oldPassword: string; newPassword: string; confirmPassword: string }) => {
    if (values.newPassword !== values.confirmPassword) {
      msg.error('两次输入的新密码不一致');
      return;
    }
    if (values.newPassword.length < 6) {
      msg.error('密码长度不能少于6位');
      return;
    }
    try {
      setLoading(true);
      const encrypt = new JSEncrypt();
      encrypt.setPublicKey(PUBLIC_KEY);
      const encryptedOld = encrypt.encrypt(values.oldPassword);
      const encryptedNew = encrypt.encrypt(values.newPassword);
      if (!encryptedOld || !encryptedNew) {
        msg.error('密码加密失败');
        return;
      }
      await changePasswordApi({
        oldPassword: encryptedOld,
        newPassword: encryptedNew,
        confirmPassword: encryptedNew,
      });
      msg.success('密码修改成功，请重新登录');
      // Logout and go to login
      localStorage.clear();
      navigate('/login', { replace: true });
    } catch (error: any) {
      msg.error(error?.response?.data?.msg || '密码修改失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{ padding: 24 }}>
      <div style={{ marginBottom: 16 }}>
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate(-1)}>返回</Button>
      </div>
      <Card title="修改密码" style={{ maxWidth: 500, margin: '0 auto' }}>
        <Form
          form={form}
          layout="vertical"
          onFinish={handleSubmit}
        >
          <Form.Item
            name="oldPassword"
            label="原密码"
            rules={[{ required: true, message: '请输入原密码' }]}
          >
            <Input.Password
              prefix={<LockOutlined />}
              placeholder="请输入原密码"
              size="large"
            />
          </Form.Item>
          <Form.Item
            name="newPassword"
            label="新密码"
            rules={[
              { required: true, message: '请输入新密码' },
              { min: 6, message: '密码长度不能少于6位' },
            ]}
          >
            <Input.Password
              prefix={<LockOutlined />}
              placeholder="请输入新密码(至少6位)"
              size="large"
            />
          </Form.Item>
          <Form.Item
            name="confirmPassword"
            label="确认新密码"
            rules={[
              { required: true, message: '请确认新密码' },
              ({ getFieldValue }) => ({
                validator(_, value) {
                  if (!value || getFieldValue('newPassword') === value) {
                    return Promise.resolve();
                  }
                  return Promise.reject(new Error('两次输入的密码不一致'));
                },
              }),
            ]}
          >
            <Input.Password
              prefix={<LockOutlined />}
              placeholder="请再次输入新密码"
              size="large"
            />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" loading={loading} block size="large">
              确认修改
            </Button>
          </Form.Item>
        </Form>
      </Card>
    </div>
  );
};

export default UserCenterPage;
