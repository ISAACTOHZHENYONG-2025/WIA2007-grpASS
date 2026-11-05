# Robot scripts

The scripts the scheduled GitHub job runs (Node.js or Python): read Open-Meteo, read Firestore, send push alerts, delete photos after 14 days.

- Owner: Ker Li
- The schedule itself is in `.github/workflows/`.
- Never commit service account keys. Use GitHub Secrets.
