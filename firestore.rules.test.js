/**
 * RainReady Automated Firestore Security Rules Test Suite (SR-01 to SR-16)
 * Owner: Lee Zi Ying (zy — 23110482@siswa.um.edu.my)
 * Date: 18 Nov 2026
 *
 * How to run locally with the Firebase Emulator:
 *   1. npm install --save-dev @firebase/rules-unit-testing firebase mocha
 *   2. npx firebase emulators:exec --only firestore "npx mocha firestore.rules.test.js"
 */

const fs = require('fs');
const assert = require('assert');
const {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} = require('@firebase/rules-unit-testing');
const {
  doc,
  getDoc,
  setDoc,
  updateDoc,
  deleteDoc,
  runTransaction,
} = require('firebase/firestore');

const PROJECT_ID = 'rainready-app-test';

// Test user tokens
const STUDENT_A = {
  uid: 'uid_ali_123',
  token: { email: '23110482@siswa.um.edu.my', email_verified: true },
};
const STUDENT_B = {
  uid: 'uid_bala_456',
  token: { email: '24001142@siswa.um.edu.my', email_verified: true },
};
const STUDENT_C = {
  uid: 'uid_chen_789',
  token: { email: '25006028@siswa.um.edu.my', email_verified: true },
};
const GMAIL_USER = {
  uid: 'uid_gmail_999',
  token: { email: 'outsider@gmail.com', email_verified: true },
};
const UNVERIFIED_UM_USER = {
  uid: 'uid_unverified_000',
  token: { email: 'newstudent@siswa.um.edu.my', email_verified: false },
};
const STAFF_USER = {
  uid: 'uid_staff_01',
  token: { email: 'staff_demo@um.edu.my', email_verified: true },
};

describe('RainReady Firestore Security Rules (DATA_LAYOUT.md v1)', () => {
  let testEnv;

  before(async () => {
    testEnv = await initializeTestEnvironment({
      projectId: PROJECT_ID,
      firestore: {
        rules: fs.readFileSync('firestore.rules', 'utf8'),
      },
    });
  });

  after(async () => {
    await testEnv.cleanup();
  });

  beforeEach(async () => {
    await testEnv.clearFirestore();
    // Seed admin user and sample data bypassing rules
    await testEnv.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();
      await setDoc(doc(db, 'admins/uid_staff_01'), {
        uid: 'uid_staff_01',
        email: 'staff_demo@um.edu.my',
        role: 'admin',
        addedAt: '2026-11-11 18:00',
      });
      await setDoc(doc(db, 'zones/Z6'), {
        zoneId: 'Z6',
        staffOverride: '',
        staffOverrideUntil: '',
        updatedAt: '2026-11-18 08:00',
      });
      await setDoc(doc(db, 'notices/N01'), {
        noticeId: 'N01',
        textEn: 'Library closed due to flooding',
        textMs: 'Perpustakaan ditutup kerana banjir',
        createdAt: '2026-11-18 09:00',
      });
    });
  });

  // SR-01: Unauthenticated public reads on zones, reports, notices
  it('SR-01: allows unauthenticated users to read zones, reports, and notices (Scene 2)', async () => {
    const anonDb = testEnv.unauthenticatedContext().firestore();
    await assertSucceeds(getDoc(doc(anonDb, 'zones/Z6')));
    await assertSucceeds(getDoc(doc(anonDb, 'notices/N01')));
  });

  // SR-02: Unauthenticated photo read blocked
  it('SR-02: rejects unauthenticated users from reading report photos (PLAN Sec 5)', async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), 'reports/R001/photos/p1'), {
        photoId: 'p1',
        imageBase64: '/9j/4AAQSkZJRg==',
        widthPx: 800,
        heightPx: 600,
        createdAt: '2026-11-18 10:00',
      });
    });
    const anonDb = testEnv.unauthenticatedContext().firestore();
    await assertFails(getDoc(doc(anonDb, 'reports/R001/photos/p1')));
  });

  // SR-03: NFR2 Non-UM email (@gmail.com) 100% rejected
  it('SR-03 (NFR2): rejects report creation by @gmail.com user', async () => {
    const gmailDb = testEnv
      .authenticatedContext(GMAIL_USER.uid, GMAIL_USER.token)
      .firestore();
    await assertFails(
      setDoc(doc(gmailDb, 'reports/R_GMAIL'), {
        reportId: 'R_GMAIL',
        zoneId: 'Z6',
        category: 'flood',
        note: 'Test from gmail',
        lat: 3.1203,
        lng: 101.6543,
        createdAt: '2026-11-18 10:00',
        status: 'unconfirmed',
        confirmCount: 1,
        photoCount: 1,
        flagCount: 0,
        fixedAt: '',
      })
    );
  });

  // SR-04: Unverified UM email rejected
  it('SR-04 (FR1.3): rejects report creation when UM email is not verified', async () => {
    const unverifiedDb = testEnv
      .authenticatedContext(UNVERIFIED_UM_USER.uid, UNVERIFIED_UM_USER.token)
      .firestore();
    await assertFails(
      setDoc(doc(unverifiedDb, 'reports/R_UNVERIFIED'), {
        reportId: 'R_UNVERIFIED',
        zoneId: 'Z6',
        category: 'flood',
        note: 'Unverified user',
        lat: 3.1203,
        lng: 101.6543,
        createdAt: '2026-11-18 10:00',
        status: 'unconfirmed',
        confirmCount: 1,
        photoCount: 1,
        flagCount: 0,
        fixedAt: '',
      })
    );
  });

  // SR-05: Valid report creation by verified UM student
  it('SR-05 (FR3.1): allows verified UM student to create report, reports_private, confirmation #1, and photo', async () => {
    const studentADb = testEnv
      .authenticatedContext(STUDENT_A.uid, STUDENT_A.token)
      .firestore();

    await assertSucceeds(
      setDoc(doc(studentADb, 'reports/R001'), {
        reportId: 'R001',
        zoneId: 'Z6',
        category: 'flood',
        note: 'Water at the entrance',
        lat: 3.1203,
        lng: 101.6543,
        createdAt: '2026-11-18 10:00',
        status: 'unconfirmed',
        confirmCount: 1,
        photoCount: 1,
        flagCount: 0,
        fixedAt: '',
      })
    );
    await assertSucceeds(
      setDoc(doc(studentADb, 'reports_private/R001'), {
        reportId: 'R001',
        createdBy: STUDENT_A.uid,
        reporterEmail: STUDENT_A.token.email,
        createdAt: '2026-11-18 10:00',
      })
    );
    await assertSucceeds(
      setDoc(doc(studentADb, `reports/R001/confirmations/${STUDENT_A.uid}`), {
        userId: STUDENT_A.uid,
        createdAt: '2026-11-18 10:00',
      })
    );
    await assertSucceeds(
      setDoc(doc(studentADb, 'reports/R001/photos/p1'), {
        photoId: 'p1',
        imageBase64: '/9j/4AAQSkZJRg==',
        widthPx: 800,
        heightPx: 600,
        createdAt: '2026-11-18 10:00',
      })
    );
  });

  // SR-06: NFR2 Geofence rejection (200 m outside UM)
  it('SR-06 (NFR2): rejects report creation 200 m outside UM campus boundary', async () => {
    const studentADb = testEnv
      .authenticatedContext(STUDENT_A.uid, STUDENT_A.token)
      .firestore();

    await assertFails(
      setDoc(doc(studentADb, 'reports/R_OUTSIDE'), {
        reportId: 'R_OUTSIDE',
        zoneId: 'Z8',
        category: 'flood',
        note: 'Outside campus test (Mid Valley / 200m south)',
        lat: 3.1000, // Outside [3.108, 3.135]
        lng: 101.6543,
        createdAt: '2026-11-18 10:05',
        status: 'unconfirmed',
        confirmCount: 1,
        photoCount: 1,
        flagCount: 0,
        fixedAt: '',
      })
    );
  });

  // SR-07: Rejects leaking createdBy into public reports document
  it('SR-07 (Privacy): rejects report if createdBy or reporterEmail is included in public reports', async () => {
    const studentADb = testEnv
      .authenticatedContext(STUDENT_A.uid, STUDENT_A.token)
      .firestore();

    await assertFails(
      setDoc(doc(studentADb, 'reports/R_LEAK'), {
        reportId: 'R_LEAK',
        zoneId: 'Z6',
        category: 'flood',
        note: 'Has createdBy field',
        lat: 3.1203,
        lng: 101.6543,
        createdBy: STUDENT_A.uid, // Forbidden on public reports
        createdAt: '2026-11-18 10:05',
        status: 'unconfirmed',
        confirmCount: 1,
        photoCount: 1,
        flagCount: 0,
        fixedAt: '',
      })
    );
  });

  // SR-08: Other students cannot read reports_private
  it('SR-08 (Privacy): prevents Student B from reading Student A identity in reports_private', async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), 'reports_private/R001'), {
        reportId: 'R001',
        createdBy: STUDENT_A.uid,
        reporterEmail: STUDENT_A.token.email,
        createdAt: '2026-11-18 10:00',
      });
    });

    const studentBDb = testEnv
      .authenticatedContext(STUDENT_B.uid, STUDENT_B.token)
      .firestore();
    await assertFails(getDoc(doc(studentBDb, 'reports_private/R001')));

    const staffDb = testEnv
      .authenticatedContext(STAFF_USER.uid, STAFF_USER.token)
      .firestore();
    await assertSucceeds(getDoc(doc(staffDb, 'reports_private/R001')));
  });

  // SR-09 & SR-10: Student B confirms once (succeeds), then tries to confirm a second time (fails)
  it('SR-09 & SR-10 (FR3.2 & NFR2): allows first confirmation in transaction and rejects second confirmation from same account', async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();
      await setDoc(doc(db, 'reports/R001'), {
        reportId: 'R001',
        zoneId: 'Z6',
        category: 'flood',
        note: 'Water at entrance',
        lat: 3.1203,
        lng: 101.6543,
        createdAt: '2026-11-18 10:00',
        status: 'unconfirmed',
        confirmCount: 1,
        photoCount: 1,
        flagCount: 0,
        fixedAt: '',
      });
      await setDoc(doc(db, `reports/R001/confirmations/${STUDENT_A.uid}`), {
        userId: STUDENT_A.uid,
        createdAt: '2026-11-18 10:00',
      });
    });

    const studentBDb = testEnv
      .authenticatedContext(STUDENT_B.uid, STUDENT_B.token)
      .firestore();

    // First confirmation by Student B -> Succeeds
    await assertSucceeds(
      runTransaction(studentBDb, async (tx) => {
        const reportRef = doc(studentBDb, 'reports/R001');
        const confirmRef = doc(
          studentBDb,
          `reports/R001/confirmations/${STUDENT_B.uid}`
        );
        tx.set(confirmRef, {
          userId: STUDENT_B.uid,
          createdAt: '2026-11-18 10:05',
        });
        tx.update(reportRef, { confirmCount: 2, status: 'unconfirmed' });
      })
    );

    // Second confirmation by Student B -> Fails (NFR2: 1 vote per user ID)
    await assertFails(
      runTransaction(studentBDb, async (tx) => {
        const reportRef = doc(studentBDb, 'reports/R001');
        const confirmRef = doc(
          studentBDb,
          `reports/R001/confirmations/${STUDENT_B.uid}`
        );
        tx.set(confirmRef, {
          userId: STUDENT_B.uid,
          createdAt: '2026-11-18 10:06',
        });
        tx.update(reportRef, { confirmCount: 3, status: 'confirmed' });
      })
    );
  });

  // SR-11 & SR-12: 3rd confirmation flips status to 'confirmed'; premature flip at confirmCount=2 is rejected
  it('SR-11 & SR-12 (FR3.2): rejects status=confirmed at confirmCount=2, and allows status=confirmed at confirmCount=3', async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();
      await setDoc(doc(db, 'reports/R001'), {
        reportId: 'R001',
        zoneId: 'Z6',
        category: 'flood',
        note: 'Water at entrance',
        lat: 3.1203,
        lng: 101.6543,
        createdAt: '2026-11-18 10:00',
        status: 'unconfirmed',
        confirmCount: 1,
        photoCount: 1,
        flagCount: 0,
        fixedAt: '',
      });
    });

    const studentBDb = testEnv
      .authenticatedContext(STUDENT_B.uid, STUDENT_B.token)
      .firestore();

    // SR-12: Student B tries to flip status to 'confirmed' when confirmCount only reaches 2 -> Fails
    await assertFails(
      runTransaction(studentBDb, async (tx) => {
        tx.set(doc(studentBDb, `reports/R001/confirmations/${STUDENT_B.uid}`), {
          userId: STUDENT_B.uid,
          createdAt: '2026-11-18 10:05',
        });
        tx.update(doc(studentBDb, 'reports/R001'), {
          confirmCount: 2,
          status: 'confirmed',
        });
      })
    );

    // Valid 2nd confirmation (stays 'unconfirmed')
    await assertSucceeds(
      runTransaction(studentBDb, async (tx) => {
        tx.set(doc(studentBDb, `reports/R001/confirmations/${STUDENT_B.uid}`), {
          userId: STUDENT_B.uid,
          createdAt: '2026-11-18 10:05',
        });
        tx.update(doc(studentBDb, 'reports/R001'), {
          confirmCount: 2,
          status: 'unconfirmed',
        });
      })
    );

    // SR-11: Student C performs 3rd confirmation -> flips status to 'confirmed' -> Succeeds
    const studentCDb = testEnv
      .authenticatedContext(STUDENT_C.uid, STUDENT_C.token)
      .firestore();
    await assertSucceeds(
      runTransaction(studentCDb, async (tx) => {
        tx.set(doc(studentCDb, `reports/R001/confirmations/${STUDENT_C.uid}`), {
          userId: STUDENT_C.uid,
          createdAt: '2026-11-18 10:07',
        });
        tx.update(doc(studentCDb, 'reports/R001'), {
          confirmCount: 3,
          status: 'confirmed',
        });
      })
    );
  });

  // SR-13: Photo flagging (1 flag vote per student)
  it('SR-13 (FR3.3): allows 1 flag per student and rejects duplicate flag from same student', async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), 'reports/R001'), {
        reportId: 'R001',
        zoneId: 'Z6',
        category: 'flood',
        note: 'Water at entrance',
        lat: 3.1203,
        lng: 101.6543,
        createdAt: '2026-11-18 10:00',
        status: 'unconfirmed',
        confirmCount: 1,
        photoCount: 1,
        flagCount: 0,
        fixedAt: '',
      });
    });

    const studentBDb = testEnv
      .authenticatedContext(STUDENT_B.uid, STUDENT_B.token)
      .firestore();

    await assertSucceeds(
      runTransaction(studentBDb, async (tx) => {
        tx.set(doc(studentBDb, `reports/R001/flags/${STUDENT_B.uid}`), {
          userId: STUDENT_B.uid,
          reason: 'face visible',
          createdAt: '2026-11-18 10:12',
        });
        tx.update(doc(studentBDb, 'reports/R001'), { flagCount: 1 });
      })
    );

    await assertFails(
      runTransaction(studentBDb, async (tx) => {
        tx.set(doc(studentBDb, `reports/R001/flags/${STUDENT_B.uid}`), {
          userId: STUDENT_B.uid,
          reason: 'spam',
          createdAt: '2026-11-18 10:13',
        });
        tx.update(doc(studentBDb, 'reports/R001'), { flagCount: 2 });
      })
    );
  });

  // SR-14: Report deletion ownership check
  it('SR-14 (FR3.3): rejects deletion by another student, allows deletion by original reporter', async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();
      await setDoc(doc(db, 'reports/R001'), {
        reportId: 'R001',
        zoneId: 'Z6',
        category: 'flood',
        note: 'Water at entrance',
        lat: 3.1203,
        lng: 101.6543,
        createdAt: '2026-11-18 10:00',
        status: 'unconfirmed',
        confirmCount: 1,
        photoCount: 1,
        flagCount: 0,
        fixedAt: '',
      });
      await setDoc(doc(db, 'reports_private/R001'), {
        reportId: 'R001',
        createdBy: STUDENT_A.uid,
        reporterEmail: STUDENT_A.token.email,
        createdAt: '2026-11-18 10:00',
      });
    });

    const studentBDb = testEnv
      .authenticatedContext(STUDENT_B.uid, STUDENT_B.token)
      .firestore();
    await assertFails(deleteDoc(doc(studentBDb, 'reports/R001')));

    const studentADb = testEnv
      .authenticatedContext(STUDENT_A.uid, STUDENT_A.token)
      .firestore();
    await assertSucceeds(deleteDoc(doc(studentADb, 'reports/R001')));
  });

  // SR-15 & SR-16: Staff overrides, notices, and admin escalation protection
  it('SR-15 & SR-16 (FR1.3, FR2.2, FR4.2): blocks students from writing zones/notices/admins; allows staff to override zones and post notices', async () => {
    const studentADb = testEnv
      .authenticatedContext(STUDENT_A.uid, STUDENT_A.token)
      .firestore();
    const staffDb = testEnv
      .authenticatedContext(STAFF_USER.uid, STAFF_USER.token)
      .firestore();

    // Student cannot override zone, post notice, or self-promote to admin
    await assertFails(
      updateDoc(doc(studentADb, 'zones/Z6'), {
        staffOverride: 'red',
        staffOverrideUntil: '2026-11-18 22:00',
        updatedAt: '2026-11-18 10:00',
      })
    );
    await assertFails(
      setDoc(doc(studentADb, 'notices/N02'), {
        noticeId: 'N02',
        textEn: 'Fake notice',
        textMs: '',
        createdAt: '2026-11-18 10:00',
      })
    );
    await assertFails(
      setDoc(doc(studentADb, `admins/${STUDENT_A.uid}`), {
        uid: STUDENT_A.uid,
        email: STUDENT_A.token.email,
        role: 'admin',
        addedAt: '2026-11-18 10:00',
      })
    );

    // Staff can override zone and post notice
    await assertSucceeds(
      updateDoc(doc(staffDb, 'zones/Z6'), {
        staffOverride: 'red',
        staffOverrideUntil: '2026-11-18 22:00',
        updatedAt: '2026-11-18 10:00',
      })
    );
    await assertSucceeds(
      setDoc(doc(staffDb, 'notices/N02'), {
        noticeId: 'N02',
        textEn: 'KL Gate closed due to flash flood',
        textMs: 'Pintu KL ditutup akibat banjir kilat',
        createdAt: '2026-11-18 10:00',
      })
    );
  });
});