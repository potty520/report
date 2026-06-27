import React, { useState } from 'react';
import { Outlet, useNavigate } from 'react-router-dom';
import { Layout, Dropdown, Avatar, theme, App } from 'antd';
import {
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  UserOutlined,
  LogoutOutlined,
  KeyOutlined,
} from '@ant-design/icons';
import SiderMenu from '@/components/SiderMenu';
import PageLoading from '@/components/PageLoading';
import { useAuthStore } from '@/stores/useAuthStore';
import './MainLayout.css';

const { Header, Sider, Content, Footer } = Layout;

const MainLayout: React.FC = () => {
  const [collapsed, setCollapsed] = useState(false);
  const navigate = useNavigate();
  const { message: msg, modal } = App.useApp();
  const { userInfo, menus, logout } = useAuthStore();
  const { token: themeToken } = theme.useToken();

  const handleMenuCollapse = () => {
    setCollapsed(!collapsed);
  };

  const handleLogout = () => {
    modal.confirm({
      title: '确认退出',
      content: '确定要退出登录吗？',
      okText: '确定',
      cancelText: '取消',
      onOk: async () => {
        await logout();
        msg.success('已退出登录');
        navigate('/login', { replace: true });
      },
    });
  };

  const handleChangePassword = () => {
    navigate('/userCenter');
  };

  const userMenuItems = {
    items: [
      {
        key: 'profile',
        icon: <UserOutlined />,
        label: userInfo?.nickname || userInfo?.username || '用户',
        disabled: true,
      },
      { type: 'divider' as const },
      {
        key: 'changePassword',
        icon: <KeyOutlined />,
        label: '修改密码',
        onClick: handleChangePassword,
      },
      { type: 'divider' as const },
      {
        key: 'logout',
        icon: <LogoutOutlined />,
        label: '退出登录',
        danger: true,
        onClick: handleLogout,
      },
    ],
  };

  return (
    <Layout className="main-layout">
      <Sider
        trigger={null}
        collapsible
        collapsed={collapsed}
        width={240}
        theme="dark"
        style={{
          overflow: 'auto',
          height: '100vh',
          position: 'fixed',
          left: 0,
          top: 0,
          bottom: 0,
          zIndex: 100,
        }}
      >
        <div className={`logo ${collapsed ? 'logo-collapsed' : ''}`}>
          {collapsed ? '填报' : '数据填报系统'}
        </div>
        <SiderMenu menus={menus} collapsed={collapsed} />
      </Sider>
      <Layout style={{ marginLeft: collapsed ? 80 : 240, transition: 'margin-left 0.2s' }}>
        <Header className="ant-layout-header" style={{ position: 'sticky', top: 0, zIndex: 99, width: '100%', background: themeToken.colorBgContainer }}>
          <div className="header-left">
            <span className="trigger-btn" onClick={handleMenuCollapse}>
              {collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
            </span>
          </div>
          <div className="header-right">
            <Dropdown menu={userMenuItems} placement="bottomRight">
              <div className="user-info">
                <Avatar size="small" icon={<UserOutlined />} style={{ backgroundColor: themeToken.colorPrimary }} />
                <span>{userInfo?.nickname || userInfo?.username || '管理员'}</span>
              </div>
            </Dropdown>
          </div>
        </Header>
        <Content className="main-content">
          <React.Suspense fallback={<PageLoading />}>
            <Outlet />
          </React.Suspense>
        </Content>
        <Footer className="main-footer">
          数据填报系统 ©{new Date().getFullYear()} Created with React + Ant Design
        </Footer>
      </Layout>
    </Layout>
  );
};

export default MainLayout;
