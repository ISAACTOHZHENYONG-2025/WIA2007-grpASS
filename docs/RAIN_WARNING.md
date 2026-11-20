# Rain warning job (GitHub Actions)

Owner: Ker Li. Files: `.github/workflows/rain-warning.yml`, `robot/rain-logic.js`, `robot/rain-warning.js`, `robot/rain-logic.test.js`.

## Agreed rules
Decided by Isaac on 20 Nov 2026: [LINK TO ISAAC'S DECISION / TEAM PLAN SECTION]

- Source: Open-Meteo hourly `precipitation` (mm). Each value is the sum of the **preceding** hour (the 15:00 value covers 14:00-15:00).
- Location: one campus point, lat 3.1231, lon 101.6529 (UM map centre), reused for all zones.
- Trigger: warn if **either** hourly interval overlapping the next 60 minutes is **strictly above 15 mm**. 15.0 does not trigger. At an exact hour, the interval that just ended is excluded.
- Topics: `rain_z1` to `rain_z10`.
- Wording: "Heavy rain forecast around UM soon. Check conditions before travelling."
- Schedule: every 15 minutes. GitHub may start scheduled runs late, and only runs them from `main`.
- Cooldown: 60 minutes per zone, stored in `zoneWarningCooldowns/{zoneId}.lastWarningSentAt`, written only after FCM accepts the send.
- Only one run at a time (workflow `concurrency` group `rain-warning`, `cancel-in-progress: false`).

## Operations
- Secret: uses the repository secret `FIREBASE_SERVICE_ACCOUNT` (already used by `test-push`).
- Who watches live runs: [NAME, agreed with the team]. GitHub emails failed scheduled runs to the person who last changed the cron schedule in the workflow file.
- Failed runs (red): forecast source down, missing forecast, or a zone that could not be sent or recorded.

## Known limitations (not blocking, from PR #14 review)
a. **All 10 zone topics fire together.** A user who follows several zones would get one notification per zone. The shared eventId / dedupe design (Isaac's 20 Nov request) must be agreed **before the app starts subscribing to zone topics**.
b. **Effective cooldown is 60-75 minutes, not exactly 60.** The timestamp is written after the send, and the job only runs every 15 minutes, so the first run after the 60 minutes are up can be up to 15 minutes later (more if GitHub starts the run late).
c. `firebase-admin` is pinned to **14.5.1** in the workflow. Update it on purpose, not automatically.
d. Phone backup (Isaac's WorkManager) and leave-now alerts are separate and not handled by this job.
