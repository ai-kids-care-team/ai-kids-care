import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: 'export',
  transpilePackages: ['reagraph', '@react-three/fiber', '@react-three/drei'],
};

export default nextConfig;
