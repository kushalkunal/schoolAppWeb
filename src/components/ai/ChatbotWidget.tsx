'use client';

import { useEffect, useRef, useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { Bot, Send, Sparkles, X, Minus } from 'lucide-react';
import { aiApi } from '@/api/endpoints/ai';
import { useBranding } from '@/brand/BrandingProvider';
import { isApiError } from '@/api/errors';
import { Button } from '@/components/ui/Button';
import { cn } from '@/lib/utils';

interface Message {
  id: number;
  role: 'user' | 'assistant';
  text: string;
  ts: number;
}

interface ChatbotWidgetProps {
  tenantId: string;
}

/**
 * Floating AI assistant — bottom-right of every tenant page. Sits above all content
 * (z-40, below toasts at z-50). Single-turn for now; multi-turn memory comes when
 * the backend grows a conversation table.
 *
 * Design notes:
 * - Brand-gradient header, glass body, soft shadows. Premium feel.
 * - Collapsed = circular pill button. Expanded = 380×520 panel anchored bottom-right.
 * - On narrow screens the panel becomes near-fullscreen (max-w-[calc(100vw-1rem)]).
 * - Failure modes: shows the API error message inline; never blocks UI.
 */
export function ChatbotWidget({ tenantId }: ChatbotWidgetProps) {
  const b = useBranding();
  const [open, setOpen]       = useState(false);
  const [minimised, setMin]   = useState(false);
  const [input, setInput]     = useState('');
  const [messages, setMessages] = useState<Message[]>([{
    id: 0,
    role: 'assistant',
    text: `Hi 👋  I'm ${b.shortName}'s AI assistant. Ask me anything about fees, exams, policies, or what to do next.`,
    ts: Date.now(),
  }]);
  const scrollerRef = useRef<HTMLDivElement>(null);

  const ask = useMutation({
    mutationFn: (q: string) => aiApi.chat(tenantId, q),
    onSuccess: (data) => {
      setMessages((m) => [...m, { id: Date.now(), role: 'assistant', text: data.reply, ts: Date.now() }]);
    },
    onError: (err) => {
      const msg = isApiError(err) ? err.message : 'Sorry — I couldn\'t reach the AI service. Please try again.';
      setMessages((m) => [...m, { id: Date.now(), role: 'assistant', text: msg, ts: Date.now() }]);
    },
  });

  // Auto-scroll on new message.
  useEffect(() => {
    if (scrollerRef.current) scrollerRef.current.scrollTop = scrollerRef.current.scrollHeight;
  }, [messages, ask.isPending]);

  function submit() {
    const q = input.trim();
    if (!q || ask.isPending) return;
    setMessages((m) => [...m, { id: Date.now(), role: 'user', text: q, ts: Date.now() }]);
    setInput('');
    ask.mutate(q);
  }

  // ---------------- Collapsed launcher ----------------
  if (!open) {
    return (
      <button
        onClick={() => { setOpen(true); setMin(false); }}
        className={cn(
          'no-print fixed z-40 bottom-5 right-5 inline-flex items-center gap-2',
          'px-4 h-12 rounded-full text-primary-fg font-medium text-sm',
          'bg-brand-gradient shadow-lift hover:-translate-y-px transition-all',
          'animate-fade-in',
        )}
        aria-label="Open AI assistant"
      >
        <Sparkles size={16} />
        <span className="hidden sm:inline">Ask {b.shortName} AI</span>
      </button>
    );
  }

  // ---------------- Expanded panel ----------------
  return (
    <div
      className={cn(
        'no-print fixed z-40 bottom-5 right-5 w-[380px] max-w-[calc(100vw-1rem)]',
        'bg-white border border-slate-200 rounded-brand shadow-lift overflow-hidden',
        'flex flex-col animate-slide-up',
        minimised ? 'h-14' : 'h-[min(520px,calc(100vh-2.5rem))]',
      )}
    >
      {/* Header — brand gradient */}
      <header className="bg-brand-gradient text-primary-fg px-4 py-3 flex items-center justify-between">
        <div className="flex items-center gap-2">
          <span className="w-7 h-7 rounded-full bg-white/15 grid place-items-center">
            <Bot size={14} />
          </span>
          <div>
            <div className="text-sm font-semibold leading-tight">{b.shortName} AI</div>
            <div className="text-[10px] text-white/75 leading-tight">Powered by school's LLM</div>
          </div>
        </div>
        <div className="flex items-center gap-1">
          <button
            onClick={() => setMin(!minimised)}
            className="w-7 h-7 grid place-items-center rounded hover:bg-white/15 transition"
            aria-label={minimised ? 'Expand' : 'Minimise'}
          >
            <Minus size={14} />
          </button>
          <button
            onClick={() => setOpen(false)}
            className="w-7 h-7 grid place-items-center rounded hover:bg-white/15 transition"
            aria-label="Close"
          >
            <X size={14} />
          </button>
        </div>
      </header>

      {!minimised && (
        <>
          {/* Message list */}
          <div ref={scrollerRef} className="flex-1 overflow-y-auto px-4 py-3 space-y-3 bg-slate-50/40">
            {messages.map((m) => <ChatBubble key={m.id} msg={m} brandShort={b.shortName} />)}
            {ask.isPending && <TypingBubble />}
          </div>

          {/* Composer */}
          <form
            onSubmit={(e) => { e.preventDefault(); submit(); }}
            className="border-t border-slate-200 bg-white p-2 flex items-end gap-2"
          >
            <textarea
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' && !e.shiftKey) {
                  e.preventDefault();
                  submit();
                }
              }}
              rows={1}
              placeholder="Ask anything…"
              className="flex-1 resize-none rounded-brand border border-slate-200 px-3 py-2 text-sm focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/15 transition min-h-[40px] max-h-32"
              disabled={ask.isPending}
            />
            <Button
              type="submit"
              size="sm"
              disabled={!input.trim() || ask.isPending}
              loading={ask.isPending}
              className="h-10 w-10 px-0"
              aria-label="Send"
            >
              {!ask.isPending && <Send size={14} />}
            </Button>
          </form>
        </>
      )}
    </div>
  );
}

function ChatBubble({ msg, brandShort }: { msg: Message; brandShort: string }) {
  const isUser = msg.role === 'user';
  return (
    <div className={cn('flex items-end gap-2', isUser ? 'flex-row-reverse' : 'flex-row')}>
      {!isUser && (
        <div className="w-6 h-6 rounded-full bg-primary-soft text-primary grid place-items-center shrink-0">
          <Bot size={12} />
        </div>
      )}
      <div
        className={cn(
          'rounded-2xl px-3 py-2 text-sm leading-relaxed max-w-[80%] whitespace-pre-wrap break-words',
          isUser
            ? 'bg-primary text-primary-fg rounded-br-sm shadow-sm'
            : 'bg-white border border-slate-200 text-slate-800 rounded-bl-sm',
        )}
      >
        {msg.text}
      </div>
    </div>
  );
}

function TypingBubble() {
  return (
    <div className="flex items-end gap-2">
      <div className="w-6 h-6 rounded-full bg-primary-soft text-primary grid place-items-center shrink-0">
        <Bot size={12} />
      </div>
      <div className="bg-white border border-slate-200 rounded-2xl rounded-bl-sm px-3 py-2.5 flex items-center gap-1">
        <span className="w-1.5 h-1.5 rounded-full bg-slate-400 animate-pulse [animation-delay:0ms]" />
        <span className="w-1.5 h-1.5 rounded-full bg-slate-400 animate-pulse [animation-delay:150ms]" />
        <span className="w-1.5 h-1.5 rounded-full bg-slate-400 animate-pulse [animation-delay:300ms]" />
      </div>
    </div>
  );
}
