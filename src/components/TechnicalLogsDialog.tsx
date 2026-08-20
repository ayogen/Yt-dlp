import React, { useState } from 'react';
import { Terminal, Copy, Trash2, X, Filter } from 'lucide-react';
import { useApp } from '../context/AppContext';
import { LogLevel } from '../types';

export const TechnicalLogsDialog: React.FC = () => {
  const { isLogsDialogOpen, setIsLogsDialogOpen, logs, clearLogs, copyToClipboard } = useApp();
  const [selectedLevel, setSelectedLevel] = useState<string>('ALL');

  if (!isLogsDialogOpen) return null;

  const filteredLogs = logs.filter((log) => {
    if (selectedLevel === 'ALL') return true;
    return log.level === selectedLevel;
  });

  const allLogsText = logs
    .map(
      (l) =>
        `[${new Date(l.timestamp).toLocaleTimeString()}] [${l.level}] [${l.tag}]${
          l.taskId ? ` [Task:${l.taskId}]` : ''
        } ${l.message}`
    )
    .join('\n');

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-sm animate-fade-in">
      <div className="bg-[#2B2930] border border-[#49454F] rounded-3xl w-full max-w-3xl flex flex-col max-h-[85vh] shadow-2xl overflow-hidden">
        {/* Header */}
        <div className="flex items-center justify-between p-4 sm:p-5 border-b border-[#49454F]/80">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-xl bg-[#4A4458] flex items-center justify-center text-[#D0BCFF]">
              <Terminal className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-base font-bold text-[#E6E1E5]">Diagnostics & Real-Time Engine Logs</h2>
              <p className="text-xs text-[#938F99]">Live traces, execution metrics, and extraction outputs</p>
            </div>
          </div>
          <button
            onClick={() => setIsLogsDialogOpen(false)}
            className="w-8 h-8 rounded-full flex items-center justify-center text-[#CAC4D0] hover:text-white hover:bg-[#4A4458] transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Toolbar */}
        <div className="p-3 bg-[#1C1B1F] border-b border-[#49454F]/60 flex flex-wrap items-center justify-between gap-2">
          {/* Level Filter Chips */}
          <div className="flex items-center gap-1.5 overflow-x-auto py-1">
            <Filter className="w-3.5 h-3.5 text-[#938F99] mr-1" />
            {['ALL', 'INFO', 'WARNING', 'ERROR', 'DEBUG'].map((level) => (
              <button
                key={level}
                onClick={() => setSelectedLevel(level)}
                className={`px-2.5 py-1 rounded-lg text-xs font-semibold transition-all ${
                  selectedLevel === level
                    ? 'bg-[#D0BCFF] text-[#381E72]'
                    : 'bg-[#2B2930] text-[#CAC4D0] hover:text-[#E6E1E5]'
                }`}
              >
                {level}
              </button>
            ))}
          </div>

          {/* Action buttons */}
          <div className="flex items-center gap-2">
            <button
              onClick={() => copyToClipboard(allLogsText, 'Diagnostics Logs')}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-[#2B2930] hover:bg-[#4A4458] text-[#CAC4D0] hover:text-[#E6E1E5] text-xs font-medium transition-all"
            >
              <Copy className="w-3.5 h-3.5 text-[#D0BCFF]" />
              <span>Copy All</span>
            </button>
            <button
              onClick={clearLogs}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-[#2B2930] hover:bg-[#F2B8B5]/20 text-[#F2B8B5] text-xs font-medium transition-all"
            >
              <Trash2 className="w-3.5 h-3.5" />
              <span>Clear</span>
            </button>
          </div>
        </div>

        {/* Log Viewer Content */}
        <div className="p-4 flex-1 overflow-y-auto bg-[#141218] font-mono text-xs text-[#E6E1E5] space-y-1.5">
          {filteredLogs.length === 0 ? (
            <div className="text-center py-12 text-[#938F99]">No logs recorded for filter: {selectedLevel}</div>
          ) : (
            filteredLogs.map((log) => {
              const time = new Date(log.timestamp).toLocaleTimeString();
              let levelColor = 'text-[#D0BCFF]';
              if (log.level === LogLevel.ERROR) levelColor = 'text-[#F2B8B5]';
              if (log.level === LogLevel.WARNING) levelColor = 'text-[#FFB74D]';
              if (log.level === LogLevel.DEBUG) levelColor = 'text-[#938F99]';

              return (
                <div key={log.logId} className="flex items-start gap-2 leading-relaxed hover:bg-white/[0.03] p-1 rounded">
                  <span className="text-[#938F99] shrink-0">[{time}]</span>
                  <span className={`font-bold shrink-0 ${levelColor}`}>[{log.level}]</span>
                  <span className="text-[#80D8FF] shrink-0 font-semibold">[{log.tag}]</span>
                  {log.taskId && <span className="text-[#938F99] shrink-0">[{log.taskId}]</span>}
                  <span className="text-[#CAC4D0] break-all">{log.message}</span>
                </div>
              );
            })
          )}
        </div>

        {/* Footer */}
        <div className="p-3 bg-[#1C1B1F] border-t border-[#49454F]/80 flex items-center justify-between text-xs text-[#938F99]">
          <span>Total Entries: {logs.length}</span>
          <button
            onClick={() => setIsLogsDialogOpen(false)}
            className="px-4 py-1.5 rounded-xl bg-[#D0BCFF] text-[#381E72] hover:bg-[#E8DEF8] font-bold transition-colors"
          >
            Close
          </button>
        </div>
      </div>
    </div>
  );
};
