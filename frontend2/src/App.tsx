import React, { lazy, useEffect } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { ConfigProvider, App as AntApp, theme } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import MainLayout from '@/layouts/MainLayout';
import PageLoading from '@/components/PageLoading';
import { useAuthStore } from '@/stores/useAuthStore';

// Lazy load pages
const Login = lazy(() => import('@/pages/Login'));
const Overview = lazy(() => import('@/pages/Overview'));
const UserCenter = lazy(() => import('@/pages/UserCenter'));

// System pages
const UserPage = lazy(() => import('@/pages/System/User'));
const RolePage = lazy(() => import('@/pages/System/Role'));
const MenuPage = lazy(() => import('@/pages/System/Menu'));
const LogPage = lazy(() => import('@/pages/System/Log'));

// Org pages
const DeptPage = lazy(() => import('@/pages/Org/Dept'));
const ProjectPage = lazy(() => import('@/pages/Org/Project'));

// Template pages
const TemplateList = lazy(() => import('@/pages/Template/List'));
const TemplateAdd = lazy(() => import('@/pages/Template/Add'));
const TemplateDetail = lazy(() => import('@/pages/Template/Detail'));

// Datasource
const DatasourceList = lazy(() => import('@/pages/Datasource/List'));

// Task pages
const TaskFormulation = lazy(() => import('@/pages/Task/Formulation'));
const TaskAssign = lazy(() => import('@/pages/Task/Assign'));
const TaskLedger = lazy(() => import('@/pages/Task/Ledger'));

// Fill pages
const FillTask = lazy(() => import('@/pages/Fill/Task'));
const FillAudit = lazy(() => import('@/pages/Fill/Audit'));

// Data Apply pages
const DataApplyApply = lazy(() => import('@/pages/DataApply/Apply'));
const DataApplyApprove = lazy(() => import('@/pages/DataApply/Approve'));

// Audit pages (P0-C)
const DeptAudit = lazy(() => import('@/pages/Audit/DeptAudit'));
const TaskAudit = lazy(() => import('@/pages/Audit/TaskAudit'));
const Trajectory = lazy(() => import('@/pages/Audit/Trajectory'));
const Correct = lazy(() => import('@/pages/Audit/Correct'));

const App: React.FC = () => {
  const { token, initialize } = useAuthStore();

  useEffect(() => {
    initialize();
  }, [initialize]);

  return (
    <ConfigProvider
      locale={zhCN}
      theme={{
        algorithm: theme.defaultAlgorithm,
        token: {
          colorPrimary: '#1677ff',
          borderRadius: 6,
        },
      }}
    >
      <AntApp>
        <BrowserRouter>
          <Routes>
            <Route path="/login" element={token ? <Navigate to="/overview" replace /> : <React.Suspense fallback={<PageLoading />}><Login /></React.Suspense>} />
            <Route path="/" element={token ? <MainLayout /> : <Navigate to="/login" replace />}>
              <Route index element={<Navigate to="/overview" replace />} />
              <Route path="overview" element={<Overview />} />
              <Route path="userCenter" element={<UserCenter />} />

              {/* System */}
              <Route path="system/user" element={<UserPage />} />
              <Route path="system/role" element={<RolePage />} />
              <Route path="system/menu" element={<MenuPage />} />
              <Route path="system/log" element={<LogPage />} />

              {/* Org */}
              <Route path="org/dept" element={<DeptPage />} />
              <Route path="org/project" element={<ProjectPage />} />

              {/* Template */}
              <Route path="template/list" element={<TemplateList />} />
              <Route path="template/add" element={<TemplateAdd />} />
              <Route path="template/detail/:id" element={<TemplateDetail />} />

              {/* Datasource */}
              <Route path="datasource/list" element={<DatasourceList />} />

              {/* Task */}
              <Route path="task/formulation" element={<TaskFormulation />} />
              <Route path="task/assign" element={<TaskAssign />} />
              <Route path="task/ledger" element={<TaskLedger />} />

              {/* Fill */}
              <Route path="fill/task" element={<FillTask />} />
              <Route path="fill/audit" element={<FillAudit />} />

              {/* Data Apply */}
              <Route path="dataApply/apply" element={<DataApplyApply />} />
              <Route path="dataApply/approve" element={<DataApplyApprove />} />

              {/* Audit (P0-C) */}
              <Route path="audit/dept-audit" element={<DeptAudit />} />
              <Route path="audit/task-audit" element={<TaskAudit />} />
              <Route path="audit/trajectory" element={<Trajectory />} />
              <Route path="audit/correct" element={<Correct />} />
            </Route>
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </BrowserRouter>
      </AntApp>
    </ConfigProvider>
  );
};

export default App;
