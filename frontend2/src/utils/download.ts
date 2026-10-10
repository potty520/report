import type { AxiosResponse } from 'axios';

/**
 * 从 Content-Disposition 解析文件名（支持 filename*=UTF-8''xxx）。
 */
export function parseFileName(disposition: string | undefined, fallback: string): string {
  if (!disposition) return fallback;
  const star = /filename\*=UTF-8''([^;]+)/i.exec(disposition);
  if (star && star[1]) {
    try {
      return decodeURIComponent(star[1]);
    } catch {
      return fallback;
    }
  }
  const plain = /filename="?([^";]+)"?/i.exec(disposition);
  return plain && plain[1] ? plain[1] : fallback;
}

/** 触发浏览器保存 blob */
export function saveBlob(blob: Blob, fileName: string): void {
  const url = window.URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = fileName;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  window.URL.revokeObjectURL(url);
}

/**
 * 处理下载响应。
 *
 * 后端下载类接口失败时会以「HTTP 200 + JSON(code=500) 的 blob」返回
 * （见交接文档《已知坑》#8），这里把 blob 还原成真正的错误提示，
 * 成功时按 Content-Disposition 的表单名保存文件。
 */
export async function handleDownload(resp: AxiosResponse<Blob>, fallbackName: string): Promise<void> {
  const blob = resp.data;
  const type = (blob && blob.type) || '';
  if (type.includes('json')) {
    const text = await blob.text();
    let msg = '下载失败';
    try {
      const j = JSON.parse(text);
      msg = j.msg || msg;
    } catch {
      /* 非 JSON 内容，保留默认提示 */
    }
    throw new Error(msg);
  }
  const headers = resp.headers as unknown as Record<string, string> | undefined;
  const name = parseFileName(headers?.['content-disposition'], fallbackName);
  saveBlob(blob, name);
}
