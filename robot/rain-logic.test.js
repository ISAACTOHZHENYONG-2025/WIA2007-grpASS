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
  const marks = [];
  const store = { ...last };
  return {
    sends, marks, store,
    deps: {
      nowMs: NOW,
      log: () => {},
      getForecast: async () => { if (forecastError) throw new Error(forecastError); return forecast; },
      getLastSentMs: async (z) => { if (failRead.includes(z)) throw new Error('read error'); return store[z] ?? null; },
      sendToTopic: async (topic, z) => { if (failSend.includes(z)) throw new Error('fcm error'); sends.push(topic); },
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
