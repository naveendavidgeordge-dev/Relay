# ADR-0001: Monorepo with three toolchains

- **Status:** accepted
- **Context:** Relay has a Java engine/API, a TypeScript console and a Python AI adapter, plus
  read-only fixtures (`data/`, `scripts/`) that every part and the CI must share at exactly one
  version. The smoke test and duplication check are pinned to the fixture files.
- **Decision:** One repository. `backend/` is a standalone Gradle build, `frontend/` is an npm
  workspace (root `package.json`), `ai-sidecar/` is a Python project. Fixtures stay at the root and
  are copied into the backend classpath by `processResources`, never duplicated by hand. A root
  `Makefile` and one CI workflow drive all three.
- **Consequences:** One PR can change the API and the console together; the drill runs against the
  exact fixtures in the commit. CI installs three toolchains. The brief mentions `pnpm` in CI and npm
  workspaces in the layout. We use npm throughout (it ships with Node), recorded as conflict C5 in
  DESIGN/00.
