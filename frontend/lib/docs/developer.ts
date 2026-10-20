import { backend, database, engine } from "./developer/backend";
import { frontend } from "./developer/frontend";
import { architecture, overview } from "./developer/intro";
import { api, security, sidecar } from "./developer/platform";
import { contributing, observability, testing, troubleshooting } from "./developer/quality";
import { configuration, docker, setup } from "./developer/setup";
import type { DocCollection } from "./types";

/** /docs — developer documentation, from first setup to Docker hosting. Static; no backend calls. */
export const DEVELOPER_DOCS: DocCollection = {
  base: "/docs",
  title: "Developer guide",
  tagline: "How Relay is built and run — from the first clone to hosting it in Docker.",
  pages: [
    overview,
    architecture,
    setup,
    configuration,
    docker,
    backend,
    engine,
    database,
    api,
    security,
    frontend,
    sidecar,
    testing,
    observability,
    troubleshooting,
    contributing,
  ],
};
