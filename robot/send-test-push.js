// Sends ONE notification through FCM (HTTP v1 API).
// Env vars:
//   FIREBASE_SERVICE_ACCOUNT  whole service-account JSON (GitHub secret). Never printed.
//   TOPIC         topic name (default "test"), used when DEVICE_TOKEN is empty
//   TITLE, BODY   notification text (have defaults)
//   DEVICE_TOKEN  optional. Only for the FAILURE test, use a fake value.
// Node 18+ (built-in fetch).

const { GoogleAuth } = require('google-auth-library');

async function main() {
  const raw = process.env.FIREBASE_SERVICE_ACCOUNT;
  if (!raw) {
    throw new Error('Secret FIREBASE_SERVICE_ACCOUNT is missing or empty (check the secret name).');
  }

  let creds;
  try {
    creds = JSON.parse(raw);
  } catch (e) {
    throw new Error('FIREBASE_SERVICE_ACCOUNT is not valid JSON. Paste the WHOLE key file contents.');
  }

  const topic = (process.env.TOPIC || 'test').trim();
  const title = process.env.TITLE || 'RainReady test';
  const body = process.env.BODY || 'If you see this, the robot works';
  const deviceToken = (process.env.DEVICE_TOKEN || '').trim();

  if (!deviceToken && !/^[a-zA-Z0-9-_.~%]+$/.test(topic)) {
    throw new Error('TOPIC has invalid characters. Use letters, numbers, - _ . ~ % only.');
  }

  // 1) Short-lived access token from the service account
  const auth = new GoogleAuth({
    credentials: creds,
    scopes: ['https://www.googleapis.com/auth/firebase.messaging'],
  });
  const client = await auth.getClient();
  const { token } = await client.getAccessToken();
  if (!token) throw new Error('Could not get an access token.');

  // 2) Build message: to one device token (failure test) or to a topic
  const message = { notification: { title, body } };
  if (deviceToken) {
    message.token = deviceToken;
  } else {
    message.topic = topic;
  }

  const projectId = creds.project_id;
  const url = `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`;
  const sentAt = new Date().toISOString();
  console.log(`Project: ${projectId}`);
  console.log(`Target: ${deviceToken ? 'device token (test value)' : 'topic ' + topic}`);
  console.log(`Sent at (UTC): ${sentAt}`);

  const res = await fetch(url, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ message }),
  });

  const text = await res.text();
  console.log(`HTTP status: ${res.status}`);

  // 3) Fail loudly, print only the safe error fields
  if (!res.ok) {
    let detail = '';
    try {
      const e = JSON.parse(text).error || {};
      detail = `${e.status || ''} ${e.message || ''}`.trim();
    } catch (_) {}
    console.error(`FCM error: ${detail || '(no detail)'}`);
    throw new Error(`FCM send failed with status ${res.status}`);
  }

  console.log(`FCM response: ${text}`);
  console.log('SUCCESS: message accepted by FCM. Now check the phone and note the time.');
}

main().catch((err) => {
  console.error('FAILED:', err.message);
  process.exit(1);
});
