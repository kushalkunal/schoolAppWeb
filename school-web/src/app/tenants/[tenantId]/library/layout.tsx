'use client';

import Link from 'next/link';
import { useParams, usePathname } from 'next/navigation';
import { BookOpen, BookCopy, BookMarked } from 'lucide-react';
import { PageHeader } from '@/components/ui/PageHeader';
import { cn } from '@/lib/utils';

export default function LibraryLayout({ children }: { children: React.ReactNode }) {
  const params = useParams();
  const pathname = usePathname() ?? '';
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';

  const tabs = [
    { label: 'Books',     href: `/tenants/${tenantId}/library/books`,     icon: BookOpen },
    { label: 'Issues',    href: `/tenants/${tenantId}/library/issues`,    icon: BookCopy },
  ];

  return (
    <div className="space-y-5">
      <PageHeader
        title="Library"
        description="Catalogue, issuing, returns, and fines"
        icon={<BookMarked size={18} />}
      />

      <nav className="border-b border-slate-200 -mx-6 px-6 flex gap-1 text-sm">
        {tabs.map((t) => {
          const active = pathname.startsWith(t.href);
          return (
            <Link
              key={t.href}
              href={t.href}
              className={cn(
                'inline-flex items-center gap-1.5 px-3 py-2 border-b-2 -mb-px transition',
                active
                  ? 'border-primary text-primary font-medium'
                  : 'border-transparent text-slate-600 hover:text-slate-900',
              )}
            >
              <t.icon size={14} />
              {t.label}
            </Link>
          );
        })}
      </nav>

      {children}
    </div>
  );
}
