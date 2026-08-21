import React from 'react';

export function App() {
  return (
    <div className="min-h-screen bg-[#1C1B1F] text-[#E6E1E5] flex flex-col items-center justify-center p-6 text-center">
      <div className="max-w-md w-full p-8 rounded-2xl bg-[#2B2930] border border-[#49454F] shadow-2xl space-y-4">
        <div className="w-16 h-16 rounded-2xl bg-[#D0BCFF] text-[#381E72] flex items-center justify-center mx-auto text-2xl font-bold">
          ⬇️
        </div>
        <h1 className="text-2xl font-bold text-[#E6E1E5]">Video Downloader</h1>
        <p className="text-sm text-[#CAC4D0]">
          Native Android application powered by Kotlin, Jetpack Compose, Room Database, and yt-dlp.
        </p>
        <div className="pt-2">
          <span className="inline-flex items-center px-3 py-1 rounded-full text-xs font-semibold bg-[#4A4458] text-[#E8DEF8]">
            Android Kotlin + Compose Engine Ready
          </span>
        </div>
      </div>
    </div>
  );
}

export default App;
