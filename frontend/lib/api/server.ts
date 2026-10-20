import "server-only";
import { createRelayClient } from "./client";

/**
 * Server-side client for React Server Components. The platform token stays on the server
 * (RELAY_API_TOKEN) — the browser only ever talks to the /api/relay proxy.
 */
export function serverApi() {
  return createRelayClient({
    baseUrl: process.env.RELAY_API_URL ?? "http://localhost:8080",
    token: process.env.RELAY_API_TOKEN ?? "relay-demo-token",
    init: { cache: "no-store" },
  });
}
