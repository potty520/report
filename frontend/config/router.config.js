export default [
  {
    path: '/login',
    component: '@/pages/Login',
    layout: false,
  },
  {
    path: '/',
    component: '@/layouts/BasicLayout',
    routes: [
      {
        path: '/',
        redirect: '/overview',
      },
      {
        path: '/overview',
        name: 'overview',
        component: '@/pages/Overview',
        icon: 'DashboardOutlined',
        title: '填报总览',
      },
      {
        path: '/system',
        name: 'system',
        icon: 'SettingOutlined',
        title: '授权管理',
        routes: [
          {
            path: '/system/user',
            name: 'user',
            component: '@/pages/System/User',
            title: '用户管理',
          },
          {
            path: '/system/role',
            name: 'role',
            component: '@/pages/System/Role',
            title: '角色管理',
          },
          {
            path: '/system/menu',
            name: 'menu',
            component: '@/pages/System/Menu',
            title: '菜单管理',
          },
          {
            path: '/system/log',
            name: 'log',
            component: '@/pages/System/Log',
            title: '登录日志',
          },
        ],
      },
      {
        path: '/org',
        name: 'org',
        icon: 'ApartmentOutlined',
        title: '组织架构',
        routes: [
          {
            path: '/org/dept',
            name: 'dept',
            component: '@/pages/Org/Dept',
            title: '部门管理',
          },
          {
            path: '/org/project',
            name: 'project',
            component: '@/pages/Org/Project',
            title: '项目管理',
          },
        ],
      },
      {
        path: '/template',
        name: 'template',
        icon: 'FileTextOutlined',
        title: '模板管理',
        routes: [
          {
            path: '/template/list',
            name: 'templateList',
            component: '@/pages/Template/List',
            title: '模板列表',
          },
          {
            path: '/template/add',
            name: 'templateAdd',
            component: '@/pages/Template/Add',
            title: '新增模板',
            hideInMenu: true,
          },
          {
            path: '/template/detail',
            name: 'templateDetail',
            component: '@/pages/Template/Detail',
            title: '模板详情',
            hideInMenu: true,
          },
        ],
      },
      {
        path: '/datasource/list',
        name: 'datasource',
        icon: 'DatabaseOutlined',
        component: '@/pages/DataSource/List',
        title: '数据源管理',
      },
      {
        path: '/task',
        name: 'task',
        icon: 'ScheduleOutlined',
        title: '任务管理',
        routes: [
          {
            path: '/task/formulation',
            name: 'taskFormulation',
            component: '@/pages/Task/Formulation',
            title: '填报任务制定',
          },
          {
            path: '/task/assign',
            name: 'taskAssign',
            component: '@/pages/Task/Assign',
            title: '任务分派',
          },
          {
            path: '/task/ledger',
            name: 'taskLedger',
            component: '@/pages/Task/Ledger',
            title: '任务台账',
          },
        ],
      },
      {
        path: '/fill',
        name: 'fill',
        icon: 'FormOutlined',
        title: '填报管理',
        routes: [
          {
            path: '/fill/task',
            name: 'fillTask',
            component: '@/pages/Fill/Task',
            title: '数据填报',
          },
          {
            path: '/fill/audit',
            name: 'fillAudit',
            component: '@/pages/Fill/Audit',
            title: '审核管理',
          },
        ],
      },
      {
        path: '/dataApply',
        name: 'dataApply',
        icon: 'SwapOutlined',
        title: '数据申请',
        routes: [
          {
            path: '/dataApply/apply',
            name: 'dataApplyApply',
            component: '@/pages/DataApply/Apply',
            title: '数据申请',
          },
          {
            path: '/dataApply/approve',
            name: 'dataApplyApprove',
            component: '@/pages/DataApply/Approve',
            title: '申请审批',
          },
        ],
      },
      {
        path: '/userCenter',
        name: 'userCenter',
        component: '@/pages/UserCenter',
        title: '个人中心',
        hideInMenu: true,
      },
    ],
  },
];
