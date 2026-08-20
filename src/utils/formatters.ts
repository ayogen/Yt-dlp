export function formatBytes(bytes: number): string {
  if (bytes <= 0 || isNaN(bytes)) return '0 B';
  if (bytes < 1024) return `${bytes} B`;
  const exp = Math.floor(Math.log(bytes) / Math.log(1024));
  const pre = 'KMGTPE'[exp - 1] || '';
  return `${(bytes / Math.pow(1024, exp)).toFixed(2)} ${pre}B`;
}

export function formatSpeed(bytesPerSec: number): string {
  if (bytesPerSec <= 0 || isNaN(bytesPerSec)) return '0 B/s';
  const exp = Math.min(Math.floor(Math.log(bytesPerSec) / Math.log(1024)), 4);
  const pre = exp > 0 ? 'KMGTPE'[exp - 1] : '';
  return `${(bytesPerSec / Math.pow(1024, exp)).toFixed(2)} ${pre}B/s`;
}

export function formatEta(etaSeconds: number): string {
  if (etaSeconds <= 0 || isNaN(etaSeconds)) return '--';
  const hours = Math.floor(etaSeconds / 3600);
  const minutes = Math.floor((etaSeconds % 3600) / 60);
  const seconds = Math.floor(etaSeconds % 60);
  if (hours > 0) {
    return `${hours}h ${String(minutes).padStart(2, '0')}m`;
  } else if (minutes > 0) {
    return `${minutes}m ${String(seconds).padStart(2, '0')}s`;
  } else {
    return `${seconds}s`;
  }
}

export function formatDuration(durationSeconds: number): string {
  if (durationSeconds <= 0 || isNaN(durationSeconds)) return '--:--';
  const hours = Math.floor(durationSeconds / 3600);
  const minutes = Math.floor((durationSeconds % 3600) / 60);
  const seconds = Math.floor(durationSeconds % 60);
  if (hours > 0) {
    return `${hours}:${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
  } else {
    return `${minutes}:${String(seconds).padStart(2, '0')}`;
  }
}
