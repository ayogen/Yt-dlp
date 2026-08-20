import React from 'react';
import { DownloadStatus, MediaType, EngineState } from '../types';

export const StatusBadge: React.FC<{ status: DownloadStatus }> = ({ status }) => {
  switch (status) {
    case DownloadStatus.COMPLETED:
      return (
        <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold bg-[#4ADE80]/15 text-[#4ADE80] border border-[#4ADE80]/30">
          ✓ Completed
        </span>
      );
    case DownloadStatus.DOWNLOADING:
      return (
        <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold bg-[#D0BCFF]/15 text-[#D0BCFF] border border-[#D0BCFF]/30 animate-pulse">
          ⬇ Downloading
        </span>
      );
    case DownloadStatus.PAUSED:
      return (
        <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold bg-[#FFB74D]/15 text-[#FFB74D] border border-[#FFB74D]/30">
          ⏸ Paused
        </span>
      );
    case DownloadStatus.ANALYZING:
    case DownloadStatus.PROCESSING:
    case DownloadStatus.QUEUED:
      return (
        <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold bg-[#80D8FF]/15 text-[#80D8FF] border border-[#80D8FF]/30">
          ⏳ {status}
        </span>
      );
    case DownloadStatus.FAILED:
      return (
        <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold bg-[#F2B8B5]/15 text-[#F2B8B5] border border-[#F2B8B5]/30">
          ✕ Failed
        </span>
      );
    case DownloadStatus.CANCELLED:
      return (
        <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold bg-[#938F99]/15 text-[#938F99] border border-[#938F99]/30">
          Cancelled
        </span>
      );
    default:
      return null;
  }
};

export const MediaTypeBadge: React.FC<{ type: MediaType }> = ({ type }) => {
  switch (type) {
    case MediaType.VIDEO:
      return (
        <span className="inline-flex items-center px-2 py-0.5 rounded text-[11px] font-bold bg-[#D0BCFF]/20 text-[#D0BCFF]">
          VIDEO
        </span>
      );
    case MediaType.AUDIO:
      return (
        <span className="inline-flex items-center px-2 py-0.5 rounded text-[11px] font-bold bg-[#80D8FF]/20 text-[#80D8FF]">
          AUDIO
        </span>
      );
    case MediaType.PLAYLIST:
      return (
        <span className="inline-flex items-center px-2 py-0.5 rounded text-[11px] font-bold bg-[#4ADE80]/20 text-[#4ADE80]">
          PLAYLIST
        </span>
      );
    default:
      return null;
  }
};

export const EngineStateBadge: React.FC<{ state: EngineState; label?: string }> = ({ state, label }) => {
  switch (state) {
    case EngineState.READY:
      return (
        <span className="inline-flex items-center px-2 py-0.5 rounded text-[11px] font-bold bg-[#4ADE80]/15 text-[#4ADE80]">
          {label || 'Ready ✓'}
        </span>
      );
    case EngineState.UPDATING:
    case EngineState.INSTALLING:
      return (
        <span className="inline-flex items-center px-2 py-0.5 rounded text-[11px] font-bold bg-[#D0BCFF]/15 text-[#D0BCFF] animate-pulse">
          {label || 'Updating...'}
        </span>
      );
    case EngineState.MISSING:
      return (
        <span className="inline-flex items-center px-2 py-0.5 rounded text-[11px] font-bold bg-[#FFB74D]/15 text-[#FFB74D]">
          {label || 'Missing'}
        </span>
      );
    case EngineState.INVALID:
    case EngineState.ERROR:
      return (
        <span className="inline-flex items-center px-2 py-0.5 rounded text-[11px] font-bold bg-[#F2B8B5]/15 text-[#F2B8B5]">
          {label || 'Error'}
        </span>
      );
    default:
      return null;
  }
};
