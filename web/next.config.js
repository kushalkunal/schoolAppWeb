/** @type {import('next').NextConfig} */
const withPWA = require('next-pwa')({
  dest: 'public',
  register: true,
  skipWaiting: true,
  // Disable in development — hot reload and service workers conflict.
  disable: process.env.NODE_ENV === 'development',
  buildExcludes: [/middleware-manifest\.json$/],
});

const nextConfig = {
  reactStrictMode: true,
  // Don't fail the build on ESLint warnings — typecheck is enforced separately.
  eslint: { ignoreDuringBuilds: false },
};

module.exports = withPWA(nextConfig);
