import React from 'react';
import { Menu } from 'antd';

const iconMap = {
  DashboardOutlined: null,
  SettingOutlined: null,
  ApartmentOutlined: null,
  FileTextOutlined: null,
  DatabaseOutlined: null,
  ScheduleOutlined: null,
  FormOutlined: null,
  SwapOutlined: null,
};

function buildMenuItems(routes, parentPath) {
  return routes
    .filter((route) => route.name && !route.hideInMenu)
    .map((route) => {
      const item = {
        key: route.path,
        label: route.title || route.name,
        icon: route.icon || null,
      };
      if (route.routes) {
        item.children = buildMenuItems(route.routes, route.path);
      }
      return item;
    });
}

const SiderMenu = ({ routes, ...rest }) => {
  const menuItems = buildMenuItems(routes, '/');
  return <Menu {...rest} items={menuItems} />;
};

export default SiderMenu;
