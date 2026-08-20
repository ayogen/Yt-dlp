import React from 'react';
import { Home, ArrowDownToLine, History as HistoryIcon, Settings } from 'lucide-react';
import { useApp } from '../context/AppContext';
import { NavigationTab, DownloadStatus } from '../types';

export const Navigation: React.FC = () => {
  const { activeTab, setActiveTab, tasks } = useApp();

  const activeDownloadsCount = tasks.filter(
    (t) => t.status === DownloadStatus.DOWNLOADING || t.status === DownloadStatus.ANALYZING || t.status === DownloadStatus.QUEUED
  ).length;

  const navItems = [
    {
      tab: NavigationTab.HOME,
      label: 'Home',
      icon: Home,
    },
    {
      tab: NavigationTab.DOWNLOADS,
      label: 'Downloads',
      icon: ArrowDownToLine,
      badge: activeDownloadsCount > 0 ? activeDownloadsCount : null,
    },
    {
      tab: NavigationTab.HISTORY,
      label: 'History',
      icon: HistoryIcon,
    },
    {
      tab: NavigationTab.SETTINGS,
      label: 'Settings',
      icon: Settings,
    },
  ];

  return (
    <nav className="fixed bottom-0 left-0 right-0 z-20 bg-[#1C1B1F]/95 backdrop-blur-lg border-t border-[#49454F]/70 px-4 py-2 sm:px-6">
      <div className="max-w-md mx-auto grid grid-cols-4 gap-2">
        {navItems.map((item) => {
          const Icon = item.icon;
          const isActive = activeTab === item.tab;

          return (
            <button
              key={item.tab}
              onClick={() => setActiveTab(item.tab)}
              className={`relative flex flex-col items-center justify-center py-1.5 px-3 rounded-2xl transition-all duration-200 ${
                isActive
                  ? 'bg-[#4A4458] text-[#E8DEF8] shadow-sm shadow-[#D0BCFF]/10'
                  : 'text-[#CAC4D0] hover:text-[#E6E1E5] hover:bg-[#2B2930]'
              }`}
            >
              <div className="relative">
                <Icon className={`w-5 h-5 transition-transform duration-200 ${isActive ? 'scale-110 text-[#D0BCFF]' : ''}`} />
                {item.badge !== null && (
                  <span className="absolute -top-1.5 -right-2 px-1.5 py-0.2 bg-[#D0BCFF] text-[#381E72] font-black text-[10px] rounded-full animate-bounce">
                    {item.badge}
                  </span>
                )}
              </div>
              <span className={`text-[11px] mt-1 font-medium ${isActive ? 'font-bold text-[#E8DEF8]' : 'text-[#938F99]'}`}>
                {item.label}
              </span>
            </button>
          );
        })}
      </div>
    </nav>
  );
};
