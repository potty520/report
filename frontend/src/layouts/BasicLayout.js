import React, { useState, useEffect } from 'react';
import { Layout, Menu, Avatar, Dropdown, Spin } from 'antd';
import {
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  UserOutlined,
  LogoutOutlined,
  KeyOutlined,
  DashboardOutlined,
  SettingOutlined,
  ApartmentOutlined,
  FileTextOutlined,
  DatabaseOutlined,
  ScheduleOutlined,
  FormOutlined,
  SwapOutlined,
} from '@ant-design/icons';
import { history, useSelector, useDispatch, useLocation } from 'umi';
import { Helmet } from 'react-helmet-async';
import { getToken, clearToken, clearCurrentUser } from '@/utils/authority';
import routerConfig from '../../config/router.config';
import styles from './BasicLayout.less';

const { Header, Sider, Content } = Layout;

const iconMap = {
  DashboardOutlined: <DashboardOutlined />,
  SettingOutlined: <SettingOutlined />,
  ApartmentOutlined: <ApartmentOutlined />,
  FileTextOutlined: <FileTextOutlined />,
  DatabaseOutlined: <DatabaseOutlined />,
  ScheduleOutlined: <ScheduleOutlined />,
  FormOutlined: <FormOutlined />,
  SwapOutlined: <SwapOutlined />,
};

function getIcon(icon) {
  return iconMap[icon] || <DashboardOutlined />;
}

function getMenuData(routes) {
  return routes
    .filter((route) => route.name && !route.hideInMenu)
    .map((route) => {
      const item = {
        key: route.path,
        label: route.title || route.name,
        icon: route.icon ? getIcon(route.icon) : null,
      };
      if (route.routes) {
        item.children = getMenuData(route.routes);
      }
      return item;
    });
}

function findBreadcrumb(routes, pathname) {
  for (const route of routes) {
    if (route.path === pathname) return [route];
    if (route.routes) {
      for (const child of route.routes) {
        if (child.path === pathname) return [route, child];
        const nested = findBreadcrumb([child], pathname);
        if (nested.length) return [route, ...nested];
      }
    }
  }
  return [];
}

function findMenuRoutes(config) {
  return config
    .filter((r) => r.path === '/')
    .flatMap((r) => r.routes || []);
}

const BasicLayout = (props) => {
  const dispatch = useDispatch();
  const location = useLocation();
  const currentUser = useSelector((state) => state.global.currentUser);
  const token = useSelector((state) => state.global.token);

  const [collapsed, setCollapsed] = useState(false);
  const [menuData, setMenuData] = useState([]);
  const [selectedKeys, setSelectedKeys] = useState([]);
  const [openKeys, setOpenKeys] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!token && !getToken()) {
      history.replace('/login');
      return;
    }
    const menuRoutes = findMenuRoutes(routerConfig);
    const menus = getMenuData(menuRoutes);
    setMenuData(menus);
    setLoading(false);
  }, []);

  useEffect(() => {
    const pathname = location.pathname;
    setSelectedKeys([pathname]);
    const parentPath = pathname.split('/').slice(0, 3).join('/');
    if (parentPath !== pathname) {
      setOpenKeys((prev) => {
        const keys = [...prev];
        if (!keys.includes(parentPath)) {
          keys.push(parentPath);
        }
        return keys;
      });
    }
  }, [location.pathname]);

  const breadcrumb = findBreadcrumb(findMenuRoutes(routerConfig), location.pathname);

  const handleMenuClick = ({ key }) => {
    history.push(key);
  };

  const handleLogout = () => {
    dispatch({ type: 'global/logout' });
  };

  const userMenuItems = [
    {
      key: 'userCenter',
      icon: <UserOutlined />,
      label: '个人中心',
    },
    {
      key: 'changePassword',
      icon: <KeyOutlined />,
      label: '修改密码',
    },
    { type: 'divider' },
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      label: '退出登录',
    },
  ];

  const handleUserMenuClick = ({ key }) => {
    if (key === 'logout') {
      handleLogout();
    } else if (key === 'userCenter') {
      history.push('/userCenter');
    } else if (key === 'changePassword') {
      history.push('/userCenter');
    }
  };

  const onOpenChange = (keys) => {
    setOpenKeys(keys);
  };

  if (loading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100vh' }}>
        <Spin size="large" tip="加载中..." />
      </div>
    );
  }

  const displayName = currentUser?.realName || currentUser?.username || '管理员';

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Helmet>
        <title>填报系统</title>
      </Helmet>
      <Sider
        trigger={null}
        collapsible
        collapsed={collapsed}
        width={220}
        style={{
          overflow: 'auto',
          height: '100vh',
          position: 'fixed',
          left: 0,
          top: 0,
          bottom: 0,
          zIndex: 10,
        }}
      >
        <div className={styles.logo}>
          {collapsed ? '填报' : '数据填报系统'}
        </div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={selectedKeys}
          openKeys={openKeys}
          onOpenChange={onOpenChange}
          onClick={handleMenuClick}
          items={menuData}
        />
      </Sider>
      <Layout style={{ marginLeft: collapsed ? 80 : 220, transition: 'all 0.2s' }}>
        <Header
          style={{
            padding: '0 24px',
            background: '#fff',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            boxShadow: '0 1px 4px rgba(0, 21, 41, 0.08)',
            position: 'sticky',
            top: 0,
            zIndex: 9,
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center' }}>
            {React.createElement(collapsed ? MenuUnfoldOutlined : MenuFoldOutlined, {
              style: { fontSize: 18, cursor: 'pointer' },
              onClick: () => setCollapsed(!collapsed),
            })}
            <div style={{ marginLeft: 16, display: 'flex', alignItems: 'center' }}>
              {breadcrumb.length > 0 && (
                <span style={{ color: '#999', fontSize: 14 }}>
                  {breadcrumb.map((item, index) => (
                    <span key={item.path}>
                      {index > 0 && ' / '}
                      {item.title || item.name}
                    </span>
                  ))}
                </span>
              )}
            </div>
          </div>
          <Dropdown
            menu={{ items: userMenuItems, onClick: handleUserMenuClick }}
            placement="bottomRight"
          >
            <div style={{ cursor: 'pointer', display: 'flex', alignItems: 'center' }}>
              <Avatar style={{ backgroundColor: '#1890ff' }} icon={<UserOutlined />} />
              <span style={{ marginLeft: 8 }}>{displayName}</span>
            </div>
          </Dropdown>
        </Header>
        <Content
          style={{
            margin: 16,
            padding: 24,
            background: '#fff',
            borderRadius: 4,
            minHeight: 280,
          }}
        >
          {props.children}
        </Content>
      </Layout>
    </Layout>
  );
};

export default BasicLayout;
