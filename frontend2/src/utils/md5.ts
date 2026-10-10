/**
 * 增量式 MD5（无第三方依赖）。
 *
 * 后端用 MD5 做秒传判定（`rpt_file.md5`）与分片续传检查，
 * 浏览器没有内置 MD5（WebCrypto 只有 SHA 系列），因此这里自带一份实现，
 * 并支持流式 update，避免把大文件整块读进内存。
 */

const S = [
  7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22,
  5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20,
  4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23,
  6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21,
];

const K = (() => {
  const table = new Uint32Array(64);
  for (let i = 0; i < 64; i++) {
    table[i] = Math.floor(Math.abs(Math.sin(i + 1)) * 4294967296) >>> 0;
  }
  return table;
})();

function rotl(x: number, c: number): number {
  return ((x << c) | (x >>> (32 - c))) >>> 0;
}

export class Md5 {
  private a = 0x67452301;
  private b = 0xefcdab89;
  private c = 0x98badcfe;
  private d = 0x10325476;
  private buf = new Uint8Array(64);
  private bufLen = 0;
  private total = 0;

  update(data: Uint8Array): this {
    this.total += data.length;
    let offset = 0;
    if (this.bufLen > 0) {
      const need = 64 - this.bufLen;
      const take = Math.min(need, data.length);
      this.buf.set(data.subarray(0, take), this.bufLen);
      this.bufLen += take;
      offset = take;
      if (this.bufLen === 64) {
        this.block(this.buf, 0);
        this.bufLen = 0;
      }
    }
    while (offset + 64 <= data.length) {
      this.block(data, offset);
      offset += 64;
    }
    if (offset < data.length) {
      this.buf.set(data.subarray(offset), 0);
      this.bufLen = data.length - offset;
    }
    return this;
  }

  private block(bytes: Uint8Array, off: number): void {
    const M = new Uint32Array(16);
    for (let i = 0; i < 16; i++) {
      const j = off + i * 4;
      M[i] = (bytes[j] | (bytes[j + 1] << 8) | (bytes[j + 2] << 16) | (bytes[j + 3] << 24)) >>> 0;
    }
    let A = this.a;
    let B = this.b;
    let C = this.c;
    let D = this.d;
    for (let i = 0; i < 64; i++) {
      let F: number;
      let g: number;
      if (i < 16) {
        F = (B & C) | (~B & D);
        g = i;
      } else if (i < 32) {
        F = (D & B) | (~D & C);
        g = (5 * i + 1) % 16;
      } else if (i < 48) {
        F = B ^ C ^ D;
        g = (3 * i + 5) % 16;
      } else {
        F = C ^ (B | ~D);
        g = (7 * i) % 16;
      }
      F = (F + A + K[i] + M[g]) >>> 0;
      A = D;
      D = C;
      C = B;
      B = (B + rotl(F, S[i])) >>> 0;
    }
    this.a = (this.a + A) >>> 0;
    this.b = (this.b + B) >>> 0;
    this.c = (this.c + C) >>> 0;
    this.d = (this.d + D) >>> 0;
  }

  /** 计算摘要（hex）。注意：调用后再 update 结果不再有效。 */
  digest(): string {
    const bitLen = this.total * 8;
    const padLen = this.bufLen < 56 ? 64 : 128;
    const pad = new Uint8Array(padLen);
    pad.set(this.buf.subarray(0, this.bufLen), 0);
    pad[this.bufLen] = 0x80;
    const lenPos = padLen - 8;
    const lo = bitLen >>> 0;
    const hi = Math.floor(bitLen / 4294967296) >>> 0;
    pad[lenPos] = lo & 0xff;
    pad[lenPos + 1] = (lo >>> 8) & 0xff;
    pad[lenPos + 2] = (lo >>> 16) & 0xff;
    pad[lenPos + 3] = (lo >>> 24) & 0xff;
    pad[lenPos + 4] = hi & 0xff;
    pad[lenPos + 5] = (hi >>> 8) & 0xff;
    pad[lenPos + 6] = (hi >>> 16) & 0xff;
    pad[lenPos + 7] = (hi >>> 24) & 0xff;
    for (let off = 0; off < padLen; off += 64) {
      this.block(pad, off);
    }
    const words = [this.a, this.b, this.c, this.d];
    let hex = '';
    for (const w of words) {
      for (let i = 0; i < 4; i++) {
        hex += ((w >>> (i * 8)) & 0xff).toString(16).padStart(2, '0');
      }
    }
    return hex;
  }
}

/** 计算一段数据的 MD5（hex） */
export function md5Hex(data: Uint8Array): string {
  return new Md5().update(data).digest();
}

/**
 * 流式计算文件 MD5，onProgress 返回 0~100 的百分比。
 */
export async function md5File(
  file: File,
  onProgress?: (percent: number) => void,
  sliceSize = 2 * 1024 * 1024,
): Promise<string> {
  const md5 = new Md5();
  let offset = 0;
  while (offset < file.size) {
    const end = Math.min(offset + sliceSize, file.size);
    const buffer = await file.slice(offset, end).arrayBuffer();
    md5.update(new Uint8Array(buffer));
    offset = end;
    if (onProgress) {
      onProgress(file.size === 0 ? 100 : Math.min(100, Math.round((offset / file.size) * 100)));
    }
  }
  return md5.digest();
}
