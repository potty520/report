import React, { useState, useEffect, useRef } from 'react';
import { Form, Input, Button, Checkbox, message, Layout, Card } from 'antd';
import { UserOutlined, LockOutlined, SafetyCertificateOutlined } from '@ant-design/icons';
import { useSelector, useDispatch } from 'umi';
import { getRememberMe, setRememberMe, clearRememberMe } from '@/utils/authority';
import styles from './LoginLayout.less';

const { Content } = Layout;

const LoginLayout = () => {
  const dispatch = useDispatch();
  const captcha = useSelector((state) => state.global.captcha);
  const captchaUuid = useSelector((state) => state.global.captchaUuid);
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    dispatch({ type: 'global/getCaptcha' });
    const remembered = getRememberMe();
    if (remembered) {
      form.setFieldsValue({
        username: remembered.username,
        password: remembered.password,
        remember: true,
      });
    }
  }, []);

  const handleSubmit = async (values) => {
    setLoading(true);
    try {
      const success = await dispatch({
        type: 'global/login',
        payload: {
          username: values.username,
          password: values.password,
          captcha: values.captcha,
          uuid: captchaUuid,
        },
      });

      if (values.remember) {
        setRememberMe(values.username, values.password);
      } else {
        clearRememberMe();
      }
    } catch (error) {
      message.error('登录失败，请检查用户名和密码');
      dispatch({ type: 'global/getCaptcha' });
      form.setFieldValue('captcha', '');
    } finally {
      setLoading(false);
    }
  };

  const refreshCaptcha = () => {
    dispatch({ type: 'global/getCaptcha' });
    form.setFieldValue('captcha', '');
  };

  return (
    <Layout className={styles.loginLayout}>
      <Content style={{ display: 'flex', justifyContent: 'center', alignItems: 'center' }}>
        <div style={{ width: 400 }}>
          <div style={{ textAlign: 'center', marginBottom: 40 }}>
            <h1 style={{ fontSize: 33, color: '#1890ff', margin: 0 }}>数据填报系统</h1>
            <p style={{ color: 'rgba(0,0,0,0.45)', marginTop: 12, fontSize: 14 }}>
              欢迎使用数据填报管理平台
            </p>
          </div>
          <Card bordered={false} style={{ boxShadow: '0 2px 12px rgba(0,0,0,0.1)' }}>
            <Form
              form={form}
              onFinish={handleSubmit}
              size="large"
              initialValues={{ remember: true }}
            >
              <Form.Item
                name="username"
                rules={[{ required: true, message: '请输入用户名' }]}
              >
                <Input
                  prefix={<UserOutlined style={{ color: 'rgba(0,0,0,0.25)' }} />}
                  placeholder="用户名"
                  autoComplete="username"
                />
              </Form.Item>

              <Form.Item
                name="password"
                rules={[
                  { required: true, message: '请输入密码' },
                  { min: 6, message: '密码不少于6位' },
                ]}
              >
                <Input.Password
                  prefix={<LockOutlined style={{ color: 'rgba(0,0,0,0.25)' }} />}
                  placeholder="密码"
                  autoComplete="current-password"
                />
              </Form.Item>

              <Form.Item>
                <div style={{ display: 'flex', gap: 8 }}>
                  <Form.Item
                    name="captcha"
                    noStyle
                    rules={[{ required: true, message: '请输入验证码' }]}
                  >
                    <Input
                      prefix={<SafetyCertificateOutlined style={{ color: 'rgba(0,0,0,0.25)' }} />}
                      placeholder="验证码"
                      style={{ flex: 1 }}
                    />
                  </Form.Item>
                  <img
                    src={captcha ? `data:image/png;base64,${captcha}` : ''}
                    alt="验证码"
                    onClick={refreshCaptcha}
                    style={{
                      height: 40,
                      cursor: 'pointer',
                      border: '1px solid #d9d9d9',
                      borderRadius: 4,
                    }}
                  />
                </div>
              </Form.Item>

              <Form.Item>
                <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <Form.Item name="remember" valuePropName="checked" noStyle>
                    <Checkbox>记住密码</Checkbox>
                  </Form.Item>
                </div>
              </Form.Item>

              <Form.Item>
                <Button type="primary" htmlType="submit" loading={loading} block>
                  登录
                </Button>
              </Form.Item>
            </Form>
          </Card>
        </div>
      </Content>
      <footer style={{ textAlign: 'center', padding: '12px 0', color: 'rgba(0,0,0,0.45)' }}>
        数据填报系统 ©2024
      </footer>
    </Layout>
  );
};

export default LoginLayout;
