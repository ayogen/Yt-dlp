import React from 'react';
import { X, Download, Play, Music, Film, ExternalLink } from 'lucide-react';
import { useApp } from '../context/AppContext';
import { MediaType } from '../types';

export const MediaPlayerModal: React.FC = () => {
  const { playbackItem, setPlaybackItem, copyToClipboard } = useApp();

  if (!playbackItem) return null;

  const isAudio = playbackItem.mediaType === MediaType.AUDIO;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/85 backdrop-blur-md animate-fade-in">
      <div className="bg-[#2B2930] border border-[#49454F] rounded-3xl w-full max-w-2xl overflow-hidden shadow-2xl">
        {/* Header */}
        <div className="flex items-center justify-between p-4 sm:p-5 border-b border-[#49454F]/80">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-xl bg-[#4A4458] flex items-center justify-center text-[#D0BCFF]">
              {isAudio ? <Music className="w-5 h-5" /> : <Film className="w-5 h-5" />}
            </div>
            <div className="max-w-md">
              <h2 className="text-sm sm:text-base font-bold text-[#E6E1E5] truncate">{playbackItem.title}</h2>
              <p className="text-xs text-[#938F99]">Media Playback & Inspection Preview</p>
            </div>
          </div>
          <button
            onClick={() => setPlaybackItem(null)}
            className="w-8 h-8 rounded-full flex items-center justify-center text-[#CAC4D0] hover:text-white hover:bg-[#4A4458] transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Player Media Container */}
        <div className="p-5 flex flex-col items-center justify-center bg-[#141218]">
          {isAudio ? (
            <div className="w-full max-w-md flex flex-col items-center py-6">
              {playbackItem.thumbnail ? (
                <img
                  src={playbackItem.thumbnail}
                  alt={playbackItem.title}
                  className="w-48 h-48 rounded-2xl object-cover mb-6 shadow-xl border border-[#49454F]/50"
                  referrerPolicy="no-referrer"
                />
              ) : (
                <div className="w-40 h-40 rounded-2xl bg-[#4A4458]/40 flex items-center justify-center text-[#D0BCFF] mb-6">
                  <Music className="w-16 h-16" />
                </div>
              )}
              <audio
                controls
                autoPlay
                src={playbackItem.url}
                className="w-full rounded-xl"
              >
                Your browser does not support audio playback.
              </audio>
            </div>
          ) : (
            <div className="w-full aspect-video rounded-2xl overflow-hidden bg-black flex items-center justify-center shadow-lg">
              <video
                controls
                autoPlay
                poster={playbackItem.thumbnail}
                src={playbackItem.url}
                className="w-full h-full object-contain"
              >
                Your browser does not support video playback.
              </video>
            </div>
          )}
        </div>

        {/* Footer controls */}
        <div className="flex flex-wrap items-center justify-between gap-3 p-4 bg-[#1C1B1F] border-t border-[#49454F]/80">
          <button
            onClick={() => copyToClipboard(playbackItem.url, 'Stream URL')}
            className="flex items-center gap-1.5 px-3.5 py-2 rounded-xl bg-[#2B2930] hover:bg-[#4A4458] text-[#CAC4D0] hover:text-[#E6E1E5] text-xs font-semibold transition-all"
          >
            <ExternalLink className="w-3.5 h-3.5 text-[#D0BCFF]" />
            <span>Copy Stream URL</span>
          </button>

          <div className="flex items-center gap-2">
            <a
              href={playbackItem.url}
              download={playbackItem.title}
              target="_blank"
              rel="noreferrer"
              className="flex items-center gap-2 px-4 py-2 rounded-xl bg-[#D0BCFF] text-[#381E72] hover:bg-[#E8DEF8] text-xs font-bold transition-all shadow-sm"
            >
              <Download className="w-3.5 h-3.5" />
              <span>Save File to Device</span>
            </a>
            <button
              onClick={() => setPlaybackItem(null)}
              className="px-4 py-2 rounded-xl bg-[#4A4458] text-[#E6E1E5] hover:bg-[#938F99]/40 text-xs font-semibold transition-colors"
            >
              Close
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
