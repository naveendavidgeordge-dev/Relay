import { NextResponse, type NextRequest } from "next/server";

/**
 * Same-origin proxy to the Relay API. The browser never sees RELAY_API_TOKEN; only a fixed set of
 * route prefixes is forwarded (no /hooks — webhooks are for external callers, not the console).
 */
const ALLOWED = ["workflows", "runs", "approvals", "catalog", "samples"];

export const dynamic = "force-dynamic";

async function forward(req: NextRequest, { params }: { params: { path: string[] } }) {
  const [head] = params.path;
  if (!head || !ALLOWED.includes(head)) {
    return NextResponse.json(
      { error: { message: "Not proxied", code: "not_found" } },
      { status: 404 },
    );
  }
  const base = (process.env.RELAY_API_URL ?? "http://localhost:8080").replace(/\/$/, "");
  const target = `${base}/${params.path.map(encodeURIComponent).join("/")}${req.nextUrl.search}`;
  const body = req.method === "GET" || req.method === "HEAD" ? undefined : await req.text();
  try {
    const upstream = await fetch(target, {
      method: req.method,
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
        Authorization: `Bearer ${process.env.RELAY_API_TOKEN ?? "relay-demo-token"}`,
      },
      body: body || undefined,
      cache: "no-store",
    });
    return new NextResponse(await upstream.text(), {
      status: upstream.status,
      headers: { "Content-Type": upstream.headers.get("Content-Type") ?? "application/json" },
    });
  } catch {
    return NextResponse.json(
      { error: { message: "Relay API is unreachable", code: "upstream_unavailable" } },
      { status: 502 },
    );
  }
}

export { forward as GET, forward as POST, forward as PUT };
