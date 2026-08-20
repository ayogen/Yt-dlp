import React, { useState } from 'react';
import {
  Search,
  Clipboard,
  X,
  Sparkles,
  ArrowRight,
  Play,
  Film,
  Music,
  Download,
  AlertCircle,
  HelpCircle,
} from 'lucide-react';
import { MediaMetadata, NavigationTab } from '../types';
import { useApp } from '../context/AppContext';
import { MediaAnalysisModal } from '../components/MediaAnalysisModal';

const SAMPLE_URLS = [
  {
    label: 'Big Buck Bunny (Direct MP4)',
    url: 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4',
    type: 'Direct Stream',
  },
  {
    label: 'Lo-Fi Chill Ambient (YouTube)',
    url: 'https://www.youtube.com/watch?v=5qap5aO4i9A',
    type: 'YouTube Stream',
  },
  {
    label: 'Cinematic Architecture (Vimeo)',
    url: 'https://vimeo.com/76979871',
    type: 'Vimeo 4K',
  },
  {
    label: 'Studio Synthesizer Playlist',
    url: 'https://www.youtube.com/playlist?list=PL4fGSI1pDJn6jXS_Tv_N9q8CEjYCEmq4L',
    type: 'Playlist (4 Items)',
  },
];

export const HomeScreen: React.FC = () => {
  const { setActiveTab, history, setPlaybackItem, addLog } = useApp();

  const [inputUrl, setInputUrl] = useState('');
  const [isAnalyzing, setIsAnalyzing] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [analyzedMetadata, setAnalyzedMetadata] = useState<MediaMetadata | null>(null);

  const handlePaste = async () => {
    try {
      if (navigator.clipboard) {
        const text = await navigator.clipboard.readText();
        if (text) {
          setInputUrl(text.trim());
          setErrorMessage(null);
        }
      }
    } catch {
      // Fallback
    }
  };

  const handleClear = () => {
    setInputUrl('');
    setErrorMessage(null);
  };

  const handleAnalyze = async (urlToAnalyze?: string) => {
    const targetUrl = (urlToAnalyze || inputUrl).trim();
    if (!targetUrl) {
      setErrorMessage('Please enter a valid media or video URL.');
      return;
    }

    setIsAnalyzing(true);
    setErrorMessage(null);

    try {
      const response = await fetch('/api/analyze', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ url: targetUrl }),
      });

      if (!response.ok) {
        const errData = await response.json().catch(() => ({}));
        throw new Error(errData.error || `HTTP error ${response.status}`);
      }

      const data: MediaMetadata = await response.json();
      setAnalyzedMetadata(data);
    } catch (err: any) {
      setErrorMessage(
        err.message || 'Failed to extract video streams. Please check your URL and try again.'
      );
    } finally {
      setIsAnalyzing(false);
    }
  };

  return (
    <div className="space-y-6 pb-16">
      {/* Hero Input Card */}
      <div className="relative overflow-hidden rounded-3xl bg-gradient-to-b from-[#2B2930] to-[#1C1B1F] border border-[#49454F]/70 p-6 sm:p-8 shadow-xl">
        <div className="relative z-10 max-w-2xl">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-[#D0BCFF]/10 border border-[#D0BCFF]/30 text-[#D0BCFF] text-xs font-semibold mb-3">
            <Sparkles className="w-3.5 h-3.5" />
            Universal Multi-Platform Extractor
          </div>
          <h1 className="text-2xl sm:text-3xl font-extrabold tracking-tight text-[#E6E1E5] mb-2">
            Download Any Video or Audio
          </h1>
          <p className="text-sm text-[#CAC4D0] leading-relaxed mb-6">
            Paste any YouTube, Vimeo, TikTok, Twitter/X, or direct media URL to inspect formats, select custom bitrates, and save with FFmpeg muxing.
          </p>

          {/* URL Input Form */}
          <div className="space-y-3">
            <div className="relative flex items-center bg-[#1C1B1F] border border-[#49454F] focus-within:border-[#D0BCFF] rounded-2xl p-1.5 transition-all shadow-inner">
              <Search className="w-5 h-5 text-[#938F99] ml-3 shrink-0" />
              <input
                type="text"
                value={inputUrl}
                onChange={(e) => {
                  setInputUrl(e.target.value);
                  if (errorMessage) setErrorMessage(null);
                }}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' && !isAnalyzing) {
                    handleAnalyze();
                  }
                }}
                placeholder="Paste video, audio, or playlist link here..."
                className="w-full bg-transparent px-3 py-2 text-sm text-[#E6E1E5] placeholder-[#938F99] focus:outline-none"
              />

              {inputUrl && (
                <button
                  onClick={handleClear}
                  className="p-1.5 text-[#938F99] hover:text-[#E6E1E5] transition-colors rounded-lg"
                  title="Clear input"
                >
                  <X className="w-4 h-4" />
                </button>
              )}

              <button
                onClick={handlePaste}
                className="flex items-center gap-1 px-3 py-1.5 rounded-xl bg-[#2B2930] hover:bg-[#4A4458] text-[#CAC4D0] hover:text-[#E6E1E5] text-xs font-semibold transition-colors shrink-0"
                title="Paste from clipboard"
              >
                <Clipboard className="w-3.5 h-3.5 text-[#D0BCFF]" />
                <span className="hidden sm:inline">Paste</span>
              </button>
            </div>

            {/* Error Message banner */}
            {errorMessage && (
              <div className="flex items-center gap-2 p-3 rounded-xl bg-[#F2B8B5]/10 border border-[#F2B8B5]/30 text-[#F2B8B5] text-xs font-medium animate-shake">
                <AlertCircle className="w-4 h-4 shrink-0" />
                <span>{errorMessage}</span>
              </div>
            )}

            {/* Analyze Action Button */}
            <button
              onClick={() => handleAnalyze()}
              disabled={isAnalyzing || !inputUrl.trim()}
              className="w-full sm:w-auto flex items-center justify-center gap-2 px-8 py-3.5 rounded-2xl bg-[#D0BCFF] text-[#381E72] hover:bg-[#E8DEF8] disabled:opacity-50 disabled:cursor-not-allowed font-extrabold text-sm shadow-lg shadow-[#D0BCFF]/20 transition-all cursor-pointer"
            >
              {isAnalyzing ? (
                <>
                  <div className="w-4 h-4 border-2 border-[#381E72] border-t-transparent rounded-full animate-spin" />
                  <span>Extracting Formats & Manifests...</span>
                </>
              ) : (
                <>
                  <span>Extract Formats & Info</span>
                  <ArrowRight className="w-4 h-4" />
                </>
              )}
            </button>
          </div>
        </div>
      </div>

      {/* Quick Sample Links & Supported Extractors */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {/* Sample Links Card */}
        <div className="p-5 rounded-2xl bg-[#2B2930]/70 border border-[#49454F]/50">
          <h2 className="text-xs font-bold uppercase tracking-wider text-[#938F99] mb-3 flex items-center gap-2">
            <Sparkles className="w-3.5 h-3.5 text-[#D0BCFF]" />
            Quick Test Media URLs
          </h2>
          <div className="space-y-2">
            {SAMPLE_URLS.map((sample, idx) => (
              <button
                key={idx}
                onClick={() => {
                  setInputUrl(sample.url);
                  handleAnalyze(sample.url);
                }}
                className="w-full flex items-center justify-between p-2.5 rounded-xl bg-[#1C1B1F] hover:bg-[#4A4458]/40 border border-[#49454F]/40 text-left transition-all group"
              >
                <div>
                  <p className="text-xs font-bold text-[#E6E1E5] group-hover:text-[#D0BCFF] transition-colors">
                    {sample.label}
                  </p>
                  <span className="text-[10px] text-[#938F99] font-mono">{sample.type}</span>
                </div>
                <ArrowRight className="w-3.5 h-3.5 text-[#938F99] group-hover:text-[#D0BCFF] group-hover:translate-x-0.5 transition-all" />
              </button>
            ))}
          </div>
        </div>

        {/* Supported Engines & Platforms */}
        <div className="p-5 rounded-2xl bg-[#2B2930]/70 border border-[#49454F]/50 flex flex-col justify-between">
          <div>
            <h2 className="text-xs font-bold uppercase tracking-wider text-[#938F99] mb-3 flex items-center gap-2">
              <Film className="w-3.5 h-3.5 text-[#80D8FF]" />
              Supported Formats & Platforms
            </h2>
            <div className="flex flex-wrap gap-1.5 mb-4">
              {[
                'YouTube',
                'Vimeo',
                'TikTok',
                'Twitter / X',
                'Reddit',
                'SoundCloud',
                'Twitch VODs',
                'Bandcamp',
                'Direct MP4 / WebM',
                'HLS / m3u8 Streams',
              ].map((platform) => (
                <span
                  key={platform}
                  className="px-2.5 py-1 rounded-lg bg-[#1C1B1F] border border-[#49454F]/50 text-[11px] text-[#CAC4D0] font-medium"
                >
                  {platform}
                </span>
              ))}
            </div>
          </div>

          <div className="p-3 rounded-xl bg-[#1C1B1F] border border-[#49454F]/40 text-xs text-[#938F99] leading-relaxed">
            💡 <strong className="text-[#CAC4D0]">Automatic Subfolder Routing:</strong> Videos are placed into <code className="text-[#D0BCFF]">Video/</code> and music is stored in <code className="text-[#80D8FF]">Music/</code> automatically.
          </div>
        </div>
      </div>

      {/* Recent Activity Quick Preview */}
      {history.length > 0 && (
        <div className="p-5 rounded-2xl bg-[#2B2930]/40 border border-[#49454F]/40">
          <div className="flex items-center justify-between mb-3">
            <h2 className="text-xs font-bold uppercase tracking-wider text-[#938F99]">
              Recent Downloads
            </h2>
            <button
              onClick={() => setActiveTab(NavigationTab.HISTORY)}
              className="text-xs font-bold text-[#D0BCFF] hover:underline"
            >
              View Full History →
            </button>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
            {history.slice(0, 3).map((item) => (
              <div
                key={item.id}
                onClick={() =>
                  setPlaybackItem({
                    title: item.title,
                    url: item.mediaUrl || 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4',
                    thumbnail: item.thumbnail,
                    mediaType: item.mediaType,
                  })
                }
                className="flex items-center gap-3 p-2.5 rounded-xl bg-[#1C1B1F] border border-[#49454F]/40 hover:border-[#D0BCFF]/60 cursor-pointer transition-all group"
              >
                <div className="relative w-14 h-14 rounded-lg overflow-hidden bg-black shrink-0">
                  <img
                    src={item.thumbnail}
                    alt={item.title}
                    className="w-full h-full object-cover"
                    referrerPolicy="no-referrer"
                  />
                  <div className="absolute inset-0 bg-black/30 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity">
                    <Play className="w-5 h-5 text-white fill-white" />
                  </div>
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-xs font-bold text-[#E6E1E5] truncate group-hover:text-[#D0BCFF] transition-colors">
                    {item.title}
                  </p>
                  <p className="text-[11px] text-[#938F99] truncate mt-0.5">{item.formatDescription}</p>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Media Analysis Modal */}
      {analyzedMetadata && (
        <MediaAnalysisModal
          metadata={analyzedMetadata}
          onClose={() => setAnalyzedMetadata(null)}
        />
      )}
    </div>
  );
};
