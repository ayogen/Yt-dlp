import React from 'react';
import { Terminal, Cpu, Download, CheckCircle2, AlertTriangle } from 'lucide-react';
import { useApp } from '../context/AppContext';
import { EngineState } from '../types';

export const Header: React.FC = () => {
  const { ytdlpStatus, ffmpegStatus, setIsEngineSetupOpen, setIsLogsDialogOpen } = useApp();

  const isAllReady =
    ytdlpStatus.state === EngineState.READY && ffmpegStatus.state === EngineState.READY;

  return (
    <header className="sticky top-0 z-30 bg-[#1C1B1F]/90 backdrop-blur-md border-b border-[#49454F]/60 px-4 py-3 sm:px-6">
      <div className="max-w-7xl mx-auto flex items-center justify-between">
        {/* Brand */}
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-[#381E72] to-[#D0BCFF] p-0.5 flex items-center justify-center shadow-lg shadow-[#D0BCFF]/10">
            <div className="w-full h-full bg-[#1C1B1F] rounded-[10px] flex items-center justify-center">
              <Download className="w-5 h-5 text-[#D0BCFF]" />
            </div>
          </div>
          <div>
            <div className="flex items-center gap-2">
              <span className="text-lg font-extrabold tracking-tight text-[#E6E1E5]">Transcode</span>
              <span className="text-[10px] font-bold uppercase tracking-wider px-1.5 py-0.5 rounded bg-[#4A4458] text-[#D0BCFF]">
                v2.5
              </span>
            </div>
            <p className="text-xs text-[#938F99]">yt-dlp & FFmpeg Universal Media Studio</p>
          </div>
        </div>

        {/* Status and Action Buttons */}
        <div className="flex items-center gap-2 sm:gap-3">
          {/* Engine Health Pill */}
          <button
            onClick={() => setIsEngineSetupOpen(true)}
            className={`flex items-center gap-2 px-3 py-1.5 rounded-full text-xs font-semibold border transition-all ${
              isAllReady
                ? 'bg-[#4ADE80]/10 border-[#4ADE80]/30 text-[#4ADE80] hover:bg-[#4ADE80]/20'
                : 'bg-[#FFB74D]/10 border-[#FFB74D]/30 text-[#FFB74D] hover:bg-[#FFB74D]/20'
            }`}
            title="Inspect Media Engines"
          >
            {isAllReady ? (
              <CheckCircle2 className="w-3.5 h-3.5 text-[#4ADE80]" />
            ) : (
              <AlertTriangle className="w-3.5 h-3.5 text-[#FFB74D]" />
            )}
            <span className="hidden sm:inline">
              {isAllReady ? 'Engines Ready' : 'Engine Warning'}
            </span>
            <Cpu className="w-3.5 h-3.5 text-[#D0BCFF]" />
          </button>

          {/* Live System Logs Button */}
          <button
            onClick={() => setIsLogsDialogOpen(true)}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-[#2B2930] hover:bg-[#4A4458] border border-[#49454F] text-[#CAC4D0] hover:text-[#E6E1E5] text-xs font-medium transition-all"
            title="View Real-Time Engine Logs"
          >
            <Terminal className="w-3.5 h-3.5 text-[#D0BCFF]" />
            <span className="hidden md:inline">Logs</span>
          </button>
        </div>
      </div>
    </header>
  );
};
