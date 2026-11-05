# Features and Requirements

5 modules, each with functional requirements (FR) numbered for use in the demo video and report, plus 2 non-functional requirements (NFR). Each demo scene shows its requirement number on screen.

SDG 13, target 13.1. Main users: UM students; staff (JPPHB, college and faculty offices) as admins.

---

## Module 1: Settings and Access (owner: Isaac)

**FR1.1 Language.** The user shall be able to switch the whole app between English and Malay. Pop-up warnings carry both languages in one message. Staff notices have an English box and a Malay box; if the Malay box is empty, English is shown. Route-check verdicts are shown in the chosen language.

**FR1.2 Follow zones.** The user shall be able to tick the campus zones they care about. Pop-up warnings are sent only for ticked zones; official staff notices go to everyone. If nothing is ticked, warnings cover the whole campus. The choice is stored only on the user's own phone.

**FR1.3 UM-email sign-in and roles.** Viewing the map, receiving alerts and using the route check shall need no sign-in; viewing report photos shall need a signed-in UM account. Reporting and confirming shall need a verified `@siswa.um.edu.my` or `@um.edu.my` email (sign-in by email link). Staff on the admin list shall get an admin page; nobody can make themselves staff from inside the app.

## Module 2: Flood Map and Route Check (owners: Emmanuel, Jingying)

**FR2.1 Campus map with zones.** The app shall show a flat 2D campus map divided into 8 to 10 zones, each with a ground level (low, medium, high) set from free public elevation data. Each zone shows a colour, a symbol (circle, triangle, octagon) and a word (Clear, Caution, Flooded). "Last updated" time is always shown; data older than 15 minutes shows a grey offline banner.

**FR2.2 Report pins and colour rules.** The map shall show pins for reports: grey for unconfirmed, red for confirmed. Zone colour is decided in this order: staff override (lasts until cleared, or 12 hours); a confirmed unfixed report makes the zone red; rain on low ground makes it yellow at most; otherwise green. A confirmed report goes stale after 6 hours. A yellow that comes only from the rain forecast is labelled "forecast only". Unconfirmed reports never change a zone colour.

**FR2.3 "Can I get to class?" route check (main feature).** The user shall be able to pick a destination building from a list. The start is the phone's location, read once and snapped to the nearest building; if location is off, the user picks the start building by hand. The app checks the hand-made campus paths to that building (10 to 15 paths between main buildings, each with the zones it passes and its walking minutes) and takes the worst zone colour on each path:
1. Usual path all green: **"Go."**
2. Usual path has yellow: **"Go, take care."** If heavy rain is forecast within the next hour on low ground, it adds **"Leave 10 minutes early."**
3. Usual path has red and another path avoids red: **"Use safer route"**, showing the extra walking minutes and highlighting the path.
4. Every path has red: **"No safe route reported. Wait, or check official notices."**

Every verdict says why using the report's category (for example "Fallen tree confirmed by 3 people in Zone 4") and shows the "last updated" time. It says "based on current reports", never "safe" or "all clear". No timetable data and no live routing service are used.

## Module 3: Reporting (owner: zy)

**FR3.1 Report a flood.** A signed-in UM user shall be able to report with 1 to 3 live camera photos (no gallery), an automatic location, and a note. Photos are shrunk to about 100 KB, stripped of hidden data, and have faces blurred on the phone before upload. The reporter sees the blurred photo and is asked to check that no face or number plate is visible; if the blur fails, the upload is refused and the user retakes the photo. Photos are visible only to signed-in UM users. The first report shows a short privacy notice. The location must be inside UM plus a 100 m margin; it is read once and rounded to about 20 m on the map. The report appears immediately as unconfirmed. Limits: one new report per zone per hour and 5 per person per day; deleting your own report frees the slot. If an open report is within about 50 m in the same zone, the app asks whether it is the same flood and offers to confirm it instead.

**FR3.2 Confirm a report.** A signed-in UM user inside the UM area (same one-time location check as reporting) shall be able to tap "still flooded" once per report. The report turns red when 3 people have confirmed it; the reporter counts as the first. Staff can confirm instantly. Simultaneous confirmations are counted correctly.

**FR3.3 Fix, delete and flag.** Staff shall be able to mark a report fixed or delete any report or photo. A student shall be able to delete their own report. Any student shall be able to flag a photo with "Report a problem"; a photo with 3 flags from different people is hidden until staff review it. Photos are removed 14 days after a report is fixed, or 14 days after creation if never fixed, by the scheduled GitHub job (the admin page has a manual "clean now" button); an anonymous record (zone, date, confirmed or not, time to fix) remains.

## Module 4: Alerts (owner: Ker Li; Isaac builds the admin page that posts notices, the Notices tab and the phone backup rain check)

**FR4.1 Rain warning.** The app shall warn followed zones when heavy rain is forecast (Open-Meteo). A scheduled GitHub job sends the warning; each phone also checks as a backup. The same warning is never shown twice.

**FR4.2 Official notices.** Staff shall be able to post a notice that appears as a card on the home screen and as a pop-up to everyone.

**FR4.3 Leave-now alert.** The app shall tell the user to go, and which route to avoid, when a followed zone turns red or heavy rain is about to hit it. The scheduled GitHub job applies the red-zone rule to the reports and sends the push.

## Module 5: AI and Risk (owner: Chen Xi)

Each AI feature has a short written justification in the report: why AI adds value over a simple alternative, and what happens when it is wrong.

**FR5.1 Photo check (AI).** The app shall suggest what the first photo shows (flood, fallen tree, blocked drain). The user can correct it or switch the check off and pick a category by hand. The AI sees only the blurred photo. A notice explains that the photo is checked by AI. If Google's free tier may use photos to improve its products, the check is opt-in.

**FR5.2 Face blur (AI).** Before upload, faces are blurred on the phone. Number-plate blur is added only if time allows (best effort).

**FR5.3 "Is it safe?" chatbot (AI, optional).** Decision on Mon 7 Dec: built only if the route check, admin page and Malay text are done. The user can ask in English or Malay whether a place is safe. The chatbot is a thin add-on over the same zone and route data: it answers only from the current map and the safety guidelines, says "I don't know" otherwise, points to official notices, and never calls a red zone safe. It needs internet and says so when offline.

**FR5.4 Flood-risk score.** The app shall compute an "indicative" score from forecast rain and a zone's ground level (rule-based, not AI):

| Forecast rain | High ground | Medium ground | Low ground |
|---|---|---|---|
| Light (under 5 mm/h) | green | green | green |
| Moderate (5 to 15) | green | green | yellow |
| Heavy (over 15) | green | yellow | yellow |

The score never shows red.

Module 5 has three required features (FR5.1, FR5.2, FR5.4) without the chatbot.

---

## Non-functional requirements

**NFR1 Speed.** With 50 phones using the app at the same time, a confirmed report shall turn the zone red on another phone within 5 seconds, and the map and route verdict shall be ready within 5 seconds on wifi.
*Why it matters:* a flood warning that arrives late is useless.
*How we measure:* a script simulates 50 phones watching the map for about 2 minutes while 3 real phones time 10 confirmations. Results: average and worst time.

**NFR2 Privacy and safety.** Only verified UM emails shall be able to report or confirm; reports outside UM plus a 100 m margin shall be rejected; at least 80% of clearly visible faces shall be blurred in the test set.
*Why it matters:* report photos can show people, so only UM users see them, and students must trust that reports are real and from campus.
*How we measure:* try a non-UM email on camera; try an out-of-area report; run the 30-photo blur test and report the results.

---

## Quality rules that apply everywhere

- Colour is never the only signal (symbol and word always shown); contrast at least 4.5:1; touch targets at least 48 dp with 8 dp gaps; screen reader reads the banner as one sentence.
- "No problems reported" is used instead of "All clear".
- Offline: last known colours stay visible with a "last updated" time; reports wait and send later.

## Not included

An iPhone version (the team has no Mac, and the brief does not ask for one); haze (dropped); non-rain blockages such as construction or events (outside SDG 13.1); a profile page; 3D map; live pedestrian routing; timetable data; Park smart (bonus only if time is left).
