import React, { useState } from 'react';
import { Tabs } from 'antd';
import * as Icons from '@ant-design/icons';

const iconList = [
  'DashboardOutlined', 'SettingOutlined', 'ApartmentOutlined', 'FileTextOutlined',
  'DatabaseOutlined', 'ScheduleOutlined', 'FormOutlined', 'SwapOutlined',
  'UserOutlined', 'TeamOutlined', 'SafetyOutlined', 'KeyOutlined',
  'HomeOutlined', 'AppstoreOutlined', 'BarChartOutlined', 'BellOutlined',
  'BookOutlined', 'CalendarOutlined', 'CameraOutlined', 'CarOutlined',
  'CheckCircleOutlined', 'CloudOutlined', 'CodeOutlined', 'CompassOutlined',
  'ContactsOutlined', 'ContainerOutlined', 'ControlOutlined', 'CreditCardOutlined',
  'CrownOutlined', 'CustomerServiceOutlined', 'DeleteOutlined', 'EditOutlined',
  'EnvironmentOutlined', 'ExperimentOutlined', 'EyeOutlined', 'FileOutlined',
  'FilterOutlined', 'FireOutlined', 'FlagOutlined', 'FolderOutlined',
  'FrownOutlined', 'GiftOutlined', 'GlobalOutlined', 'HeartOutlined',
  'HistoryOutlined', 'IdcardOutlined', 'InboxOutlined', 'InfoCircleOutlined',
  'LaptopOutlined', 'LayoutOutlined', 'LikeOutlined', 'LinkOutlined',
  'LockOutlined', 'MailOutlined', 'MedicineBoxOutlined', 'MessageOutlined',
  'MobileOutlined', 'MoneyCollectOutlined', 'MonitorOutlined', 'NotificationOutlined',
  'PhoneOutlined', 'PictureOutlined', 'PieChartOutlined', 'PlayCircleOutlined',
  'PrinterOutlined', 'ProfileOutlined', 'ProjectOutlined', 'PushpinOutlined',
  'QrcodeOutlined', 'ReadOutlined', 'ReconciliationOutlined', 'RedEnvelopeOutlined',
  'RestOutlined', 'RocketOutlined', 'SaveOutlined', 'SearchOutlined',
  'SendOutlined', 'ShopOutlined', 'ShoppingOutlined', 'SkinOutlined',
  'SmileOutlined', 'SolutionOutlined', 'SoundOutlined', 'StarOutlined',
  'SyncOutlined', 'TableOutlined', 'TagOutlined', 'TagsOutlined',
  'ThunderboltOutlined', 'ToolOutlined', 'TrademarkOutlined', 'TrophyOutlined',
  'UnlockOutlined', 'UploadOutlined', 'VideoCameraOutlined', 'WalletOutlined',
  'WifiOutlined', 'WindowsOutlined', 'YoutubeOutlined',
];

const IconSelector = ({ value, onChange, ...rest }) => {
  const [activeTab, setActiveTab] = useState('directional');

  const tabs = [
    { key: 'directional', label: '方向性' },
    { key: 'suggested', label: '提示性' },
    { key: 'editor', label: '编辑类' },
    { key: 'data', label: '数据类' },
    { key: 'brand', label: '品牌类' },
    { key: 'generic', label: '通用' },
  ];

  const handleSelect = (iconName) => {
    if (onChange) {
      onChange(iconName);
    }
  };

  const renderIcon = (iconName) => {
    const IconComponent = Icons[iconName];
    if (!IconComponent) return null;
    return (
      <span
        key={iconName}
        onClick={() => handleSelect(iconName)}
        style={{
          display: 'inline-flex',
          alignItems: 'center',
          justifyContent: 'center',
          width: 36,
          height: 36,
          margin: 4,
          cursor: 'pointer',
          border: value === iconName ? '2px solid #1890ff' : '1px solid #d9d9d9',
          borderRadius: 4,
          background: value === iconName ? '#e6f7ff' : '#fff',
          fontSize: 18,
        }}
        title={iconName}
      >
        <IconComponent />
      </span>
    );
  };

  return (
    <div>
      <Tabs activeKey={activeTab} onChange={setActiveTab} size="small">
        <Tabs.TabPane tab="全部" key="all">
          <div style={{ maxHeight: 200, overflowY: 'auto' }}>
            {iconList.map(renderIcon)}
          </div>
        </Tabs.TabPane>
        <Tabs.TabPane tab="常用" key="common">
          <div style={{ maxHeight: 200, overflowY: 'auto' }}>
            {iconList.slice(0, 30).map(renderIcon)}
          </div>
        </Tabs.TabPane>
      </Tabs>
      {value && (
        <div style={{ marginTop: 8, color: '#666' }}>
          已选择: {value}
        </div>
      )}
    </div>
  );
};

export default IconSelector;
