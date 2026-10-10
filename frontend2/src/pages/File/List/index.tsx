import React, { useEffect, useState } from 'react';
import { Table, Button, Space, Tag, App, Card, Popconfirm, Tooltip, Upload, Input } from 'antd';
import { ReloadOutlined, DownloadOutlined, DeleteOutlined, PaperClipOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { useFileStore } from '@/stores/useFileStore';
import {
  downloadFileApi, FileItem, uploadVolumeApi, getVolumeGroupsApi,
  downloadVolumeGroupApi, deleteVolumeGroupApi, VolumeGroup,
} from '@/services/api';
import { handleDownload } from '@/utils/download';
import ChunkUpload from '@/components/ChunkUpload';
import TaskSelector from '@/components/TaskSelector';

const sizeText = (size?: number) => {
  if (!size && size !== 0) return '-';
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
  return `${(size / 1024 / 1024).toFixed(2)} MB`;
};

/** 附件管理（P1-D）：分片上传 / 秒传 / 断点续传 / 列表 / 下载 / 删除 */
const FileListPage: React.FC = () => {
  const { message: msg } = App.useApp();
  const { files, loading, taskId, fetchFiles, removeFile, clear } = useFileStore();
  const [selectedTask, setSelectedTask] = useState<number | undefined>(undefined);

  // ---- 分卷附件 ----
  const [groups, setGroups] = useState<VolumeGroup[]>([]);
  const [groupKey, setGroupKey] = useState('');
  const [volumeUploading, setVolumeUploading] = useState(false);

  const loadGroups = async (id?: number) => {
    try {
      const res = await getVolumeGroupsApi(id);
      setGroups(res.data.data || []);
    } catch {
      setGroups([]);
    }
  };

  /** 多卷上传：一次选择多个文件，按选择顺序登记为 0..n-1 卷 */
  const uploadVolumes = async (files: File[]) => {
    const key = groupKey.trim() || `grp-${Date.now()}`;
    if (!selectedTask) {
      msg.warning('请先选择任务');
      return;
    }
    setGroupKey(key);
    setVolumeUploading(true);
    try {
      for (let i = 0; i < files.length; i++) {
        await uploadVolumeApi(files[i], { groupKey: key, volumeIndex: i, taskId: selectedTask });
      }
      msg.success(`已上传 ${files.length} 个分卷（组：${key}）`);
      await loadGroups(selectedTask);
      await fetchFiles(selectedTask);
    } catch (e: any) {
      msg.error(e?.message || '分卷上传失败');
    } finally {
      setVolumeUploading(false);
    }
  };

  const downloadGroup = async (record: VolumeGroup) => {
    try {
      const res = await downloadVolumeGroupApi(record.groupKey);
      await handleDownload(res as any, `分卷附件-${record.groupKey}.zip`);
    } catch (e: any) {
      msg.error(e?.message || '打包下载失败');
    }
  };

  const removeGroup = async (record: VolumeGroup) => {
    try {
      await deleteVolumeGroupApi(record.groupKey);
      msg.success('已删除该分卷组');
      if (selectedTask) { await loadGroups(selectedTask); await fetchFiles(selectedTask); }
    } catch (e: any) {
      msg.error(e?.message || '删除失败');
    }
  };

  const groupColumns: ColumnsType<VolumeGroup> = [
    { title: '分卷组', dataIndex: 'groupKey', ellipsis: true },
    { title: '卷数', dataIndex: 'partCount', width: 80, align: 'center' },
    {
      title: '总大小', dataIndex: 'totalSize', width: 110, align: 'right',
      render: (v: number) => sizeText(v),
    },
    {
      title: '卷号明细', dataIndex: 'parts', ellipsis: true,
      render: (parts: VolumeGroup['parts']) =>
        (parts || []).map((p) => `#${p.volumeIndex} ${p.fileName}`).join('，') || '-',
    },
    {
      title: '操作', key: 'action', width: 190,
      render: (_, record) => (
        <Space>
          <Button type="link" size="small" icon={<DownloadOutlined />} onClick={() => downloadGroup(record)}>打包下载</Button>
          <Popconfirm title="删除整个分卷组？" onConfirm={() => removeGroup(record)} okText="删除" cancelText="取消">
            <Button type="link" size="small" danger icon={<DeleteOutlined />}>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  useEffect(() => () => clear(), [clear]);

  const load = async (id?: number) => {
    if (!id) {
      msg.warning('请先选择任务');
      return;
    }
    setSelectedTask(id);
    await fetchFiles(id);
  };

  const download = async (record: FileItem) => {
    try {
      const res = await downloadFileApi(record.id);
      await handleDownload(res as any, record.fileName);
    } catch (e: any) {
      msg.error(e?.message || '下载失败');
    }
  };

  const remove = async (record: FileItem) => {
    try {
      await removeFile(record.id);
      msg.success('已删除');
    } catch (e: any) {
      msg.error(e?.message || '删除失败');
    }
  };

  const columns: ColumnsType<FileItem> = [
    {
      title: '文件名', dataIndex: 'fileName', ellipsis: true,
      render: (v, r) => <Space><PaperClipOutlined />{v || `#${r.id}`}</Space>,
    },
    { title: '大小', dataIndex: 'fileSize', width: 110, align: 'right', render: sizeText },
    { title: '类型', dataIndex: 'fileType', width: 120, render: (v) => (v ? <Tag>{v}</Tag> : '-') },
    { title: 'MD5', dataIndex: 'md5', width: 140, ellipsis: true, render: (v) => (v ? <Tooltip title={v}>{String(v).slice(0, 12)}…</Tooltip> : '-') },
    { title: '上传人', dataIndex: 'uploadByName', width: 120, render: (v) => v || '-' },
    {
      title: '上传时间', dataIndex: 'createTime', width: 165,
      render: (v) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm:ss') : '-'),
    },
    {
      title: '操作', key: 'action', width: 150, fixed: 'right',
      render: (_, record) => (
        <Space>
          <Button type="link" size="small" icon={<DownloadOutlined />} onClick={() => download(record)}>下载</Button>
          <Popconfirm title="确认删除该附件？" onConfirm={() => remove(record)} okText="删除" cancelText="取消">
            <Button type="link" size="small" danger icon={<DeleteOutlined />}>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div style={{ padding: 24, background: '#fff', borderRadius: 8 }}>
      <Space style={{ marginBottom: 16 }} wrap>
        <span>任务：</span>
        <TaskSelector value={selectedTask} onChange={setSelectedTask} />
        <Button type="primary" icon={<ReloadOutlined />} onClick={() => load(selectedTask)}>加载附件</Button>
      </Space>

      <Card size="small" title="上传附件" style={{ marginBottom: 16 }}>
        <ChunkUpload
          taskId={selectedTask}
          disabled={!selectedTask}
          onDone={() => selectedTask && fetchFiles(selectedTask)}
        />
        {!selectedTask && (
          <div style={{ marginTop: 8, color: '#d46b08' }}>请先选择任务后再上传（附件记录需要归属到任务）。</div>
        )}
      </Card>

      <Card size="small" title="分卷附件（多卷成组上传，成组打包下载）" style={{ marginBottom: 16 }}>
        <Space style={{ marginBottom: 8 }} wrap>
          <span>分卷组标识：</span>
          <Input
            placeholder="留空自动生成，例如 report-2026-10"
            style={{ width: 260 }}
            value={groupKey}
            onChange={(e) => setGroupKey(e.target.value)}
          />
          <Upload
            multiple
            showUploadList={false}
            disabled={!selectedTask || volumeUploading}
            beforeUpload={() => false}
            onChange={({ fileList }) => {
              const files = (fileList || []).map((f) => f.originFileObj as File).filter(Boolean);
              if (files.length > 0) uploadVolumes(files);
            }}
          >
            <Button type="primary" icon={<PaperClipOutlined />} loading={volumeUploading} disabled={!selectedTask}>
              选择多个分卷上传
            </Button>
          </Upload>
          <Button onClick={() => loadGroups(selectedTask)} disabled={!selectedTask}>刷新分卷组</Button>
        </Space>
        <Table
          columns={groupColumns}
          dataSource={groups}
          rowKey="groupKey"
          size="small"
          pagination={{ pageSize: 5, showSizeChanger: false }}
          locale={{ emptyText: '暂无分卷组（选择多个文件上传即自动成组）' }}
        />
      </Card>

      <Card size="small" title={`附件列表${taskId ? `（任务 ${taskId}）` : ''}`}>
        <Table
          columns={columns}
          dataSource={files}
          rowKey="id"
          loading={loading}
          size="small"
          scroll={{ x: 1100 }}
          pagination={{ pageSize: 10, showSizeChanger: true, showTotal: (t) => `共 ${t} 条` }}
        />
      </Card>
    </div>
  );
};

export default FileListPage;
