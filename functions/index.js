const crypto = require("node:crypto");
const admin = require("firebase-admin");
const { defineSecret, defineString } = require("firebase-functions/params");
const { HttpsError, onCall } = require("firebase-functions/v2/https");
const { AccessToken } = require("livekit-server-sdk");

admin.initializeApp();
const db = admin.firestore();

const LIVEKIT_URL = defineString("LIVEKIT_URL");
const LIVEKIT_API_KEY = defineSecret("LIVEKIT_API_KEY");
const LIVEKIT_API_SECRET = defineSecret("LIVEKIT_API_SECRET");

const ALLOWED_ROOMS = new Set(["roblox", "music", "late-night", "meet-people"]);
const RECOVERY_WINDOW_MS = 15 * 60 * 1000;
const RECOVERY_MAX_ATTEMPTS = 5;

function cleanNick(value) {
  return typeof value === "string" ? value.trim().replace(/^@/, "").toLowerCase() : "";
}

function hashRecoveryCode(value) {
  const normalized = typeof value === "string" ? value.replace(/-/g, "").trim().toUpperCase() : "";
  return crypto.createHash("sha256").update(normalized).digest("hex");
}

function makeRecoveryCode() {
  const alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  const bytes = crypto.randomBytes(16);
  const chars = Array.from(bytes, (b) => alphabet[b % alphabet.length]);
  const raw = chars.join("");
  return chars.join("").match(/.{1,4}/g).join("-");
}

async function rateLimitRecovery(ip) {
  const ipHash = crypto.createHash("sha256").update(String(ip || "unknown")).digest("hex").slice(0, 40);
  const ref = db.collection("_recoveryRateLimits").doc(ipHash);
  const now = Date.now();

  await db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(ref);
    const previous = snapshot.exists ? snapshot.data() : {};
    const start = Number(previous.windowStartMs || 0);
    const attempts = Number(previous.attempts || 0);
    if (!snapshot.exists || now - start >= RECOVERY_WINDOW_MS) {
      transaction.set(ref, { windowStartMs: now, attempts: 1 });
      return;
    }
    if (attempts >= RECOVERY_MAX_ATTEMPTS) {
      throw new HttpsError("resource-exhausted", "Muitas tentativas. Tente novamente em 15 minutos.");
    }
    transaction.update(ref, { attempts: attempts + 1 });
  });
}

exports.createVoiceToken = onCall(
  { secrets: [LIVEKIT_API_KEY, LIVEKIT_API_SECRET] },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Entre na sua conta para usar as salas.");
    }

    const roomName = request.data && request.data.roomName;
    if (typeof roomName !== "string" || !ALLOWED_ROOMS.has(roomName)) {
      throw new HttpsError("invalid-argument", "Sala inválida.");
    }

    const profile = await db.collection("profiles").doc(request.auth.uid).get();
    const username = profile.get("username") || "hangout-user";

    const token = new AccessToken(LIVEKIT_API_KEY.value(), LIVEKIT_API_SECRET.value(), {
      identity: request.auth.uid,
      name: username,
      ttl: "1h"
    });
    token.addGrant({
      roomJoin: true,
      room: roomName,
      canPublish: true,
      canSubscribe: true
    });

    return {
      url: LIVEKIT_URL.value(),
      token: await token.toJwt()
    };
  }
);

exports.createRecoveryCode = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Entre na sua conta para gerar um código.");
  }

  const profileRef = db.collection("profiles").doc(request.auth.uid);
  const profile = await profileRef.get();
  if (!profile.exists) {
    throw new HttpsError("failed-precondition", "O perfil ainda não está pronto.");
  }

  const username = profile.get("username");
  if (typeof username !== "string" || !/^[a-z0-9_.]{3,20}$/.test(username)) {
    throw new HttpsError("failed-precondition", "O perfil não tem um nick válido.");
  }

  const recoveryCode = makeRecoveryCode();
  await db.collection("privateRecovery").doc(request.auth.uid).set({
    uid: request.auth.uid,
    recoveryHash: hashRecoveryCode(recoveryCode),
    updatedAt: admin.firestore.FieldValue.serverTimestamp()
  });

  // Remove a hash legada do perfil público, caso alguma versão anterior a tenha guardado.
  if (profile.get("recoveryHash")) {
    await profileRef.update({ recoveryHash: admin.firestore.FieldValue.delete() });
  }

  return { recoveryCode };
});

exports.recoverAccount = onCall(async (request) => {
  const raw = request.data || {};
  const username = cleanNick(raw.username);
  const recoveryCode = raw.recoveryCode;
  const newPassword = raw.newPassword;

  if (!/^[a-z0-9_.]{3,20}$/.test(username)
      || typeof recoveryCode !== "string"
      || typeof newPassword !== "string"
      || newPassword.length < 8
      || newPassword.length > 128) {
    throw new HttpsError("invalid-argument", "Confira os campos. A nova senha precisa ter pelo menos 8 caracteres.");
  }

  await rateLimitRecovery(request.rawRequest && request.rawRequest.ip);

  const match = await db.collection("profiles")
    .where("username", "==", username)
    .limit(1)
    .get();

  if (match.empty) {
    throw new HttpsError("permission-denied", "Nick ou código de recuperação inválido.");
  }

  const profileDoc = match.docs[0];
  const uid = profileDoc.get("uid");
  if (typeof uid !== "string" || !uid) {
    throw new HttpsError("failed-precondition", "A conta não está pronta para recuperação.");
  }

  const recoveryDoc = await db.collection("privateRecovery").doc(uid).get();
  const savedHash = recoveryDoc.get("recoveryHash");
  const proposedHash = hashRecoveryCode(recoveryCode);
  const savedBytes = typeof savedHash === "string" ? Buffer.from(savedHash, "hex") : Buffer.alloc(0);
  const proposedBytes = Buffer.from(proposedHash, "hex");

  if (savedBytes.length !== proposedBytes.length
      || !crypto.timingSafeEqual(savedBytes, proposedBytes)) {
    throw new HttpsError("permission-denied", "Nick ou código de recuperação inválido.");
  }

  await admin.auth().updateUser(uid, { password: newPassword });
  const nextRecoveryCode = makeRecoveryCode();
  await db.collection("privateRecovery").doc(uid).set({
    uid,
    recoveryHash: hashRecoveryCode(nextRecoveryCode),
    updatedAt: admin.firestore.FieldValue.serverTimestamp()
  });

  return { success: true, newRecoveryCode: nextRecoveryCode };
});
