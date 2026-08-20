import React, { useState } from 'react';
import {
  X,
  Download,
  Film,
  Music,
  ListVideo,
  CheckCircle2,
  Subtitles,
  Image as ImageIcon,
  Clock,
  User,
  Eye,
  Check,
} from 'lucide-react';
import {
  MediaMetadata,
  MediaType,
  OutputContainer,
  AudioFormat,
  VideoQualityPreset,
  AudioQualityPreset,
} from '../types';
import { formatBytes, formatDuration } from '../utils/formatters';
import { useApp } from '../context/AppContext';

interface MediaAnalysisModalProps {
  metadata: MediaMetadata | null;
  onClose: () => void;
}

export const MediaAnalysisModal: React.FC<MediaAnalysisModalProps> = ({ metadata, onClose }) => {
  const { startDownload, settings } = useApp();

  const [selectedTab, setSelectedTab] = useState<'video' | 'audio' | 'playlist'>('video');

  // Video Options
  const [videoQuality, setVideoQuality] = useState<VideoQualityPreset>(settings.defaultVideoQuality);
  const [selectedFormatId, setSelectedFormatId] = useState<string>('1080p');
  const [container, setContainer] = useState<OutputContainer>(settings.defaultContainer);
  const [embedSubs, setEmbedSubs] = useState<boolean>(settings.embedSubtitles);
  const [embedThumbnail, setEmbedThumbnail] = useState<boolean>(settings.embedThumbnail);
  const [subtitleLang, setSubtitleLang] = useState<string>('en');

  // Audio Options
  const [audioFormat, setAudioFormat] = useState<AudioFormat>(settings.defaultAudioFormat);
  const [audioQuality, setAudioQuality] = useState<AudioQualityPreset>(settings.defaultAudioQuality);

  // Playlist Options
  const [selectedPlaylistIndices, setSelectedPlaylistIndices] = useState<number[]>(() =>
    metadata?.playlistEntries.map((_, i) => i) || []
  );

  if (!metadata) return null;

  // Filter video / audio formats
  const videoFormats = metadata.formats.filter((f) => !f.isAudioOnly);
  const audioFormats = metadata.formats.filter((f) => f.isAudioOnly);

  const selectedFormatObj = metadata.formats.find((f) => f.formatId === selectedFormatId) || videoFormats[0];

  const handleStartDownload = () => {
    if (selectedTab === 'video') {
      startDownload({
        metadata,
        mediaType: MediaType.VIDEO,
        formatId: selectedFormatId || 'best',
        formatDescription: `${selectedFormatObj?.resolution || videoQuality} (${container.toUpperCase()})`,
        outputContainer: container,
        embedSubs,
        embedThumbnail,
        subtitleLangs: subtitleLang,
      });
    } else if (selectedTab === 'audio') {
      startDownload({
        metadata,
        mediaType: MediaType.AUDIO,
        formatId: audioFormats[0]?.formatId || 'bestaudio',
        formatDescription: `${audioFormat.toUpperCase()} • ${audioQuality}`,
        outputContainer: audioFormat,
        audioFormat,
        audioBitrate: 320,
        embedSubs: false,
        embedThumbnail,
      });
    } else if (selectedTab === 'playlist') {
      startDownload({
        metadata,
        mediaType: MediaType.VIDEO,
        formatId: selectedFormatId || 'best',
        formatDescription: `Playlist (${selectedPlaylistIndices.length} items)`,
        outputContainer: container,
        embedSubs,
        embedThumbnail,
        selectedPlaylistIndices,
      });
    }
    onClose();
  };

  const togglePlaylistItem = (index: number) => {
    setSelectedPlaylistIndices((prev) =>
      prev.includes(index) ? prev.filter((i) => i !== index) : [...prev, index]
    );
  };

  const toggleAllPlaylist = () => {
    if (selectedPlaylistIndices.length === metadata.playlistEntries.length) {
      setSelectedPlaylistIndices([]);
    } else {
      setSelectedPlaylistIndices(metadata.playlistEntries.map((_, i) => i));
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-3 sm:p-4 bg-black/80 backdrop-blur-md animate-fade-in">
      <div className="bg-[#2B2930] border border-[#49454F] rounded-3xl w-full max-w-2xl flex flex-col max-h-[90vh] shadow-2xl overflow-hidden">
        {/* Header Preview Banner */}
        <div className="relative bg-[#1C1B1F] p-4 sm:p-5 border-b border-[#49454F]/80">
          <button
            onClick={onClose}
            className="absolute top-4 right-4 z-10 w-8 h-8 rounded-full bg-[#1C1B1F]/80 text-[#CAC4D0] hover:text-white flex items-center justify-center transition-colors"
          >
            <X className="w-5 h-5" />
          </button>

          <div className="flex flex-col sm:flex-row gap-4 items-start">
            {/* Thumbnail */}
            <div className="relative w-full sm:w-44 aspect-video rounded-xl overflow-hidden bg-black shrink-0 border border-[#49454F]/50">
              <img
                src={metadata.thumbnail}
                alt={metadata.title}
                className="w-full h-full object-cover"
                referrerPolicy="no-referrer"
              />
              <div className="absolute bottom-1.5 right-1.5 px-2 py-0.5 rounded bg-black/80 text-[10px] font-mono text-[#D0BCFF]">
                {formatDuration(metadata.durationSeconds)}
              </div>
            </div>

            {/* Meta Info */}
            <div className="flex-1 min-w-0 pr-8 sm:pr-0">
              <span className="inline-block px-2 py-0.5 rounded text-[10px] font-bold uppercase bg-[#D0BCFF]/15 text-[#D0BCFF] mb-1">
                {metadata.extractorName}
              </span>
              <h2 className="text-sm sm:text-base font-bold text-[#E6E1E5] line-clamp-2 leading-snug">
                {metadata.title}
              </h2>
              <div className="flex flex-wrap items-center gap-3 mt-2 text-xs text-[#938F99]">
                <span className="flex items-center gap-1">
                  <User className="w-3.5 h-3.5 text-[#CAC4D0]" />
                  <span className="text-[#CAC4D0] font-medium truncate max-w-[140px]">{metadata.uploader}</span>
                </span>
                {metadata.viewCount && (
                  <span className="flex items-center gap-1">
                    <Eye className="w-3.5 h-3.5" />
                    <span>{metadata.viewCount.toLocaleString()} views</span>
                  </span>
                )}
              </div>
            </div>
          </div>
        </div>

        {/* Tab Controls */}
        <div className="grid grid-cols-3 bg-[#1C1B1F] border-b border-[#49454F]/60 p-1.5 gap-1.5">
          <button
            onClick={() => setSelectedTab('video')}
            className={`flex items-center justify-center gap-2 py-2.5 rounded-xl text-xs font-bold transition-all ${
              selectedTab === 'video'
                ? 'bg-[#4A4458] text-[#E8DEF8] shadow-sm'
                : 'text-[#CAC4D0] hover:text-[#E6E1E5]'
            }`}
          >
            <Film className="w-4 h-4 text-[#D0BCFF]" />
            <span>Video</span>
          </button>
          <button
            onClick={() => setSelectedTab('audio')}
            className={`flex items-center justify-center gap-2 py-2.5 rounded-xl text-xs font-bold transition-all ${
              selectedTab === 'audio'
                ? 'bg-[#4A4458] text-[#E8DEF8] shadow-sm'
                : 'text-[#CAC4D0] hover:text-[#E6E1E5]'
            }`}
          >
            <Music className="w-4 h-4 text-[#80D8FF]" />
            <span>Audio Only</span>
          </button>
          <button
            onClick={() => setSelectedTab('playlist')}
            disabled={!metadata.isPlaylist}
            className={`flex items-center justify-center gap-2 py-2.5 rounded-xl text-xs font-bold transition-all ${
              selectedTab === 'playlist'
                ? 'bg-[#4A4458] text-[#E8DEF8] shadow-sm'
                : metadata.isPlaylist
                ? 'text-[#CAC4D0] hover:text-[#E6E1E5]'
                : 'text-[#938F99]/40 opacity-40 cursor-not-allowed'
            }`}
          >
            <ListVideo className="w-4 h-4 text-[#4ADE80]" />
            <span>Playlist {metadata.isPlaylist ? `(${metadata.playlistCount})` : ''}</span>
          </button>
        </div>

        {/* Tab Contents */}
        <div className="p-5 flex-1 overflow-y-auto space-y-5">
          {selectedTab === 'video' && (
            <>
              {/* Quality Presets */}
              <div>
                <label className="text-xs font-bold uppercase text-[#938F99] block mb-2 tracking-wider">
                  Target Resolution & Streams
                </label>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                  {videoFormats.map((fmt) => {
                    const isSelected = selectedFormatId === fmt.formatId;
                    return (
                      <button
                        key={fmt.formatId}
                        onClick={() => setSelectedFormatId(fmt.formatId)}
                        className={`p-3 rounded-2xl border text-left transition-all flex items-center justify-between ${
                          isSelected
                            ? 'bg-[#D0BCFF]/15 border-[#D0BCFF] text-[#E8DEF8]'
                            : 'bg-[#1C1B1F] border-[#49454F]/50 text-[#CAC4D0] hover:border-[#938F99]'
                        }`}
                      >
                        <div>
                          <div className="flex items-center gap-1.5">
                            <span className="font-bold text-sm text-[#E6E1E5]">{fmt.resolution}</span>
                            {fmt.fps && <span className="text-[10px] px-1 bg-[#4A4458] rounded text-[#D0BCFF] font-mono">{fmt.fps}fps</span>}
                          </div>
                          <span className="text-[11px] text-[#938F99] block mt-0.5 font-mono">
                            {fmt.vcodec} + {fmt.acodec}
                          </span>
                        </div>
                        <div className="text-right">
                          <span className="text-xs font-bold text-[#D0BCFF] font-mono">
                            {formatBytes(fmt.filesize || 0)}
                          </span>
                        </div>
                      </button>
                    );
                  })}
                </div>
              </div>

              {/* Output Container format */}
              <div>
                <label className="text-xs font-bold uppercase text-[#938F99] block mb-2 tracking-wider">
                  Video Container
                </label>
                <div className="flex gap-2">
                  {[OutputContainer.MP4, OutputContainer.MKV, OutputContainer.WEBM].map((cont) => (
                    <button
                      key={cont}
                      onClick={() => setContainer(cont)}
                      className={`px-4 py-2 rounded-xl text-xs font-bold transition-all ${
                        container === cont
                          ? 'bg-[#D0BCFF] text-[#381E72]'
                          : 'bg-[#1C1B1F] text-[#CAC4D0] hover:text-[#E6E1E5] border border-[#49454F]/50'
                      }`}
                    >
                      {cont.toUpperCase()}
                    </button>
                  ))}
                </div>
              </div>

              {/* Post-Processing Options */}
              <div className="p-4 rounded-2xl bg-[#1C1B1F] border border-[#49454F]/60 space-y-3">
                <span className="text-xs font-bold uppercase text-[#938F99] block tracking-wider">
                  FFmpeg Post-Processing Options
                </span>

                {/* Subtitles toggle */}
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <Subtitles className="w-4 h-4 text-[#D0BCFF]" />
                    <span className="text-xs text-[#E6E1E5] font-medium">Embed Subtitle Streams</span>
                  </div>
                  <input
                    type="checkbox"
                    checked={embedSubs}
                    onChange={(e) => setEmbedSubs(e.target.checked)}
                    className="w-4 h-4 accent-[#D0BCFF] rounded cursor-pointer"
                  />
                </div>

                {/* Thumbnail toggle */}
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <ImageIcon className="w-4 h-4 text-[#80D8FF]" />
                    <span className="text-xs text-[#E6E1E5] font-medium">Embed Video Thumbnail as Cover Art</span>
                  </div>
                  <input
                    type="checkbox"
                    checked={embedThumbnail}
                    onChange={(e) => setEmbedThumbnail(e.target.checked)}
                    className="w-4 h-4 accent-[#D0BCFF] rounded cursor-pointer"
                  />
                </div>
              </div>
            </>
          )}

          {selectedTab === 'audio' && (
            <>
              {/* Audio Format */}
              <div>
                <label className="text-xs font-bold uppercase text-[#938F99] block mb-2 tracking-wider">
                  Audio Format / Codec
                </label>
                <div className="grid grid-cols-2 sm:grid-cols-3 gap-2">
                  {[
                    AudioFormat.MP3,
                    AudioFormat.M4A,
                    AudioFormat.OPUS,
                    AudioFormat.FLAC,
                    AudioFormat.WAV,
                  ].map((fmt) => (
                    <button
                      key={fmt}
                      onClick={() => setAudioFormat(fmt)}
                      className={`p-3 rounded-2xl border text-left transition-all ${
                        audioFormat === fmt
                          ? 'bg-[#80D8FF]/15 border-[#80D8FF] text-[#80D8FF]'
                          : 'bg-[#1C1B1F] border-[#49454F]/50 text-[#CAC4D0] hover:border-[#938F99]'
                      }`}
                    >
                      <span className="font-bold text-sm text-[#E6E1E5] uppercase">{fmt}</span>
                      <span className="text-[11px] text-[#938F99] block mt-0.5">
                        {fmt === 'flac' || fmt === 'wav' ? 'Lossless Master' : 'High Quality'}
                      </span>
                    </button>
                  ))}
                </div>
              </div>

              {/* Bitrate */}
              <div>
                <label className="text-xs font-bold uppercase text-[#938F99] block mb-2 tracking-wider">
                  Audio Bitrate
                </label>
                <div className="flex flex-wrap gap-2">
                  {[
                    AudioQualityPreset.BEST,
                    AudioQualityPreset.KBPS_320,
                    AudioQualityPreset.KBPS_256,
                    AudioQualityPreset.KBPS_192,
                    AudioQualityPreset.KBPS_128,
                  ].map((preset) => (
                    <button
                      key={preset}
                      onClick={() => setAudioQuality(preset)}
                      className={`px-3.5 py-2 rounded-xl text-xs font-semibold transition-all ${
                        audioQuality === preset
                          ? 'bg-[#80D8FF] text-[#00363A]'
                          : 'bg-[#1C1B1F] text-[#CAC4D0] hover:text-[#E6E1E5] border border-[#49454F]/50'
                      }`}
                    >
                      {preset}
                    </button>
                  ))}
                </div>
              </div>

              {/* Cover Art Toggle */}
              <div className="p-4 rounded-2xl bg-[#1C1B1F] border border-[#49454F]/60 flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <ImageIcon className="w-4 h-4 text-[#80D8FF]" />
                  <span className="text-xs text-[#E6E1E5] font-medium">Embed ID3 Album Cover Artwork</span>
                </div>
                <input
                  type="checkbox"
                  checked={embedThumbnail}
                  onChange={(e) => setEmbedThumbnail(e.target.checked)}
                  className="w-4 h-4 accent-[#80D8FF] rounded cursor-pointer"
                />
              </div>
            </>
          )}

          {selectedTab === 'playlist' && metadata.isPlaylist && (
            <div className="space-y-3">
              <div className="flex items-center justify-between pb-2 border-b border-[#49454F]/40">
                <span className="text-xs text-[#CAC4D0]">
                  Selected: {selectedPlaylistIndices.length} of {metadata.playlistEntries.length} items
                </span>
                <button
                  onClick={toggleAllPlaylist}
                  className="text-xs font-bold text-[#D0BCFF] hover:underline"
                >
                  {selectedPlaylistIndices.length === metadata.playlistEntries.length
                    ? 'Deselect All'
                    : 'Select All'}
                </button>
              </div>

              <div className="space-y-2 max-h-60 overflow-y-auto">
                {metadata.playlistEntries.map((entry, idx) => {
                  const isChecked = selectedPlaylistIndices.includes(idx);
                  return (
                    <div
                      key={entry.id || idx}
                      onClick={() => togglePlaylistItem(idx)}
                      className={`flex items-center gap-3 p-2.5 rounded-xl border cursor-pointer transition-all ${
                        isChecked
                          ? 'bg-[#4A4458]/40 border-[#D0BCFF]/60'
                          : 'bg-[#1C1B1F] border-[#49454F]/40 opacity-70'
                      }`}
                    >
                      <input
                        type="checkbox"
                        checked={isChecked}
                        onChange={() => {}}
                        className="w-4 h-4 accent-[#D0BCFF] rounded"
                      />
                      <span className="text-xs font-mono font-bold text-[#938F99] w-5">
                        {String(idx + 1).padStart(2, '0')}
                      </span>
                      <div className="flex-1 min-w-0">
                        <p className="text-xs font-semibold text-[#E6E1E5] truncate">{entry.title}</p>
                        <span className="text-[10px] text-[#938F99]">{formatDuration(entry.durationSeconds)}</span>
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          )}
        </div>

        {/* Footer Download Trigger */}
        <div className="p-4 bg-[#1C1B1F] border-t border-[#49454F]/80 flex items-center justify-between">
          <div className="text-xs text-[#938F99]">
            {selectedTab === 'playlist' ? (
              <span>{selectedPlaylistIndices.length} tasks will be queued</span>
            ) : (
              <span>Est. Size: ~{formatBytes(selectedFormatObj?.filesize || 35000000)}</span>
            )}
          </div>

          <div className="flex items-center gap-2">
            <button
              onClick={onClose}
              className="px-4 py-2.5 rounded-xl bg-[#2B2930] hover:bg-[#4A4458] text-[#CAC4D0] text-xs font-semibold transition-colors"
            >
              Cancel
            </button>
            <button
              onClick={handleStartDownload}
              className="flex items-center gap-2 px-6 py-2.5 rounded-xl bg-[#D0BCFF] text-[#381E72] hover:bg-[#E8DEF8] font-bold text-xs shadow-lg shadow-[#D0BCFF]/20 transition-all"
            >
              <Download className="w-4 h-4" />
              <span>DOWNLOAD NOW</span>
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
