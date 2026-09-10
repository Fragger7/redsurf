# Archived documents — SUPERSEDED, DO NOT TRUST

These files were written by Gemini-based agents (Google AI Studio / Antigravity CLI) during the
original RedSurf build. **They are kept only for provenance. Do not use them to decide anything.**

Every one of them makes claims that were verified false against the code on 2026-09-10:

| File | Why it's archived |
|---|---|
| `PROJECT_VISION_AND_HISTORY.md` | Not a vision document — an agent's summary of its own work. Lost the actual product vision entirely. The real one is `../vision/PRODUCT_VISION.md`. |
| `BACKLOG.md` | Marks nearly every feature `[x]` complete. Most were never wired up: Global Search, favourites, group management, backup, Xtream VOD and the OTA updater were all dead or non-functional. |
| `ARCHITECTURE.md` | Describes components that exist as files but are never called. |
| `AGENT_HANDOFF.md` | Describes RedSurf as a Next.js/Capacitor web app. That architecture is dead code and has been deleted. |
| `GEMINI.md` | Agent instructions for the previous tooling. |

**Current sources of truth:** `../vision/` for what RedSurf is, `../plans/` for what is being built
now, and `../../AGENTS.md` for how to work in this repo.
