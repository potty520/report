import React, { useState, useEffect, useCallback } from 'react';
import { TreeSelect } from 'antd';
import PropTypes from 'prop-types';

const TreeSelector = ({ value, onChange, fetchData, placeholder, multiple, treeCheckable, ...rest }) => {
  const [treeData, setTreeData] = useState([]);
  const [loading, setLoading] = useState(false);

  const loadData = useCallback(async () => {
    if (!fetchData) return;
    setLoading(true);
    try {
      const response = await fetchData();
      if (response && response.data) {
        const data = Array.isArray(response.data) ? response.data : [response.data];
        setTreeData(formatTreeData(data));
      }
    } catch (error) {
      console.error('TreeSelector loadData error:', error);
    } finally {
      setLoading(false);
    }
  }, [fetchData]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const formatTreeData = (data) => {
    return data.map((item) => ({
      title: item.name || item.label || item.title,
      value: item.id || item.value,
      key: item.id || item.value || item.key,
      children: item.children ? formatTreeData(item.children) : undefined,
      disabled: item.disabled,
    }));
  };

  const handleChange = (val) => {
    if (onChange) {
      onChange(val);
    }
  };

  return (
    <TreeSelect
      value={value}
      onChange={handleChange}
      treeData={treeData}
      loading={loading}
      placeholder={placeholder || '请选择'}
      treeCheckable={treeCheckable !== undefined ? treeCheckable : multiple}
      multiple={multiple}
      showCheckedStrategy={multiple ? TreeSelect.SHOW_CHILD : undefined}
      style={{ width: '100%' }}
      treeDefaultExpandAll
      {...rest}
    />
  );
};

TreeSelector.propTypes = {
  value: PropTypes.any,
  onChange: PropTypes.func,
  fetchData: PropTypes.func,
  placeholder: PropTypes.string,
  multiple: PropTypes.bool,
  treeCheckable: PropTypes.bool,
};

export default TreeSelector;
