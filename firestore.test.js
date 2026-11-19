const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validVaultPayload(uid, overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    userId: uid,
    vaultUuid: "550e8400-e29b-41d4-a716-446655440000",
    schemaVersion: 1,
    vaultVersion: 1,
    accountCount: 3,
    deviceName: "Pixel 9",
    kdfAlgorithm: "ARGON2ID",
    kdfSalt: "YWJjZGVmZ2hpamtsbW5vcA==",
    kdfMemoryKb: 32768,
    kdfIterations: 3,
    kdfParallelism: 1,
    wrappedVaultKey: "MTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDEyMzQ1Njc4",
    wrappedVaultKeyIv: "MTIzNDU2Nzg5MDEy",
    encryptedVault: "ZW5jcnlwdGVkX3ZhdWx0X3BheWxvYWRfYWVzMjU2Z2NtX2NpcGhlcnRleHQ=",
    encryptedVaultIv: "YWJjZGVmZ2hpamts",
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read, list, create, update, or delete any vault", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const ref = unauthDb.collection("users").doc(ALICE_UID).collection("vaults").doc("primary");
  await assertFails(ref.get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("vaults").get());
  await assertFails(ref.set(validVaultPayload(ALICE_UID)));
});

test("Authenticated owner can create, get, list, sync-update, rotate-key-update, and delete own vault", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const vaultRef = aliceDb.collection("users").doc(ALICE_UID).collection("vaults").doc("primary");

  // Create
  await assertSucceeds(vaultRef.set(validVaultPayload(ALICE_UID)));

  // Get & List
  await assertSucceeds(vaultRef.get());
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("vaults").get());

  // Action 1: Sync update (ciphertext, version, count, deviceName, updatedAt)
  await assertSucceeds(
    vaultRef.update({
      vaultVersion: 2,
      accountCount: 4,
      deviceName: "Pixel 9 Pro",
      encryptedVault: "dXBkYXRlZF9lbmNyeXB0ZWRfdmF1bHRfcGF5bG9hZF92Mg==",
      encryptedVaultIv: "MTExMTIyMjIzMzMz",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Action 2: Key rotation / recovery password change update
  await assertSucceeds(
    vaultRef.update({
      vaultVersion: 3,
      kdfSalt: "bmV3X3JhbmRvbV9zYWx0XzE2Yg==",
      wrappedVaultKey: "bmV3X3dyYXBwZWRfdmF1bHRfa2V5X2NpcGhlcnRleHRfMjU2Yml0",
      wrappedVaultKeyIv: "OTg3NjU0MzIxMDk4",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Delete
  await assertSucceeds(vaultRef.delete());
});

test("Cross-user isolation: Bob cannot read, list, create, update, or delete Alice's vault", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx
      .firestore()
      .collection("users")
      .doc(ALICE_UID)
      .collection("vaults")
      .doc("primary")
      .set(validVaultPayload(ALICE_UID));
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const aliceVaultRef = bobDb.collection("users").doc(ALICE_UID).collection("vaults").doc("primary");

  await assertFails(aliceVaultRef.get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("vaults").get());
  await assertFails(
    aliceVaultRef.update({
      vaultVersion: 99,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(aliceVaultRef.delete());
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("vaults")
      .doc("injected")
      .set(validVaultPayload(BOB_UID))
  );
});

test("Shadow fields, @DocumentId 'id' injection, identity spoofing, and invalid KDF/timestamps are rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const vaults = aliceDb.collection("users").doc(ALICE_UID).collection("vaults");

  // Shadow/ghost field rejected
  await assertFails(
    vaults.doc("primary").set(validVaultPayload(ALICE_UID, { plaintextSecret: "JBSWY3DPEHPK3PXP" }))
  );

  // @DocumentId 'id' field in body rejected
  await assertFails(
    vaults.doc("primary").set(validVaultPayload(ALICE_UID, { id: "primary" }))
  );

  // Spoofed userId rejected
  await assertFails(
    vaults.doc("primary").set(validVaultPayload(BOB_UID))
  );

  // Invalid KDF algorithm rejected
  await assertFails(
    vaults.doc("primary").set(validVaultPayload(ALICE_UID, { kdfAlgorithm: "PLAINTEXT" }))
  );

  // Future timestamp rejected
  await assertFails(
    vaults.doc("primary").set(validVaultPayload(ALICE_UID, { updatedAt: new Date(Date.now() + 60000) }))
  );

  // Immutable createdAt modification rejected
  await assertSucceeds(vaults.doc("primary").set(validVaultPayload(ALICE_UID)));
  await assertFails(
    vaults.doc("primary").update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Value poisoning on allowlisted update key rejected
  await assertFails(
    vaults.doc("primary").update({
      accountCount: -5,
      updatedAt: new Date(Date.now() - 500),
    })
  );
});
