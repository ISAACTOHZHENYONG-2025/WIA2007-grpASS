# Schedule

Plan updated 2 Nov 2026 (v2). Exam and busy dates are not collected, so the weeks below assume none.

**Hard deadlines**
- Video: 12pm Sun 3 Jan 2027 (we submit Sat 2 Jan)
- Report: 12pm Sun 10 Jan 2027 (we submit Sat 9 Jan)
- If we need extra time, the leader emails the lecturer **before** the deadline.

## Key dates

| Date | Milestone |
|---|---|
| Week 1 (slipped from 1 Nov) | Trello board ready; docs-only first push (after plan approval) |
| Wed 4 Nov | Survey goes out (all six send it) |
| Fri 6 Nov | One UM office emailed (optional) |
| Sun 8 Nov | Week 1 ends. Unfinished week-1 items carry into week 2 (decided 5 Nov) |
| Tue 10 Nov | 3 students recruited for usability round 1 |
| Wed 11 Nov | Clickable Figma screens ready (home, map, route check, report); core data layout locked; Firebase project open to all six |
| Sun 15 Nov | Survey closes; all week-1 set-up and checks done; one photo or news link each; team rebalance check |
| Mon 16 Nov | **Feature list locked**; S1 (water depth) decided |
| Sun 22 Nov | **Flat 2D map with zones and pins works** (slipped from Sun 15 Nov); stuck-task check; buddy takes over anything still stuck |
| Sun 29 Nov | **"Can I get to class?" route check works end to end** |
| Sun 6 Dec | Cut check; chatbot decision on Mon 7 Dec |
| Sun 20 Dec | **Feature freeze**; "demo" build tagged; first drafts of all report sections due |
| Sun 27 Dec | Final video recording done |
| Sat 2 Jan | Video submitted |
| Sat 9 Jan | Report submitted |

## Week by week

| Week | Dates | Work | Owners |
|---|---|---|---|
| **Week 1** | 2 to 8 Nov | Survey out Wed 4 Nov; email one UM office (optional). The rest of week 1 slipped to Sun 15 Nov (decided 5 Nov): laptop set-up, skeleton app and theme, Firebase project (open to all by Wed 11 Nov), Figma start, draft building and path lists, week-1 checks (below). **Core data layout locked Wed 11 Nov** | All six |
| **Week 2** | 9 to 15 Nov | The slipped week-1 items; **survey closes Sun 15 Nov**; report flow; usability round 1 (3 students, Figma; Ker Li writes the plan, Isaac runs it); UM-email sign-in starts (Isaac); data layout in code (Jingying); face-blur prototype (Chen Xi); test cases for the route check (Ker Li) | zy, Emmanuel, Isaac, Jingying, Chen Xi, Ker Li |
| **Week 3** | 16 to 22 Nov | **Flat 2D map with zones and pins by Sun 22 Nov**; confirmations and trust rules; zone colour engine; face blur; path list built and walked on campus; test the alert sending; sign-in finished and settings screen (Isaac) | zy, Jingying, Emmanuel, Chen Xi, Ker Li, Isaac |
| **Week 4** | 23 to 29 Nov | **Route check ("Can I get to class?")**; GitHub rain robot; photo check | Jingying, Emmanuel, Ker Li, Chen Xi |
| **Dec wk 1** | 30 Nov to 6 Dec | Admin page; Notices tab; onboarding and colour legend; phone backup rain check; staff-notice pop-ups; Malay text and verdict wording; risk score; "simulate storm" script (Jingying reviews); map polish | Isaac, Ker Li, Chen Xi, zy, Jingying, Emmanuel |
| **Dec wk 2** | 7 to 13 Dec | Integration; leave-now alert; **chatbot decision Mon 7 Dec (only if route check, admin page and Malay text are done); plate blur only if on schedule**; 50-phone script ready | All six |
| **Dec wk 3** | 14 to 20 Dec | Testing; AI accuracy tests; 50-phone test and Jingying's speed analysis; usability round 2 (5 students); report section first drafts; **freeze on 20 Dec** | All six |
| 21 to 27 Dec | | Two practice runs; record video scene by scene; each member records own part | All |
| 28 Dec to 2 Jan | | Edit video; **submit Sat 2 Jan** | Isaac |
| 3 to 9 Jan | | Finish report; **submit Sat 9 Jan** | All; Isaac merges |

Each person writes their own report section as they go, not all at the end. Each person's own week-by-week list is in [TEAM_BRIEFING.md](TEAM_BRIEFING.md).

## Week-1 checklist (everyone has one thing; due Sun 15 Nov)

| Check | Who |
|---|---|
| Photo size test with 10 real photos: still clearly a flood after shrinking to about 800 px and 100 KB? | zy, Chen Xi |
| Google AI free daily limit is enough; read Google's data policy for the free tier | Chen Xi |
| UM-email sign-in link arrives and is not blocked or sent to spam (check Firebase's current Android set-up, since Dynamic Links has shut down) | zy |
| OpenFreeMap works for UM; flat 2D map with zone shapes shows; test on the cheapest phone | Emmanuel |
| Draft the building list and 10 to 15 campus paths | Jingying, Emmanuel |
| Firebase free-plan daily limits confirmed | Jingying |
| GitHub rain job can send a test pop-up | Ker Li |
| Survey sent to all six groups by Wed 4 Nov; one UM office emailed by Fri 6 Nov (optional, skip if no reply) | Isaac |

## Cut order if we fall behind

First dropped is first:
1. Chatbot
2. Plate blur
3. Onboarding polish
4. Park smart and extras
5. Leave-now alert
6. Photo AI classification (people pick the category by hand)

Never cut: report, confirm, red pin, warning, the "Can I get to class?" verdict, and face blur. A flat 2D map must work by Sun 22 Nov.

## Weekly rhythm

- 15-minute check-in, same day and time each week: what is done, what is stuck. It starts with `main` installing and running on two phones; broken builds are fixed first.
- Freeze means no new features. Round 2 fixes only if under 2 hours; the demo build is tagged on 20 Dec and not touched after.
- Hours logged on the Trello card the same week.
- Isaac checks the AI Usage Log weekly.
- Jingying checks Firebase usage weekly, and the day before recording.

## Recording-day plan

- Record scene by scene, not one long take.
- The video opens with a 30-second teaser of the route check, then follows the brief's section order (full time and presenter table in PLAN.md section 10).
- Everyone matches one sample clip (1080p, quiet room, shared slide template) by Sun 20 Dec; Isaac shares the template in Dec week 1.
- Practice version first (21 to 22 Dec), so a usable video always exists.
- Frozen "demo" build; nobody changes it during recording.
- The demo uses a "simulate storm" switch that plays a scripted scenario; it is shown on screen and said out loud that this is demo data.
- Reset command restores zones, test accounts and seeded reports for each retake.
- Campus wifi plus a phone hotspot as backup; emulator screen recordings as a spare.
