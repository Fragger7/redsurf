# RedSurf Vision Archive

These documents are the **source of truth for what RedSurf is meant to be**. They outrank
any status summary, backlog, or progress report in this repo. Where a doc elsewhere claims a
feature is complete and this archive describes something richer, this archive wins.

## Provenance

All files here were written during **TVMime**, an earlier attempt at the same product
(github.com/Fragger7/tvmime, halted). They were never carried over when RedSurf started, so
RedSurf's own docs were written from scratch by an agent summarising its own work — which
lost the entire product vision. Recovered 2026-09-10.

## Contents

| File | What it is | Why it matters |
|---|---|---|
| `PRODUCT_VISION.md` | The core UX vision | "The Player IS The App", the full D-Pad control matrix, the zap experience, and §6 the Cloud+Local credential-locker sync model |
| `IPTV_DOMAIN_KNOWLEDGE.md` | 13 sections of IPTV engineering knowledge | OOM JSON trap, anti-bot handshakes, fast zapping, D-pad focus traps, separator channels, CDN redirects |
| `RESEARCH_OpenSourceIPTVProjects.md` | Competitive/architectural research | Deep comparison of StreamVault, OwnTV, IPTV Mine Pro; where FOSS wins and where TiViMate still dominates |
| `TVMIME_POST_MORTEM.md` | Why TVMime died | **Read before any architecture decision.** Records that Dagger Hilt caused infinite KSP compiler loops, and that transplanting StreamVault's UI produced a "Frankenstein" codebase |
| `TVMIME_IMPLEMENTATION_PLAN.md` | TVMime's build plan | Historical; useful for technique, not to be followed literally |
| `TVMIME_AI_SURVIVAL_MANIFESTO.md` | Agent working rules from TVMime | Historical context |
| `mockups/` | 14 rendered UI mockups | The visual spec. `tv_livetv_red_black`, `tv_settings_red_black`, `tv_vod_red_black` are the primary direction; `tv_radical_*` are alternates |

## Two lessons that are binding

1. **No Dagger Hilt.** It caused the KSP compiler loops that killed TVMime. Use manual
   constructor injection.
2. **Borrow techniques, never transplant UI.** Wholesale-porting StreamVault's UI is what
   produced the Frankenstein. Read how they do focus and buffering; write our own.
