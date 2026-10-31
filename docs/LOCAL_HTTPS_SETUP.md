# Local HTTPS for Kellie's Voice (no Cloud required)

Kellie's Voice can pair and renew a dedicated TV login **without Home Assistant Cloud** if your Home Assistant has a **local HTTPS** endpoint that the TV's Android WebView trusts.

Plain HTTP discovery and the eight-digit physical pairing already work on a local network. Ongoing renewable login (Assist, alerts, token refresh) currently requires HTTPS because access and refresh tokens travel in ordinary HTTP headers. Sending them over plain HTTP would make them readable to other devices on the LAN.

## Transport matrix

| Path | Discovery / pairing | Renewable login | Notes |
|------|---------------------|-----------------|-------|
| Local HTTPS (no WAN) | Yes | Yes | Recommended zero-cloud path |
| HA Cloud HTTPS | Yes | Yes | Works, but not required |
| Local HTTP only | Yes (pairing) | No | Discovery works; renewable login is rejected |

The one-time encrypted pairing handoff protects the initial credential delivery. It does **not** encrypt later token renewals. Those use the saved HA origin.

## Practical local HTTPS options

### Option A: Home Assistant's built-in HTTPS (simplest)

1. In HA go to **Settings → System → Network**.
2. Set a local HTTPS URL (for example `https://homeassistant.local:8123` or your HA host's IP with a certificate).
3. Use a certificate the Android TV trusts (a publicly trusted cert, or a certificate from a local CA that you install on the TV).
4. Confirm the TV can open that HTTPS URL in a browser without a certificate warning.
5. Start secure pairing on the TV and approve it in HA. Renewable login will use the HTTPS origin.

### Option B: Local reverse proxy with a trusted certificate

Use Caddy, nginx, or Traefik on your LAN with a certificate from Let's Encrypt (DNS challenge) or a local CA. Point it at HA's HTTP port and give the TV a DNS name that resolves only on your network.

### Option C: Self-signed certificate (advanced)

A self-signed certificate will **not** be trusted by Android WebView by default. You would need to install the CA on the TV. This is not recommended for ordinary users.

## What is not supported

- Automatic renewable login over plain `http://` (tokens would be readable on the LAN).
- Silently disabling the HTTPS requirement.
- Disabling certificate verification.
- Using a broad insecure TrustManager.

If your HA is HTTP-only and you do not want to set up local HTTPS, you can still:
- Discover and verify the TV with the eight-digit code.
- Use the screensaver with an HTTP dashboard URL (unauthenticated or HA's own login).
- Manually enter a long-lived token if you accept the risk (not recommended).

## Security note

Independent review in issue #214 is required before any credential-bearing HTTP mode is considered. This document does not implement one.
