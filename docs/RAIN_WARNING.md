# Rain warning job (GitHub Actions)

Owner: Ker Li. Files: `.github/workflows/rain-warning.yml`, `robot/rain-logic.js`, `robot/rain-warning.js`, `robot/rain-logic.test.js`.

## Agreed rules
Decided by Isaac: team plan section 6.2A "Rain-warning decisions (19 Nov 2026)" https://docs.google.com/document/d/19hqdpRRAfHQJw9O91C5fkT34Pw4ly6BHhCg_7B3ssis/edit?pli=1&tab=t.0#heading=h.ys47xm3loec4 and section 4 "Weather" (UM coordinate, 20 Nov). The overlapping-hours window and the warning wording were decided by Isaac in the team group chat on 20 Nov 2026.

- Source: Open-Meteo hourly `precipitation` (mm). Each value is the sum of the **preceding** hour (the 15:00 value covers 14:00-15:00).
- Location: one campus point, lat 3.1231, lon 101.6529 (UM map centre), reused for all zones.
- Trigger: warn if **either** hourly interval overlapping the next 60 minutes is **strictly above 15 mm**. 15.0 does not trigger. At an exact hour, the interval that just ended is excluded.
- Topics: `rain_z1` to `rain_z10`.
- Wording: "Heavy rain forecast around UM soon. Check conditions before travelling."
- Schedule: every 15 minutes. GitHub may start scheduled runs late, and only runs them from `main`.
- Cooldown: 60 minutes per zone, stored in `zoneWarningCooldowns/{zoneId}.lastWarningSentAt`, written only after FCM accepts the send. Phones have read-only access (zy's rules), for Isaac's WorkManager backup check. Layout: `docs/DATA_LAYOUT.md` section 3.10.
- Only one run at a time (workflow `concurrency` group `rain-warning`, `cancel-in-progress: false`).

## Dedupe with the phone backup (team design, led by Jingying, 24 Nov 2026)
- `eventId` = `rain_<endUnixSeconds>`: end time (unix seconds, UTC) of the **earliest** hourly interval in the next 60 minutes with mm > 15. Campus-wide, no zone in it. Same rain hour gives the same ID on the robot and the phone.
- The robot sends a **data-only** FCM message (no `notification` block), `android.priority: high`. The app posts the notification itself with tag `rain_warning` and ID `2001`, so a robot warning and a phone-backup warning replace each other instead of showing twice.
- Sample payload (topic `rain_z6`, interval ending 20 Nov 15:00 MYT):
```json
{
  "topic": "rain_z6",
  "data": {
    "type": "rain_warning",
    "eventId": "rain_1795158000",
    "zoneId": "Z6",
    "title": "RainReady",
    "body": "Heavy rain forecast around UM soon. Check conditions before travelling."
  },
  "android": { "priority": "high" }
}
```
- Tests: EV-01 to EV-07 in `robot/rain-logic.test.js`.
- **Needs the app:** a data-only message shows nothing until the app handles it (Isaac's app change). The current skeleton app does not, so phone checks of the robot wait for that change. If the app is force-stopped, Android may not deliver data messages.

## Operations
- Secret: uses the repository secret `FIREBASE_SERVICE_ACCOUNT` (already used by `test-push`).
- Who watches live runs: Isaac and Jingying (agreed in the PR #14 review). GitHub emails failed scheduled runs to the person who last changed the cron schedule in the workflow file.
- Failed runs (red): forecast source down, missing forecast, or a zone that could not be sent or recorded.

## Known limitations (not blocking, from PR #14 review)
a. **All 10 zone topics fire together.** A user following several zones gets one message per zone, but they share one `eventId` and the app shows them under the same tag + ID, so only one notification stays visible (see Dedupe above).
b. **Effective cooldown is 60-75 minutes, not exactly 60.** The timestamp is written after the send, and the job only runs every 15 minutes, so the first run after the 60 minutes are up can be up to 15 minutes later (more if GitHub starts the run late).
c. `firebase-admin` is pinned to **14.5.1** in the workflow. Update it on purpose, not automatically.
d. Phone backup (Isaac's WorkManager) and leave-now alerts are separate and not handled by this job.
