import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));

/** @type {import("next").NextConfig} */
const nextConfig = {
  output: "standalone",
  reactStrictMode: true,
  poweredByHeader: false,
  experimental: {
    // npm workspaces hoist node_modules to the repo root; trace from there so the standalone
    // bundle contains them (server entry: .next/standalone/frontend/server.js).
    outputFileTracingRoot: path.join(here, ".."),
  },
};

export default nextConfig;
