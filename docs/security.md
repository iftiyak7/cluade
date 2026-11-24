# Hayat — Security & Privacy

## Threat model (summary)

| Threat | Mitigation |
|---|---|
| Data leaving the device | No `INTERNET`/network permissions (removed with `tools:node="remove"` and asserted by `ManifestPolicyTest`); no analytics/ads SDKs; no cloud sync. |
| Cloud backup / device transfer extraction | `allowBackup="false"`, `fullBackupContent="false"`, `dataExtractionRules` excluding every domain for both cloud backup and device transfer. |
| Someone using an unlocked phone | Optional app lock: 4–8 digit PIN, optional biometric (BIOMETRIC_STRONG), lock after configurable background timeout. Content behind the lock overlay is neither drawn nor exposed to accessibility services. |
| Brute-forcing the PIN | PBKDF2-HMAC-SHA256 (120k iterations, 16-byte salt); constant-time compare; 5 free attempts then exponential lockout (30 s → 1 h), persisted across restarts. |
| Offline cracking of a copied PIN record | The PBKDF2 hash is wrapped with an AES-256-GCM key generated inside AndroidKeyStore (non-exportable). A copied file alone cannot be brute-forced off-device. Stored in `noBackupFilesDir`. |
| Lost Keystore key | Verification fails closed (never unlocks). The lock screen explains that the only recovery is clearing the app's data. No false promise of recovery. |
| Shoulder-surfing / Recents | Optional `FLAG_SECURE` ("Hide content in recent apps", also blocks screenshots). Notifications use `VISIBILITY_PRIVATE` with a content-free public version on the lock screen. Locked notes never put their body into notification text. |
| Sensitive notes and journal entries | Per-item lock requiring biometric or device credential (`BIOMETRIC_WEAK | DEVICE_CREDENTIAL`). Search never returns or snippets locked bodies. |
| Malicious or corrupt backup files | Size caps (64 MB file, 256 MB decompressed — zip-bomb safe); magic/format/schema version checks; SHA-256 integrity (plain) or GCM tag (encrypted) with the header as AAD; table names from a fixed allow-list; column names checked against the live schema (no SQL built from input); values bound through `ContentValues`; `PRAGMA foreign_key_check`; single transaction — any failure rolls back and leaves existing data untouched (tests cover tampering, truncation, wrong passphrase, newer schema, injected column names, orphan rows). |
| Path traversal | Backups use the Storage Access Framework streams only; Hayat never constructs file paths from user or file input. There are no file attachments in v1. |
| Exported components | Only the launcher activity and `SystemEventReceiver` (accepts a fixed list of system actions and only enqueues work). Alarm receivers, `AlarmActivity` are not exported; every PendingIntent is `FLAG_IMMUTABLE` with a unique identity. |
| Logging | Diagnostic logs store IDs and event types only (`alarm_delivery_logs`), pruned after 30 days; release builds strip `Log.v/d/i` via R8. |

## Backup encryption

`AES-256-GCM` with a key from `PBKDF2-HMAC-SHA256` (210,000 iterations, random 16-byte salt,
random 12-byte IV). The iteration count is stored in the header so it can be raised later.
Passphrases are required to be ≥ 8 characters and are wiped from memory (`CharArray.fill`)
after use where the platform allows.

## Database at rest (decision)

The Room database is protected by the app sandbox and Android file-based encryption, but is
**not** additionally encrypted (e.g. SQLCipher) in v1 — see ADR-10 in `architecture.md`. An
attacker with root can use the app's own Keystore keys, so the benefit is limited, while a
Keystore failure would make the whole database unreadable. This is listed as a candidate for a
future release once it can be verified on real devices.

## Secrets in source

None. Release signing credentials are read from an untracked `keystore.properties`
(`.gitignore` excludes it and `*.jks`/`*.keystore`).
