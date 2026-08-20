import React from 'react';
import { AlertOctagon, Copy, X, Check, ArrowRight } from 'lucide-react';
import { useApp } from '../context/AppContext';

export const DiagnosticErrorDialog: React.FC = () => {
  const { errorDialog, setErrorDialog, copyToClipboard } = useApp();

  if (!errorDialog) return null;

  const fullDiagnosticInfo = `Title: ${errorDialog.title}\nReason: ${errorDialog.reason}\nSuggested Action: ${errorDialog.suggestedAction}\n\nTechnical Details:\n${errorDialog.technicalDetails}`;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-sm animate-fade-in">
      <div className="bg-[#2B2930] border border-[#F2B8B5]/40 rounded-3xl w-full max-w-lg shadow-2xl overflow-hidden">
        {/* Header */}
        <div className="flex items-center justify-between p-5 bg-[#F2B8B5]/10 border-b border-[#F2B8B5]/30">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-[#F2B8B5]/20 flex items-center justify-center text-[#F2B8B5]">
              <AlertOctagon className="w-6 h-6" />
            </div>
            <div>
              <h2 className="text-base font-bold text-[#F2B8B5]">{errorDialog.title}</h2>
              <p className="text-xs text-[#CAC4D0]">Error Analysis & Automated Diagnosis</p>
            </div>
          </div>
          <button
            onClick={() => setErrorDialog(null)}
            className="w-8 h-8 rounded-full flex items-center justify-center text-[#CAC4D0] hover:text-white hover:bg-[#4A4458] transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Body */}
        <div className="p-5 space-y-4 max-h-[70vh] overflow-y-auto">
          {/* Reason Card */}
          <div className="p-3.5 rounded-2xl bg-[#1C1B1F] border border-[#49454F]/60">
            <span className="text-[11px] font-bold uppercase text-[#938F99] block mb-1">Root Cause</span>
            <p className="text-xs text-[#E6E1E5] leading-relaxed">{errorDialog.reason}</p>
          </div>

          {/* Suggested Action Card */}
          <div className="p-3.5 rounded-2xl bg-[#D0BCFF]/10 border border-[#D0BCFF]/30">
            <span className="text-[11px] font-bold uppercase text-[#D0BCFF] flex items-center gap-1.5 mb-1">
              <ArrowRight className="w-3.5 h-3.5" />
              Suggested Solution
            </span>
            <p className="text-xs text-[#E8DEF8] leading-relaxed font-medium">
              {errorDialog.suggestedAction}
            </p>
          </div>

          {/* Technical Trace */}
          {errorDialog.technicalDetails && (
            <div className="p-3 rounded-xl bg-[#141218] border border-[#49454F]/40 font-mono text-[11px] text-[#938F99] max-h-40 overflow-y-auto whitespace-pre-wrap">
              {errorDialog.technicalDetails}
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="flex items-center justify-between p-4 bg-[#1C1B1F] border-t border-[#49454F]/80">
          <button
            onClick={() => copyToClipboard(fullDiagnosticInfo, 'Diagnostic Report')}
            className="flex items-center gap-2 px-4 py-2 rounded-xl bg-[#2B2930] hover:bg-[#4A4458] text-[#CAC4D0] hover:text-[#E6E1E5] text-xs font-semibold transition-all"
          >
            <Copy className="w-3.5 h-3.5 text-[#D0BCFF]" />
            <span>Copy Diagnostics</span>
          </button>
          <button
            onClick={() => setErrorDialog(null)}
            className="px-5 py-2 rounded-xl bg-[#D0BCFF] text-[#381E72] hover:bg-[#E8DEF8] text-xs font-bold transition-all shadow-sm"
          >
            Dismiss
          </button>
        </div>
      </div>
    </div>
  );
};
