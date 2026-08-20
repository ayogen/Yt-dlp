import React, { useState, useMemo } from 'react';
import {
  History as HistoryIcon,
  Search,
  X,
  Trash2,
  Play,
  Share2,
  Copy,
  Folder,
  Calendar,
  HardDrive,
  Filter,
  ArrowUpDown,
  Check,
} from 'lucide-react';
import { useApp } from '../context/AppContext';
import { DownloadHistoryItem, HistorySortOrder, MediaType } from '../types';
import { formatBytes } from '../utils/formatters';
import { MediaTypeBadge } from '../components/Badges';
import { EmptyState } from '../components/EmptyState';

export const HistoryScreen: React.FC = () => {
  const { history, clearHistory, deleteHistoryItem, setPlaybackItem, copyToClipboard, showNotification } = useApp();

  const [searchQuery, setSearchQuery] = useState('');
  const [filterType, setFilterType] = useState<'ALL' | MediaType>('ALL');
  const [sortOrder, setSortOrder] = useState<HistorySortOrder>(HistorySortOrder.NEWEST);
  const [showClearConfirm, setShowClearConfirm] = useState(false);

  const filteredHistory = useMemo(() => {
    return history
      .filter((item) => {
        // Filter by type
        if (filterType !== 'ALL' && item.mediaType !== filterType) {
          return false;
        }
        // Filter by search query
        if (searchQuery.trim()) {
          const q = searchQuery.toLowerCase();
          return (
            item.title.toLowerCase().includes(q) ||
            item.uploader.toLowerCase().includes(q) ||
            item.filePath.toLowerCase().includes(q)
          );
        }
        return true;
      })
      .sort((a, b) => {
        switch (sortOrder) {
          case HistorySortOrder.NEWEST:
            return b.completedTimestamp - a.completedTimestamp;
          case HistorySortOrder.OLDEST:
            return a.completedTimestamp - b.completedTimestamp;
          case HistorySortOrder.SIZE_DESC:
            return b.fileSize - a.fileSize;
          case HistorySortOrder.NAME_ASC:
            return a.title.localeCompare(b.title);
          default:
            return 0;
        }
      });
  }, [history, searchQuery, filterType, sortOrder]);

  return (
    <div className="space-y-5 pb-16">
      {/* Search & Filter Header Bar */}
      <div className="p-4 sm:p-5 rounded-3xl bg-[#2B2930] border border-[#49454F]/70 shadow-xl space-y-4">
        {/* Search input */}
        <div className="relative flex items-center bg-[#1C1B1F] border border-[#49454F] focus-within:border-[#D0BCFF] rounded-2xl p-1 transition-all shadow-inner">
          <Search className="w-4 h-4 text-[#938F99] ml-3 shrink-0" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search downloads by title, author, or filepath..."
            className="w-full bg-transparent px-3 py-2 text-xs sm:text-sm text-[#E6E1E5] placeholder-[#938F99] focus:outline-none"
          />
          {searchQuery && (
            <button
              onClick={() => setSearchQuery('')}
              className="p-1.5 text-[#938F99] hover:text-[#E6E1E5] transition-colors rounded-lg mr-1"
            >
              <X className="w-4 h-4" />
            </button>
          )}
        </div>

        {/* Filters and Sorters */}
        <div className="flex flex-wrap items-center justify-between gap-3 pt-1">
          {/* Media Type Filter Chips */}
          <div className="flex items-center gap-1.5 overflow-x-auto py-1">
            <Filter className="w-3.5 h-3.5 text-[#938F99] mr-1" />
            {[
              { label: 'All', val: 'ALL' },
              { label: 'Videos', val: MediaType.VIDEO },
              { label: 'Audio Only', val: MediaType.AUDIO },
            ].map((f) => (
              <button
                key={f.val}
                onClick={() => setFilterType(f.val as any)}
                className={`px-3 py-1.5 rounded-xl text-xs font-semibold transition-all ${
                  filterType === f.val
                    ? 'bg-[#D0BCFF] text-[#381E72] shadow-sm'
                    : 'bg-[#1C1B1F] text-[#CAC4D0] hover:text-[#E6E1E5] border border-[#49454F]/50'
                }`}
              >
                {f.label}
              </button>
            ))}
          </div>

          {/* Sort selection and Clear History */}
          <div className="flex items-center gap-2">
            <div className="flex items-center gap-1.5 bg-[#1C1B1F] border border-[#49454F]/50 rounded-xl px-2.5 py-1">
              <ArrowUpDown className="w-3 h-3 text-[#D0BCFF]" />
              <select
                value={sortOrder}
                onChange={(e) => setSortOrder(e.target.value as HistorySortOrder)}
                className="bg-transparent text-xs text-[#CAC4D0] focus:outline-none cursor-pointer"
              >
                <option value={HistorySortOrder.NEWEST} className="bg-[#2B2930] text-[#E6E1E5]">
                  Newest First
                </option>
                <option value={HistorySortOrder.OLDEST} className="bg-[#2B2930] text-[#E6E1E5]">
                  Oldest First
                </option>
                <option value={HistorySortOrder.SIZE_DESC} className="bg-[#2B2930] text-[#E6E1E5]">
                  Largest Size
                </option>
                <option value={HistorySortOrder.NAME_ASC} className="bg-[#2B2930] text-[#E6E1E5]">
                  Name (A-Z)
                </option>
              </select>
            </div>

            {history.length > 0 && (
              <button
                onClick={() => setShowClearConfirm(true)}
                className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-[#1C1B1F] hover:bg-[#F2B8B5]/20 text-[#CAC4D0] hover:text-[#F2B8B5] text-xs font-semibold transition-all border border-[#49454F]/50"
              >
                <Trash2 className="w-3.5 h-3.5" />
                <span className="hidden sm:inline">Clear History</span>
              </button>
            )}
          </div>
        </div>
      </div>

      {/* Clear History Confirmation Banner */}
      {showClearConfirm && (
        <div className="p-4 rounded-2xl bg-[#F2B8B5]/15 border border-[#F2B8B5]/40 flex flex-wrap items-center justify-between gap-3 animate-fade-in">
          <div className="text-xs text-[#E6E1E5]">
            <strong className="text-[#F2B8B5]">Confirm:</strong> Clear all {history.length} saved history records?
          </div>
          <div className="flex items-center gap-2">
            <button
              onClick={() => {
                clearHistory();
                setShowClearConfirm(false);
              }}
              className="px-3.5 py-1.5 rounded-xl bg-[#F2B8B5] text-[#601410] font-bold text-xs hover:bg-[#F9DEDC] transition-colors"
            >
              Yes, Clear All
            </button>
            <button
              onClick={() => setShowClearConfirm(false)}
              className="px-3.5 py-1.5 rounded-xl bg-[#2B2930] text-[#CAC4D0] text-xs font-semibold hover:bg-[#4A4458] transition-colors"
            >
              Cancel
            </button>
          </div>
        </div>
      )}

      {/* History Items List */}
      {filteredHistory.length === 0 ? (
        <EmptyState
          icon={HistoryIcon}
          title="No History Items Found"
          subtitle={
            searchQuery || filterType !== 'ALL'
              ? 'No downloads match your search or filter criteria.'
              : 'Completed downloads will appear here with instant in-browser playback.'
          }
        />
      ) : (
        <div className="space-y-3">
          {filteredHistory.map((item) => (
            <div
              key={item.id}
              className="p-4 rounded-2xl bg-[#2B2930] border border-[#49454F]/60 shadow-md space-y-3 transition-all hover:border-[#938F99]/60"
            >
              <div className="flex gap-3 items-start">
                {/* Thumbnail with quick play button */}
                <div
                  onClick={() =>
                    setPlaybackItem({
                      title: item.title,
                      url: item.mediaUrl || 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4',
                      thumbnail: item.thumbnail,
                      mediaType: item.mediaType,
                    })
                  }
                  className="relative w-24 sm:w-28 aspect-video rounded-xl overflow-hidden bg-black shrink-0 border border-[#49454F]/50 cursor-pointer group"
                >
                  <img
                    src={item.thumbnail}
                    alt={item.title}
                    className="w-full h-full object-cover group-hover:scale-105 transition-transform"
                    referrerPolicy="no-referrer"
                  />
                  <div className="absolute inset-0 bg-black/40 flex items-center justify-center opacity-80 group-hover:opacity-100 transition-opacity">
                    <Play className="w-6 h-6 text-white fill-white drop-shadow" />
                  </div>
                </div>

                {/* Details */}
                <div className="flex-1 min-w-0">
                  <div className="flex flex-wrap items-center gap-2 mb-1">
                    <MediaTypeBadge type={item.mediaType} />
                    <span className="text-[10px] font-mono text-[#4ADE80] bg-[#4ADE80]/15 px-2 py-0.5 rounded">
                      {formatBytes(item.fileSize)}
                    </span>
                  </div>

                  <h2 className="text-xs sm:text-sm font-bold text-[#E6E1E5] line-clamp-2">
                    {item.title}
                  </h2>
                  <p className="text-[11px] text-[#CAC4D0] mt-0.5 truncate">{item.uploader}</p>
                  <p className="text-[11px] text-[#938F99] mt-0.5 font-mono truncate">{item.filePath}</p>
                </div>
              </div>

              {/* Action Toolbar */}
              <div className="flex flex-wrap items-center justify-between gap-2 pt-2 border-t border-[#49454F]/40 text-xs">
                <span className="text-[11px] text-[#938F99] flex items-center gap-1">
                  <Calendar className="w-3 h-3" />
                  {new Date(item.completedTimestamp).toLocaleDateString()} {new Date(item.completedTimestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                </span>

                <div className="flex items-center gap-2">
                  <button
                    onClick={() =>
                      setPlaybackItem({
                        title: item.title,
                        url: item.mediaUrl || 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4',
                        thumbnail: item.thumbnail,
                        mediaType: item.mediaType,
                      })
                    }
                    className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-[#D0BCFF] text-[#381E72] hover:bg-[#E8DEF8] font-bold text-xs transition-colors shadow-sm"
                  >
                    <Play className="w-3.5 h-3.5 fill-current" />
                    <span>Play</span>
                  </button>

                  <button
                    onClick={() => copyToClipboard(item.filePath, 'File Path')}
                    className="flex items-center gap-1 px-2.5 py-1.5 rounded-lg bg-[#1C1B1F] hover:bg-[#4A4458] text-[#CAC4D0] hover:text-[#E6E1E5] text-xs transition-colors"
                    title="Copy local filepath"
                  >
                    <Copy className="w-3 h-3 text-[#D0BCFF]" />
                    <span className="hidden sm:inline">Path</span>
                  </button>

                  <button
                    onClick={() => deleteHistoryItem(item.id)}
                    className="p-1.5 rounded-lg text-[#938F99] hover:text-[#F2B8B5] hover:bg-[#F2B8B5]/10 transition-colors"
                    title="Delete record"
                  >
                    <Trash2 className="w-4 h-4" />
                  </button>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};
