import React, { createContext, useContext, useState, useEffect, useCallback, useRef } from 'react';
import {
  DownloadTask,
  DownloadHistoryItem,
  AppSettings,
  LogEntry,
  DownloadStatus,
  MediaType,
  OutputContainer,
  AudioFormat,
  VideoQualityPreset,
  AudioQualityPreset,
  LogLevel,
  EngineState,
  NavigationTab,
  YtDlpStatus,
  FFmpegStatus,
  EngineDiagnosticError,
  MediaMetadata,
} from '../types';
import { FilenameFormatter } from '../utils/filenameFormatter';

interface StartDownloadParams {
  metadata: MediaMetadata;
  mediaType: MediaType;
  formatId: string;
  formatDescription: string;
  outputContainer: string;
  audioFormat?: string;
  audioBitrate?: number;
  embedSubs?: boolean;
  embedThumbnail?: boolean;
  subtitleLangs?: string;
  selectedPlaylistIndices?: number[];
}

interface AppContextType {
  activeTab: NavigationTab;
  setActiveTab: (tab: NavigationTab) => void;
  tasks: DownloadTask[];
  history: DownloadHistoryItem[];
  settings: AppSettings;
  logs: LogEntry[];
  ytdlpStatus: YtDlpStatus;
  ffmpegStatus: FFmpegStatus;
  deviceAbi: string;
  isUpdatingYtDlp: boolean;
  isUpdatingFFmpeg: boolean;
  isCheckingUpdates: boolean;
  updateCheckMessage: string | null;
  ytdlpUpdateProgress: number;
  ffmpegUpdateProgress: number;
  isEngineSetupOpen: boolean;
  setIsEngineSetupOpen: (open: boolean) => void;
  isLogsDialogOpen: boolean;
  setIsLogsDialogOpen: (open: boolean) => void;
  errorDialog: EngineDiagnosticError | null;
  setErrorDialog: (error: EngineDiagnosticError | null) => void;
  playbackItem: { title: string; url: string; thumbnail?: string; mediaType: MediaType } | null;
  setPlaybackItem: (item: { title: string; url: string; thumbnail?: string; mediaType: MediaType } | null) => void;
  notification: string | null;
  
  // Actions
  startDownload: (params: StartDownloadParams) => void;
  pauseTask: (id: string) => void;
  resumeTask: (id: string) => void;
  cancelTask: (id: string) => void;
  retryTask: (id: string) => void;
  deleteTask: (id: string) => void;
  clearFinishedDownloads: () => void;
  clearHistory: () => void;
  deleteHistoryItem: (id: string) => void;
  updateSettings: (newSettings: Partial<AppSettings>) => void;
  resetSettings: () => void;
  checkForEngineUpdates: () => Promise<void>;
  updateYtDlpBinary: () => Promise<void>;
  installOrUpdateFFmpeg: () => Promise<void>;
  addLog: (level: LogLevel, tag: string, message: string, taskId?: string) => void;
  clearLogs: () => void;
  copyToClipboard: (text: string, label?: string) => void;
  showNotification: (msg: string) => void;
}

const defaultSettings: AppSettings = {
  maxConcurrentDownloads: 3,
  defaultVideoQuality: VideoQualityPreset.BEST,
  defaultAudioQuality: AudioQualityPreset.BEST,
  defaultContainer: OutputContainer.MP4,
  defaultAudioFormat: AudioFormat.MP3,
  downloadLocationUri: '',
  downloadLocationDisplayName: 'Default Storage (App Isolated / Internal)',
  resumeDownloads: true,
  retryCount: 3,
  customYtDlpArgs: '',
  verboseLogging: true,
  cookiesFilePath: '',
  sanitizeFilenames: true,
  organizeByUploader: false,
  embedSubtitles: false,
  embedThumbnail: true,
  filenameTemplate: '%(title)s.%(ext)s',
  autoStartDownloads: true,
  confirmDelete: true,
  darkTheme: true,
};

const AppContext = createContext<AppContextType | undefined>(undefined);

export const AppProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [activeTab, setActiveTab] = useState<NavigationTab>(NavigationTab.HOME);

  // Load state from localStorage or defaults
  const [settings, setSettings] = useState<AppSettings>(() => {
    try {
      const saved = localStorage.getItem('transcode_settings');
      return saved ? { ...defaultSettings, ...JSON.parse(saved) } : defaultSettings;
    } catch {
      return defaultSettings;
    }
  });

  const [tasks, setTasks] = useState<DownloadTask[]>(() => {
    try {
      const saved = localStorage.getItem('transcode_tasks');
      return saved ? JSON.parse(saved) : [];
    } catch {
      return [];
    }
  });

  const [history, setHistory] = useState<DownloadHistoryItem[]>(() => {
    try {
      const saved = localStorage.getItem('transcode_history');
      if (saved) return JSON.parse(saved);
      // Seed initial history item for clean preview
      return [
        {
          id: 'hist-1',
          title: 'Cinematic 4K Architecture & Nature Film',
          url: 'https://vimeo.com/76979871',
          thumbnail: 'https://images.unsplash.com/photo-1536240478700-b869070f9279?w=800&auto=format&fit=crop&q=80',
          filePath: 'Video/Cinematic 4K Architecture & Nature Film.mp4',
          fileSize: 45200000,
          mediaType: MediaType.VIDEO,
          formatDescription: 'Full HD 1080p60 (MP4)',
          completedTimestamp: Date.now() - 3600000 * 2,
          uploader: 'Vivid Studio',
          durationSeconds: 432,
          mediaUrl: 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4',
        },
      ];
    } catch {
      return [];
    }
  });

  const [logs, setLogs] = useState<LogEntry[]>([]);
  const [ytdlpStatus, setYtdlpStatus] = useState<YtDlpStatus>({
    isReady: true,
    version: '2025.02.19',
    state: EngineState.READY,
  });
  const [ffmpegStatus, setFfmpegStatus] = useState<FFmpegStatus>({
    isAvailable: true,
    version: '7.1-static',
    ffprobeVersion: '7.1-static',
    state: EngineState.READY,
  });
  const [deviceAbi, setDeviceAbi] = useState('linux-x86_64 / WebAssembly');

  const [isUpdatingYtDlp, setIsUpdatingYtDlp] = useState(false);
  const [isUpdatingFFmpeg, setIsUpdatingFFmpeg] = useState(false);
  const [isCheckingUpdates, setIsCheckingUpdates] = useState(false);
  const [updateCheckMessage, setUpdateCheckMessage] = useState<string | null>(null);
  const [ytdlpUpdateProgress, setYtdlpUpdateProgress] = useState(0);
  const [ffmpegUpdateProgress, setFfmpegUpdateProgress] = useState(0);

  const [isEngineSetupOpen, setIsEngineSetupOpen] = useState(false);
  const [isLogsDialogOpen, setIsLogsDialogOpen] = useState(false);
  const [errorDialog, setErrorDialog] = useState<EngineDiagnosticError | null>(null);
  const [playbackItem, setPlaybackItem] = useState<{ title: string; url: string; thumbnail?: string; mediaType: MediaType } | null>(null);
  const [notification, setNotification] = useState<string | null>(null);

  const activeDownloadsRef = useRef<{ [taskId: string]: number }>({});

  const showNotification = useCallback((msg: string) => {
    setNotification(msg);
    setTimeout(() => {
      setNotification((curr) => (curr === msg ? null : curr));
    }, 3500);
  }, []);

  const copyToClipboard = useCallback((text: string, label: string = 'Text') => {
    if (navigator.clipboard) {
      navigator.clipboard.writeText(text);
      showNotification(`${label} copied to clipboard!`);
    }
  }, [showNotification]);

  const addLog = useCallback((level: LogLevel, tag: string, message: string, taskId?: string) => {
    const newEntry: LogEntry = {
      logId: Date.now() + Math.floor(Math.random() * 1000),
      taskId: taskId || null,
      level,
      tag,
      message,
      timestamp: Date.now(),
    };
    setLogs((prev) => [newEntry, ...prev].slice(0, 500));
  }, []);

  // Save changes to localStorage
  useEffect(() => {
    try {
      localStorage.setItem('transcode_settings', JSON.stringify(settings));
    } catch (e) {
      console.error(e);
    }
  }, [settings]);

  useEffect(() => {
    try {
      localStorage.setItem('transcode_tasks', JSON.stringify(tasks));
    } catch (e) {
      console.error(e);
    }
  }, [tasks]);

  useEffect(() => {
    try {
      localStorage.setItem('transcode_history', JSON.stringify(history));
    } catch (e) {
      console.error(e);
    }
  }, [history]);

  // Initial engine check from backend
  useEffect(() => {
    fetch('/api/engines/status')
      .then((res) => res.json())
      .then((data) => {
        if (data.ytdlp) setYtdlpStatus(data.ytdlp);
        if (data.ffmpeg) setFfmpegStatus(data.ffmpeg);
        if (data.deviceAbi) setDeviceAbi(data.deviceAbi);
      })
      .catch((err) => {
        console.warn('Backend engine status check fallback:', err);
      });

    fetch('/api/logs')
      .then((res) => res.json())
      .then((data) => {
        if (Array.isArray(data) && data.length > 0) {
          setLogs(data);
        }
      })
      .catch(() => {});
  }, []);

  const updateSettings = useCallback((newSettings: Partial<AppSettings>) => {
    setSettings((prev) => ({ ...prev, ...newSettings }));
    addLog(LogLevel.INFO, 'AppSettings', 'Application settings updated');
  }, [addLog]);

  const resetSettings = useCallback(() => {
    setSettings(defaultSettings);
    addLog(LogLevel.INFO, 'AppSettings', 'Application settings reset to factory defaults');
    showNotification('Settings restored to defaults');
  }, [addLog, showNotification]);

  const checkForEngineUpdates = useCallback(async () => {
    setIsCheckingUpdates(true);
    setUpdateCheckMessage(null);
    addLog(LogLevel.INFO, 'EngineSetup', 'Checking remote releases for yt-dlp and FFmpeg...');
    try {
      const res = await fetch('/api/engines/update', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ engine: 'all' }),
      });
      const data = await res.json();
      setUpdateCheckMessage(`✓ ${data.message || 'All engines are up to date and verified.'}`);
      setYtdlpStatus((s) => ({ ...s, isReady: true, state: EngineState.READY }));
      setFfmpegStatus((s) => ({ ...s, isAvailable: true, state: EngineState.READY }));
      addLog(LogLevel.INFO, 'EngineSetup', 'Engine update check completed: all components healthy.');
    } catch (e: any) {
      setUpdateCheckMessage('⚠️ Connection error checking updates. Local binaries remain active.');
      addLog(LogLevel.WARNING, 'EngineSetup', `Engine update check error: ${e.message}`);
    } finally {
      setIsCheckingUpdates(false);
    }
  }, [addLog]);

  const updateYtDlpBinary = useCallback(async () => {
    setIsUpdatingYtDlp(true);
    setYtdlpUpdateProgress(0);
    addLog(LogLevel.INFO, 'YtDlpManager', 'Starting yt-dlp binary reinstall/update sequence...');

    for (let p = 0; p <= 100; p += 20) {
      setYtdlpUpdateProgress(p);
      await new Promise((r) => setTimeout(r, 200));
    }

    setYtdlpStatus({
      isReady: true,
      version: '2025.02.19',
      state: EngineState.READY,
    });
    setIsUpdatingYtDlp(false);
    addLog(LogLevel.INFO, 'YtDlpManager', 'yt-dlp core binary updated and verified executable.');
    showNotification('yt-dlp binary updated successfully');
  }, [addLog, showNotification]);

  const installOrUpdateFFmpeg = useCallback(async () => {
    setIsUpdatingFFmpeg(true);
    setFFmpegUpdateProgress(0);
    addLog(LogLevel.INFO, 'FFmpegManager', 'Downloading static FFmpeg build for current architecture...');

    for (let p = 0; p <= 100; p += 25) {
      setFFmpegUpdateProgress(p);
      await new Promise((r) => setTimeout(r, 250));
    }

    setFfmpegStatus({
      isAvailable: true,
      version: '7.1-static',
      ffprobeVersion: '7.1-static',
      state: EngineState.READY,
    });
    setIsUpdatingFFmpeg(false);
    addLog(LogLevel.INFO, 'FFmpegManager', 'FFmpeg binaries unpacked and configured successfully.');
    showNotification('FFmpeg tools verified and active');
  }, [addLog, showNotification]);

  // Download simulation and task lifecycle runner
  const runTaskLoop = useCallback((taskId: string, targetBytes: number) => {
    if (activeDownloadsRef.current[taskId]) return;

    addLog(LogLevel.INFO, 'DownloadEngine', `Allocating download stream for task ${taskId}`, taskId);

    const interval = window.setInterval(() => {
      setTasks((prevTasks) => {
        const task = prevTasks.find((t) => t.id === taskId);
        if (!task || task.status === DownloadStatus.PAUSED || task.status === DownloadStatus.CANCELLED) {
          clearInterval(interval);
          delete activeDownloadsRef.current[taskId];
          return prevTasks;
        }

        if (task.status === DownloadStatus.FAILED) {
          clearInterval(interval);
          delete activeDownloadsRef.current[taskId];
          return prevTasks;
        }

        const stepSpeed = Math.floor(1800000 + Math.random() * 3200000); // 1.8 - 5.0 MB/s
        const stepBytes = Math.floor(stepSpeed * 0.25);
        const newDownloaded = Math.min(task.downloadedBytes + stepBytes, targetBytes);
        const newProgress = Math.min(100, Number(((newDownloaded / targetBytes) * 100).toFixed(1)));
        const remainingBytes = targetBytes - newDownloaded;
        const eta = stepSpeed > 0 ? Math.ceil(remainingBytes / stepSpeed) : 0;

        if (newProgress >= 100) {
          clearInterval(interval);
          delete activeDownloadsRef.current[taskId];

          addLog(LogLevel.INFO, 'DownloadEngine', `Download completed successfully for: ${task.title}`, taskId);

          // Add to history
          const historyItem: DownloadHistoryItem = {
            id: `hist-${Date.now()}-${Math.floor(Math.random() * 1000)}`,
            title: task.title,
            url: task.url,
            thumbnail: task.thumbnail,
            filePath: task.outputPath,
            fileSize: targetBytes,
            mediaType: task.mediaType,
            formatDescription: task.formatDescription,
            completedTimestamp: Date.now(),
            uploader: 'Downloaded Source',
            durationSeconds: 300,
            mediaUrl: task.mediaUrl || 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4',
          };

          setHistory((prev) => [historyItem, ...prev]);

          return prevTasks.map((t) =>
            t.id === taskId
              ? {
                  ...t,
                  status: DownloadStatus.COMPLETED,
                  progress: 100,
                  downloadedBytes: targetBytes,
                  speedBytesPerSec: 0,
                  etaSeconds: 0,
                  completedTimestamp: Date.now(),
                }
              : t
          );
        }

        return prevTasks.map((t) =>
          t.id === taskId
            ? {
                ...t,
                status: DownloadStatus.DOWNLOADING,
                progress: newProgress,
                downloadedBytes: newDownloaded,
                speedBytesPerSec: stepSpeed,
                etaSeconds: eta,
              }
            : t
        );
      });
    }, 250);

    activeDownloadsRef.current[taskId] = interval;
  }, [addLog]);

  const startDownload = useCallback((params: StartDownloadParams) => {
    const {
      metadata,
      mediaType,
      formatId,
      formatDescription,
      outputContainer,
      audioBitrate,
      embedSubs = false,
      embedThumbnail = true,
      subtitleLangs = 'en',
      selectedPlaylistIndices,
    } = params;

    const subfolder = mediaType === MediaType.AUDIO ? 'Music' : 'Video';
    const ext = mediaType === MediaType.AUDIO ? (params.audioFormat || 'mp3') : outputContainer || 'mp4';

    if (metadata.isPlaylist && selectedPlaylistIndices && selectedPlaylistIndices.length > 0) {
      const selectedEntries = metadata.playlistEntries.filter((_, idx) => selectedPlaylistIndices.includes(idx));

      selectedEntries.forEach((entry, idx) => {
        const taskId = `task-${Date.now()}-${idx}-${Math.floor(Math.random() * 1000)}`;
        const filename = FilenameFormatter.format(
          settings.filenameTemplate,
          entry.title,
          entry.uploader || metadata.uploader,
          entry.id,
          ext
        );
        const outputPath = `${subfolder}/${filename}`;
        const totalBytes = Math.floor(18000000 + Math.random() * 25000000);

        const newTask: DownloadTask = {
          id: taskId,
          url: entry.url,
          title: entry.title,
          thumbnail: entry.thumbnail || metadata.thumbnail,
          status: DownloadStatus.DOWNLOADING,
          progress: 0,
          downloadedBytes: 0,
          totalBytes,
          speedBytesPerSec: 0,
          etaSeconds: 0,
          formatId,
          formatDescription: `${formatDescription} (Playlist ${idx + 1}/${selectedEntries.length})`,
          mediaType,
          outputPath,
          mediaUrl: 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4',
          detailedLogs: `[yt-dlp] Extracting playlist item ${idx + 1}...\n[download] Destination: ${outputPath}\n`,
          createdTimestamp: Date.now(),
          isPlaylist: true,
          playlistIndex: idx + 1,
          playlistTotal: selectedEntries.length,
          audioBitrate: audioBitrate || 320,
          targetContainer: ext,
          embedSubs,
          embedThumbnail,
          subtitleLangs,
        };

        setTasks((prev) => [newTask, ...prev]);
        addLog(LogLevel.INFO, 'DownloadManager', `Queued playlist task ${idx + 1}/${selectedEntries.length}: ${entry.title}`, taskId);
        setTimeout(() => runTaskLoop(taskId, totalBytes), idx * 400);
      });

      showNotification(`Started downloading ${selectedEntries.length} items from playlist!`);
    } else {
      const taskId = `task-${Date.now()}-${Math.floor(Math.random() * 1000)}`;
      const filename = FilenameFormatter.format(
        settings.filenameTemplate,
        metadata.title,
        metadata.uploader,
        metadata.id,
        ext
      );
      const outputPath = `${subfolder}/${filename}`;
      const totalBytes =
        metadata.formats.find((f) => f.formatId === formatId)?.filesize ||
        Math.floor(32000000 + Math.random() * 20000000);

      const newTask: DownloadTask = {
        id: taskId,
        url: metadata.webpageUrl,
        title: metadata.title,
        thumbnail: metadata.thumbnail,
        status: DownloadStatus.DOWNLOADING,
        progress: 0,
        downloadedBytes: 0,
        totalBytes,
        speedBytesPerSec: 0,
        etaSeconds: 0,
        formatId,
        formatDescription,
        mediaType,
        outputPath,
        mediaUrl: metadata.directDownloadUrl || 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4',
        detailedLogs: `[yt-dlp] Initializing format selection ${formatId}...\n[download] Output file: ${outputPath}\n[ffmpeg] Transcoder mux stream ready\n`,
        createdTimestamp: Date.now(),
        isPlaylist: false,
        playlistIndex: 0,
        playlistTotal: 0,
        audioBitrate: audioBitrate || 320,
        targetContainer: ext,
        embedSubs,
        embedThumbnail,
        subtitleLangs,
      };

      setTasks((prev) => [newTask, ...prev]);
      addLog(LogLevel.INFO, 'DownloadManager', `Started downloading task: ${metadata.title}`, taskId);
      runTaskLoop(taskId, totalBytes);
      showNotification(`Download started: ${metadata.title}`);
    }

    setActiveTab(NavigationTab.DOWNLOADS);
  }, [settings.filenameTemplate, addLog, runTaskLoop, showNotification]);

  const pauseTask = useCallback((id: string) => {
    if (activeDownloadsRef.current[id]) {
      clearInterval(activeDownloadsRef.current[id]);
      delete activeDownloadsRef.current[id];
    }
    setTasks((prev) =>
      prev.map((t) =>
        t.id === id
          ? {
              ...t,
              status: DownloadStatus.PAUSED,
              speedBytesPerSec: 0,
              etaSeconds: 0,
            }
          : t
      )
    );
    addLog(LogLevel.INFO, 'DownloadEngine', `Download paused by user for task ${id}`, id);
  }, [addLog]);

  const resumeTask = useCallback((id: string) => {
    const task = tasks.find((t) => t.id === id);
    if (!task) return;

    setTasks((prev) =>
      prev.map((t) =>
        t.id === id
          ? {
              ...t,
              status: DownloadStatus.DOWNLOADING,
            }
          : t
      )
    );
    addLog(LogLevel.INFO, 'DownloadEngine', `Resuming download stream for task ${id}`, id);
    runTaskLoop(id, task.totalBytes);
  }, [tasks, addLog, runTaskLoop]);

  const cancelTask = useCallback((id: string) => {
    if (activeDownloadsRef.current[id]) {
      clearInterval(activeDownloadsRef.current[id]);
      delete activeDownloadsRef.current[id];
    }
    setTasks((prev) =>
      prev.map((t) =>
        t.id === id
          ? {
              ...t,
              status: DownloadStatus.CANCELLED,
              speedBytesPerSec: 0,
              etaSeconds: 0,
            }
          : t
      )
    );
    addLog(LogLevel.WARNING, 'DownloadEngine', `Download cancelled for task ${id}`, id);
  }, [addLog]);

  const retryTask = useCallback((id: string) => {
    const task = tasks.find((t) => t.id === id);
    if (!task) return;

    setTasks((prev) =>
      prev.map((t) =>
        t.id === id
          ? {
              ...t,
              status: DownloadStatus.DOWNLOADING,
              errorMessage: null,
            }
          : t
      )
    );
    addLog(LogLevel.INFO, 'DownloadEngine', `Retrying task ${id}`, id);
    runTaskLoop(id, task.totalBytes);
  }, [tasks, addLog, runTaskLoop]);

  const deleteTask = useCallback((id: string) => {
    if (activeDownloadsRef.current[id]) {
      clearInterval(activeDownloadsRef.current[id]);
      delete activeDownloadsRef.current[id];
    }
    setTasks((prev) => prev.filter((t) => t.id !== id));
    addLog(LogLevel.INFO, 'DownloadManager', `Deleted task record ${id}`);
    showNotification('Download task removed');
  }, [addLog, showNotification]);

  const clearFinishedDownloads = useCallback(() => {
    setTasks((prev) => prev.filter((t) => t.status === DownloadStatus.DOWNLOADING || t.status === DownloadStatus.PAUSED || t.status === DownloadStatus.QUEUED));
    addLog(LogLevel.INFO, 'DownloadManager', 'Cleared completed, failed, and cancelled tasks');
    showNotification('Cleaned up finished tasks');
  }, [addLog, showNotification]);

  const clearHistory = useCallback(() => {
    setHistory([]);
    addLog(LogLevel.INFO, 'HistoryManager', 'Cleared all download history records');
    showNotification('History cleared');
  }, [addLog, showNotification]);

  const deleteHistoryItem = useCallback((id: string) => {
    setHistory((prev) => prev.filter((h) => h.id !== id));
    addLog(LogLevel.INFO, 'HistoryManager', `Deleted history item ${id}`);
    showNotification('History item removed');
  }, [addLog, showNotification]);

  const clearLogs = useCallback(() => {
    setLogs([]);
    showNotification('System logs cleared');
  }, [showNotification]);

  return (
    <AppContext.Provider
      value={{
        activeTab,
        setActiveTab,
        tasks,
        history,
        settings,
        logs,
        ytdlpStatus,
        ffmpegStatus,
        deviceAbi,
        isUpdatingYtDlp,
        isUpdatingFFmpeg,
        isCheckingUpdates,
        updateCheckMessage,
        ytdlpUpdateProgress,
        ffmpegUpdateProgress,
        isEngineSetupOpen,
        setIsEngineSetupOpen,
        isLogsDialogOpen,
        setIsLogsDialogOpen,
        errorDialog,
        setErrorDialog,
        playbackItem,
        setPlaybackItem,
        notification,
        startDownload,
        pauseTask,
        resumeTask,
        cancelTask,
        retryTask,
        deleteTask,
        clearFinishedDownloads,
        clearHistory,
        deleteHistoryItem,
        updateSettings,
        resetSettings,
        checkForEngineUpdates,
        updateYtDlpBinary,
        installOrUpdateFFmpeg,
        addLog,
        clearLogs,
        copyToClipboard,
        showNotification,
      }}
    >
      {children}
    </AppContext.Provider>
  );
};

export const useApp = () => {
  const context = useContext(AppContext);
  if (!context) {
    throw new Error('useApp must be used within an AppProvider');
  }
  return context;
};
