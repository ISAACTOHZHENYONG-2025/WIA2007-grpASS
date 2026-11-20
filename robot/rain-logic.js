'use strict';
// Rain-warning decision logic (no Firebase or network code here, so it can be unit tested).
//
// Agreed rules (Isaac, 20 Nov 2026):
//  - Source: Open-Meteo hourly "precipitation" (mm). Each value is the SUM OF THE PRECEDING HOUR,
//    so the value at 15:00 covers 14:00-15:00.
//  - Warn if EITHER hourly interval overlapping the next 60 minutes is STRICTLY above 15 mm.
//    Exactly 15.0 does not trigger. At an exact hour, the interval that has just ended is excluded.
//  - One campus forecast, reused for all zones Z1-Z10. Topics rain_z1 ... rain_z10.
//  - 60-minute cooldown per zone, using zoneWarningCooldowns/{zoneId}.lastWarningSentAt,
//    updated only after a successful FCM send.

const THRESHOLD_MM = 15;
const HOUR_MS = 60 * 60 * 1000;
const COOLDOWN_MS = 60 * 60 * 1000;
const ZONES = ['Z1', 'Z2', 'Z3', 'Z4', 'Z5', 'Z6', 'Z7', 'Z8', 'Z9', 'Z10'];
const WARNING_TITLE = 'RainReady';
const WARNING_BODY = 'Heavy rain forecast around UM soon. Check conditions before travelling.';

function topicForZone(zoneId) {
  return 'rain_' + zoneId.toLowerCase(); // 'Z1' -> 'rain_z1'
}

// hourly = { time: [unix seconds, end of each interval], precipitation: [mm] }
// Interval i covers (end - 1h, end]. The next 60 minutes are (now, now + 1h].
// They overlap when end > now AND end - 1h < now + 1h, i.e. now < end < now + 2h.
// "end > now" (strict) is what excludes the interval that has just ended at an exact hour.
function relevantIntervals(hourly, nowMs) {
  if (!hourly || !Array.isArray(hourly.time) || !Array.isArray(hourly.precipitation)) return [];
  const out = [];
  for (let i = 0; i < hourly.time.length; i++) {
    const endMs = Number(hourly.time[i]) * 1000;
    if (endMs > nowMs && endMs < nowMs + 2 * HOUR_MS) {
      out.push({ endMs, mm: hourly.precipitation[i] });
    }
  }
  return out;
}

function myt(ms) {
  return new Date(ms).toLocaleString('en-GB', {
    timeZone: 'Asia/Kuala_Lumpur', day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit', hour12: false,
  }) + ' MYT';
}

function isValidMm(v) {
  return typeof v === 'number' && Number.isFinite(v) && v >= 0;
}

// Returns { decision: 'warn' | 'no_warning' | 'missing', maxMm, intervals, partial }
function decideForecast(hourly, nowMs) {
  const intervals = relevantIntervals(hourly, nowMs);
  const valid = intervals.filter((x) => isValidMm(x.mm));
  const expected = nowMs % HOUR_MS === 0 ? 1 : 2;
  if (valid.length === 0) {
    return { decision: 'missing', maxMm: null, intervals, partial: false };
  }
  const maxMm = Math.max(...valid.map((x) => x.mm));
  return {
    decision: maxMm > THRESHOLD_MM ? 'warn' : 'no_warning',
    maxMm,
    intervals,
    partial: valid.length < expected, // some overlapping hour had no usable value
  };
}

// At least 60 minutes must pass: exactly 60 minutes later is allowed again.
function inCooldown(lastSentMs, nowMs) {
  return typeof lastSentMs === 'number' && nowMs - lastSentMs < COOLDOWN_MS;
}

// deps:
//   getForecast()            -> hourly object (may throw = source failure)
//   getLastSentMs(zoneId)    -> number (ms) or null (may throw)
//   sendToTopic(topic, zone) -> resolves on FCM success (may throw)
//   markSent(zoneId)         -> writes lastWarningSentAt (may throw)
//   nowMs, log
//   testTopic (optional)     -> send ONE message to this topic, no cooldown read/write
//   dryRun (optional)        -> decide and log only, no send, no write
async function runRainWarning(deps) {
  const { getForecast, getLastSentMs, sendToTopic, markSent, nowMs, log } = deps;
  const zones = deps.zones || ZONES;
  const result = { status: '', forecast: null, sent: [], skipped: [], failed: [] };

  let hourly;
  try {
    hourly = await getForecast();
  } catch (e) {
    log(`Forecast source failed: ${e.message}`);
    result.status = 'source_failure';
    return result;
  }

  const f = decideForecast(hourly, nowMs);
  result.forecast = f;
  for (const iv of f.intervals) {
    log(`Interval ${myt(iv.endMs - HOUR_MS)} to ${myt(iv.endMs)}: ${iv.mm} mm`);
  }
  if (f.partial) log('Note: an overlapping hour had no usable value; decided on the values available.');

  if (f.decision === 'missing') {
    log('No usable forecast value for the next 60 minutes. No warning sent.');
    result.status = 'missing';
    return result;
  }
  if (f.decision === 'no_warning') {
    log(`Max ${f.maxMm} mm is not above ${THRESHOLD_MM} mm. No warning.`);
    result.status = 'no_warning';
    return result;
  }

  log(`Max ${f.maxMm} mm is above ${THRESHOLD_MM} mm. Warning condition met.`);

  if (deps.testTopic) {
    if (deps.dryRun) {
      log(`DRY RUN: would send one test message to topic ${deps.testTopic}.`);
      result.status = 'dry_run';
      return result;
    }
    try {
      await sendToTopic(deps.testTopic, 'TEST');
      result.sent.push(deps.testTopic);
      log(`TEST MODE: sent to topic ${deps.testTopic}. Cooldowns not read or written.`);
      result.status = 'warned_test';
    } catch (e) {
      result.failed.push({ zone: 'TEST', reason: e.message });
      result.status = 'partial_failure';
    }
    return result;
  }

  for (const zone of zones) {
    const topic = topicForZone(zone);
    let last;
    try {
      last = await getLastSentMs(zone);
    } catch (e) {
      // Assumption to confirm with the team: if we cannot read the cooldown, do not send.
      log(`${zone}: could not read cooldown (${e.message}). Not sent.`);
      result.failed.push({ zone, reason: 'cooldown read failed' });
      continue;
    }
    if (inCooldown(last, nowMs)) {
      const mins = Math.floor((nowMs - last) / 60000);
      log(`${zone}: skipped, last warning ${mins} min ago (cooldown 60 min).`);
      result.skipped.push(zone);
      continue;
    }
    if (deps.dryRun) {
      log(`DRY RUN: would send to ${topic} and update ${zone} cooldown.`);
      result.sent.push(topic);
      continue;
    }
    try {
      await sendToTopic(topic, zone);
    } catch (e) {
      log(`${zone}: FCM send failed (${e.message}). Cooldown NOT updated.`);
      result.failed.push({ zone, reason: 'send failed' });
      continue;
    }
    try {
      await markSent(zone);
      result.sent.push(topic);
      log(`${zone}: sent to ${topic}, cooldown updated.`);
    } catch (e) {
      log(`${zone}: sent, but cooldown write failed (${e.message}).`);
      result.failed.push({ zone, reason: 'sent but cooldown not recorded' });
    }
  }

  if (deps.dryRun) result.status = 'dry_run';
  else if (result.failed.length) result.status = 'partial_failure';
  else if (result.sent.length) result.status = 'warned';
  else result.status = 'all_in_cooldown';
  return result;
}

module.exports = {
  THRESHOLD_MM, HOUR_MS, COOLDOWN_MS, ZONES, WARNING_TITLE, WARNING_BODY,
  topicForZone, myt, relevantIntervals, decideForecast, inCooldown, runRainWarning,
};
