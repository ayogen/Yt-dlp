import React, { useState } from 'react';
import { Download, Film, Music, Settings, History, CheckCircle2, Play, Pause, X, ExternalLink, Sparkles, FolderDown, ShieldCheck, Cpu } from 'lucide-react';

interface DownloadItem {
  id: string;
  title: string;
  uploader: string;
  duration: string;
  size: string;
  format: string;
  quality: string;
  progress: number;
  speed: string;
  eta: string;
  status: 'downloading' | 'completed' | 'paused' | 'queued' | 'error';
  thumbnail: string;
  type: 'video' | 'audio';
  addedAt: string;
}

export function App() {
  const [activeTab, setActiveTab] = useState<'home' | 'downloads' | 'history' | 'settings'>('home');
  const [urlInput, setUrlInput] = useState('');
  const [isAnalyzing, setIsAnalyzing] = useState(false);
  const [analysisResult, setAnalysisResult] = useState<any | null>(null);
  const [selectedFormat, setSelectedFormat] = useState<'video' | 'audio'>('video');
  const [selectedQuality, setSelectedQuality] = useState('1080p');
  const [selectedContainer, setSelectedContainer] = useState('mp4');

  const [downloads, setDownloads] = useState<DownloadItem[]>([
    {
      id: '1',
      title: 'Rick Astley - Never Gonna Give You Up (Official Music Video)',
      uploader: 'RickAstleyVEVO',
      duration: '3:33',
      size: '42.5 MB',
      format: 'MP4',
      quality: '1080p HD',
      progress: 68,
      speed: '4.8 MB/s',
      eta: '4s',
      status: 'downloading',
      thumbnail: 'https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=600&auto=format&fit=crop&q=80',
      type: 'video',
      addedAt: 'Just now'
    },
    {
      id: '2',
      title: 'Lofi Hip Hop Radio - Beats to Relax/Study to',
      uploader: 'Lofi Girl',
      duration: '45:12',
      size: '62.1 MB',
      format: 'MP3',
      quality: '320 kbps',
      progress: 100,
      speed: '0 KB/s',
      eta: '0s',
      status: 'completed',
      thumbnail: 'https://images.unsplash.com/photo-1518609878373-06d740f60d8b?w=600&auto=format&fit=crop&q=80',
      type: 'audio',
      addedAt: '10 mins ago'
    }
  ]);

  const handleAnalyze = (e: React.FormEvent) => {
    e.preventDefault();
    if (!urlInput.trim()) return;
    setIsAnalyzing(true);
    setTimeout(() => {
      setIsAnalyzing(false);
      setAnalysisResult({
        id: 'dQw4w9WgXcQ',
        title: 'Rick Astley - Never Gonna Give You Up (Official Music Video)',
        uploader: 'RickAstleyVEVO',
        duration: '3:33',
        views: '1.5B',
        thumbnail: 'https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=600&auto=format&fit=crop&q=80',
        qualities: ['2160p (4K)', '1440p (2K)', '1080p (Full HD)', '720p (HD)', '480p', 'Audio (320k MP3)', 'Audio (FLAC Lossless)']
      });
    }, 900);
  };

  const handleStartDownload = () => {
    if (!analysisResult) return;
    const newItem: DownloadItem = {
      id: Date.now().toString(),
      title: analysisResult.title,
      uploader: analysisResult.uploader,
      duration: analysisResult.duration,
      size: selectedFormat === 'video' ? '38.4 MB' : '8.2 MB',
      format: selectedFormat === 'video' ? selectedContainer.toUpperCase() : 'MP3',
      quality: selectedQuality,
      progress: 12,
      speed: '3.2 MB/s',
      eta: '11s',
      status: 'downloading',
      thumbnail: analysisResult.thumbnail,
      type: selectedFormat,
      addedAt: 'Just now'
    };
    setDownloads([newItem, ...downloads]);
    setAnalysisResult(null);
    setUrlInput('');
    setActiveTab('downloads');
  };

  return (
    <div className="min-h-screen bg-[#141218] text-[#E6E1E5] flex flex-col font-sans selection:bg-[#D0BCFF] selection:text-[#381E72]">
      {/* Top Header */}
      <header className="sticky top-0 z-30 bg-[#1C1B1F]/90 backdrop-blur-md border-b border-[#49454F]/40 px-4 py-3 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-[#D0BCFF] to-[#381E72] flex items-center justify-center shadow-md">
            <Download className="w-5 h-5 text-[#1C1B1F]" />
          </div>
          <div>
            <h1 className="text-base font-bold tracking-tight text-[#E6E1E5] leading-tight">Video Downloader</h1>
            <p className="text-xs text-[#CAC4D0]">Android Engine & Multi-Format Transcoder</p>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium bg-[#2B2930] text-[#D0BCFF] border border-[#49454F]/50">
            <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse"></span>
            yt-dlp Core Ready
          </span>
        </div>
      </header>

      {/* Main Content Area */}
      <main className="flex-1 max-w-3xl w-full mx-auto p-4 pb-24 space-y-6">
        {activeTab === 'home' && (
          <div className="space-y-6 animate-fadeIn">
            {/* Input Hero Card */}
            <div className="p-6 rounded-3xl bg-[#211F26] border border-[#49454F]/40 shadow-xl space-y-4">
              <div className="flex items-center justify-between">
                <span className="text-xs font-semibold uppercase tracking-wider text-[#D0BCFF] flex items-center gap-1.5">
                  <Sparkles className="w-3.5 h-3.5" /> Instant Media Extraction
                </span>
                <span className="text-xs text-[#CAC4D0]">YouTube, Instagram, TikTok, Facebook & 1000+ sites</span>
              </div>

              <form onSubmit={handleAnalyze} className="space-y-3">
                <div className="relative">
                  <input
                    type="url"
                    value={urlInput}
                    onChange={(e) => setUrlInput(e.target.value)}
                    placeholder="Paste video or playlist link (e.g. https://...)"
                    className="w-full pl-4 pr-24 py-3.5 bg-[#1C1B1F] border border-[#49454F] rounded-2xl text-sm text-[#E6E1E5] placeholder-[#938F99] focus:outline-none focus:border-[#D0BCFF] transition-all"
                  />
                  <button
                    type="submit"
                    disabled={isAnalyzing || !urlInput.trim()}
                    className="absolute right-2 top-2 bottom-2 px-4 rounded-xl bg-[#D0BCFF] text-[#381E72] font-semibold text-xs flex items-center gap-1.5 hover:bg-[#E8DEF8] active:scale-95 transition-all disabled:opacity-50 disabled:cursor-not-allowed"
                  >
                    {isAnalyzing ? (
                      <span className="flex items-center gap-1">
                        <span className="w-3 h-3 border-2 border-[#381E72] border-t-transparent rounded-full animate-spin"></span>
                        Analyzing
                      </span>
                    ) : (
                      <>
                        <Sparkles className="w-3.5 h-3.5" /> Analyze
                      </>
                    )}
                  </button>
                </div>
              </form>

              {/* Supported Badges */}
              <div className="flex flex-wrap gap-2 pt-1 text-xs text-[#CAC4D0]">
                {['MP4 HD/4K', 'MP3 320k', 'FLAC', 'M4A', 'Playlists', 'Subtitles', 'Background Service'].map((badge) => (
                  <span key={badge} className="px-2.5 py-1 rounded-lg bg-[#2B2930] border border-[#49454F]/30 text-[11px]">
                    {badge}
                  </span>
                ))}
              </div>
            </div>

            {/* Analysis Result Card */}
            {analysisResult && (
              <div className="p-5 rounded-3xl bg-[#2B2930] border border-[#D0BCFF]/30 shadow-2xl space-y-4 animate-scaleUp">
                <div className="flex gap-4">
                  <img
                    src={analysisResult.thumbnail}
                    alt={analysisResult.title}
                    className="w-28 h-20 rounded-xl object-cover border border-[#49454F]"
                  />
                  <div className="flex-1 min-w-0">
                    <h3 className="text-sm font-bold text-[#E6E1E5] line-clamp-2">{analysisResult.title}</h3>
                    <p className="text-xs text-[#CAC4D0] mt-1">{analysisResult.uploader} • {analysisResult.duration}</p>
                  </div>
                </div>

                {/* Format Picker */}
                <div className="grid grid-cols-2 gap-2 pt-2">
                  <button
                    type="button"
                    onClick={() => setSelectedFormat('video')}
                    className={`p-3 rounded-2xl border text-left flex items-center gap-3 transition-all ${
                      selectedFormat === 'video'
                        ? 'bg-[#381E72]/40 border-[#D0BCFF] text-[#E6E1E5]'
                        : 'bg-[#1C1B1F] border-[#49454F]/60 text-[#CAC4D0] hover:border-[#938F99]'
                    }`}
                  >
                    <Film className={`w-5 h-5 ${selectedFormat === 'video' ? 'text-[#D0BCFF]' : 'text-[#938F99]'}`} />
                    <div>
                      <div className="text-xs font-bold">Video</div>
                      <div className="text-[10px] text-[#CAC4D0]">MP4 / MKV / WebM</div>
                    </div>
                  </button>

                  <button
                    type="button"
                    onClick={() => setSelectedFormat('audio')}
                    className={`p-3 rounded-2xl border text-left flex items-center gap-3 transition-all ${
                      selectedFormat === 'audio'
                        ? 'bg-[#381E72]/40 border-[#D0BCFF] text-[#E6E1E5]'
                        : 'bg-[#1C1B1F] border-[#49454F]/60 text-[#CAC4D0] hover:border-[#938F99]'
                    }`}
                  >
                    <Music className={`w-5 h-5 ${selectedFormat === 'audio' ? 'text-[#D0BCFF]' : 'text-[#938F99]'}`} />
                    <div>
                      <div className="text-xs font-bold">Audio Only</div>
                      <div className="text-[10px] text-[#CAC4D0]">MP3 / M4A / FLAC</div>
                    </div>
                  </button>
                </div>

                {/* Quality & Container Selection */}
                <div className="flex flex-wrap gap-2">
                  {selectedFormat === 'video' ? (
                    ['2160p (4K)', '1080p (Full HD)', '720p', '480p'].map((q) => (
                      <button
                        key={q}
                        onClick={() => setSelectedQuality(q)}
                        className={`px-3 py-1.5 rounded-xl text-xs font-semibold border transition-all ${
                          selectedQuality === q
                            ? 'bg-[#D0BCFF] text-[#381E72] border-[#D0BCFF]'
                            : 'bg-[#1C1B1F] text-[#CAC4D0] border-[#49454F] hover:border-[#938F99]'
                        }`}
                      >
                        {q}
                      </button>
                    ))
                  ) : (
                    ['320 kbps (High)', '256 kbps', '128 kbps', 'FLAC Lossless'].map((q) => (
                      <button
                        key={q}
                        onClick={() => setSelectedQuality(q)}
                        className={`px-3 py-1.5 rounded-xl text-xs font-semibold border transition-all ${
                          selectedQuality === q
                            ? 'bg-[#D0BCFF] text-[#381E72] border-[#D0BCFF]'
                            : 'bg-[#1C1B1F] text-[#CAC4D0] border-[#49454F] hover:border-[#938F99]'
                        }`}
                      >
                        {q}
                      </button>
                    ))
                  )}
                </div>

                <div className="pt-2 flex items-center justify-end gap-3">
                  <button
                    type="button"
                    onClick={() => setAnalysisResult(null)}
                    className="px-4 py-2.5 rounded-xl text-xs font-semibold text-[#CAC4D0] hover:bg-[#1C1B1F]"
                  >
                    Cancel
                  </button>
                  <button
                    type="button"
                    onClick={handleStartDownload}
                    className="px-5 py-2.5 rounded-xl bg-gradient-to-r from-[#D0BCFF] to-[#CCC2DC] text-[#381E72] font-bold text-xs flex items-center gap-2 hover:opacity-90 active:scale-95 transition-all shadow-md"
                  >
                    <FolderDown className="w-4 h-4" /> Start Download
                  </button>
                </div>
              </div>
            )}

            {/* Quick Engine Status Card */}
            <div className="p-5 rounded-3xl bg-[#211F26] border border-[#49454F]/30 space-y-3">
              <h3 className="text-xs font-bold uppercase tracking-wider text-[#CAC4D0]">Android Engine Architecture</h3>
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 text-xs">
                <div className="p-3 rounded-2xl bg-[#1C1B1F] border border-[#49454F]/40 space-y-1">
                  <div className="flex items-center gap-1.5 font-bold text-[#E6E1E5]">
                    <ShieldCheck className="w-4 h-4 text-emerald-400" /> yt-dlp Native
                  </div>
                  <p className="text-[11px] text-[#CAC4D0]">Python-free and embedded engine extractors</p>
                </div>
                <div className="p-3 rounded-2xl bg-[#1C1B1F] border border-[#49454F]/40 space-y-1">
                  <div className="flex items-center gap-1.5 font-bold text-[#E6E1E5]">
                    <Cpu className="w-4 h-4 text-[#D0BCFF]" /> Room Database
                  </div>
                  <p className="text-[11px] text-[#CAC4D0]">Offline history & persistent queue tables</p>
                </div>
                <div className="p-3 rounded-2xl bg-[#1C1B1F] border border-[#49454F]/40 space-y-1">
                  <div className="flex items-center gap-1.5 font-bold text-[#E6E1E5]">
                    <FolderDown className="w-4 h-4 text-amber-400" /> Scoped Storage
                  </div>
                  <p className="text-[11px] text-[#CAC4D0]">Direct MediaStore and SAF output folder routing</p>
                </div>
              </div>
            </div>
          </div>
        )}

        {activeTab === 'downloads' && (
          <div className="space-y-4 animate-fadeIn">
            <div className="flex items-center justify-between">
              <h2 className="text-sm font-bold text-[#E6E1E5]">Active & Completed Downloads ({downloads.length})</h2>
              <span className="text-xs text-[#CAC4D0]">Foreground Service Active</span>
            </div>

            <div className="space-y-3">
              {downloads.map((item) => (
                <div key={item.id} className="p-4 rounded-2xl bg-[#211F26] border border-[#49454F]/40 space-y-3 shadow-md">
                  <div className="flex gap-3">
                    <img
                      src={item.thumbnail}
                      alt={item.title}
                      className="w-20 h-16 rounded-xl object-cover border border-[#49454F]"
                    />
                    <div className="flex-1 min-w-0">
                      <h4 className="text-xs font-bold text-[#E6E1E5] line-clamp-1">{item.title}</h4>
                      <p className="text-[11px] text-[#CAC4D0] mt-0.5">{item.uploader} • {item.quality} • {item.format}</p>

                      <div className="flex items-center justify-between text-[11px] text-[#CAC4D0] mt-2">
                        <span>{item.progress}%</span>
                        {item.status === 'downloading' && <span>{item.speed} • {item.eta} left</span>}
                        {item.status === 'completed' && <span className="text-emerald-400 font-semibold flex items-center gap-1"><CheckCircle2 className="w-3 h-3" /> Completed</span>}
                      </div>
                    </div>
                  </div>

                  {/* Progress Bar */}
                  <div className="w-full h-1.5 bg-[#1C1B1F] rounded-full overflow-hidden">
                    <div
                      className={`h-full transition-all duration-300 ${item.status === 'completed' ? 'bg-emerald-400' : 'bg-[#D0BCFF]'}`}
                      style={{ width: `${item.progress}%` }}
                    ></div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {activeTab === 'history' && (
          <div className="space-y-4 animate-fadeIn">
            <h2 className="text-sm font-bold text-[#E6E1E5]">Download History & Export Logs</h2>
            <div className="p-4 rounded-2xl bg-[#211F26] border border-[#49454F]/40 space-y-2">
              <p className="text-xs text-[#CAC4D0]">All finished downloads are automatically indexed in Room Database and synced to the device gallery or audio library.</p>
              <div className="pt-2 text-xs font-mono text-[#D0BCFF]">
                ✓ Total items recorded: {downloads.length}<br/>
                ✓ Storage path: /storage/emulated/0/Download/VideoDownloader
              </div>
            </div>
          </div>
        )}

        {activeTab === 'settings' && (
          <div className="space-y-4 animate-fadeIn">
            <h2 className="text-sm font-bold text-[#E6E1E5]">Settings & Preferences</h2>
            <div className="p-5 rounded-3xl bg-[#211F26] border border-[#49454F]/40 space-y-4 text-xs">
              <div className="flex items-center justify-between">
                <div>
                  <div className="font-bold text-[#E6E1E5]">Auto Embed Subtitles</div>
                  <div className="text-[11px] text-[#CAC4D0]">Embed multi-language subtitles into MP4/MKV</div>
                </div>
                <input type="checkbox" defaultChecked className="w-4 h-4 accent-[#D0BCFF]" />
              </div>
              <hr className="border-[#49454F]/30" />
              <div className="flex items-center justify-between">
                <div>
                  <div className="font-bold text-[#E6E1E5]">Embed Thumbnail Art</div>
                  <div className="text-[11px] text-[#CAC4D0]">Embed high-res cover art in audio files</div>
                </div>
                <input type="checkbox" defaultChecked className="w-4 h-4 accent-[#D0BCFF]" />
              </div>
              <hr className="border-[#49454F]/30" />
              <div className="flex items-center justify-between">
                <div>
                  <div className="font-bold text-[#E6E1E5]">Concurrent Downloads</div>
                  <div className="text-[11px] text-[#CAC4D0]">Maximum parallel active download threads</div>
                </div>
                <span className="font-bold text-[#D0BCFF]">3 items</span>
              </div>
            </div>
          </div>
        )}
      </main>

      {/* Bottom Navigation */}
      <nav className="fixed bottom-0 left-0 right-0 z-30 bg-[#1C1B1F]/95 backdrop-blur-lg border-t border-[#49454F]/40 px-6 py-2">
        <div className="max-w-md mx-auto flex items-center justify-between">
          <button
            onClick={() => setActiveTab('home')}
            className={`flex flex-col items-center gap-1 py-1 px-3 rounded-2xl transition-all ${
              activeTab === 'home' ? 'text-[#D0BCFF]' : 'text-[#CAC4D0] hover:text-[#E6E1E5]'
            }`}
          >
            <Download className="w-5 h-5" />
            <span className="text-[10px] font-semibold">Home</span>
          </button>

          <button
            onClick={() => setActiveTab('downloads')}
            className={`flex flex-col items-center gap-1 py-1 px-3 rounded-2xl transition-all ${
              activeTab === 'downloads' ? 'text-[#D0BCFF]' : 'text-[#CAC4D0] hover:text-[#E6E1E5]'
            }`}
          >
            <FolderDown className="w-5 h-5" />
            <span className="text-[10px] font-semibold">Downloads</span>
          </button>

          <button
            onClick={() => setActiveTab('history')}
            className={`flex flex-col items-center gap-1 py-1 px-3 rounded-2xl transition-all ${
              activeTab === 'history' ? 'text-[#D0BCFF]' : 'text-[#CAC4D0] hover:text-[#E6E1E5]'
            }`}
          >
            <History className="w-5 h-5" />
            <span className="text-[10px] font-semibold">History</span>
          </button>

          <button
            onClick={() => setActiveTab('settings')}
            className={`flex flex-col items-center gap-1 py-1 px-3 rounded-2xl transition-all ${
              activeTab === 'settings' ? 'text-[#D0BCFF]' : 'text-[#CAC4D0] hover:text-[#E6E1E5]'
            }`}
          >
            <Settings className="w-5 h-5" />
            <span className="text-[10px] font-semibold">Settings</span>
          </button>
        </div>
      </nav>
    </div>
  );
}

export default App;
