// Sends ONE test notification to FCM topic "test" using the HTTP v1 API.
// Needs env var FIREBASE_SERVICE_ACCOUNT = the whole service-account JSON (GitHub secret).
// Node 18+ (has built-in fetch). Never print the secret.

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

  // 1) Get a short-lived access token from the service account
  const auth = new GoogleAuth({
    credentials: creds,
    scopes: ['https://www.googleapis.com/auth/firebase.messaging'],
  });
  const client = await auth.getClient();
  const { token } = await client.getAccessToken();
  if (!token) throw new Error('Could not get an access token.');

  // 2) Send one message to topic "test"
  const projectId = creds.project_id;
  const url = `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`;
  const res = await fetch(url, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      message: {
        topic: 'test',
        notification: {
          title: 'RainReady test',
          body: 'If you see this, the robot works',
        },
      },
    }),
  });

  const text = await res.text();
  console.log(`Project: ${projectId}`);
  console.log(`HTTP status: ${res.status}`);
  console.log(`FCM response: ${text}`);

  // 3) Fail loudly
  if (!res.ok) {
    throw new Error(`FCM send failed with status ${res.status}`);
  }
  console.log('SUCCESS: message accepted by FCM. Now check the phone.');
}

main().catch((err) => {
  console.error('FAILED:', err.message);
  process.exit(1);
});
