import React from 'react';
import {
  Download,
  Pause,
  Play,
  XCircle,
  RotateCcw,
  CheckCircle2,
  Trash2,
  AlertTriangle,
  Terminal,
  Activity,
  FolderOpen,
  Eye,
  Copy,
} from 'lucide-react';
import { useApp } from '../context/AppContext';
import { DownloadStatus, MediaType } from '../types';
import { formatBytes, formatSpeed, formatEta } from '../utils/formatters';
import { StatusBadge, MediaTypeBadge } from '../components/Badges';
import { GradientProgressBar } from '../components/GradientProgressBar';
import { EmptyState } from '../components/EmptyState';

export const DownloadsScreen: React.FC = () => {
  const {
    tasks,
    pauseTask,
    resumeTask,
    cancelTask,
    retryTask,
    deleteTask,
    clearFinishedDownloads,
    setIsLogsDialogOpen,
    setErrorDialog,
    setPlaybackItem,
    copyToClipboard,
    setActiveTab,
  } = useApp();

  const activeTasks = tasks.filter((t) => t.status === DownloadStatus.DOWNLOADING);
  const queuedTasks = tasks.filter(
    (t) => t.status === DownloadStatus.QUEUED || t.status === DownloadStatus.ANALYZING || t.status === DownloadStatus.PROCESSING
  );

  const totalSpeed = activeTasks.reduce((acc, t) => acc + t.speedBytesPerSec, 0);

  return (
    <div className="space-y-5 pb-16">
      {/* Metrics Summary Card */}
      <div className="rounded-3xl bg-[#2B2930] border border-[#49454F]/70 p-5 shadow-xl">
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 pb-4 border-b border-[#49454F]/60">
          {/* Active Metric */}
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-2xl bg-[#D0BCFF]/15 flex items-center justify-center text-[#D0BCFF]">
              <Download className="w-5 h-5" />
            </div>
            <div>
              <span className="text-[11px] font-bold uppercase text-[#938F99]">Active Downloads</span>
              <p className="text-xl font-extrabold text-[#E6E1E5]">{activeTasks.length}</p>
            </div>
          </div>

          {/* Queued Metric */}
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-2xl bg-[#80D8FF]/15 flex items-center justify-center text-[#80D8FF]">
              <Activity className="w-5 h-5" />
            </div>
            <div>
              <span className="text-[11px] font-bold uppercase text-[#938F99]">Queued / Staged</span>
              <p className="text-xl font-extrabold text-[#E6E1E5]">{queuedTasks.length}</p>
            </div>
          </div>

          {/* Live Speed Metric */}
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-2xl bg-[#4ADE80]/15 flex items-center justify-center text-[#4ADE80]">
              <Activity className="w-5 h-5" />
            </div>
            <div>
              <span className="text-[11px] font-bold uppercase text-[#938F99]">Total Downlink Speed</span>
              <p className="text-xl font-extrabold font-mono text-[#4ADE80]">
                {formatSpeed(totalSpeed)}
              </p>
            </div>
          </div>
        </div>

        {/* Global Task Actions */}
        <div className="flex flex-wrap items-center justify-between gap-2 pt-3">
          <span className="text-xs text-[#938F99]">
            Total Task Queue: {tasks.length} {tasks.length === 1 ? 'item' : 'items'}
          </span>

          <div className="flex items-center gap-2">
            <button
              onClick={() => setIsLogsDialogOpen(true)}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-[#1C1B1F] hover:bg-[#4A4458] text-[#CAC4D0] hover:text-[#E6E1E5] text-xs font-semibold transition-all border border-[#49454F]/50"
            >
              <Terminal className="w-3.5 h-3.5 text-[#D0BCFF]" />
              <span>Engine Logs</span>
            </button>

            <button
              onClick={clearFinishedDownloads}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-[#1C1B1F] hover:bg-[#F2B8B5]/20 text-[#CAC4D0] hover:text-[#F2B8B5] text-xs font-semibold transition-all border border-[#49454F]/50"
            >
              <Trash2 className="w-3.5 h-3.5" />
              <span>Clear Finished</span>
            </button>
          </div>
        </div>
      </div>

      {/* Task List */}
      {tasks.length === 0 ? (
        <EmptyState
          icon={Download}
          title="No Active Downloads"
          subtitle="Your download queue is empty. Extract any video or audio stream from the Home tab to start downloading."
          actionText="Explore & Add Downloads"
          onAction={() => setActiveTab(0 as any)}
        />
      ) : (
        <div className="space-y-3">
          {tasks.map((task) => {
            const isDownloading = task.status === DownloadStatus.DOWNLOADING;
            const isPaused = task.status === DownloadStatus.PAUSED;
            const isCompleted = task.status === DownloadStatus.COMPLETED;
            const isFailed = task.status === DownloadStatus.FAILED;

            return (
              <div
                key={task.id}
                className="p-4 rounded-2xl bg-[#2B2930] border border-[#49454F]/60 shadow-md space-y-3 transition-all hover:border-[#938F99]/60"
              >
                {/* Header Row */}
                <div className="flex gap-3 items-start">
                  {/* Thumbnail */}
                  <div className="relative w-20 sm:w-24 aspect-video rounded-xl overflow-hidden bg-black shrink-0 border border-[#49454F]/50">
                    <img
                      src={task.thumbnail}
                      alt={task.title}
                      className="w-full h-full object-cover"
                      referrerPolicy="no-referrer"
                    />
                    {isDownloading && (
                      <div className="absolute inset-0 bg-black/40 flex items-center justify-center">
                        <Download className="w-5 h-5 text-[#D0BCFF] animate-bounce" />
                      </div>
                    )}
                  </div>

                  {/* Title & Metadata */}
                  <div className="flex-1 min-w-0">
                    <div className="flex flex-wrap items-center gap-2 mb-1">
                      <MediaTypeBadge type={task.mediaType} />
                      <StatusBadge status={task.status} />
                      {task.isPlaylist && (
                        <span className="text-[10px] font-mono text-[#D0BCFF] bg-[#4A4458] px-1.5 py-0.5 rounded">
                          Item {task.playlistIndex}/{task.playlistTotal}
                        </span>
                      )}
                    </div>

                    <h2 className="text-xs sm:text-sm font-bold text-[#E6E1E5] truncate">
                      {task.title}
                    </h2>
                    <p className="text-[11px] text-[#938F99] truncate mt-0.5 font-mono">
                      {task.outputPath}
                    </p>
                  </div>
                </div>

                {/* Progress Bar & Rate stats */}
                <div className="space-y-1.5 bg-[#1C1B1F] p-3 rounded-xl border border-[#49454F]/40">
                  <div className="flex items-center justify-between text-xs">
                    <span className="font-bold text-[#E6E1E5]">{task.progress.toFixed(1)}%</span>
                    <div className="flex items-center gap-3 text-[11px] text-[#CAC4D0] font-mono">
                      <span>
                        {formatBytes(task.downloadedBytes)} / {formatBytes(task.totalBytes)}
                      </span>
                      {isDownloading && (
                        <>
                          <span className="text-[#4ADE80] font-bold">
                            {formatSpeed(task.speedBytesPerSec)}
                          </span>
                          <span className="text-[#80D8FF]">
                            ETA: {formatEta(task.etaSeconds)}
                          </span>
                        </>
                      )}
                    </div>
                  </div>

                  <GradientProgressBar progress={task.progress} animated={isDownloading} />
                </div>

                {/* Action Controls Toolbar */}
                <div className="flex flex-wrap items-center justify-between gap-2 pt-1">
                  <span className="text-[11px] text-[#938F99]">{task.formatDescription}</span>

                  <div className="flex items-center gap-2">
                    {/* Pause / Resume */}
                    {isDownloading && (
                      <button
                        onClick={() => pauseTask(task.id)}
                        className="flex items-center gap-1 px-3 py-1.5 rounded-xl bg-[#4A4458] hover:bg-[#938F99]/40 text-[#E6E1E5] text-xs font-semibold transition-colors"
                      >
                        <Pause className="w-3.5 h-3.5 text-[#FFB74D]" />
                        <span>Pause</span>
                      </button>
                    )}

                    {isPaused && (
                      <button
                        onClick={() => resumeTask(task.id)}
                        className="flex items-center gap-1 px-3 py-1.5 rounded-xl bg-[#D0BCFF] text-[#381E72] hover:bg-[#E8DEF8] text-xs font-bold transition-colors"
                      >
                        <Play className="w-3.5 h-3.5 fill-current" />
                        <span>Resume</span>
                      </button>
                    )}

                    {/* Retry on fail */}
                    {isFailed && (
                      <>
                        <button
                          onClick={() => retryTask(task.id)}
                          className="flex items-center gap-1 px-3 py-1.5 rounded-xl bg-[#D0BCFF] text-[#381E72] text-xs font-bold transition-colors"
                        >
                          <RotateCcw className="w-3.5 h-3.5" />
                          <span>Retry</span>
                        </button>
                        <button
                          onClick={() =>
                            setErrorDialog({
                              title: 'Download Task Error',
                              reason: task.errorMessage || 'Network stream interrupted during FFmpeg muxing phase.',
                              suggestedAction: 'Verify active network connection and ensure yt-dlp binary is up-to-date.',
                              technicalDetails: task.detailedLogs,
                            })
                          }
                          className="flex items-center gap-1 px-3 py-1.5 rounded-xl bg-[#F2B8B5]/20 text-[#F2B8B5] text-xs font-semibold transition-colors"
                        >
                          <AlertTriangle className="w-3.5 h-3.5" />
                          <span>Diagnostics</span>
                        </button>
                      </>
                    )}

                    {/* Play media if completed */}
                    {isCompleted && (
                      <button
                        onClick={() =>
                          setPlaybackItem({
                            title: task.title,
                            url: task.mediaUrl || 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4',
                            thumbnail: task.thumbnail,
                            mediaType: task.mediaType,
                          })
                        }
                        className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-[#D0BCFF] text-[#381E72] hover:bg-[#E8DEF8] text-xs font-bold transition-colors shadow-sm"
                      >
                        <Play className="w-3.5 h-3.5 fill-current" />
                        <span>Preview / Play</span>
                      </button>
                    )}

                    {/* Cancel button if active */}
                    {(isDownloading || isPaused) && (
                      <button
                        onClick={() => cancelTask(task.id)}
                        className="p-1.5 rounded-lg text-[#938F99] hover:text-[#F2B8B5] hover:bg-[#F2B8B5]/10 transition-colors"
                        title="Cancel download"
                      >
                        <XCircle className="w-4 h-4" />
                      </button>
                    )}

                    {/* Delete button if finished / failed / cancelled */}
                    {(isCompleted || isFailed || task.status === DownloadStatus.CANCELLED) && (
                      <button
                        onClick={() => deleteTask(task.id)}
                        className="p-1.5 rounded-lg text-[#938F99] hover:text-[#F2B8B5] hover:bg-[#F2B8B5]/10 transition-colors"
                        title="Delete task record"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    )}
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
};
