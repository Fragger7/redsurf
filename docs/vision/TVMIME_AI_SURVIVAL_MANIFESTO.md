# 🧠 AI Survival Manifesto & "Vibe Coding" Strategy

*This document was forged on September 6, 2026, after a massive architectural rescue. It serves as a meta-playbook for the human orchestrator and future AI agents on how to navigate the dangers of AI-assisted software engineering in complex codebases.*

## 1. The "Vibe Coding" Trap (What We Rescued Ourselves From)
"Vibe coding" is the act of throwing high-level, conceptual prompts at an AI and expecting it to intuitively architect a full-stack feature. This works perfectly for simple Python scripts or single-page web apps. 

**For a complex, multi-threaded, hardware-accelerated IPTV app, vibe coding is a death sentence.**

What happened today? We tried to vibe-code a UI transplant from `StreamVault`. We asked the AI to "integrate the UI." Because AI agents lack broad, system-wide, cause-and-effect foresight, it attempted an **Organ Transplant**. It dragged in StreamVault's entire Dagger Hilt dependency injection framework and 19 bloated domain repositories. 
- It caused cascading compilation failures in the CI/CD pipeline.
- It created a "Frankenstein" architecture that violated our Kotlin Multiplatform (KMP) vision.
- It resulted in severe **Prompt Fatigue** for the human, who was left fighting endless unresolved references and explaining standard IPTV hierarchies (like TiviMate) to an AI that was drowning in token limits.

## 2. The Symptoms of the Rut (When to Stop)
If you find yourself in the following situations, you are in the AI Rut. **STOP.**
1. **The Whac-A-Mole Compile:** You ask the AI to fix one compile error, and it creates three more in files you don't care about (e.g., chasing Dagger Hilt stubs).
2. **Prompt Fatigue:** You are typing paragraphs explaining how a UI should look and how the data should flow, and the AI keeps forgetting the backend architecture.
3. **The Monolithic PR:** The AI tries to write the Database DAO, the ViewModel, and the Jetpack Compose UI all in a single, massive, hallucinated response.

## 3. The Escape Strategy (The Phoenix Playbook)
If you fall into the rut, execute this exact strategy to break out:

### A. Decouple Form and Function (Stateless Visual Cloning)
AI models are terrible at wiring complex architectures, but they are *incredible* at copying visual code. 
- **The Fix:** Never ask the AI to build a "Live TV Screen." Ask it to build a "Stateless Live TV Shell." 
- Command it to look at a beautiful open-source app (like StreamVault) and extract *only* the paint: the colors, the padding, the glassmorphism blur, the Jetpack Compose focus scaling. 
- Force it to replace all ViewModels and databases with simple data class parameters (`val channels: List<Channel>`).

### B. The Orchestrator Pattern (You are the Conductor)
Once the AI gives you the beautiful, "dumb" UI shell, you (or a highly constrained AI prompt) build the **Orchestrator**.
- The Orchestrator (e.g., `LiveTvRoute.kt`) is the only file allowed to talk to your pristine backend (SQLite/Ktor).
- It fetches the data and simply drops it into the "dumb" UI shell. 
- *Cause and effect are now isolated.* If the UI looks bad, it's a Compose problem. If the data is missing, it's a backend problem. They never tangle.

### C. Child-Proof the Agent (Active Directives)
AI agents behave like brilliant but overly eager interns with amnesia. You cannot just point them at a repo. 
- **The Fix:** Maintain an `AGENTS.md` file that acts as an unskippable Boot Sequence.
- **Active vs. Historical:** Prepend your immediate, unbreakable rules at the very top under an `🚨 ACTIVE DIRECTIVE 🚨` banner. Keep all the amazing historical context below it. The AI will read the rules first and obey, but retain the context of *why* the app exists.
- **Literal Execution:** Write your `TODO.md` backlog as literal instructions: *"Delete File X"*, *"Remove Annotation Y"*. Do not use abstract concepts like *"Refactor the DI layer"*.

## 4. In Case of Emergency (The Reset Button)
If the project ever goes completely off the rails again:
1. Revert your Git branch to the last known pristine backend state.
2. Update `AGENTS.md` with a new `🚨 ACTIVE DIRECTIVE 🚨` explaining exactly what failed.
3. Rewrite `TODO.md` with literal, step-by-step unblocking tasks.
4. Give the agent a single-sentence prompt: *"Read AGENTS.md Boot Sequence. Execute TODO.md Sprint 1 literally. Do not plan, do not summarize, do not hallucinate backend logic."*

*Remember: You own the Lamborghini engine (the V3 backend). You only use the AI to forge the fiberglass body kit and bolt it on.*
