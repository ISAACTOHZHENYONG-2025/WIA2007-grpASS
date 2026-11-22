# RainReady app: Data layout

> **Status: LOCKED v1.** Locked on Wed 11 Nov 2026 by Jingying and zy (`23110482@siswa.um.edu.my`).
> Owner: Jingying (with zy). Last updated: 22 Nov 2026 (incorporating zy's security rules and report-flow review from [zy_data-layout-review_2026-11-11](https://docs.google.com/document/d/1wxrNVUmAaC0vjTq9VCjrRdpDvyX2k9Cz99GL3hfxe5Q/edit?usp=drivesdk&ouid=117941780725129036897) based on [DATA_LAYOUT_DRAFT.md](https://drive.google.com/file/d/19PSdQf-2EhZrgA2TfIAYjfGQG5XVJVDx/view?usp=drivesdk) and [PLAN (team decisions)](https://docs.google.com/document/d/19hqdpRRAfHQJw9O91C5fkT34Pw4ly6BHhCg_7B3ssis/edit?usp=drivesdk&ouid=117941780725129036897)).
> After the lock, any change to a field name or collection needs Jingying's approval (pull request review).

## 1. What this file is

This file defines **what data the app stores, what each piece is called, and how the pieces link together**. Everyone writes code against the same names, so the app does not break because two people used different names.

Words used:

* **collection**: a folder of records in Firestore (like a sheet in Excel)

* **document**: one record (one row)

* **field**: one piece of information in a record (one column)

* **row name / ID**: the unique name of a record

## 2. Naming rules (LOCKED)

* Field names use `camelCase`: `zoneId`, `createdAt`, `buildingId`.

* IDs come from Emmanuel's files: zones `Z1` to `Z10`; buildings use his short codes (`FSKTM`, `MAINLIB`, `KK8`, ...).

* Positions are written `lat, lng` (latitude first). Note: GeoJSON files list longitude first (`[lng, lat]`).

## 3. Stored in Firestore (changes while the app is running)

### 3.1 `zones` (live staff override state only)

Each phone computes the displayed zone colour locally from active confirmed reports + `staffOverride` + rain forecast (Decision 1 = Option B, per [PLAN (team decisions)](https://docs.google.com/document/d/19hqdpRRAfHQJw9O91C5fkT34Pw4ly6BHhCg_7B3ssis/edit?usp=drivesdk&ouid=117941780725129036897) Section 4). Only staff (`admins`) write to `zones`.

| Field | Type | Meaning | Example | Requirement | 
| ----- | ----- | ----- | ----- | ----- | 
| `zoneId` (row name) | text | Unique ID of the zone (`Z1` to `Z10`) | `Z6` | FR2.1 | 
| `staffOverride` | text | Colour set by staff (`green` / `yellow` / `red`), or empty (`""`) | `red` | FR2.2 | 
| `staffOverrideUntil` | time | When the override ends (max 12 hours from set time) | `2026-11-20 22:00` | FR2.2 | 
| `updatedAt` | time | Last time this row changed | `2026-11-20 10:08` | FR2.1 | 

### 3.2 `reports` (public map pin metadata — text only)

Readable by everyone (including unauthenticated users checking routes in Demo Scene 2). Contains **no reporter identity** (stored in `reports_private/{reportId}`) and **no raw photo data** (stored in `reports/{reportId}/photos/{photoId}`).

| Field | Type | Meaning | Example | Requirement | 
| ----- | ----- | ----- | ----- | ----- | 
| `reportId` (row name) | text | Unique ID of the report | `R001` | FR3.1 | 
| `zoneId` | text | Which zone it belongs to (`Z1` to `Z10`) | `Z6` | FR3.1, FR2.2 | 
| `category` | text | `flood` / `fallen tree` / `blocked drain` | `flood` | FR3.1, FR5.1 | 
| `note` | text | Short note from the reporter | `Water at the entrance` | FR3.1 | 
| `lat`, `lng` | two numbers | Position rounded to about 20 m (must be inside UM + 100 m margin) | `3.12, 101.65` | FR3.1, NFR2 | 
| `createdAt` | time | When it was reported (confirmed reports go stale after 6 hours) | `2026-11-20 10:00` | FR3.1, FR2.2 | 
| `status` | text | `unconfirmed` / `confirmed` / `fixed` | `unconfirmed` | FR3.2, FR3.3 | 
| `confirmCount` | number | Number of confirmations (starts at `1` for reporter; updated in a transaction; `status = confirmed` at `>= 3` or staff confirm) | `1` | FR3.2 | 
| `photoCount` | number | Number of photos attached (`1` to `3`) | `2` | FR3.1 | 
| `flagCount` | number | Number of distinct students who flagged the photo (`>= 3` hides photos until staff review) | `0` | FR3.3 | 
| `fixedAt` | time | When staff marked it fixed (empty/null if not fixed) | `empty` | FR3.3 | 

### 3.3 `reports_private` (reporter identity — admin and reporter only)

Path: `reports_private / {reportId}`

Because Firestore cannot hide a single field inside a publicly readable document, reporter identity lives in this separate top-level collection (per [PLAN (team decisions)](https://docs.google.com/document/d/19hqdpRRAfHQJw9O91C5fkT34Pw4ly6BHhCg_7B3ssis/edit?usp=drivesdk&ouid=117941780725129036897) Section 5 and [Week 1 guide](https://docs.google.com/document/d/1MEt3deuklLMtBQ6Lt5y5Qhr7bYKniIE1yI9y1r-7n0Y/edit?usp=drivesdk&ouid=117941780725129036897)). Readable only by the reporter (`resource.data.createdBy == request.auth.uid`) and `admins`. Used to verify ownership when a student deletes their own report (`FR3.3`) and to enforce the "5 reports per person per day" limit (`FR3.1`).

| Field | Type | Meaning | Example | Requirement | 
| ----- | ----- | ----- | ----- | ----- | 
| `reportId` (row name) | text | Matches `reports/{reportId}` | `R001` | FR3.1, FR3.3 | 
| `createdBy` | text | Firebase Auth UID of the reporter | `uid_ali_123` | FR3.1, NFR2 | 
| `reporterEmail` | text | Verified UM email of the reporter | `23110482@siswa.um.edu.my` | FR1.3, NFR2 | 
| `createdAt` | time | When the report was created (for 5/day limit query) | `2026-11-20 10:00` | FR3.1 | 

### 3.4 `photos` (inside each report — signed-in UM users only)

Path: `reports / {reportId} / photos / {photoId}`

Loaded only when a signed-in UM user opens a report (and hidden if the parent report's `flagCount >= 3` until staff clear the flags). Stored in Firestore because Cloud Storage for Firebase requires the paid Blaze plan ([Default bucket and billing requirements for Cloud Storage](https://firebase.google.com/docs/storage/faqs-storage-changes-announced-sept-2024); [Firebase Pricing](https://firebase.google.com/pricing)).

| Field | Type | Meaning | Example | Requirement | 
| ----- | ----- | ----- | ----- | ----- | 
| `photoId` (row name) | text | Photo slot ID (`p1`, `p2`, or `p3`) | `p1` | FR3.1 | 
| `imageBase64` | text | Shrunk JPEG (`<= 100 KB`, max `800 px`, EXIF stripped, faces blurred on-device before upload) | `/9j/4AAQSkZJRg...` | FR3.1, FR5.2, NFR2 | 
| `widthPx` | number | Image width in pixels (max 800) | `800` | FR3.1 | 
| `heightPx` | number | Image height in pixels (max 800) | `600` | FR3.1 | 
| `createdAt` | time | When uploaded (deleted 14 days after fixed/created by GitHub robot) | `2026-11-20 10:00` | FR3.3 | 

### 3.5 `confirmations` (inside each report)

Path: `reports / {reportId} / confirmations / {userId}`

| Field | Type | Meaning | Example | Requirement | 
| ----- | ----- | ----- | ----- | ----- | 
| `userId` (row name) | text | Firebase Auth UID of the person who confirmed. Using `userId` as the row name guarantees one person = one row = one vote. | `uid_bala_456` | FR3.2 | 
| `createdAt` | time | When they tapped "still flooded" | `2026-11-20 10:05` | FR3.2 | 

When a student confirms, a Firestore Transaction creates `confirmations/{userId}`, increments `confirmCount` on `reports/{reportId}`, and sets `status = "confirmed"` if `confirmCount >= 3`. Staff confirm instantly, setting `status = "confirmed"` directly.

### 3.6 `flags` (inside each report)

Path: `reports / {reportId} / flags / {userId}`

| Field | Type | Meaning | Example | Requirement | 
| ----- | ----- | ----- | ----- | ----- | 
| `userId` (row name) | text | Firebase Auth UID of the student who flagged the photo (1 flag vote per person) | `uid_chen_789` | FR3.3 | 
| `reason` | text | Short reason (`face visible` / `wrong place` / `spam`) | `face visible` | FR3.3, NFR2 | 
| `createdAt` | time | When flagged | `2026-11-20 10:12` | FR3.3 | 

### 3.7 `notices` (official staff announcements)

| Field | Type | Meaning | Example | Requirement | 
| ----- | ----- | ----- | ----- | ----- | 
| `noticeId` (row name) | text | Unique ID | `N01` | FR4.2 | 
| `textEn` | text | English message | `Library closed due to flooding` | FR4.2, FR1.1 | 
| `textMs` | text | Malay message (can be empty; English is shown then) | `Perpustakaan ditutup kerana banjir` | FR1.1, FR4.2 | 
| `createdAt` | time | When it was posted | `2026-11-20 09:30` | FR4.2 | 

### 3.8 `admins` (staff role lookup)

Edited by Isaac in the Firebase Console. Used by `firestore.rules` and the app to unlock the staff admin screen.

| Field | Type | Meaning | Example | Requirement | 
| ----- | ----- | ----- | ----- | ----- | 
| `uid` (row name) | text | Firebase Auth UID of the staff account | `uid_staff_01` | FR1.3, FR3.3 | 
| `email` | text | Staff email address | `staff_demo@um.edu.my` | FR1.3 | 
| `role` | text | `staff` or `admin` | `admin` | FR1.3 | 
| `addedAt` | time | When added | `2026-11-11 18:00` | FR1.3 | 

### 3.9 `stats` (anonymous record kept after 14-day photo clean-up)

Written when the GitHub robot (or admin "clean now" button) cleans up expired reports after 14 days (per [PLAN (team decisions)](https://docs.google.com/document/d/19hqdpRRAfHQJw9O91C5fkT34Pw4ly6BHhCg_7B3ssis/edit?usp=drivesdk&ouid=117941780725129036897) Section 5 & 6.4).

| Field | Type | Meaning | Example | Requirement | 
| ----- | ----- | ----- | ----- | ----- | 
| `statId` (row name) | text | Matches old `reportId` | `R001` | FR3.3 | 
| `zoneId` | text | Zone ID (`Z1` to `Z10`) | `Z6` | FR3.3 | 
| `category` | text | `flood` / `fallen tree` / `blocked drain` | `flood` | FR3.3 | 
| `date` | text | Date reported (`YYYY-MM-DD`) | `2026-11-20` | FR3.3 | 
| `wasConfirmed` | boolean | Whether the report reached `confirmed` status | `true` | FR3.3 | 
| `timeToFixMinutes` | number | Minutes from `createdAt` to `fixedAt` (null if never marked fixed) | `85` | FR3.3 | 

### 3.10 `zoneWarningCooldowns` (rain-warning 60-minute cooldown)

Path: `zoneWarningCooldowns / {zoneId}`

Written only by the scheduled GitHub rain-warning robot using the service account (Admin SDK). Phones have read-only access (`allow read: if isValidZoneId(zoneId); allow write: if false;`) so the WorkManager backup rain check can avoid sending duplicate warnings during the 60-minute cooldown (FR4.1).

| Field | Type | Meaning | Example | Requirement | 
| ----- | ----- | ----- | ----- | ----- | 
| `zoneId` (row name) | text | Zone ID (`Z1` to `Z10`) | `Z6` | FR4.1 | 
| `lastWarningSentAt` | Timestamp | Server timestamp written by the GitHub robot (`serverTimestamp()`) when it last sent a rain warning for this zone | `2026-11-20T14:00:00Z` (Firestore Timestamp) | FR4.1 | 

> **Deduplication note:** Reading `zoneWarningCooldowns` only covers the **robot-first** case (so the phone backup check skips firing if the robot already sent a warning within 60 minutes). **Phone-first** and **simultaneous** cases are deduped on the device using a local record plus one fixed notification tag per zone (best-effort, not guaranteed).

## 4. Stored inside the app as static files (LOCKED — Decision 5 = Option A)

These do not change while the app is running. Bundling them inside the app (`assets/`) saves Firestore daily reads (50,000/day Spark limit per [Firebase Pricing](https://firebase.google.com/pricing)) and avoids Firestore's restriction on nested coordinate lists. Source: Emmanuel's drafts (9 and 10 Nov 2026), to be verified on campus in Week 3.

### 4.1 Static zones (`assets/zones.geojson`)

| Field | Type | Meaning | Example | Requirement | 
| ----- | ----- | ----- | ----- | ----- | 
| `zoneId` | text | Zone ID (`Z1` to `Z10`) | `Z8` | FR2.1 | 
| `name` | text | Zone name | `South-east: KK1, Law, Exam Hall, KL Gate` | FR2.1 | 
| `groundLevel` | text | `low` / `medium` / `high` (guess from elevation data until Week 3 walk) | `low` | FR2.1, FR2.2, FR5.4 | 
| `groundHeightM` | number | Ground height in metres, optional | `52` | FR2.1 | 
| `shape` | list of lists | Polygon coordinates (`[[lng, lat], ...]`) in GeoJSON | `[[[101.657, 3.115], ...]]` | FR2.1 | 

### 4.2 Buildings (`assets/buildings.json`)

| Field | Type | Meaning | Example | Requirement | 
| ----- | ----- | ----- | ----- | ----- | 
| `buildingId` | text | Short code from Emmanuel (35 buildings) | `MAINLIB` | FR2.3 | 
| `name` | text | Building name | `Main Library` | FR2.3 | 
| `zoneId` | text | Which zone it is in | `Z6` | FR2.3 | 
| `lat`, `lng` | two numbers | Position (to check on campus in Week 3; `KK13` is uncertain) | `3.1203, 101.6543` | FR2.3 | 
| `groundHeightM` | number | Optional | `58` | FR2.3 | 

### 4.3 Paths (`assets/paths.json`)

| Field | Type | Meaning | Example | Requirement | 
| ----- | ----- | ----- | ----- | ----- | 
| `pathId` | text | Unique ID (14 paths: 10 usual + 4 alternatives) | `P01` | FR2.3 | 
| `fromBuildingId` | text | Start building ID | `FSKTM` | FR2.3 | 
| `toBuildingId` | text | End building ID | `MAINLIB` | FR2.3 | 
| `zoneIds` | list | Zones the path passes (inside zone or within 3 m of edge for `>= 25 m`) | `["Z1", "Z2", "Z6"]` | FR2.3 | 
| `walkingMinutes` | number | Walking time in minutes (75 m/min draft; timed on campus in Week 3) | `9` | FR2.3 | 
| `isUsual` | boolean | `true` = usual path; `false` = alternative skipping a flooded zone | `true` | FR2.3 | 

## 5. Name mapping (Emmanuel's names to this layout)

| Emmanuel's current name | Layout name | Note | 
| ----- | ----- | ----- | 
| `zone_id` | `zoneId` | Used in zones and buildings | 
| `ground_level_guess` | `groundLevel` | Still a guess until Week 3 campus walk | 
| `id` (buildings) | `buildingId` | Short building code | 
| `coordinates [lng, lat]` | `lat, lng` | Swapped in app/Firestore (`lat` first); kept `[lng, lat]` inside raw GeoJSON geometry | 
| `fill`, `stroke`, ... | *(not stored)* | Colours are computed dynamically by the app from zone state | 

## 6. Locked decisions summary (11 Nov 2026)

| \# | Question | Option A | Option B | Locked Decision | Decided By | 
| ----- | ----- | ----- | ----- | ----- | ----- | 
| **1** | Where is the zone colour worked out? | Stored in `zones` | Computed on each phone (+ GitHub robot uses same rule) | **Option B** (computed on each phone & robot) | Jingying, zy | 
| **2** | How to check "5 reports/person/day, 1/zone/hour"? | Query active `reports` / `reports_private` | Keep a counter per person | **Option A** (query active documents; deleting frees slot) | Jingying, zy | 
| **3** | How to count confirmations? | Count rows in `confirmations` only | Also keep `confirmCount` on `reports` via Transaction | **Option B** (`confirmCount` + `confirmations/{userId}` in a Transaction) | Jingying, zy | 
| **4** | Where are photos stored? | Inside free Firestore (`reports/{id}/photos/{photoId}`) | Cloud Storage (requires paid Blaze plan) | **Option A** (Firestore `photos` subcollection, `<= 100 KB` JPEG Base64) | Isaac, zy, Jingying | 
| **5** | Where do fixed data live? | Bundled inside the app as static files | In Firestore | **Option A** (inside the app as static files) | Jingying, Emmanuel, zy | 
| **6** | Naming style | `camelCase` | `snake_case` | **Option A** (`camelCase`) | Jingying, Emmanuel | 

## 7. Change process after the lock

1. Open a pull request that changes `docs/DATA_LAYOUT.md` and the code together.

2. Tag Jingying (and zy if it affects `reports`, `photos`, `confirmations`, or `firestore.rules`) as reviewer.

3. Jingying checks the change against the other modules (reports, alerts, AI, map) before approving.