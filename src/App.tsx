import React from 'react';
import { AppProvider, useApp } from './context/AppContext';
import { NavigationTab } from './types';
import { Header } from './components/Header';
import { Navigation } from './components/Navigation';
import { HomeScreen } from './views/HomeScreen';
import { DownloadsScreen } from './views/DownloadsScreen';
import { HistoryScreen } from './views/HistoryScreen';
import { SettingsScreen } from './views/SettingsScreen';
import { EngineSetupDialog } from './components/EngineSetupDialog';
import { TechnicalLogsDialog } from './components/TechnicalLogsDialog';
import { DiagnosticErrorDialog } from './components/DiagnosticErrorDialog';
import { MediaPlayerModal } from './components/MediaPlayerModal';

const AppContent: React.FC = () => {
  const { activeTab, notification } = useApp();

  return (
    <div className="min-h-screen bg-[#1C1B1F] text-[#E6E1E5] flex flex-col antialiased selection:bg-[#D0BCFF] selection:text-[#381E72]">
      {/* Top Header */}
      <Header />

      {/* Main View Area */}
      <main className="flex-1 max-w-5xl w-full mx-auto p-4 sm:p-6 pb-24">
        {activeTab === NavigationTab.HOME && <HomeScreen />}
        {activeTab === NavigationTab.DOWNLOADS && <DownloadsScreen />}
        {activeTab === NavigationTab.HISTORY && <HistoryScreen />}
        {activeTab === NavigationTab.SETTINGS && <SettingsScreen />}
      </main>

      {/* Persistent Bottom / Tab Navigation */}
      <Navigation />

      {/* Global Dialogs & Modals */}
      <EngineSetupDialog />
      <TechnicalLogsDialog />
      <DiagnosticErrorDialog />
      <MediaPlayerModal />

      {/* Floating Notification Toast */}
      {notification && (
        <div className="fixed bottom-20 left-1/2 -translate-x-1/2 z-50 px-4 py-2.5 rounded-2xl bg-[#4A4458] text-[#E8DEF8] border border-[#D0BCFF]/40 shadow-2xl text-xs font-semibold animate-fade-in flex items-center gap-2">
          <div className="w-2 h-2 rounded-full bg-[#D0BCFF] animate-ping" />
          <span>{notification}</span>
        </div>
      )}
    </div>
  );
};

export function App() {
  return (
    <AppProvider>
      <AppContent />
    </AppProvider>
  );
}

export default App;
