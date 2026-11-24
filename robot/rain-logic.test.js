'use strict';
// Unit tests for the rain-warning logic. Run: node --test robot/rain-logic.test.js
// ALL forecasts, sends and Firestore records here are SIMULATED (fake functions). No network, no Firebase.

const test = require('node:test');
const assert = require('node:assert/strict');
const L = require('./rain-logic');

const H = L.HOUR_MS;
const MIN = 60 * 1000;
// 20 Nov 2026, 14:20 Malaysia time = 06:20 UTC
const NOW = Date.UTC(2026, 10, 20, 6, 20);
const HOUR_START = Date.UTC(2026, 10, 20, 6, 0); // 14:00 MYT

// Build a fake Open-Meteo "hourly" object. ends = interval END times (ms), mms = values.
function hourly(ends, mms) {
  return { time: ends.map((e) => e / 1000), precipitation: mms };
}
// Intervals ending 14:00, 15:00, 16:00, 17:00 MYT
const E14 = HOUR_START, E15 = HOUR_START + H, E16 = HOUR_START + 2 * H, E17 = HOUR_START + 3 * H;

// Fake Firebase: records every send and cooldown write.
function fakes({ last = {}, failSend = [], failRead = [], failMark = [], forecast, forecastError } = {}) {
  const sends = [];
  const sentIds = [];
  const marks = [];
  const store = { ...last };
  return {
    sends, sentIds, marks, store,
    deps: {
      nowMs: NOW,
      log: () => {},
      getForecast: async () => { if (forecastError) throw new Error(forecastError); return forecast; },
      getLastSentMs: async (z) => { if (failRead.includes(z)) throw new Error('read error'); return store[z] ?? null; },
      sendToTopic: async (topic, z, eventId) => { if (failSend.includes(z)) throw new Error('fcm error'); sends.push(topic); sentIds.push(eventId); },
      markSent: async (z) => { if (failMark.includes(z)) throw new Error('write error'); marks.push(z); store[z] = NOW; },
    },
  };
}

test('topic names: Z1 -> rain_z1, Z10 -> rain_z10', () => {
  assert.equal(L.topicForZone('Z1'), 'rain_z1');
  assert.equal(L.topicForZone('Z10'), 'rain_z10');
});

test('RW-01 below threshold (10 mm): no warning', async () => {
  const f = fakes({ forecast: hourly([E15, E16], [10, 10]) });
  const r = await L.runRainWarning(f.deps);
  assert.equal(r.status, 'no_warning');
  assert.equal(f.sends.length, 0);
});

test('RW-02 exactly at threshold (15.0 mm): no warning', async () => {
  const f = fakes({ forecast: hourly([E15, E16], [15.0, 15.0]) });
  const r = await L.runRainWarning(f.deps);
  assert.equal(r.status, 'no_warning');
  assert.equal(f.sends.length, 0);
});

test('RW-03 above threshold (15.1 mm): warns all 10 zones and records cooldowns', async () => {
  const f = fakes({ forecast: hourly([E15, E16], [15.1, 0]) });
  const r = await L.runRainWarning(f.deps);
  assert.equal(r.status, 'warned');
  assert.deepEqual(f.sends, L.ZONES.map(L.topicForZone));
  assert.deepEqual(f.marks, L.ZONES);
});

test('RW-04 at 14:20 BOTH overlapping hours count: second hour 16 mm triggers', async () => {
  const f = fakes({ forecast: hourly([E15, E16], [5, 16]) });
  const r = await L.runRainWarning(f.deps);
  assert.equal(r.status, 'warned');
});

test('RW-05 hour outside the next 60 minutes is ignored (17:00 interval = 50 mm)', async () => {
  const f = fakes({ forecast: hourly([E15, E16, E17], [5, 5, 50]) });
  const r = await L.runRainWarning(f.deps);
  assert.equal(r.status, 'no_warning');
});

test('RW-06 at exact hour 14:00 the interval that just ended (30 mm) is excluded', async () => {
  const f = fakes({ forecast: hourly([E14, E15, E16], [30, 5, 40]) });
  f.deps.nowMs = HOUR_START; // exactly 14:00
  const r = await L.runRainWarning(f.deps);
  // Only 14:00-15:00 (5 mm) overlaps the next 60 minutes. 15:00-16:00 starts exactly at 15:00 = not inside.
  assert.equal(r.status, 'no_warning');
  assert.equal(r.forecast.intervals.length, 1);
});

test('RW-07 missing forecast (all values null): no warning, status missing', async () => {
  const f = fakes({ forecast: hourly([E15, E16], [null, null]) });
  const r = await L.runRainWarning(f.deps);
  assert.equal(r.status, 'missing');
  assert.equal(f.sends.length, 0);
});

test('RW-07b missing forecast (no hourly data at all): status missing', async () => {
  const f = fakes({ forecast: {} });
  const r = await L.runRainWarning(f.deps);
  assert.equal(r.status, 'missing');
});

test('RW-07c one hour missing, other above 15: still warns, marked partial', async () => {
  const f = fakes({ forecast: hourly([E15, E16], [null, 20]) });
  const r = await L.runRainWarning(f.deps);
  assert.equal(r.status, 'warned');
  assert.equal(r.forecast.partial, true);
});

test('RW-08 repeated run within 60 minutes: second run sends nothing', async () => {
  const f = fakes({ forecast: hourly([E15, E16], [20, 20]) });
  const r1 = await L.runRainWarning(f.deps);
  assert.equal(r1.sent.length, 10);
  f.deps.nowMs = NOW + 15 * MIN; // next scheduled run, 15 min later
  const r2 = await L.runRainWarning(f.deps);
  assert.equal(r2.status, 'all_in_cooldown');
  assert.equal(r2.skipped.length, 10);
  assert.equal(f.sends.length, 10); // still only the first 10
});

test('RW-08b exactly 60 minutes later: warning allowed again', async () => {
  const f = fakes({ forecast: hourly([E15, E16], [20, 20]), last: { Z1: NOW - 60 * MIN, Z2: NOW - 59 * MIN } });
  const r = await L.runRainWarning(f.deps);
  assert.ok(f.sends.includes('rain_z1'));
  assert.ok(!f.sends.includes('rain_z2'));
  assert.deepEqual(r.skipped, ['Z2']);
});

test('RW-09 source failure (simulated): no warning, status source_failure', async () => {
  const f = fakes({ forecastError: 'timeout' });
  const r = await L.runRainWarning(f.deps);
  assert.equal(r.status, 'source_failure');
  assert.equal(f.sends.length, 0);
  assert.equal(f.marks.length, 0);
});

test('RW-10 FCM send fails for Z3: Z3 cooldown NOT updated, others fine', async () => {
  const f = fakes({ forecast: hourly([E15, E16], [20, 20]), failSend: ['Z3'] });
  const r = await L.runRainWarning(f.deps);
  assert.equal(r.status, 'partial_failure');
  assert.ok(!f.marks.includes('Z3'));
  assert.equal(f.marks.length, 9);
});

test('RW-11 cooldown cannot be read for Z4: Z4 not sent (assumption to confirm)', async () => {
  const f = fakes({ forecast: hourly([E15, E16], [20, 20]), failRead: ['Z4'] });
  const r = await L.runRainWarning(f.deps);
  assert.ok(!f.sends.includes('rain_z4'));
  assert.equal(r.status, 'partial_failure');
});

test('RW-12 test topic mode: one send to "test", no cooldown read or write', async () => {
  const f = fakes({ forecast: hourly([E15, E16], [20, 20]), failRead: L.ZONES });
  const r = await L.runRainWarning({ ...f.deps, testTopic: 'test' });
  assert.equal(r.status, 'warned_test');
  assert.deepEqual(f.sends, ['test']);
  assert.equal(f.marks.length, 0);
});

test('RW-13 dry run: decides but sends and writes nothing', async () => {
  const f = fakes({ forecast: hourly([E15, E16], [20, 20]) });
  const r = await L.runRainWarning({ ...f.deps, dryRun: true });
  assert.equal(r.status, 'dry_run');
  assert.equal(f.sends.length, 0);
  assert.equal(f.marks.length, 0);
});

// ---------- eventId (team dedupe design, 24 Nov) ----------
const sec = (ms) => Math.floor(ms / 1000);

test('EV-01 two intervals above 15: eventId uses the EARLIEST end', () => {
  const f = L.decideForecast(hourly([E15, E16], [20, 30]), NOW); // 14:20
  assert.equal(f.eventId, 'rain_' + sec(E15));
});

test('EV-01b only the later interval above 15: eventId uses that one', () => {
  const f = L.decideForecast(hourly([E15, E16], [5, 30]), NOW);
  assert.equal(f.eventId, 'rain_' + sec(E16));
});

test('EV-02 exact hour 14:00: just-ended interval (13:00-14:00, 30 mm) is excluded', () => {
  const f = L.decideForecast(hourly([E14, E15], [30, 20]), HOUR_START);
  assert.equal(f.eventId, 'rain_' + sec(E15));
});

test('EV-03 same interval at 14:20 and 14:35: same eventId', () => {
  const data = hourly([E15, E16], [20, 0]);
  const a = L.decideForecast(data, NOW);
  const b = L.decideForecast(data, NOW + 15 * MIN);
  assert.equal(a.eventId, b.eventId);
});

test('EV-04 next hour (run at 15:05, rain now 15:00-16:00): different eventId', () => {
  const a = L.decideForecast(hourly([E15, E16], [20, 0]), NOW); // 14:20
  const b = L.decideForecast(hourly([E16, E17], [20, 0]), HOUR_START + H + 5 * MIN); // 15:05
  assert.notEqual(a.eventId, b.eventId);
  assert.equal(b.eventId, 'rain_' + sec(E16));
});

test('EV-05 no warning: eventId is null', () => {
  assert.equal(L.decideForecast(hourly([E15, E16], [15, 10]), NOW).eventId, null);
  assert.equal(L.decideForecast({}, NOW).eventId, null);
});

test('EV-06 campus-wide: all 10 zones get the same eventId, with no zone in it', async () => {
  const f = fakes({ forecast: hourly([E15, E16], [20, 20]) });
  await L.runRainWarning(f.deps);
  assert.equal(f.sentIds.length, 10);
  assert.equal(new Set(f.sentIds).size, 1);
  assert.match(f.sentIds[0], /^rain_\d+$/);
});

test('EV-07 payload: data-only, eventId and zoneId as strings, high priority, no notification block', () => {
  const m = L.buildMessage('rain_z6', 'Z6', 'rain_' + sec(E15));
  assert.equal(m.topic, 'rain_z6');
  assert.equal(m.notification, undefined);
  assert.equal(m.android.priority, 'high');
  assert.deepEqual(m.data, {
    type: 'rain_warning',
    eventId: 'rain_' + sec(E15),
    zoneId: 'Z6',
    title: L.WARNING_TITLE,
    body: L.WARNING_BODY,
  });
  for (const v of Object.values(m.data)) assert.equal(typeof v, 'string');
});
