'use strict';
// Rain-warning job: Open-Meteo forecast -> decision -> FCM topics rain_z1..rain_z10 -> cooldown record.
// Runs in GitHub Actions. Needs secret FIREBASE_SERVICE_ACCOUNT (never printed).
//
// Optional env (manual runs only; scheduled runs leave them empty = real mode):
//   DRY_RUN=true                 decide and log only, no FCM send, no Firestore write
//   TEST_TOPIC=test              send ONE message to this topic instead of the zone topics,
//                                cooldowns not read or written (for checking the test phone)
//   SIMULATE_MM=20               SIMULATION: replace the forecast with this value for the next hours
//   SIMULATE=missing             SIMULATION: forecast with no values
//   SIMULATE=source_failure      SIMULATION: forecast request fails

const admin = require('firebase-admin');
const logic = require('./rain-logic');

const LAT = 3.1231; // UM map centre (agreed with Isaac)
const LON = 101.6529;
const FORECAST_URL =
  'https://api.open-meteo.com/v1/forecast' +
  `?latitude=${LAT}&longitude=${LON}` +
  '&hourly=precipitation&precipitation_unit=mm' +
  '&timezone=Asia%2FKuala_Lumpur&timeformat=unixtime&forecast_days=2';

function log(msg) {
  console.log(msg);
}

function simulatedHourly(nowMs, mm) {
  // Interval ends at the next 3 full hours, all with the same value. Clearly a simulation.
  const firstEnd = Math.floor(nowMs / logic.HOUR_MS) * logic.HOUR_MS + logic.HOUR_MS;
  const time = [0, 1, 2].map((k) => (firstEnd + k * logic.HOUR_MS) / 1000);
  return { time, precipitation: time.map(() => mm) };
}

async function fetchOpenMeteo() {
  const res = await fetch(FORECAST_URL, { signal: AbortSignal.timeout(15000) });
  if (!res.ok) throw new Error(`Open-Meteo HTTP ${res.status}`);
  const json = await res.json();
  if (!json.hourly) throw new Error('Open-Meteo response has no "hourly" data');
  return json.hourly;
}

async function main() {
  const nowMs = Date.now();
  const dryRun = process.env.DRY_RUN === 'true';
  const testTopic = (process.env.TEST_TOPIC || '').trim();
  const simulate = (process.env.SIMULATE || '').trim(); // 'none' or empty = no simulation
  const simMmRaw = (process.env.SIMULATE_MM || '').trim();

  if (testTopic && !/^[a-zA-Z0-9-_.~%]+$/.test(testTopic)) {
    throw new Error('TEST_TOPIC has invalid characters.');
  }

  let getForecast = fetchOpenMeteo;
  if (simulate === 'source_failure') {
    log('SIMULATION: forecast source failure.');
    getForecast = async () => { throw new Error('simulated source failure'); };
  } else if (simulate === 'missing') {
    log('SIMULATION: forecast with no values.');
    getForecast = async () => {
      const h = simulatedHourly(nowMs, 0);
      return { time: h.time, precipitation: h.time.map(() => null) };
    };
  } else if (simMmRaw !== '') {
    const mm = Number(simMmRaw);
    if (!Number.isFinite(mm) || mm < 0) throw new Error('SIMULATE_MM must be a number >= 0.');
    log(`SIMULATION: forecast replaced with ${mm} mm per hour.`);
    getForecast = async () => simulatedHourly(nowMs, mm);
  }

  log(`Run at: ${new Date(nowMs).toISOString()} (UTC) = ${logic.myt(nowMs)}`);
  log(`Mode: ${dryRun ? 'DRY RUN' : 'LIVE'}${testTopic ? `, test topic "${testTopic}"` : ', zone topics rain_z1..rain_z10'}`);

  const needsFirebase = !dryRun || !testTopic;
  let db = null;
  let messaging = null;
  if (needsFirebase) {
    const raw = process.env.FIREBASE_SERVICE_ACCOUNT;
    if (!raw) throw new Error('Secret FIREBASE_SERVICE_ACCOUNT is missing or empty.');
    let creds;
    try { creds = JSON.parse(raw); } catch (e) { throw new Error('FIREBASE_SERVICE_ACCOUNT is not valid JSON.'); }
    admin.initializeApp({ credential: admin.credential.cert(creds) });
    db = admin.firestore();
    messaging = admin.messaging();
    log(`Project: ${creds.project_id}`);
  }

  const result = await logic.runRainWarning({
    nowMs,
    log,
    dryRun,
    testTopic: testTopic || undefined,
    getForecast,
    getLastSentMs: async (zoneId) => {
      const snap = await db.collection('zoneWarningCooldowns').doc(zoneId).get();
      if (!snap.exists) return null;
      const ts = snap.get('lastWarningSentAt');
      return ts && typeof ts.toMillis === 'function' ? ts.toMillis() : null;
    },
    sendToTopic: async (topic, zoneId, eventId) => {
      // Data-only message: the app shows it with tag 'rain_warning' + ID 2001 (team dedupe design).
      await messaging.send(logic.buildMessage(topic, zoneId, eventId));
    },
    markSent: async (zoneId) => {
      await db.collection('zoneWarningCooldowns').doc(zoneId).set(
        { zoneId, lastWarningSentAt: admin.firestore.FieldValue.serverTimestamp() },
        { merge: true }
      );
    },
  });

  log(`RESULT: ${result.status} | sent ${result.sent.length} | skipped ${result.skipped.length} | failed ${result.failed.length}`);
  if (['source_failure', 'missing', 'partial_failure'].includes(result.status)) {
    process.exitCode = 1; // red run so failures are visible
  }
}

main().catch((err) => {
  console.error('FAILED:', err.message);
  process.exit(1);
});
