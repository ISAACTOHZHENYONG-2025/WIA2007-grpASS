# RainReady team briefing

Read this first. It covers the idea, what each of us builds, the dates, what to research, and which tools to use. The full decisions are in `PLAN.md`; the assignment rules are in `ASSIGNMENT_BRIEF.md`. If this file and those disagree, tell Isaac.

Written 3 Nov 2026. Nothing has been coded yet.

---

## 1. The idea in one minute

Heavy rain at UM floods walkways, roads and car parks, and brings down trees and blocks drains. Today students find out by walking into it, or from scattered WhatsApp messages. Nobody can tell you which way to walk to class.

RainReady is an Android app that answers one question on a rainy day: **"Can I get to class?"** You pick the building you are going to. The app checks our hand-made list of campus paths against the live zone colours and says one of four things:

1. **Go.**
2. **Go, take care.** (rain forecast on low ground; maybe "Leave 10 minutes early")
3. **Use safer route.** (with the extra minutes and the path highlighted)
4. **No safe route reported.** Wait, or check official notices.

The zone colours come from students. Someone sees water, takes a photo, and reports it. Two more people confirm it, the zone turns red, everyone following that zone gets a push alert, and the route check sends people the other way. Staff can post notices, confirm reports and mark them fixed.

- **SDG 13 (Climate Action), target 13.1:** help people cope with climate hazards such as heavy rain.
- **What makes us different from other flood maps:** the route check, plus trust rules (3 confirmations, UM email only, must be on campus).
- **Scope:** rain hazards that block paths (flood, fallen tree, blocked drain). Not construction or events.
- **Android only.** We all use Windows, and building an iPhone app needs a Mac. The brief does not ask for iPhone.
- **Free only.** Firebase free plan, no paid services.

## 2. What the app has (5 modules)

| Module | Features | Owner |
|---|---|---|
| 1. Settings and access | English or Malay (FR1.1), follow zones (FR1.2), UM-email sign-in and staff roles (FR1.3) | Isaac |
| 2. Map and route check | 2D map with green, yellow and red zones (FR2.1), report pins and colour rules (FR2.2), "Can I get to class?" (FR2.3) | Jingying, Emmanuel |
| 3. Reporting | Report with live photos (FR3.1), confirm "still flooded" (FR3.2), fix, delete, flag, 14-day photo clean-up (FR3.3) | zy |
| 4. Alerts | Rain warning (FR4.1), staff notices (FR4.2), leave-now alert (FR4.3) | Ker Li (Isaac builds the admin page, Notices tab and phone backup check) |
| 5. AI and risk | AI photo check (FR5.1), face blur (FR5.2), optional chatbot (FR5.3), flood-risk score (FR5.4) | Chen Xi |

Two non-functional requirements:
- **NFR1 Speed.** With 50 phones on at once, a confirmed report turns the zone red on another phone within 5 seconds.
- **NFR2 Privacy and safety.** Only UM emails can report, reports from outside UM are rejected, and at least 80% of clear faces are blurred.

Full wording of every feature: `docs/FEATURES.md`.

## 3. Dates that matter

| Date | What |
|---|---|
| Wed 4 Nov | Survey goes out. Everyone sends it to their own groups (aim for about 5 replies each) |
| Fri 6 Nov | Isaac emails one UM office (optional) |
| Sun 8 Nov | Week 1 ends. Anything unfinished carries into week 2 (decided 5 Nov) |
| Wed 11 Nov | Core data layout locked; Firebase project open to everyone |
| Wed 11 Nov | Clickable Figma screens ready for usability round 1 |
| Sun 15 Nov | Survey closes; all week-1 set-up and checks done; everyone brings one photo or news link about UM flooding |
| Mon 16 Nov | Feature list locked. No new features after this |
| Sun 22 Nov | 2D map with zones and pins works. Stuck-task check: a buddy takes over anything still stuck |
| Sun 29 Nov | "Can I get to class?" works end to end |
| Sun 6 Dec | Cut check. Mon 7 Dec: chatbot yes or no |
| Sun 20 Dec | Feature freeze. Demo build tagged. First draft of your report section due |
| 21 to 27 Dec | Practice runs, then record your own part of the video |
| Sat 2 Jan | Video submitted (due Sun 3 Jan, 12pm) |
| Sat 9 Jan | Report submitted (due Sun 10 Jan, 12pm) |

## 4. Set up your laptop this week

Everyone does this by Sun 15 Nov. Tell the group chat if anything fails.

| Tool | What for | Where |
|---|---|---|
| Android Studio (latest stable) | Writing and running the app. It includes Java and the Android emulator | developer.android.com/studio |
| An Android phone with USB debugging on, or the emulator | Testing on a real device | Phone settings, then Developer options |
| Git | Saving and sharing code | git-scm.com |
| GitHub account | Our repo. Send your username to Isaac for an invite | github.com |
| Trello | Tasks and hours. One card per task | Isaac sends the board link |
| Figma (free account) | Viewing and commenting on designs. Emmanuel edits | figma.com |
| Node.js and the Firebase CLI | Running the Firebase Emulator on your laptop for daily work | firebase.google.com/docs/emulator-suite (follow "Install"; it also lists the Java version it needs) |
| Google account | Access to the Firebase project and the shared Drive folder | Jingying invites you to Firebase; Isaac shares the Drive folder |

Free learning to do first, if you are new to it:
- **Kotlin basics.** The Kotlin Koans or the "Kotlin bootcamp" on developer.android.com.
- **Jetpack Compose.** The "Android Basics with Compose" course on developer.android.com (units 1 and 2 are enough to start).
- **Git branches and pull requests.** GitHub's "Hello World" guide on docs.github.com.

## 5. How we work

- **Trello.** Columns: Backlog, This week, In progress, In review, Done. One card per task, with the owner, the requirement ID (for example FR3.2), and your hours as a comment the same week.
- **GitHub.** `main` must always work. Make a small branch per task, named like `zy/report-flow`. Open a pull request and get one teammate to review it. Commit under your own account, because commits are our evidence of who did what. Jingying approves any change to the data layout or app structure.
- **No secrets in code.** AI keys, Firebase service keys and the robot key go in GitHub's secret settings, never in a file.
- **Firebase.** Use the Emulator on your laptop day to day. Use the real Firebase project only for joint tests and the demo, so we stay inside the free limits.
- **Weekly check-in.** 15 minutes, same time every week. It starts with `main` installing and running on two phones. If it is broken, we fix that before anything else.
- **AI Usage Log.** If you use any AI tool (ChatGPT, Gemini, Copilot, Claude) for anything that ends up in our work, add a row to `docs/AI_USAGE_LOG.md` the same day. Paste your real prompt and say what you changed and tested. A missing log costs marks.
- **Our own work.** The brief says the concurrency design, race-condition fixes and the speed analysis must be our own thinking. Jingying, zy and Chen Xi write those parts without AI drafting.
- **Report.** Write your own section as you go. Your first draft is due Sun 20 Dec. The whole report has a 2,500-word limit, so keep yours to the word count in `PLAN.md` section 13.
- **Video.** You present what you built. Record in 1080p in a quiet room, using Isaac's slide template.
- **Stuck?** Say so at the check-in or in the group chat early. Your buddy helps first. Buddies: Emmanuel and Jingying, zy and Ker Li, Chen Xi and Isaac.

---

## 6. Your job

Each section lists what you build, what you deliver each week, what to research first, and your tools. Requirement IDs match `docs/FEATURES.md`. Hours are rough estimates.

### Isaac: project manager and Module 1 developer (about 95 hours)

**You build**
- UM-email sign-in by email link, the staff list, and staff roles (FR1.3)
- Settings: English or Malay, and the zones you follow (FR1.1, FR1.2)
- Onboarding screens, the "What do the colours mean?" legend, the Notices tab and the newest-notice card on the home screen
- The phone backup rain check: each phone checks Open-Meteo for its followed zones and never shows the same warning twice (FR4.1)
- The staff admin page: post notices in English and Malay, confirm a report instantly, mark fixed, delete photos, review flagged photos, and a "clean now" button
- The English and Malay text for the whole app

**You also run** the survey and its analysis, the lecturer email, one UM office email, usability sessions, Trello, the AI log check, and both submissions.

**Week by week**
| Week | Deliver |
|---|---|
| Week 1 (2 to 8 Nov) | Email the lecturer; survey out Wed 4 Nov; UM office email Fri 6 Nov; Trello board; invite everyone to GitHub; find a Malay-fluent checker |
| Week 2 (9 to 15 Nov) | Survey analysis (charts); recruit 3 testers by Tue 10 Nov; usability round 1; start sign-in; collect photo and news links |
| Week 3 (16 to 22 Nov) | Sign-in finished with staff roles; settings screen and zone following |
| Week 4 (23 to 29 Nov) | Verdict wording with Chen Xi; start the admin page |
| Dec week 1 | Admin page, Notices tab, onboarding, colour legend, phone backup rain check, Malay text; share the video slide template |
| Dec week 2 | Integration and bug fixes; chatbot decision Mon 7 Dec |
| Dec week 3 | Usability round 2 (5 students); your report sections |

**Research first**
- Firebase Auth email-link sign-in for Android (firebase.google.com/docs/auth). Firebase Dynamic Links has shut down, so follow the current set-up in the docs.
- Restricting sign-in to `@siswa.um.edu.my` and `@um.edu.my` (check the email in the app and again in Firestore security rules with zy).
- App languages in Android: `strings.xml` and a `values-ms` folder for Malay, and per-app language settings.
- DataStore for saving settings on the phone.
- Firebase Cloud Messaging topics, so following a zone means subscribing to that zone's topic (agree the topic names with Ker Li).
- WorkManager for the backup rain check (it can run at most every 15 minutes).
- Open-Meteo forecast API (open-meteo.com/en/docs), the hourly `precipitation` field.

**Tools:** Android Studio, Firebase console (Auth, Firestore), Trello, Google Forms and Sheets.

---

### Jingying: software architect and backend developer (about 95 hours)

**You build**
- The app structure: screens, navigation, and how screens get data (ViewModel with Kotlin coroutines and Flow)
- The Firebase project set-up
- The data layout, locked with zy by Wed 11 Nov (`PLAN.md` section 5)
- Zone ground levels (low, medium, high) from free elevation data, with Emmanuel
- The zone colour rules (FR2.2): staff override first, then confirmed report = red, then rain on low ground = yellow at most, else green; stale after 6 hours
- The building list, the 10 to 15 campus paths, and the route-check verdict engine (FR2.3), with Emmanuel
- Live map updates
- Safe handling of two people confirming at the same moment (with zy)
- Your own written speed analysis of the 50-phone test

**Week by week**
| Week | Deliver |
|---|---|
| Week 1 | Firebase project; skeleton app with navigation; confirm the Firebase free-plan limits; draft building and path lists. Firebase project open to all and data layout locked by Wed 11 Nov; the rest due Sun 15 Nov |
| Week 2 | Data layout in code; Emulator set up for everyone; zone ground levels |
| Week 3 | Zone colour engine; walk the campus paths with Emmanuel and Chen Xi; confirmation transaction with zy |
| Week 4 | Route-check verdict engine working end to end by Sun 29 Nov, passing Ker Li's test cases |
| Dec week 1 | Live updates; review zy's storm switch |
| Dec week 2 | Integration lead; review Ker Li's 50-phone script |
| Dec week 3 | Run the 50-phone test with Ker Li; write your speed analysis yourself |

**Research first**
- Android app architecture guide (developer.android.com/topic/architecture): ViewModel, StateFlow, the repository pattern.
- Navigation for Compose.
- Firestore data modelling, real-time listeners (`addSnapshotListener` or the Flow version), and transactions (firebase.google.com/docs/firestore).
- Firebase free-plan (Spark) limits: reads, writes and storage per day.
- Open-Meteo Elevation API (open-meteo.com/en/docs/elevation-api) for zone ground levels.

**Tools:** Android Studio, Firebase console and Emulator, a map tool such as geojson.io to check zone shapes.

---

### Emmanuel Heng: UI/UX lead and map developer (about 95 hours)

**You build**
- Figma designs, the colour scheme, and the shared Compose theme and parts everyone reuses
- Colour-blind-safe zone symbols: circle (Clear), triangle (Caution), octagon (Flooded)
- The home screen with "Can I get to class?" as the biggest card, and the bottom bar (Home, Map, Notices, Settings)
- The flat 2D map (FR2.1) with zones, report pins and the safer-route highlight
- The route-check screens: destination picker and verdict card (FR2.3)
- The map tile source and the "(c) OpenStreetMap contributors" credit
- Testing on the cheapest phone we have

**Week by week**
| Week | Deliver |
|---|---|
| Week 1 | OpenFreeMap check: UM map with zone shapes shows on the cheapest phone; Figma screens started; draft building and path lists with Jingying |
| Week 2 | Clickable Figma (home, map, route check, report) by Wed 11 Nov; 2D map with zones and pins by Sun 22 Nov; Compose theme |
| Week 3 | Home screen; walk the campus paths with Jingying and Chen Xi |
| Week 4 | Destination picker, verdict card, route highlight by Sun 29 Nov |
| Dec week 1 | Map polish; colour-blind check |
| Dec week 2 | Integration and UI fixes |
| Dec week 3 | Fixes from usability round 2 (only under 2 hours each); your report sections and video part |

**Research first**
- Material 3 design (m3.material.io) and the Material 3 Figma kit.
- Theming in Compose (`MaterialTheme`, colour and type).
- MapLibre Native for Android (maplibre.org) and OpenFreeMap (openfreemap.org): loading a style, adding GeoJSON layers for zones and pins.
- Drawing zone shapes as GeoJSON (geojson.io).
- Accessibility: contrast of at least 4.5:1, 48 dp touch targets, and Android's "Simulate colour space" developer option to test colour blindness.

**Tools:** Figma, Android Studio, geojson.io.

---

### zy: reporting and database developer (about 95 hours)

**You build**
- The report flow (FR3.1): live camera, 1 to 3 photos, shrink to about 800 px and 100 KB, strip hidden photo data, one-time location, inside-UM check, round the position to about 20 m
- The first-report privacy notice and the blurred-photo preview; refuse the upload if blurring fails
- Confirmations (FR3.2): "still flooded", one vote per person, 3 people (reporter counts as the first)
- Report limits, the duplicate check (open report within 50 m), the "Report a problem" flag, and deleting your own report (FR3.3)
- The Firestore security rules for every module, with Jingying on the data layout
- Photos stored separately from report text, and the anonymous record kept after clean-up
- The "simulate storm" demo switch and the demo-data reset

**Week by week**
| Week | Deliver |
|---|---|
| Week 1 | Photo size test with Chen Xi (10 real photos); check the UM email link arrives and is not spam; data layout locked with Jingying |
| Week 2 | Report flow: camera, shrink, strip data, location check |
| Week 3 | Confirmations and trust rules; transaction for simultaneous confirmations with Jingying; security rules |
| Week 4 | Flags, delete own report, duplicate check, privacy notice, blur preview with Chen Xi |
| Dec week 1 | "Simulate storm" script and demo reset (Jingying reviews) |
| Dec week 2 | Integration; security rules tests |
| Dec week 3 | Privacy part of NFR2 tests; first draft of the privacy and ethics section |

**Research first**
- CameraX (developer.android.com/media/camera/camerax): taking a photo in the app, no gallery.
- Shrinking a photo with `Bitmap` and saving it as JPEG; removing hidden data with `ExifInterface`.
- Location: the fused location provider, one-time "current location", and the location permission.
- Firestore security rules and how to test them with the Emulator (firebase.google.com/docs/rules).
- Firestore transactions, so two confirmations at the same moment are both counted.
- Firestore offline writes, so a report made with no signal sends later.

**Tools:** Android Studio, Firebase console and Emulator.

---

### Chen Xi: AI and risk developer (about 90 hours, plus the optional chatbot)

**You build**
- The AI photo check (FR5.1): suggests flood, fallen tree or blocked drain; the user can correct it or switch it off
- Face blur on the phone before upload (FR5.2); number-plate blur only if time allows
- The "indicative" flood-risk score from forecast rain and ground level (FR5.4, rule-based)
- The flood safety guidelines text and the route-verdict wording in English and Malay (with Isaac)
- Campus path data: walking the paths with Jingying and Emmanuel, and test data for the route check with Ker Li
- The AI accuracy tests and a written reason for each AI feature (why AI, and what happens when it is wrong)
- The chatbot (FR5.3), only if the team says yes on Mon 7 Dec

**Week by week**
| Week | Deliver |
|---|---|
| Week 1 | Check the Gemini free daily limit and Google's data policy for free-tier photos; photo size test with zy |
| Week 2 | Face-blur prototype on 10 photos; help Isaac analyse the survey |
| Week 3 | Face blur in the report flow; walk the campus paths |
| Week 4 | AI photo check working; route-check test data with Ker Li |
| Dec week 1 | Risk score; verdict wording and safety guidelines in both languages |
| Dec week 2 | Plate blur or chatbot, only if approved; collect test photos |
| Dec week 3 | AI tests: 60 photos for the photo check, 30 for face blur, 20 chatbot questions if built; write up results |

**Research first**
- ML Kit face detection (developers.google.com/ml-kit/vision/face-detection): it finds face boxes on the phone, and we blur those boxes.
- Firebase AI Logic (firebase.google.com/docs/ai-logic): calling Gemini from the app with an image, without a key in the app.
- The Gemini free-tier limits and whether free-tier photos may be used to improve Google's products. If yes, the photo check becomes opt-in.
- How to measure accuracy honestly: write the right answer for each test photo before running the test.
- Sources of flood photos you may legally use for testing, plus our own UM photos.

**Tools:** Android Studio, Firebase console, a shared Drive folder for test photos, Google Sheets for test results.

---

### Ker Li: QA lead and alerts developer (about 95 hours)

**You build**
- The GitHub "robot": a scheduled GitHub Actions job every 15 minutes that checks Open-Meteo and sends rain warnings (FR4.1), applies the red-zone rule to reports and sends leave-now alerts (FR4.3), and deletes photos after 14 days
- The leave-now alert on the phone
- Staff-notice pop-ups (FR4.2; Isaac builds the admin page that posts them)
- The test plan, including a test case for each of the four route-check verdicts, written before the feature is built
- The 50-phone test script and the NFR1 and NFR2 measurements
- The usability test plan (Isaac runs the sessions)

**Week by week**
| Week | Deliver |
|---|---|
| Week 1 | GitHub robot sends one test pop-up to a phone |
| Week 2 | Usability round 1 plan; route-check test cases; agree zone topic names with Isaac |
| Week 3 | Test the alert sending end to end; rain-warning logic |
| Week 4 | GitHub rain robot finished; leave-now rule matches the app (Jingying checks) |
| Dec week 1 | Staff-notice pop-ups; photo clean-up job |
| Dec week 2 | Leave-now alert; 50-phone script ready (Jingying reviews) |
| Dec week 3 | Run the 50-phone test; NFR measurements; usability round 2 plan; score chatbot answers with Isaac if built |

**Research first**
- GitHub Actions scheduled workflows (docs.github.com, search "schedule event"). Runs can start late when GitHub is busy, and scheduled jobs in a public repo stop after 60 days with no activity.
- Sending pushes with the FCM HTTP v1 API from a script, using a service account key stored as a GitHub secret.
- FCM topics, so a warning for one zone goes only to people following that zone.
- Reading Firestore from a script (Firebase Admin SDK for Node.js or Python).
- Writing a load-test script: 50 simulated clients listening to the map while real phones time the change to red.
- Writing test cases: given, when, then, with the expected result written first.

**Tools:** GitHub Actions, Node.js or Python, Firebase console, Google Sheets for test results.

---

## 7. What everyone does besides building

- Send the survey to your own groups on Wed 4 Nov.
- Bring one photo or news link about UM flooding or fallen trees by Sun 15 Nov.
- Log your hours on Trello each week and AI use on the day.
- Present your own part in the video (about 10 seconds over your Git and Trello evidence, plus your demo scene).
- Write your report section by Sun 20 Dec.

## 8. Video presenters

| Section | Presenter |
|---|---|
| Project overview, stakeholder validation, closing | Isaac |
| Demo: map and route check | Emmanuel |
| Demo: reporting | zy |
| Demo: AI, plus AI test results | Chen Xi |
| Demo: alerts, plus the test plan | Ker Li |
| Design and usability | Emmanuel |
| Technical implementation | Jingying |
| Non-functional requirements | Jingying (speed), zy (privacy) |
| Team contributions | Everyone |

## 9. Questions still open

- S1 (AI guesses water depth from the photo): decided Mon 16 Nov.
- The chatbot: decided Mon 7 Dec.
- Lecturer answers on appendices and word count, the AI log template (Appendix 2(b)), and group-name registration: Isaac is asking.
