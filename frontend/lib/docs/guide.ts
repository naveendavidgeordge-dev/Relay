import { create, concepts } from "./guide/build";
import { approvals, run, runs } from "./guide/operate";
import { help, scenarios } from "./guide/practice";
import { tour, welcome } from "./guide/start";
import type { DocCollection } from "./types";

/** /guide — end-user documentation. Static content; no backend calls. */
export const USER_GUIDE: DocCollection = {
  base: "/guide",
  title: "User guide",
  tagline: "How workflows work, and how to create, run and approve them.",
  pages: [welcome, tour, concepts, create, run, approvals, runs, scenarios, help],
};
