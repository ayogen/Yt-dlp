import React from 'react';
import { LucideIcon } from 'lucide-react';

interface EmptyStateProps {
  icon: LucideIcon;
  title: string;
  subtitle: string;
  actionText?: string;
  onAction?: () => void;
}

export const EmptyState: React.FC<EmptyStateProps> = ({
  icon: Icon,
  title,
  subtitle,
  actionText,
  onAction,
}) => {
  return (
    <div className="flex flex-col items-center justify-center p-8 text-center bg-[#2B2930]/40 rounded-2xl border border-[#49454F]/50 my-6">
      <div className="w-16 h-16 rounded-full bg-[#4A4458]/40 flex items-center justify-center text-[#D0BCFF] mb-4">
        <Icon className="w-8 h-8" />
      </div>
      <h3 className="text-lg font-bold text-[#E6E1E5] mb-1">{title}</h3>
      <p className="text-sm text-[#CAC4D0] max-w-sm mb-5 leading-relaxed">{subtitle}</p>
      {actionText && onAction && (
        <button
          onClick={onAction}
          className="px-5 py-2.5 rounded-xl bg-[#D0BCFF] text-[#381E72] font-semibold text-sm hover:bg-[#E8DEF8] transition-colors shadow-sm"
        >
          {actionText}
        </button>
      )}
    </div>
  );
};
