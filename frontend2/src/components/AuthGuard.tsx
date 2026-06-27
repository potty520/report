import React from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuthStore } from '@/stores/useAuthStore';
import { Spin } from 'antd';

interface AuthGuardProps {
  children: React.ReactNode;
}

const AuthGuard: React.FC<AuthGuardProps> = ({ children }) => {
  const token = useAuthStore((s) => s.token);
  const location = useLocation();

  if (!token) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  return <>{children}</>;
};

export function withAuth(Component: React.LazyExoticComponent<React.ComponentType<any>> | React.ComponentType<any>) {
  return function AuthenticatedComponent(props: any) {
    const token = useAuthStore((s) => s.token);
    if (!token) {
      return <Navigate to="/login" replace />;
    }
    return (
      <React.Suspense fallback={<div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100%' }}><Spin size="large" /></div>}>
        <Component {...props} />
      </React.Suspense>
    );
  };
}

export default AuthGuard;
