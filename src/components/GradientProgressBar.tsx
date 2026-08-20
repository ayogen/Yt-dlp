import React from 'react';

interface GradientProgressBarProps {
  progress: number; // 0 to 100
  animated?: boolean;
}

export const GradientProgressBar: React.FC<GradientProgressBarProps> = ({
  progress,
  animated = false,
}) => {
  const clamped = Math.max(0, Math.min(100, progress));

  return (
    <div className="w-full h-2 bg-[#4A4458] rounded-full overflow-hidden relative">
      <div
        className={`h-full rounded-full transition-all duration-300 ${
          animated ? 'bg-gradient-to-r from-[#D0BCFF] via-[#80D8FF] to-[#D0BCFF] bg-[length:200%_100%] animate-pulse' : 'bg-gradient-to-r from-[#D0BCFF] to-[#80D8FF]'
        }`}
        style={{ width: `${clamped}%` }}
      />
    </div>
  );
};
