/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  // Style lint shouldn't block a deploy; TypeScript errors still do.
  eslint: { ignoreDuringBuilds: true },
};

export default nextConfig;
