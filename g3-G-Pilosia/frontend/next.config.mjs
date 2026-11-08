/** @type {import('next').NextConfig} */
const nextConfig = {
  async rewrites() {
    const gateway = process.env.API_GATEWAY_URL || "http://localhost:8000";
    return [
      { source: "/api/:path*", destination: `${gateway}/api/:path*` },
      { source: "/graphql", destination: `${gateway}/graphql` },
      { source: "/notifications", destination: `${gateway}/notifications` },
    ];
  },
};

export default nextConfig;
