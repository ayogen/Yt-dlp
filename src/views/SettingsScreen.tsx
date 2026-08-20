import React, { useState } from 'react';
import {
  Sliders,
  Folder,
  FolderOpen,
  Download,
  Wand2,
  Cookie,
  FileText,
  Cpu,
  RefreshCw,
  RotateCcw,
  CheckCircle2,
  HardDrive,
  Check,
  ShieldCheck,
} from 'lucide-react';
import { useApp } from '../context/AppContext';
import { OutputContainer, AudioFormat, EngineState } from '../types';
import { FilenameFormatter } from '../utils/filenameFormatter';
import { EngineStateBadge } from '../components/Badges';

export const SettingsScreen: React.FC = () => {
  const {
    settings,
    updateSettings,
    resetSettings,
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
    setIsLogsDialogOpen,
    copyToClipboard,
    showNotification,
  } = useApp();

  const [filenameTemplateInput, setFilenameTemplateInput] = useState(settings.filenameTemplate);
  const [cookiesInput, setCookiesInput] = useState(settings.cookiesFilePath);
  const [customArgsInput, setCustomArgsInput] = useState(settings.customYtDlpArgs);

  const previewFormattedName = FilenameFormatter.format(
    filenameTemplateInput,
    'Rick Astley - Never Gonna Give You Up',
    'RickAstleyVEVO',
    'dQw4w9WgXcQ',
    settings.defaultContainer || 'mp4'
  );

  const isSafActive = Boolean(settings.downloadLocationUri);

  const handleSelectFolder = () => {
    const fakeFolderName = 'Downloads/Transcode_Media';
    updateSettings({
      downloadLocationUri: `content://com.android.externalstorage.documents/tree/primary%3A${fakeFolderName}`,
      downloadLocationDisplayName: fakeFolderName,
    });
    showNotification(`Storage root folder configured: ${fakeFolderName}`);
  };

  const handleResetFolder = () => {
    updateSettings({
      downloadLocationUri: '',
      downloadLocationDisplayName: 'Default Storage (App Isolated / Internal)',
    });
    showNotification('Download directory reset to default');
  };

  return (
    <div className="space-y-6 pb-20">
      {/* Section 1: Media Engines */}
      <div className="space-y-3">
        <div className="flex items-center gap-2 text-xs font-bold uppercase tracking-wider text-[#938F99]">
          <Sliders className="w-4 h-4 text-[#D0BCFF]" />
          <span>Media Engines</span>
        </div>

        <div className="rounded-3xl bg-[#2B2930] border border-[#49454F]/70 p-5 shadow-xl space-y-4">
          {/* Header & Update Check */}
          <div className="flex flex-wrap items-center justify-between gap-3 pb-4 border-b border-[#49454F]/60">
            <div>
              <h3 className="text-sm font-bold text-[#E6E1E5]">Engine Runtime Status</h3>
              <div className="flex items-center gap-1.5 mt-0.5">
                <Cpu className="w-3.5 h-3.5 text-[#D0BCFF]" />
                <span className="text-xs font-mono font-semibold text-[#D0BCFF]">
                  ABI: {deviceAbi}
                </span>
              </div>
            </div>

            <button
              onClick={() => checkForEngineUpdates()}
              disabled={isCheckingUpdates || isUpdatingYtDlp || isUpdatingFFmpeg}
              className="flex items-center gap-1.5 px-4 py-2 rounded-xl bg-[#D0BCFF] text-[#381E72] hover:bg-[#E8DEF8] font-bold text-xs transition-all shadow-sm disabled:opacity-50"
            >
              <RefreshCw className={`w-3.5 h-3.5 ${isCheckingUpdates ? 'animate-spin' : ''}`} />
              <span>{isCheckingUpdates ? 'Checking...' : 'Check for Updates'}</span>
            </button>
          </div>

          {/* Update result message */}
          {updateCheckMessage && (
            <div
              className={`p-3 rounded-xl text-xs font-semibold border ${
                updateCheckMessage.startsWith('✓')
                  ? 'bg-[#4ADE80]/15 border-[#4ADE80]/30 text-[#4ADE80]'
                  : 'bg-[#FFB74D]/15 border-[#FFB74D]/30 text-[#FFB74D]'
              }`}
            >
              {updateCheckMessage}
            </div>
          )}

          {/* Engine 1: yt-dlp */}
          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <div>
                <div className="flex items-center gap-2">
                  <span className="text-xs font-bold text-[#E6E1E5]">yt-dlp Core Extractor</span>
                  <EngineStateBadge state={ytdlpStatus.state} />
                </div>
                <span className="text-[11px] font-mono text-[#D0BCFF]">
                  Version: {ytdlpStatus.version}
                </span>
              </div>

              <button
                onClick={updateYtDlpBinary}
                disabled={isUpdatingYtDlp}
                className="px-3 py-1.5 rounded-xl bg-[#4A4458] hover:bg-[#938F99]/40 text-[#E6E1E5] text-xs font-semibold transition-all disabled:opacity-50"
              >
                {isUpdatingYtDlp ? 'Updating...' : ytdlpStatus.isReady ? 'Reinstall' : 'Install'}
              </button>
            </div>

            {isUpdatingYtDlp && (
              <div className="w-full bg-[#1C1B1F] h-1.5 rounded-full overflow-hidden">
                <div
                  className="bg-[#D0BCFF] h-full transition-all duration-200"
                  style={{ width: `${ytdlpUpdateProgress}%` }}
                />
              </div>
            )}
          </div>

          <div className="border-t border-[#49454F]/40" />

          {/* Engine 2: FFmpeg */}
          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <div>
                <div className="flex items-center gap-2">
                  <span className="text-xs font-bold text-[#E6E1E5]">FFmpeg Post-Processor</span>
                  <EngineStateBadge state={ffmpegStatus.state} />
                </div>
                <span className="text-[11px] font-mono text-[#D0BCFF] block">
                  FFmpeg: {ffmpegStatus.version}
                </span>
                {ffmpegStatus.ffprobeVersion && (
                  <span className="text-[10px] font-mono text-[#938F99]">
                    FFprobe: {ffmpegStatus.ffprobeVersion}
                  </span>
                )}
              </div>

              <button
                onClick={installOrUpdateFFmpeg}
                disabled={isUpdatingFFmpeg}
                className="px-3 py-1.5 rounded-xl bg-[#4A4458] hover:bg-[#938F99]/40 text-[#E6E1E5] text-xs font-semibold transition-all disabled:opacity-50"
              >
                {isUpdatingFFmpeg ? 'Installing...' : ffmpegStatus.isAvailable ? 'Reinstall' : 'Install'}
              </button>
            </div>

            {isUpdatingFFmpeg && (
              <div className="w-full bg-[#1C1B1F] h-1.5 rounded-full overflow-hidden">
                <div
                  className="bg-[#D0BCFF] h-full transition-all duration-200"
                  style={{ width: `${ffmpegUpdateProgress}%` }}
                />
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Section 2: Storage & Download Location */}
      <div className="space-y-3">
        <div className="flex items-center gap-2 text-xs font-bold uppercase tracking-wider text-[#938F99]">
          <Folder className="w-4 h-4 text-[#80D8FF]" />
          <span>Storage & Download Location</span>
        </div>

        <div className="rounded-3xl bg-[#2B2930] border border-[#49454F]/70 p-5 shadow-xl space-y-4">
          <div className="flex items-center justify-between">
            <div>
              <h3 className="text-sm font-bold text-[#E6E1E5]">Root Download Location</h3>
              <p
                className={`text-xs font-semibold mt-0.5 ${
                  isSafActive ? 'text-[#4ADE80]' : 'text-[#D0BCFF]'
                }`}
              >
                {isSafActive
                  ? `Custom SAF Root: ${settings.downloadLocationDisplayName}`
                  : 'Default Storage (App Isolated / Internal)'}
              </p>
            </div>

            <span
              className={`inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-[10px] font-bold ${
                isSafActive
                  ? 'bg-[#4ADE80]/15 text-[#4ADE80] border border-[#4ADE80]/30'
                  : 'bg-[#4A4458] text-[#CAC4D0]'
              }`}
            >
              {isSafActive ? 'SAF ACTIVE' : 'DEFAULT'}
            </span>
          </div>

          <p className="text-xs text-[#CAC4D0] leading-relaxed">
            Select one root directory. Subfolders (<code className="text-[#D0BCFF]">Video/</code>, <code className="text-[#80D8FF]">Music/</code>, <code className="text-[#4ADE80]">Subtitles/</code>) are automatically created inside your chosen directory.
          </p>

          <div className="flex items-center gap-2 pt-1">
            <button
              onClick={handleSelectFolder}
              className="flex items-center gap-1.5 px-4 py-2 rounded-xl bg-[#D0BCFF] text-[#381E72] hover:bg-[#E8DEF8] font-bold text-xs transition-colors shadow-sm"
            >
              <FolderOpen className="w-3.5 h-3.5" />
              <span>{isSafActive ? 'Change Folder' : 'Select Storage Folder'}</span>
            </button>

            {isSafActive && (
              <button
                onClick={handleResetFolder}
                className="flex items-center gap-1 px-3 py-2 rounded-xl bg-[#1C1B1F] hover:bg-[#4A4458] text-[#CAC4D0] text-xs font-semibold transition-colors border border-[#49454F]/50"
              >
                <RotateCcw className="w-3.5 h-3.5" />
                <span>Reset</span>
              </button>
            )}
          </div>

          {/* Subfolders mapping description */}
          <div className="p-3.5 rounded-2xl bg-[#141218] border border-[#49454F]/40 space-y-1">
            <span className="text-[11px] font-bold text-[#938F99] block">
              Automatic Media Subfolder Routing:
            </span>
            <div className="font-mono text-xs text-[#CAC4D0] space-y-0.5">
              <div>• <strong className="text-[#D0BCFF]">Video/</strong> (MP4, MKV, WebM high-res files)</div>
              <div>• <strong className="text-[#80D8FF]">Music/</strong> (MP3, M4A, FLAC, AAC tracks)</div>
              <div>• <strong className="text-[#4ADE80]">Subtitles/</strong> (.vtt, .srt transcriptions)</div>
            </div>
          </div>
        </div>
      </div>

      {/* Section 3: Download Configuration */}
      <div className="space-y-3">
        <div className="flex items-center gap-2 text-xs font-bold uppercase tracking-wider text-[#938F99]">
          <Download className="w-4 h-4 text-[#D0BCFF]" />
          <span>Download Configuration</span>
        </div>

        <div className="rounded-3xl bg-[#2B2930] border border-[#49454F]/70 p-5 shadow-xl space-y-5">
          {/* Max Simultaneous Downloads */}
          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <div>
                <h4 className="text-xs font-bold text-[#E6E1E5]">Simultaneous Downloads</h4>
                <p className="text-[11px] text-[#938F99]">Max active downloads processed at once</p>
              </div>
              <span className="text-base font-extrabold font-mono text-[#D0BCFF]">
                {settings.maxConcurrentDownloads}
              </span>
            </div>

            <input
              type="range"
              min={1}
              max={10}
              step={1}
              value={settings.maxConcurrentDownloads}
              onChange={(e) =>
                updateSettings({ maxConcurrentDownloads: parseInt(e.target.value, 10) })
              }
              className="w-full accent-[#D0BCFF] cursor-pointer"
            />
          </div>

          <div className="border-t border-[#49454F]/40" />

          {/* Default Video Container */}
          <div className="space-y-2">
            <h4 className="text-xs font-bold text-[#E6E1E5]">Default Video Container</h4>
            <div className="flex gap-2">
              {[OutputContainer.MP4, OutputContainer.MKV, OutputContainer.WEBM].map((cont) => (
                <button
                  key={cont}
                  onClick={() => updateSettings({ defaultContainer: cont })}
                  className={`px-4 py-2 rounded-xl text-xs font-bold transition-all ${
                    settings.defaultContainer === cont
                      ? 'bg-[#D0BCFF] text-[#381E72]'
                      : 'bg-[#1C1B1F] text-[#CAC4D0] hover:text-[#E6E1E5] border border-[#49454F]/50'
                  }`}
                >
                  {cont.toUpperCase()}
                </button>
              ))}
            </div>
          </div>

          <div className="border-t border-[#49454F]/40" />

          {/* Default Audio Format */}
          <div className="space-y-2">
            <h4 className="text-xs font-bold text-[#E6E1E5]">Default Audio Format</h4>
            <div className="flex flex-wrap gap-2">
              {[AudioFormat.MP3, AudioFormat.M4A, AudioFormat.OPUS, AudioFormat.FLAC].map((fmt) => (
                <button
                  key={fmt}
                  onClick={() => updateSettings({ defaultAudioFormat: fmt })}
                  className={`px-4 py-2 rounded-xl text-xs font-bold transition-all ${
                    settings.defaultAudioFormat === fmt
                      ? 'bg-[#80D8FF] text-[#00363A]'
                      : 'bg-[#1C1B1F] text-[#CAC4D0] hover:text-[#E6E1E5] border border-[#49454F]/50'
                  }`}
                >
                  {fmt.toUpperCase()}
                </button>
              ))}
            </div>
          </div>
        </div>
      </div>

      {/* Section 4: Filename Formatting */}
      <div className="space-y-3">
        <div className="flex items-center gap-2 text-xs font-bold uppercase tracking-wider text-[#938F99]">
          <Wand2 className="w-4 h-4 text-[#D0BCFF]" />
          <span>Filename Template</span>
        </div>

        <div className="rounded-3xl bg-[#2B2930] border border-[#49454F]/70 p-5 shadow-xl space-y-4">
          <div className="space-y-1.5">
            <label className="text-xs font-bold text-[#E6E1E5] block">Custom Template Expression</label>
            <input
              type="text"
              value={filenameTemplateInput}
              onChange={(e) => {
                setFilenameTemplateInput(e.target.value);
                updateSettings({ filenameTemplate: e.target.value });
              }}
              className="w-full p-2.5 rounded-xl bg-[#1C1B1F] border border-[#49454F] focus:border-[#D0BCFF] text-xs font-mono text-[#E6E1E5] focus:outline-none"
            />
          </div>

          {/* Presets */}
          <div className="flex flex-wrap gap-2">
            {[
              { label: 'Title', tmpl: '%(title)s.%(ext)s' },
              { label: 'Title + ID', tmpl: '%(title)s [%(id)s].%(ext)s' },
              { label: 'Uploader + Title', tmpl: '%(uploader)s - %(title)s.%(ext)s' },
            ].map((p) => (
              <button
                key={p.label}
                onClick={() => {
                  setFilenameTemplateInput(p.tmpl);
                  updateSettings({ filenameTemplate: p.tmpl });
                }}
                className={`px-3 py-1.5 rounded-xl text-xs font-semibold transition-all ${
                  settings.filenameTemplate === p.tmpl
                    ? 'bg-[#D0BCFF] text-[#381E72]'
                    : 'bg-[#1C1B1F] text-[#CAC4D0] hover:text-[#E6E1E5] border border-[#49454F]/50'
                }`}
              >
                {p.label}
              </button>
            ))}
          </div>

          {/* Live Preview */}
          <div className="p-3.5 rounded-2xl bg-[#141218] border border-[#49454F]/40 space-y-1">
            <span className="text-[10px] font-bold text-[#938F99] uppercase block">Preview Output:</span>
            <p className="text-xs font-mono text-[#D0BCFF] break-all">{previewFormattedName}</p>
          </div>
        </div>
      </div>

      {/* Section 5: Authentication & CLI Arguments */}
      <div className="space-y-3">
        <div className="flex items-center gap-2 text-xs font-bold uppercase tracking-wider text-[#938F99]">
          <Cookie className="w-4 h-4 text-[#FFB74D]" />
          <span>Authentication & CLI Arguments</span>
        </div>

        <div className="rounded-3xl bg-[#2B2930] border border-[#49454F]/70 p-5 shadow-xl space-y-4">
          <div className="space-y-1">
            <h4 className="text-xs font-bold text-[#E6E1E5]">Cookies File Path (Optional)</h4>
            <p className="text-[11px] text-[#938F99]">
              Path to cookies.txt for accessing age-gated or premium media
            </p>
            <input
              type="text"
              value={cookiesInput}
              onChange={(e) => {
                setCookiesInput(e.target.value);
                updateSettings({ cookiesFilePath: e.target.value });
              }}
              placeholder="/storage/emulated/0/cookies.txt"
              className="w-full p-2.5 rounded-xl bg-[#1C1B1F] border border-[#49454F] focus:border-[#D0BCFF] text-xs font-mono text-[#E6E1E5] focus:outline-none mt-1"
            />
          </div>

          <div className="space-y-1">
            <h4 className="text-xs font-bold text-[#E6E1E5]">Custom yt-dlp Arguments (Optional)</h4>
            <p className="text-[11px] text-[#938F99]">
              Additional CLI flags passed directly to the extractor
            </p>
            <input
              type="text"
              value={customArgsInput}
              onChange={(e) => {
                setCustomArgsInput(e.target.value);
                updateSettings({ customYtDlpArgs: e.target.value });
              }}
              placeholder="--no-mtime --geo-bypass"
              className="w-full p-2.5 rounded-xl bg-[#1C1B1F] border border-[#49454F] focus:border-[#D0BCFF] text-xs font-mono text-[#E6E1E5] focus:outline-none mt-1"
            />
          </div>
        </div>
      </div>

      {/* Section 6: Diagnostics & System Reset */}
      <div className="space-y-3">
        <div className="flex items-center gap-2 text-xs font-bold uppercase tracking-wider text-[#938F99]">
          <FileText className="w-4 h-4 text-[#D0BCFF]" />
          <span>Diagnostics & System Controls</span>
        </div>

        <div className="rounded-3xl bg-[#2B2930] border border-[#49454F]/70 p-5 shadow-xl space-y-4">
          <div className="flex flex-wrap items-center gap-3">
            <button
              onClick={() => setIsLogsDialogOpen(true)}
              className="flex items-center gap-2 px-4 py-2.5 rounded-xl bg-[#D0BCFF] text-[#381E72] hover:bg-[#E8DEF8] text-xs font-bold transition-all shadow-sm"
            >
              <FileText className="w-4 h-4" />
              <span>View Engine Logs</span>
            </button>

            <button
              onClick={() => {
                const sysDiag = `Transcode System Diagnostics:\nABI: ${deviceAbi}\nyt-dlp: ${ytdlpStatus.version} (${ytdlpStatus.state})\nFFmpeg: ${ffmpegStatus.version} (${ffmpegStatus.state})\nMax Downloads: ${settings.maxConcurrentDownloads}\nContainer: ${settings.defaultContainer}\nAudio: ${settings.defaultAudioFormat}`;
                copyToClipboard(sysDiag, 'Diagnostics Info');
              }}
              className="flex items-center gap-2 px-4 py-2.5 rounded-xl bg-[#1C1B1F] hover:bg-[#4A4458] text-[#CAC4D0] hover:text-[#E6E1E5] text-xs font-semibold transition-all border border-[#49454F]/50"
            >
              <span>Copy System Info</span>
            </button>

            <button
              onClick={resetSettings}
              className="flex items-center gap-2 px-4 py-2.5 rounded-xl bg-[#1C1B1F] hover:bg-[#F2B8B5]/20 text-[#CAC4D0] hover:text-[#F2B8B5] text-xs font-semibold transition-all border border-[#49454F]/50 ml-auto"
            >
              <RotateCcw className="w-4 h-4" />
              <span>Reset Settings</span>
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
