'use client';

/**
 * BottomSheet — mobile-first drawer built on vaul.
 *
 * On mobile: slides up from the bottom with snap points and swipe-to-dismiss.
 * On desktop (sm+): renders nothing — callers should use a Dialog or inline panel instead.
 *
 * Usage:
 *   <BottomSheet open={open} onOpenChange={setOpen} title="Select class">
 *     <div>...content...</div>
 *   </BottomSheet>
 */

import { Drawer } from 'vaul';
import { X } from 'lucide-react';
import { cn } from '@/lib/utils';

interface BottomSheetProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title?: string;
  /** Extra className on the content panel */
  className?: string;
  children: React.ReactNode;
  /** Snap points as fractions of screen height, e.g. [0.4, 0.85] */
  snapPoints?: number[];
}

export function BottomSheet({
  open,
  onOpenChange,
  title,
  className,
  children,
  snapPoints,
}: BottomSheetProps) {
  return (
    <Drawer.Root
      open={open}
      onOpenChange={onOpenChange}
      snapPoints={snapPoints}
      shouldScaleBackground
    >
      <Drawer.Portal>
        <Drawer.Overlay className="fixed inset-0 bg-black/40 z-50" />
        <Drawer.Content
          className={cn(
            'fixed bottom-0 inset-x-0 z-50 bg-white rounded-t-2xl flex flex-col outline-none',
            'max-h-[90dvh]',
            className,
          )}
          style={{ paddingBottom: 'env(safe-area-inset-bottom)' }}
          aria-labelledby={title ? 'bottom-sheet-title' : undefined}
        >
          {/* Drag handle */}
          <div className="flex justify-center pt-3 pb-1 shrink-0">
            <div className="w-10 h-1 rounded-full bg-slate-200" aria-hidden />
          </div>

          {/* Title row */}
          {title && (
            <div className="flex items-center justify-between px-4 py-2.5 border-b border-slate-100 shrink-0">
              <Drawer.Title
                id="bottom-sheet-title"
                className="font-semibold text-sm text-slate-800"
              >
                {title}
              </Drawer.Title>
              <button
                onClick={() => onOpenChange(false)}
                className="p-1.5 rounded-full hover:bg-slate-100 text-slate-500 transition"
                aria-label="Close"
              >
                <X size={16} />
              </button>
            </div>
          )}

          {/* Scrollable body */}
          <div className="overflow-y-auto overscroll-contain flex-1">
            {children}
          </div>
        </Drawer.Content>
      </Drawer.Portal>
    </Drawer.Root>
  );
}
