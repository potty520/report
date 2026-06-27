import React, { useState } from 'react';
import { connect } from 'dva';
import {
  Form, Input, Button, Card, PageHeader, message, Row, Col,
} from 'antd';
import { LockOutlined, UserOutlined, MailOutlined, PhoneOutlined } from '@ant-design/icons';
import { useSelector, useDispatch } from 'umi';

const UserCenter = () => {
  const dispatch = useDispatch();
  const [passwordForm] = Form.useForm();
  const [loading, setLoading] = useState(false);

  const handleChangePassword = async (values) => {
    if (values.newPassword !== values.confirmPassword) {
      message.error('两次输入的新密码不一致');
      return;
    }
    setLoading(true);
    try {
      const success = await dispatch({
        type: 'user/changePassword',
        payload: {
          oldPassword: values.oldPassword,
          newPassword: values.newPassword,
        },
      });
      if (success) {
        passwordForm.resetFields();
      }
    } finally {
      setLoading(false);
    }
  };

  // 模拟当前用户信息
  const currentUser = {
    username: 'admin',
    realName: '系统管理员',
    email: 'admin@example.com',
    phone: '138****8888',
  };

  return (
    <div>
      <PageHeader title="个人中心" style={{ padding: 0, marginBottom: 16 }} />
      <Row gutter={16}>
        <Col span={8}>
          <Card title="个人信息" style={{ marginBottom: 16 }}>
            <div style={{ padding: '16px 0' }}>
              <div style={{ display: 'flex', alignItems: 'center', marginBottom: 24 }}>
                <div
                  style={{
                    width: 64,
                    height: 64,
                    borderRadius: '50%',
                    background: '#1890ff',
                    color: '#fff',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontSize: 28,
                    marginRight: 16,
                  }}
                >
                  <UserOutlined />
                </div>
                <div>
                  <h3 style={{ margin: 0 }}>{currentUser.realName}</h3>
                  <p style={{ margin: '4px 0 0', color: '#999' }}>{currentUser.username}</p>
                </div>
              </div>
              <div style={{ lineHeight: 2.5 }}>
                <p>
                  <MailOutlined style={{ marginRight: 8, color: '#1890ff' }} />
                  邮箱: {currentUser.email}
                </p>
                <p>
                  <PhoneOutlined style={{ marginRight: 8, color: '#1890ff' }} />
                  手机: {currentUser.phone}
                </p>
              </div>
            </div>
          </Card>
        </Col>
        <Col span={16}>
          <Card title="修改密码">
            <Form
              form={passwordForm}
              layout="vertical"
              onFinish={handleChangePassword}
              style={{ maxWidth: 400 }}
            >
              <Form.Item
                name="oldPassword"
                label="原密码"
                rules={[{ required: true, message: '请输入原密码' }]}
              >
                <Input.Password
                  prefix={<LockOutlined />}
                  placeholder="请输入原密码"
                />
              </Form.Item>
              <Form.Item
                name="newPassword"
                label="新密码"
                rules={[
                  { required: true, message: '请输入新密码' },
                  { min: 6, message: '密码长度不能少于6位' },
                ]}
                extra="密码长度6-20位，建议包含字母、数字和特殊字符"
              >
                <Input.Password
                  prefix={<LockOutlined />}
                  placeholder="请输入新密码"
                />
              </Form.Item>
              <Form.Item
                name="confirmPassword"
                label="确认新密码"
                dependencies={['newPassword']}
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
                />
              </Form.Item>
              <Form.Item>
                <Button type="primary" htmlType="submit" loading={loading} block>
                  修改密码
                </Button>
              </Form.Item>
            </Form>
          </Card>
        </Col>
      </Row>
    </div>
  );
};

export default connect(({ user }) => ({ user }))(UserCenter);
