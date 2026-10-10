import React, { useRef, useState } from 'react';
import { Upload, Progress, Space, Typography, App, Button } from 'antd';
import { InboxOutlined, ThunderboltOutlined } from '@ant-design/icons';
import { checkChunkApi, uploadChunkApi, uploadFileApi, mergeChunkApi, FileItem } from '@/services/api';
import { md5File } from '@/utils/md5';

const { Dragger } = Upload;
const { Text } = Typography;

interface ChunkUploadProps {
  /** 关联任务（可选） */
  taskId?: number;
  dataId?: number;
  fileType?: string;
  /** 分片大小，默认 512KB。
   *  线上 nginx client_max_body_size 与 Spring multipart 默认都是 1MB，
   *  512KB 可确保在当前配置下也能上传成功；等服务端上限放开（见 docs/report_2.0_upgrade4_audit_menus.sql 说明）
   *  可通过该 prop 调大到 5MB（与原版 5.12MB 接近）。 */
  chunkSize?: number;
  /** 小于该值走普通上传，默认等于 chunkSize */
  chunkThreshold?: number;
  onDone?: (file: FileItem) => void;
  disabled?: boolean;
}

interface UploadState {
  fileName: string;
  percent: number;
  phase: string;
}

/**
 * 大文件分片上传组件：秒传 / 断点续传 / 进度 / 取消（已上传分片保留，可再续）。
 *
 * 流程：本地算 MD5 → 秒传检查 → 逐片上传（跳过已传分片）→ 合并。
 * 小文件走普通上传接口。
 */
const ChunkUpload: React.FC<ChunkUploadProps> = ({
  taskId,
  dataId,
  fileType = 'ATTACHMENT',
  chunkSize = 512 * 1024,
  chunkThreshold,
  onDone,
  disabled,
}) => {
  const { message: msg } = App.useApp();
  const threshold = chunkThreshold ?? chunkSize;
  const [state, setState] = useState<UploadState | null>(null);
  const [busy, setBusy] = useState(false);
  const cancelled = useRef(false);

  const doUpload = async (file: File) => {
    cancelled.current = false;
    setBusy(true);
    setState({ fileName: file.name, percent: 0, phase: '计算文件校验值…' });
    try {
      // 1) MD5（0~25%）
      const md5 = await md5File(file, (p) => {
        setState({ fileName: file.name, percent: Math.round(p * 0.25), phase: `计算文件校验值… ${p}%` });
      });

      if (file.size <= threshold) {
        setState({ fileName: file.name, percent: 30, phase: '上传中（普通上传）…' });
        const res = await uploadFileApi(file, { taskId, dataId, fileType });
        setState({ fileName: file.name, percent: 100, phase: '完成' });
        msg.success(`${file.name} 上传成功`);
        onDone?.(res.data.data);
        return;
      }

      // 2) 秒传 / 断点续传检查（25~30%）
      const fileKey = `f${md5}_${file.size}`;
      setState({ fileName: file.name, percent: 27, phase: '检查是否能秒传…' });
      const checkRes = await checkChunkApi({ fileKey, fileName: file.name, md5 });
      const check = checkRes.data.data;

      if (check?.skipUpload) {
        setState({ fileName: file.name, percent: 100, phase: '秒传成功（服务端已有相同文件）' });
        msg.success(`${file.name} 秒传成功，无需重复上传`);
        return;
      }

      const uploaded = new Set(check?.uploadedChunks || []);
      const chunkTotal = Math.ceil(file.size / chunkSize);
      // 已上传分片按比例折算起点
      let doneBytes = 0;
      uploaded.forEach((i) => {
        doneBytes += Math.min(chunkSize, file.size - i * chunkSize);
      });

      // 3) 逐片上传（30~95%）
      for (let i = 0; i < chunkTotal; i++) {
        if (cancelled.current) {
          setState({ fileName: file.name, percent: Math.round(30 + (doneBytes / file.size) * 65), phase: '已取消（已上传分片已保留，可再次上传续传）' });
          msg.warning('已取消上传，已传分片保留，可重新选择同一文件续传');
          return;
        }
        if (uploaded.has(i)) continue;
        const start = i * chunkSize;
        const blob = file.slice(start, Math.min(start + chunkSize, file.size));
        await uploadChunkApi(fileKey, i, blob);
        doneBytes += blob.size;
        const percent = Math.min(95, Math.round(30 + (doneBytes / file.size) * 65));
        setState({
          fileName: file.name,
          percent,
          phase: `上传分片 ${i + 1}/${chunkTotal}（${Math.round((doneBytes / file.size) * 100)}%）`,
        });
      }

      // 4) 合并（95~100%）
      setState({ fileName: file.name, percent: 96, phase: '合并分片…' });
      const res = await mergeChunkApi({
        fileKey, fileName: file.name, chunkTotal, taskId, dataId, fileType,
      });
      setState({ fileName: file.name, percent: 100, phase: '完成' });
      msg.success(`${file.name} 上传成功（${chunkTotal} 个分片）`);
      onDone?.(res.data.data);
    } catch (e: any) {
      const text = e?.message || '上传失败';
      setState({ fileName: file.name, percent: 0, phase: `失败：${text}` });
      msg.error(text);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div>
      <Dragger
        multiple={false}
        disabled={disabled || busy}
        showUploadList={false}
        beforeUpload={(file) => {
          doUpload(file as unknown as File);
          return false; // 阻止 antd 自带上传，走自定义逻辑
        }}
      >
        <p className="ant-upload-drag-icon"><InboxOutlined /></p>
        <p className="ant-upload-text">点击或拖拽文件到此处上传</p>
        <p className="ant-upload-hint">
          大于 {Math.round(threshold / 1024 / 1024)}MB 的文件自动走分片上传，支持秒传与断点续传
        </p>
      </Dragger>

      {state && (
        <div style={{ marginTop: 12 }}>
          <Space direction="vertical" style={{ width: '100%' }}>
            <Space>
              <ThunderboltOutlined />
              <Text strong>{state.fileName}</Text>
              <Text type="secondary">{state.phase}</Text>
              {busy && (
                <Button size="small" onClick={() => { cancelled.current = true; }}>取消</Button>
              )}
            </Space>
            <Progress percent={state.percent} status={state.phase.startsWith('失败') ? 'exception' : undefined} />
          </Space>
        </div>
      )}
    </div>
  );
};

export default ChunkUpload;
