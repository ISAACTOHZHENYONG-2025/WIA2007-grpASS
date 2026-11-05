# Firebase

Firestore security rules, indexes and emulator settings.

- Owners: Jingying and zy
- No keys or service account files here. Jingying decides the final layout (for example a `firebase.json` at the repo root) when she sets up Firebase.
- Run `firebase init` **once** (Jingying), from the repo root. It creates `firebase.json`, `.firebaserc`, `firestore.rules`, `firestore.indexes.json` and `storage.rules`. Everyone else pulls those files instead of running `firebase init` again, so we don't end up with two copies.
- We are on the free plan, so do not pick Cloud Functions in `firebase init`.
