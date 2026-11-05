# Roles and Responsibilities

Everyone codes and tests their own part, presents it in the video, and writes the matching report section. Nobody is documentation-only (assignment rule). Each member is expected to put in at least 20 hours; the plan below is about 90 to 95 hours each between 2 Nov and 20 Dec.

Hours are rough planning estimates. Real hours are logged on Trello cards and are the evidence used in the report. Each person's week-by-week tasks, research list and tools are in [TEAM_BRIEFING.md](TEAM_BRIEFING.md).

## Roles

### Isaac (Leader): Project manager + developer
**Builds (Module 1):**
- UM-email sign-in (email link) and the staff list (Week 2 and 3, because reporting depends on it)
- Settings screen: language (English/Malay), zones to follow (Week 3)
- Phone backup rain check: each phone checks Open-Meteo for the followed zones and never shows the same warning twice (moved from Ker Li)
- Onboarding screens (first thing cut), the "What do the colours mean?" legend, and the Notices tab with the newest-notice card on the home screen (moved from Emmanuel)
- Staff admin page: post notices (English and Malay boxes), confirm a report instantly, mark fixed, delete photos, review flagged photos, manual "clean now" button for the 14-day photo cleanup
- English and Malay text (first Malay version; a Malay-fluent person checks the verdict and safety texts before Dec week 1)

**Also:** emails the lecturer (group name, appendices and word count, Appendix 1 and 2(b)); runs the student survey, and analyses it; finds a Malay-fluent checker; collects everyone's flood photos and news links; emails one UM office (JPPHB, a college or a faculty; optional, extra credit); runs the usability test sessions; Trello, hours log, weekly check-in, weekly AI Usage Log check; submissions.
**About 95 hours.**

### Jingying: Software architect + backend developer
**Builds:**
- App structure and navigation; Firebase project setup
- The data layout, locked with zy by Wed 11 Nov
- Zone ground levels from free public elevation data (with Emmanuel)
- Zone colour rules (staff override, confirmed report = red, rain on low ground = yellow at most, stale after 6 hours)
- The list of campus buildings and 10 to 15 hand-made paths, and the **route-check verdict engine** for "Can I get to class?" (with Emmanuel)
- Live updates to the map
- Her own written analysis of the 50-phone speed result (what the bottleneck was and why); reviews Ker Li's script and zy's storm switch
- Race-condition handling for simultaneous confirmations (with zy)

**Also:** approves changes to the data layout or app structure; checks Firebase usage each week.
**About 95 hours.**

### Emmanuel Heng: UI/UX lead + map developer
**Builds:**
- Figma designs (clickable home, map, route check and report screens by Wed 11 Nov for usability round 1), colour scheme, shared Compose theme and parts
- Colour-blind-safe zone symbols (circle, triangle, octagon)
- Home screen (with "Can I get to class?" as the main card), bottom bar (onboarding and the colour legend moved to Isaac)
- The flat 2D map
- Overlays: zones, report pins, safer-route highlight
- The route-check screens: destination picker and verdict card
- Map tile source and credit line; testing on the cheapest phone

**About 95 hours.**

### zy: Reporting + database developer
**Builds (Module 3):**
- Report flow: live camera, shrink photo, strip hidden data, location check inside UM, position rounding, one-time location
- Confirmations ("still flooded"), one vote per person, trust rules
- "Report a problem" flag, delete own report
- Firestore data layout (with Jingying) and security rules for all modules
- Photos stored separately from report text
- The anonymous record kept after cleanup
- First-report privacy notice screen; blur preview and refuse-upload-if-blur-fails rule
- Race-condition handling for simultaneous confirmations (with Jingying)
- Demo-data reset command and the "simulate storm" demo switch (plays a scripted scenario; Jingying reviews)

**Also:** first draft of the privacy and ethics section of the report.
**About 95 hours.**

### Chen Xi: AI developer
**Builds (Module 5):**
- Photo check (flood / fallen tree / blocked drain; a person can correct it)
- Face blur, running in the background; plate blur only if time allows (moved from Jingying)
- Flood-risk score (rain amount and ground level), labelled "indicative" (moved from Ker Li)
- Flood safety guidelines text, used for zone explanations and by the chatbot if built (moved from Ker Li)
- Route-verdict wording in English and Malay (with Isaac)
- Campus path data: walking the 10 to 15 paths and recording zones and minutes (with Jingying and Emmanuel); route-verdict test fixtures (with Ker Li). Subject to change at the Sun 15 Nov check-in
- Bilingual "Is it safe?" chatbot, only if the team is on schedule (a thin add-on over the zone and route data)

**Also:** AI accuracy tests (60 photos, 30 photos, and 20 chatbot questions if built); a written justification for each AI feature; survey analysis support (Isaac runs the survey); checks Google's AI daily limit and data policy in week 1; writes the AI parts of the privacy section.
**About 90 hours.** Workload is checked at the Sun 15 Nov check-in.
**Note:** the chatbot is optional on top of this; Chen Xi now owns all of Module 5.

### Ker Li: QA lead + alerts developer
**Builds (Module 4):**
- GitHub scheduled job that checks Open-Meteo and sends rain warnings, applies the red-zone rule to reports for leave-now pushes, and runs the 14-day photo clean-up (Jingying checks the rule matches the app); the phone backup check moved to Isaac
- Leave-now alert
- Staff-notice pop-ups (the display side of FR4.2; Isaac builds the admin page that posts them)

**Also:** writes the 50-phone test script; writes the test plan, including a test case for each of the four route-check verdict rules; measures the two non-functional requirements; scores the 20 chatbot answers with Isaac (if built); writes the usability test plan (Isaac runs the sessions).
**About 95 hours.**

## Buddy pairs

If someone is stuck at the end of week 3 (Sun 22 Nov), the cut order applies and the buddy takes the task.

| Area | Pair |
|---|---|
| Map and route check | Emmanuel and Jingying |
| Reports and alerts | zy and Ker Li |
| AI and survey | Chen Xi and Isaac |

## Reviews

- Any teammate can review a normal pull request.
- Jingying approves changes to the data layout or app structure.

## Proposed report section owners

Each person writes the part of the report about what they built. Isaac merges and trims to the 2,500-word limit.

| Section | Owner(s) |
|---|---|
| Introduction and problem | Isaac |
| User study findings | Isaac, with Chen Xi |
| Functional requirements | Jingying (table), each owner for their module |
| Non-functional requirements | Ker Li |
| Architecture and design | Emmanuel (UI, colours), Jingying and zy (data), Chen Xi (AI architecture) |
| System implementation | Each member for their own part |
| Testing and validation | Ker Li (strategy, NFRs), Chen Xi (AI evaluation) |
| Impact, limitations, future work | zy (privacy), Isaac (impact) |
| Team roles and contributions | Isaac |
| References | Everyone for their own sources; Isaac merges |

## Evidence

- Trello cards with owner, requirement ID and hours logged
- GitHub commit and pull request history under each person's own account
- Weekly check-in notes
