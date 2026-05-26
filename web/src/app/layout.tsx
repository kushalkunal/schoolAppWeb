import type { Metadata } from 'next';
import { Inter } from 'next/font/google';
import './globals.css';
import { Providers } from '@/lib/providers';
import { DEFAULT_BRANDING } from '@/brand/branding.config';

/**
 * Self-hosted Inter — Next/font inlines the woff2 + ensures no FOUT. The variable
 * exposes `--font-inter` which Tailwind's `font-sans` references via the config.
 */
const inter = Inter({
  subsets: ['latin'],
  display: 'swap',
  variable: '--font-inter',
});

export const metadata: Metadata = {
  title: DEFAULT_BRANDING.schoolName,
  description: DEFAULT_BRANDING.tagline,
  icons: [{ rel: 'icon', url: DEFAULT_BRANDING.faviconUrl }],
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" className={inter.variable}>
      <body>
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
