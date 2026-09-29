# Security Policy

## Supported Versions

| Version | Supported          |
|---------|--------------------|
| main    | :white_check_mark: |
| < 1.0   | :x:                |

## Overview

`halakou-ai-client` is an autonomous, zero-server Android AI client. This
document describes the app's security architecture, the data flows that are
and are not protected, and how to report a vulnerability.

## Threat Model & Security Architecture

### 1. API Keys — BYOK (Bring Your Own Key)

The app does **not** ship with, own, or proxy any API credentials. Every user
supplies their own provider keys (e.g. `GEMINI_API_KEY`) via the in-app
Secrets panel.

- Keys are never collected, transmitted to the project maintainers, or bundled
  into the APK by default (see `.env.example` — the key line is commented out
  and is only injected at runtime by AI Studio from user secrets).
- Keys leave the device **only** in direct, HTTPS API calls to the chosen
  LLM provider, authorized by the user.

### 2. Key Protection — Android Hardware-Backed KeyStore (AES-256-GCM)

User secrets at rest are protected by `KeyStoreManager`
(`app/src/main/java/com/example/halakou/data/security/KeyStoreManager.kt`):

- Master key alias `HalakouMasterKey_v1` is generated inside the
  **AndroidKeyStore** provider, so key material never leaves the secure
  hardware (TEE / StrongBox where available).
- Data is encrypted with **AES/GCM/NoPadding** — 256-bit AES in Galois/Counter
  Mode, providing both confidentiality and authenticated integrity.
- A fresh **12-byte GCM IV** is generated per encryption, with a 128-bit
  authentication tag.
- On-disk preference persistence uses `androidx.security.crypto`
  (EncryptedSharedPreferences) — see `EncryptedPreferencesManager.kt`.

### 3. Local Chat History — Work in Progress

> **NOTE — known gap:** The Room database (`halakou_local_chat.db`) is
> currently **not** encrypted. Chat history is stored as plain SQLite.

Planned hardening: migration to SQLCipher via
`androidx.sqlite.SupportFactory` so the Room DB is transparently encrypted
with a KeyStore-derived key. Until then, treat chat history as unencrypted
and **do not store sensitive information in conversations**. Anyone with
physical access to an unlocked, rooted device can read the DB.

### 4. Networking — Direct HTTPS & No Middleman

- All LLM traffic goes straight from the device to the provider over HTTPS
  (OkHttp / Retrofit). There is no proxy server that could intercept or log
  prompts.
- **No telemetry, analytics, or crash reporting** is transmitted to the
  project maintainers.

### 5. Serverless GitHub Pages Routing Architecture

Dynamic multi-model routing works with **zero server-side infrastructure**:

- `config.json` (the live endpoint/model routing table) is hosted on
  **GitHub Pages** and fetched client-side by `ConfigRepository`.
- The repository's GitHub Actions workflow (`updater.yml`) rebuilds and
   redeploys `config.json` on a schedule. The autonomous healing agent
  (`autonomous-healing-agent.yml`) prunes dead endpoints.
- **Security implication:** there is no server for an attacker to compromise
  in order to alter routing. However, the routing config is **public** — it
  contains no secrets by design. If the hosting account is compromised, a
  malicious `config.json` could redirect user traffic to attacker-controlled
  endpoints.

### 6. In-App Protections

- `SecurityConfig.kt` and `CircuitBreaker` isolate misbehaving endpoints.
- `AdminAuthenticator` / `AdminManager` gate the admin panel
  (e.g. app kill-switch) behind authentication.
- `SecurityVault` / `SecureVaultRepository` enforce vault access control.

## Reporting a Vulnerability

We encourage responsible disclosure! **Please do not report security
vulnerabilities via public GitHub issues.**

Instead, use GitHub's **Private Vulnerability Reporting**:
navigate to the **Security** tab → **Report a vulnerability**.

You can also email the maintainer directly. Please include:

- Description of the issue and potential impact
- Steps to reproduce / PoC
- Affected version(s) and device/OS info
- Suggested mitigation if you have one

We will acknowledge receipt within 72 hours and send a detailed response
within 7 days indicating the next steps in handling your report.

**Please do not disclose the vulnerability publicly until a fix has been
released.** We are happy to coordinate a disclosure timeline and will credit
reporters in release notes (unless you prefer to remain anonymous).

### Bounty / Rewards

This is a free and open-source project. We do not offer monetary bounties,
but significant reports will be credited and, if a CVE is warranted, we will
assist in the CVE assignment process.
