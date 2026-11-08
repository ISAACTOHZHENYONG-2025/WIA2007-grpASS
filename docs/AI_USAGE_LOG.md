# AI Usage Log

Mandatory for this assignment (UM AI policy, Level 2: limited use). This log is attached as an appendix to the final report.

> **TODO (Isaac):** the assignment refers to a standard template, **Appendix 2(b)**. Get it from Spectrum or the lecturer and make sure these columns match it before submission. The columns below follow the brief's list.

## Rules

1. Log it **the same day** you use an AI tool, including code assistants and chatbots, for anything that ends up in our work.
2. One row per use. Say which tool, the actual prompt, how you used the answer, and what you tested and changed.
3. Paste the real prompt, not a summary.
4. AI helps with wording and code. The design decisions, the algorithms, the concurrency design and the performance analysis must be **our own thinking**. Write what you understood and verified yourself.
5. Isaac checks this log every week.

## What the brief requires each entry to show

- Which AI tool(s) were used
- The specific prompts or queries inputted
- How the output was utilized, tested, and subsequently modified in the final work

## Log

| # | Date | Who | Tool | Prompt / query | How the output was used | How it was tested and what we changed |
|---|---|---|---|---|---|---|
| 1 | 2026-10-29 to 2026-11-03 | Isaac | Claude Code (Claude Sonnet 5.5 / Opus 5.5), including the "llm-council" skill from github.com/aiwithremy/claude-skills-llm-council | Summary of the planning-stage sessions (individual prompts are kept in the team's private notes): reviewed our plan and asked questions about every feature; brainstormed and ranked about 25 project ideas for SDG 6 and 13; ran a five-advisor stress test of flood app vs "Empty Room, AC On"; asked for a mostly multiple-choice survey; renamed the project; asked for a condensed copy of the assignment brief; asked to rebalance workload and explain the tech stack. | Claude asked the questions, flagged gaps (photo storage cost, alert sending without a server, API key exposure, missing concurrency story) and drafted the planning files: `PLAN.md`, the docs in this repo, the survey text and the team briefing. | Isaac answered every question and made every decision: kept the flood app, dropped the 3D map, made "Can I get to class?" the main feature, made the chatbot optional, chose Android only, chose free-plan Firebase, removed the kill rule and backup idea, set photo and privacy rules, and moved tasks between members. Isaac cut ideas using his own knowledge of UM. Claims about UM and Firebase limits were marked for the team to verify. The team reads each file and corrects anything wrong. Concurrency design and the performance analysis are written by Jingying, zy and Chen Xi without AI drafting. |
| 2 | 2026-11-05 | Isaac | Claude Code (desktop) and Claude (claude.ai project) | Claude Code set up the folder layout and placeholder READMEs for the first commit. Then asked Claude: "what should i commit to the repo first" (pasting Claude Code's first-commit plan), and "ok do that and should we update the ai usage log for using claude for commit?" | Claude reviewed the first-commit plan. It suggested committing the contents of `repo/` as the repo root, fixed `.gitignore` so `build/` and `local.properties` are ignored in every folder (not just the top) and added `node_modules/` and `.kotlin/`, added a README note that the Android Studio project goes at the repo root, and added a note to `firebase/README.md` that `firebase init` is run once by Jingying, without Cloud Functions. | Claude also scanned the docs for phone numbers, matric numbers and personal emails (none found). Isaac reads every change and runs `git status` before committing to confirm no secrets, photos or private notes are included. (Isaac: update this cell with anything you changed.) |
| 3 | | | | | | |
| 4 | 2026-11-08 | Emmanuel | Claude Code (desktop, Claude Opus 5.5) with the Figma connector | "then what can i do now?" and then "Draft Figma screens" (Claude worked from the spec on my page of the Week 1 guide) | Claude created the Figma draft "RainReady" in my drafts: colour and text styles (Material 3 roles plus zone colours), reusable parts (zone symbol and badge, map pin, button, nav bar, zone card, building row, the four route-check verdict cards), 8 screens at 360 x 800 (Home rainy and calm, route check list and result, map, report, report sent, colour legend) and 23 prototype links. | Claude screenshotted every screen and fixed a list that ran under the nav bar and map shading that drew solid instead of see-through. Building names, zone names and the map drawing are placeholders until the building list is done. (Emmanuel: update this cell with what you checked and changed.) |
| 5 | 2026-11-08 | Emmanuel | Claude Code (desktop, Claude Opus 5.5) | "Build map test app", then "push everything to github" and "a new branch emma" | Claude wrote the throwaway test app in `experiments/map-check` (MapLibre Native 13.7.1, OpenFreeMap Liberty style, 4 rough zones placed from OpenStreetMap building positions, on-screen load times and frame counter), checked the MapLibre method names against the published library, read the OpenFreeMap terms, and drafted my map-check note for Drive. | The debug build passes on my laptop. On the Pixel 6 emulator the app launched (cold start about 10 s) and kept running with no crash; no screenshot was captured. The log showed MapLibre uses Vulkan by default, so Claude added an OpenGL fallback step to the README. The real test on the cheapest phone is still to do, and the results go in the Drive note. (Emmanuel: update this cell with the phone results and what you changed.) |

## Examples of good entries (delete before submission)

- *Tool:* ChatGPT. *Prompt:* "Explain what a Firestore transaction is and when two users updating the same counter can lose a count." *Used:* to understand why simultaneous confirmations need a transaction. *Tested/changed:* wrote our own transaction in `ConfirmationRepository`, tested with two phones tapping at once, fixed a case where the same user could vote twice.
- *Tool:* Gemini. *Prompt:* "Improve the tone of this paragraph for an academic report: ..." *Used:* reworded one paragraph of the Introduction. *Tested/changed:* checked every fact against our survey, removed a claim that we could not support, rewrote two sentences in our own words.

## Reflection (fill in near the end)

- Where did AI help most?
- Where was it wrong, and how did we find out?
- What did we deliberately keep as our own work, and why?
