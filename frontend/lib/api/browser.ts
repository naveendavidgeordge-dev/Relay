import { createRelayClient } from "./client";

/** Browser-side client: same-origin proxy that attaches the token server-side. */
export const browserApi = createRelayClient({ baseUrl: "/api/relay" });
