import React from 'react';
import { Cpu, RefreshCw, X, CheckCircle2, AlertCircle, HardDrive, ShieldCheck } from 'lucide-react';
import { useApp } from '../context/AppContext';
import { EngineState } from '../types';
import { EngineStateBadge } from './Badges';

export const EngineSetupDialog: React.FC = () => {
  const {
    isEngineSetupOpen,
    setIsEngineSetupOpen,
    ytdlpStatus,
    ffmpegStatus,
    deviceAbi,
    isUpdatingYtDlp,
    isUpdatingFFmpeg,
    isCheckingUpdates,
    updateCheckMessage,
    ytdlpUpdateProgress,
    ffmpegUpdateProgress,
    checkForEngineUpdates,
    updateYtDlpBinary,
    installOrUpdateFFmpeg,
  } = useApp();

  if (!isEngineSetupOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/75 backdrop-blur-sm animate-fade-in">
      <div className="bg-[#2B2930] border border-[#49454F] rounded-3xl w-full max-w-lg overflow-hidden shadow-2xl">
        {/* Header */}
        <div className="flex items-center justify-between p-5 border-b border-[#49454F]/80">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-xl bg-[#4A4458] flex items-center justify-center text-[#D0BCFF]">
              <Cpu className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-base font-bold text-[#E6E1E5]">Media Engine Setup & Status</h2>
              <p className="text-xs text-[#938F99]">Core extraction and post-processing runtimes</p>
            </div>
          </div>
          <button
            onClick={() => setIsEngineSetupOpen(false)}
            className="w-8 h-8 rounded-full flex items-center justify-center text-[#CAC4D0] hover:text-white hover:bg-[#4A4458] transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Content */}
        <div className="p-5 space-y-4 max-h-[75vh] overflow-y-auto">
          {/* ABI & System Platform */}
          <div className="flex items-center justify-between p-3 rounded-xl bg-[#1C1B1F] border border-[#49454F]/40">
            <div className="flex items-center gap-2">
              <HardDrive className="w-4 h-4 text-[#D0BCFF]" />
              <span className="text-xs text-[#CAC4D0] font-medium">Architecture / Environment:</span>
            </div>
            <span className="text-xs font-mono font-bold text-[#D0BCFF]">{deviceAbi}</span>
          </div>

          {/* Global Update Check Banner */}
          {updateCheckMessage && (
            <div
              className={`p-3 rounded-xl text-xs font-medium border ${
                updateCheckMessage.startsWith('✓')
                  ? 'bg-[#4ADE80]/10 border-[#4ADE80]/30 text-[#4ADE80]'
                  : 'bg-[#FFB74D]/10 border-[#FFB74D]/30 text-[#FFB74D]'
              }`}
            >
              {updateCheckMessage}
            </div>
          )}

          {/* Engine 1: yt-dlp Core */}
          <div className="p-4 rounded-2xl bg-[#1C1B1F] border border-[#49454F]/60 space-y-3">
            <div className="flex items-center justify-between">
              <div>
                <div className="flex items-center gap-2">
                  <h3 className="text-sm font-bold text-[#E6E1E5]">yt-dlp Core Extractor</h3>
                  <EngineStateBadge state={ytdlpStatus.state} />
                </div>
                <p className="text-xs font-mono text-[#D0BCFF] mt-0.5">Version: {ytdlpStatus.version}</p>
              </div>
              <button
                onClick={updateYtDlpBinary}
                disabled={isUpdatingYtDlp}
                className="px-3 py-1.5 rounded-xl bg-[#D0BCFF] text-[#381E72] hover:bg-[#E8DEF8] disabled:opacity-50 text-xs font-bold transition-all shadow-sm flex items-center gap-1.5"
              >
                {isUpdatingYtDlp && <RefreshCw className="w-3 h-3 animate-spin" />}
                <span>{ytdlpStatus.isReady ? 'Reinstall' : 'Install'}</span>
              </button>
            </div>

            {isUpdatingYtDlp && (
              <div className="w-full bg-[#4A4458] h-1.5 rounded-full overflow-hidden">
                <div
                  className="bg-[#D0BCFF] h-full transition-all duration-200"
                  style={{ width: `${ytdlpUpdateProgress}%` }}
                />
              </div>
            )}

            <p className="text-[11px] text-[#938F99] leading-relaxed">
              Handles video URL parsing, playlist extraction, format manifest inspection, and stream decryption.
            </p>
          </div>

          {/* Engine 2: FFmpeg Transcoder & Muxer */}
          <div className="p-4 rounded-2xl bg-[#1C1B1F] border border-[#49454F]/60 space-y-3">
            <div className="flex items-center justify-between">
              <div>
                <div className="flex items-center gap-2">
                  <h3 className="text-sm font-bold text-[#E6E1E5]">FFmpeg & FFprobe</h3>
                  <EngineStateBadge state={ffmpegStatus.state} />
                </div>
                <p className="text-xs font-mono text-[#D0BCFF] mt-0.5">Version: {ffmpegStatus.version}</p>
              </div>
              <button
                onClick={installOrUpdateFFmpeg}
                disabled={isUpdatingFFmpeg}
                className="px-3 py-1.5 rounded-xl bg-[#D0BCFF] text-[#381E72] hover:bg-[#E8DEF8] disabled:opacity-50 text-xs font-bold transition-all shadow-sm flex items-center gap-1.5"
              >
                {isUpdatingFFmpeg && <RefreshCw className="w-3 h-3 animate-spin" />}
                <span>{ffmpegStatus.isAvailable ? 'Reinstall' : 'Install'}</span>
              </button>
            </div>

            {isUpdatingFFmpeg && (
              <div className="w-full bg-[#4A4458] h-1.5 rounded-full overflow-hidden">
                <div
                  className="bg-[#D0BCFF] h-full transition-all duration-200"
                  style={{ width: `${ffmpegUpdateProgress}%` }}
                />
              </div>
            )}

            <p className="text-[11px] text-[#938F99] leading-relaxed">
              Merges separate high-res video and audio tracks, embeds subtitles and album artwork, and transcodes to MP4/MP3/Opus.
            </p>
          </div>

          {/* Engine Health Assurance */}
          <div className="p-3.5 rounded-xl bg-[#4A4458]/30 border border-[#49454F]/50 flex items-start gap-2.5">
            <ShieldCheck className="w-5 h-5 text-[#4ADE80] shrink-0 mt-0.5" />
            <p className="text-xs text-[#CAC4D0] leading-relaxed">
              All engine processes operate safely in sandboxed environments with automated memory cleanup and fault recovery.
            </p>
          </div>
        </div>

        {/* Footer */}
        <div className="flex items-center justify-end gap-2 p-4 bg-[#1C1B1F] border-t border-[#49454F]/80">
          <button
            onClick={() => checkForEngineUpdates()}
            disabled={isCheckingUpdates || isUpdatingYtDlp || isUpdatingFFmpeg}
            className="flex items-center gap-2 px-4 py-2 rounded-xl bg-[#4A4458] hover:bg-[#938F99]/40 text-[#E6E1E5] text-xs font-semibold transition-all disabled:opacity-50"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${isCheckingUpdates ? 'animate-spin' : ''}`} />
            <span>{isCheckingUpdates ? 'Checking...' : 'Check Updates'}</span>
          </button>
          <button
            onClick={() => setIsEngineSetupOpen(false)}
            className="px-5 py-2 rounded-xl bg-[#D0BCFF] text-[#381E72] hover:bg-[#E8DEF8] text-xs font-bold transition-all shadow-sm"
          >
            Done
          </button>
        </div>
      </div>
    </div>
  );
};
