import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Card, Form, Input, Button, App } from 'antd';
import { LockOutlined, ArrowLeftOutlined } from '@ant-design/icons';
import { changePasswordApi } from '@/services/api';
import { useAuthStore } from '@/stores/useAuthStore';

/**
 * 后端密码策略（com.reportsys.common.PasswordPolicy）的前端镜像：
 * 长度 8~64、至少 2 类字符、不能纯数字、不能是常见弱口令。
 * 前端只做前置提示，最终以后端返回为准。
 */
const MIN_LENGTH = 8;
const MAX_LENGTH = 64;
const WEAK_PASSWORDS = new Set([
  '123456', '1234567', '12345678', '123456789', '1234567890',
  '111111', '11111111', '000000', '00000000', '888888', '88888888',
  '666666', '999999', '123123', '123321', '654321', 'abcdef',
  'abc123', 'abc123456', 'a123456', 'a12345678', 'qwerty', 'qwerty123',
  'qwertyuiop', 'password', 'password1', 'password123', 'passw0rd',
  'admin', 'admin' + '123', 'admin888', 'root', 'root123', 'system',
  'iloveyou', 'welcome', 'letmein', 'monkey', 'dragon', 'master',
  'superman', 'sunshine', 'princess', 'football', 'baseball',
]);

/** 返回 null 表示通过，否则返回可直接展示的错误文案 */
function checkPasswordStrength(password: string): string | null {
  if (!password) return '请输入新密码';
  if (password.length < MIN_LENGTH) return `密码长度不能少于 ${MIN_LENGTH} 位`;
  if (password.length > MAX_LENGTH) return `密码长度不能超过 ${MAX_LENGTH} 位`;
  if (WEAK_PASSWORDS.has(password.toLowerCase())) return '密码过于简单，请勿使用常见弱口令';
  const categories = [/[A-Z]/, /[a-z]/, /[0-9]/, /[^A-Za-z0-9]/].filter((r) => r.test(password)).length;
  if (categories < 2) return '密码需包含大写字母、小写字母、数字、符号中的至少两类';
  if (/^\d+$/.test(password)) return '密码不能为纯数字';
  return null;
}

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
    const pwdError = checkPasswordStrength(values.newPassword);
    if (pwdError) {
      msg.error(pwdError);
      return;
    }
    try {
      setLoading(true);
      // 后端 AuthController.updatePassword 用 @RequestParam 接收明文并直接 BCrypt 比对，
      // 没有 RSA 私钥/解密逻辑，因此这里必须传明文（PUT /auth/password，query 参数）。
      await changePasswordApi({
        oldPassword: values.oldPassword,
        newPassword: values.newPassword,
      });
      msg.success('密码修改成功，请重新登录');
      // 清理登录态（logout 内部 finally 一定会 clearAuth，注销接口失败不阻塞跳转）
      try {
        await useAuthStore.getState().logout();
      } catch {
        /* 注销接口失败忽略：本地登录态已由 logout 的 finally 清理 */
      }
      navigate('/login', { replace: true });
    } catch (error: any) {
      msg.error(error?.response?.data?.msg || error?.message || '密码修改失败');
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
              {
                validator(_, value) {
                  const error = checkPasswordStrength(value);
                  return error ? Promise.reject(new Error(error)) : Promise.resolve();
                },
              },
            ]}
          >
            <Input.Password
              prefix={<LockOutlined />}
              placeholder="至少8位，需包含大小写字母/数字/符号中的至少两类"
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
