/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  // Don't fail the build on ESLint warnings — typecheck is enforced separately.
  eslint: { ignoreDuringBuilds: false },
};
module.exports = nextConfig;
