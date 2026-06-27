import React from 'react';
import { Result, Button } from 'antd';
import { history } from 'umi';

const Authorized = ({ children, authority, noMatch }) => {
  const currentUser = JSON.parse(localStorage.getItem('currentUser') || '{}');
  const permissions = currentUser?.permissions || [];
  const roles = currentUser?.roles || [];

  const hasPermission = () => {
    if (!authority) return true;
    if (Array.isArray(authority)) {
      return authority.some((auth) => permissions.includes(auth) || roles.includes(auth));
    }
    if (typeof authority === 'string') {
      return permissions.includes(authority) || roles.includes(authority) || authority === 'admin';
    }
    if (typeof authority === 'function') {
      return authority(currentUser);
    }
    return true;
  };

  if (hasPermission()) {
    return children;
  }

  if (noMatch) {
    return noMatch;
  }

  return (
    <Result
      status="403"
      title="403"
      subTitle="抱歉，您没有权限访问此页面。"
      extra={
        <Button type="primary" onClick={() => history.push('/')}>
          返回首页
        </Button>
      }
    />
  );
};

export default Authorized;
