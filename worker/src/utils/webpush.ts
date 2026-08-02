// ============================================================
// Web Push Notification — Cloudflare Workers Compatible
// Implements RFC 8291 (Message Encryption) + RFC 8292 (VAPID)
// Uses only Web Crypto API + fetch() (no Node.js dependencies)
// ============================================================

// --- Base64url helpers ---

function base64urlEncode(buffer: ArrayBuffer | Uint8Array): string {
  const bytes = buffer instanceof Uint8Array ? buffer : new Uint8Array(buffer);
  let binary = '';
  for (let i = 0; i < bytes.length; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function base64urlDecode(str: string): Uint8Array {
  str = str.replace(/-/g, '+').replace(/_/g, '/');
  while (str.length % 4) str += '=';
  const binary = atob(str);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes;
}

// --- HKDF (RFC 5869) using Web Crypto HMAC-SHA-256 ---

async function hkdfExtract(salt: Uint8Array, ikm: Uint8Array): Promise<ArrayBuffer> {
  const key = await crypto.subtle.importKey(
    'raw', salt, { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']
  );
  return crypto.subtle.sign('HMAC', key, ikm);
}

async function hkdfExpand(prk: ArrayBuffer, info: Uint8Array, length: number): Promise<Uint8Array> {
  const key = await crypto.subtle.importKey(
    'raw', prk, { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']
  );
  const infoWithCounter = new Uint8Array(info.length + 1);
  infoWithCounter.set(info);
  infoWithCounter[info.length] = 1;
  const result = await crypto.subtle.sign('HMAC', key, infoWithCounter);
  return new Uint8Array(result).slice(0, length);
}

// --- VAPID JWT (RFC 8292) ---

async function createVapidJwt(
  audience: string,
  subject: string,
  vapidPrivateKeyBase64url: string,
  vapidPublicKeyBase64url: string,
): Promise<string> {
  const header = base64urlEncode(new TextEncoder().encode(JSON.stringify({ typ: 'JWT', alg: 'ES256' })));
  const payload = base64urlEncode(new TextEncoder().encode(JSON.stringify({
    aud: audience,
    exp: Math.floor(Date.now() / 1000) + 12 * 3600,
    sub: subject,
  })));
  const unsignedToken = `${header}.${payload}`;

  // Build JWK from raw VAPID keys
  const pubBytes = base64urlDecode(vapidPublicKeyBase64url);
  const jwk: JsonWebKey = {
    kty: 'EC',
    crv: 'P-256',
    x: base64urlEncode(pubBytes.slice(1, 33)),
    y: base64urlEncode(pubBytes.slice(33, 65)),
    d: vapidPrivateKeyBase64url,
  };

  const privateKey = await crypto.subtle.importKey(
    'jwk', jwk, { name: 'ECDSA', namedCurve: 'P-256' }, false, ['sign']
  );

  const signature = await crypto.subtle.sign(
    { name: 'ECDSA', hash: 'SHA-256' }, privateKey,
    new TextEncoder().encode(unsignedToken)
  );

  return `${unsignedToken}.${base64urlEncode(signature)}`;
}

// --- Payload Encryption (RFC 8291 — aes128gcm content encoding) ---

async function encryptPayload(
  clientPublicKeyBase64url: string,
  authSecretBase64url: string,
  payloadText: string,
): Promise<Uint8Array> {
  const clientPublicKeyBytes = base64urlDecode(clientPublicKeyBase64url);
  const authSecret = base64urlDecode(authSecretBase64url);
  const plaintextBytes = new TextEncoder().encode(payloadText);

  // 1. Generate ephemeral ECDH key pair
  const serverKeyPair = await crypto.subtle.generateKey(
    { name: 'ECDH', namedCurve: 'P-256' }, true, ['deriveBits']
  );
  const serverPublicKeyRaw = new Uint8Array(
    await crypto.subtle.exportKey('raw', serverKeyPair.publicKey)
  );

  // 2. Import the client's public key
  const clientPublicKey = await crypto.subtle.importKey(
    'raw', clientPublicKeyBytes, { name: 'ECDH', namedCurve: 'P-256' }, false, []
  );

  // 3. ECDH shared secret
  const sharedSecret = new Uint8Array(
    await crypto.subtle.deriveBits(
      { name: 'ECDH', public: clientPublicKey }, serverKeyPair.privateKey, 256
    )
  );

  // 4. Random salt
  const salt = crypto.getRandomValues(new Uint8Array(16));

  // 5. Derive IKM via auth secret
  //    info = "WebPush: info\0" + ua_public(65) + as_public(65)
  const infoPrefix = new TextEncoder().encode('WebPush: info\0');
  const ikmInfo = new Uint8Array(infoPrefix.length + 65 + 65);
  ikmInfo.set(infoPrefix);
  ikmInfo.set(clientPublicKeyBytes, infoPrefix.length);
  ikmInfo.set(serverPublicKeyRaw, infoPrefix.length + 65);

  const ikmPrk = await hkdfExtract(authSecret, sharedSecret);
  const ikm = await hkdfExpand(ikmPrk, ikmInfo, 32);

  // 6. Derive CEK and nonce from salt + IKM
  const contentPrk = await hkdfExtract(salt, ikm);
  const cek = await hkdfExpand(contentPrk, new TextEncoder().encode('Content-Encoding: aes128gcm\0'), 16);
  const nonce = await hkdfExpand(contentPrk, new TextEncoder().encode('Content-Encoding: nonce\0'), 12);

  // 7. Pad plaintext and encrypt (AES-128-GCM)
  //    Delimiter byte 0x02 marks the end of the content in the last record
  const padded = new Uint8Array(plaintextBytes.length + 1);
  padded.set(plaintextBytes);
  padded[plaintextBytes.length] = 2;

  const aesKey = await crypto.subtle.importKey('raw', cek, { name: 'AES-GCM' }, false, ['encrypt']);
  const ciphertext = new Uint8Array(
    await crypto.subtle.encrypt({ name: 'AES-GCM', iv: nonce }, aesKey, padded)
  );

  // 8. Build the aes128gcm record: salt(16) + rs(4) + idlen(1) + keyid(65) + ciphertext
  const rs = 4096;
  const headerLen = 16 + 4 + 1 + 65;
  const record = new Uint8Array(headerLen + ciphertext.length);
  record.set(salt, 0);
  record[16] = (rs >> 24) & 0xff;
  record[17] = (rs >> 16) & 0xff;
  record[18] = (rs >> 8) & 0xff;
  record[19] = rs & 0xff;
  record[20] = 65;
  record.set(serverPublicKeyRaw, 21);
  record.set(ciphertext, headerLen);

  return record;
}

// --- Public API ---

export interface PushSubscription {
  endpoint: string;
  keys: { p256dh: string; auth: string };
}

export async function sendPushNotification(
  subscription: PushSubscription,
  payload: string,
  vapidSubject: string,
  vapidPublicKey: string,
  vapidPrivateKey: string,
): Promise<Response> {
  const body = await encryptPayload(subscription.keys.p256dh, subscription.keys.auth, payload);

  const endpointUrl = new URL(subscription.endpoint);
  const audience = `${endpointUrl.protocol}//${endpointUrl.host}`;
  const jwt = await createVapidJwt(audience, vapidSubject, vapidPrivateKey, vapidPublicKey);

  return fetch(subscription.endpoint, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/octet-stream',
      'Content-Encoding': 'aes128gcm',
      'Content-Length': String(body.length),
      TTL: '86400',
      Authorization: `vapid t=${jwt}, k=${vapidPublicKey}`,
    },
    body: body,
  });
}
