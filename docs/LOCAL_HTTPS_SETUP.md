# Local HTTPS for Kellie's Voice (no Cloud required)

Kellie's Voice can pair and renew a dedicated TV login **without Home Assistant Cloud** if your Home Assistant has a **local HTTPS** endpoint whose certificate is trusted by the Android TV app's native HTTP client. A certificate trusted by a TV browser is not necessarily trusted by the app.

Plain HTTP discovery and the eight-digit physical pairing already work on a local network. Ongoing renewable login (Assist, alerts, token refresh) currently requires HTTPS because access and refresh tokens travel in ordinary HTTP headers. Sending them over plain HTTP would make them readable to other devices on the LAN.

## Transport matrix

| Path | Discovery / pairing | Renewable login | Notes |
|------|---------------------|-----------------|-------|
| Local HTTPS (no WAN) | Yes | Yes, with a trusted certificate and reachable hostname | Recommended zero-cloud path |
| HA Cloud HTTPS | Yes | Yes | Works, but not required |
| Local HTTP only | Yes (pairing) | No | Discovery works; renewable login is rejected |

The one-time encrypted pairing handoff protects the initial credential delivery. It does **not** encrypt later token renewals. Those use the saved HA origin.

## Practical local HTTPS options

### Option A: Local reverse proxy and a trusted certificate (recommended)

Use Caddy, nginx, or Traefik on your LAN to proxy Home Assistant's existing HTTP port behind HTTPS. Use a real DNS name you control and a certificate trusted by Android's **system** trust store. A Let's Encrypt **DNS-01** challenge can issue a certificate without opening a public router port. Configure local DNS to resolve that name to your LAN proxy. Certificate issuance/renewal may need Internet access even though **TV-to-Home Assistant traffic remains local**; this does not require Home Assistant Cloud or a paid subscription.

Then configure Home Assistant's own **Settings → System → Network** URL to the working HTTPS origin. Setting the URL by itself **does not enable HTTPS** or install a certificate. The name must match the certificate and the TV must reach the proxy.

### Option B: Home Assistant built-in HTTPS

Home Assistant can be configured with SSL certificate and key paths in its HTTP integration (usually `http: ssl_certificate: ...` and `ssl_key: ...` in `configuration.yaml`). The certificate must cover the local DNS name **and** be trusted by the TV app's system TLS stack. Afterwards set the matching HTTPS origin in HA's Network settings. This is an administrator option, not a one-click toggle.

### Option C: Private CA / self-signed certificate (advanced, not assumed supported)

A local CA or self-signed cert is **not automatically trusted by the native TV app**. Android apps targeting modern API levels normally trust system CAs, not user-added CAs, unless the app explicitly opts in using Android Network Security Configuration. This app does **not** currently opt in to user CAs. Merely installing a CA on a TV, or seeing the page open in the TV browser, does **not** prove renewable-login TLS verification works. Do not disable certificate/hostname verification. Prefer an appropriately issued system-trusted certificate for now.

## What is not supported

- Automatic renewable login over plain `http://` (tokens would be readable on the LAN).
- Silently disabling the HTTPS requirement.
- Disabling certificate or hostname verification.
- Using a broad insecure TrustManager.

If your HA is HTTP-only and you do not want to set up local HTTPS, you can still:
- Discover and verify the TV with the eight-digit code.
- Use the screensaver with an HTTP dashboard URL (unauthenticated or HA's own login).
- Manually enter a long-lived token if you accept the risk (not recommended).

## Troubleshooting

An HTTPS URL in Home Assistant's Network settings is not enough. The HA server/proxy must actually serve HTTPS, its hostname and certificate must match, and Android's native app must trust the issuing CA. TV browser behavior is not proof of native OkHttp trust. Local DNS must resolve the same hostname to the LAN endpoint, and requests cannot be silently redirected to plain HTTP.

## Security note

Independent review in issue #214 is required before any credential-bearing HTTP mode is considered. This document does not implement one.
