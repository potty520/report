import React from 'react';
import { Spin } from 'antd';

const PageLoading: React.FC = () => {
  return (
    <div style={{
      display: 'flex',
      justifyContent: 'center',
      alignItems: 'center',
      height: '100%',
      minHeight: '300px',
    }}>
      <Spin size="large" tip="加载中..." />
    </div>
  );
};

export default PageLoading;
