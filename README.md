# RainReady

An Android app for Universiti Malaya (UM) students and staff that gives **one campus view of flood and rain risk**, and answers the question every student asks on a rainy day: **"Can I get to class?"**

- **Course:** WIA2007 Mobile Application Development, group project (2026/2027 Semester 1)
- **SDG:** 13 (Climate Action), target **13.1** (strengthen resilience and adaptive capacity to climate-related hazards)
- **Status:** planning. No app code has been written yet.

## The problem

Heavy rain on the UM campus floods roads and car parks and brings down trees. Students find out by walking into it, or from scattered WhatsApp messages. There is no single, trusted place that shows which parts of campus are affected right now, or which way to walk to class.

## The idea in one sentence

Someone sees water, reports it with a photo, other students confirm it, the map turns red, everyone is warned, and the route check tells you the safer way to class.

## What the app does

| Module | What it gives you |
|---|---|
| 1. Settings and access | English or Malay, follow the zones you care about, UM-email sign-in to report, staff roles |
| 2. Flood map and route check | 2D campus map with green, yellow and red zones, report pins, and **"Can I get to class?"**: pick a building and get Go / Go, take care / Use safer route / No safe route reported |
| 3. Reporting | Report with live photos and location; others confirm; staff mark fixed |
| 4. Alerts | Rain warnings, official staff notices, leave-now alert |
| 5. AI and risk | Photo check, face blur, indicative risk score; a bilingual "Is it safe?" chatbot if time allows |

Full list with requirement numbers: [docs/FEATURES.md](docs/FEATURES.md).

No sign-in is needed to view the map, get alerts, or use the route check. A verified UM email is needed only to report or confirm.

## Tech

- Android Studio, Kotlin, Jetpack Compose. Android only: building for iPhone needs a Mac, and the team works on Windows
- Firebase (Auth, Firestore, push). Free plan only: no Cloud Functions
- Open-Meteo for rain forecasts (free, no key)
- MapLibre with OpenStreetMap data for the 2D map
- A hand-made list of campus paths and buildings for the route check (no live routing service)
- Google Gemini through Firebase for the photo check (no key stored in the app)
- A scheduled GitHub job (every 15 minutes) sends rain warnings and leave-now alerts and cleans up old photos

Map data: (c) OpenStreetMap contributors.

## Team

| Member | Role |
|---|---|
| Isaac (leader) | Project manager + developer (Module 1, admin page) |
| Jingying | Software architect + backend developer |
| Emmanuel Heng | UI/UX lead + map developer |
| zy | Reporting + database developer |
| Chen Xi | AI + risk developer |
| Ker Li | QA lead + alerts developer |

Details, hours and buddy pairs: [docs/ROLES.md](docs/ROLES.md).

## Documents

- [docs/TEAM_BRIEFING.md](docs/TEAM_BRIEFING.md): start here. The idea, each person's job, week by week, what to research and which tools
- [docs/FEATURES.md](docs/FEATURES.md): the 5 modules, every functional requirement, the 2 non-functional requirements
- [docs/ROLES.md](docs/ROLES.md): who builds what and who writes which report section
- [docs/SCHEDULE.md](docs/SCHEDULE.md): week by week to 3 Jan (video) and 10 Jan (report)
- [docs/AI_USAGE_LOG.md](docs/AI_USAGE_LOG.md): every use of an AI tool, logged the same day

## What is in this repo

| Folder | What goes in it | Owner |
|---|---|---|
| `docs/` | Features, roles, schedule, team briefing, AI Usage Log | Isaac |
| `app/` (created by Android Studio) | The Android app | Everyone |
| `.github/workflows/` and `robot/` | The scheduled alert and clean-up job | Ker Li |
| `firebase/` | Security rules and Firebase settings | Jingying, zy |
| `data/` | Zones, buildings and campus paths | Jingying, Emmanuel |
| `design/` | Exported screens, colours, icons | Emmanuel |
| `tests/` | Test plan, test cases, 50-phone script, results | Ker Li |
| `ai-tests/` | AI test results and written justifications (no photos) | Chen Xi |

Each folder has a short README that says what belongs there.

Jingying creates the Android Studio project **at the top of this repo**, not in a subfolder. Android Studio makes `app/` plus `settings.gradle.kts`, `build.gradle.kts`, `gradlew` and `gradle/` side by side, and they all belong at the top level next to this README.

## How we work

- `main` always works. Nobody pushes straight to it.
- One small branch per task, named like `zy/report-flow`, merged by pull request after one teammate reviews it.
- Commit small and often, under your own GitHub account.
- **Never commit secrets** (AI keys, Firebase service keys, robot keys). They live in GitHub's hidden secrets settings.
- Tasks live on our Trello board "rainready" (the invite link is in the team group chat), one card per task, with the owner, requirement ID and hours logged in comments.
- Test photos, survey sheet, report drafts and video clips live in our [shared Google Drive folder](https://drive.google.com/drive/folders/1YjoNVD43q-0-KFF2gkpKoKAVgnb-UtDM?usp=drive_link).
- Weekly 15-minute check-in.

## Deadlines

- Video (10 to 15 minutes): due 12pm Sun 3 Jan 2027. We submit Sat 2 Jan.
- Report: due 12pm Sun 10 Jan 2027. We submit Sat 9 Jan.
