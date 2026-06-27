import React from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { Menu, MenuProps } from 'antd';
import * as Icons from '@ant-design/icons';
import type { MenuItem as MenuItemType } from '@/services/api';

// Dynamic icon lookup
const iconMap: Record<string, React.ReactNode> = {};

function getIcon(iconName?: string): React.ReactNode {
  if (!iconName) return null;
  const cached = iconMap[iconName];
  if (cached) return cached;
  const IconComp = (Icons as any)[iconName] || (Icons as any)[`${iconName}Outlined`];
  if (IconComp) {
    const el = React.createElement(IconComp);
    iconMap[iconName] = el;
    return el;
  }
  return null;
}

function buildMenuItems(menus: MenuItemType[]): MenuProps['items'] {
  return menus
    .filter((m) => m.visible !== 0 && m.menuType !== 'F') // F=按钮，不显示
    .sort((a, b) => a.sort - b.sort)
    .map((menu): any => ({
      key: `${menu.path || 'menu-' + menu.id}|${menu.menuType}`,
      icon: getIcon(menu.icon),
      label: menu.menuName,
      children: menu.children && menu.children.length > 0 ? buildMenuItems(menu.children) : undefined,
    }));
}

interface SiderMenuProps {
  menus: MenuItemType[];
  collapsed: boolean;
}

const SiderMenu: React.FC<SiderMenuProps> = ({ menus, collapsed }) => {
  const navigate = useNavigate();
  const location = useLocation();
  const pathname = location.pathname;

  const menuItems = buildMenuItems(menus);

  const handleClick: MenuProps['onClick'] = (e) => {
    // key format: "path|type" — only navigate for C (page) type, not M (directory)
    const parts = e.key.split('|');
    const path = parts[0];
    const menuType = parts[1];
    if (menuType === 'C' && path && !path.startsWith('menu-')) {
      navigate(path);
    }
  };

  // menuItems 的 key 是 "path|type"，需要提取 path 来匹配
  const selectedKeys = menuItems?.map((item: any) => {
    const k = String(item.key);
    return k.split('|')[0];
  })?.filter((p: string) => pathname === p || pathname.startsWith(p + '/')) || [pathname];

  // Find parent keys that should be open
  const findOpenKeys = (items: MenuItemType[], targetPath: string, parents: string[] = []): string[] => {
    for (const item of items) {
      const currentPath = item.path || `menu-${item.id}`;
      if (currentPath === targetPath) {
        return parents;
      }
      if (item.children && item.children.length > 0) {
        const found = findOpenKeys(item.children, targetPath, [...parents, currentPath + '|' + item.menuType]);
        if (found.length > 0 || parents.length > 0) return found.length > 0 ? found : [...parents, currentPath + '|' + item.menuType];
      }
    }
    return [];
  };

  const openKeys = findOpenKeys(menus, pathname);

  return (
    <Menu
      theme="dark"
      mode="inline"
      selectedKeys={selectedKeys}
      defaultOpenKeys={collapsed ? [] : openKeys}
      items={menuItems}
      onClick={handleClick}
    />
  );
};

export default SiderMenu;
