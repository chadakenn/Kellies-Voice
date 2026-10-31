# v0.17.0 QA: opt-in private-LAN HTTP screensaver viewer (#262)

This is a compatibility/safety guard, **not** implementation of automatic
authenticated HTTP Home Assistant TV login. The original issue #261
"Please wait" login problem remains open.

## Configure from Home Assistant or the TV

1. In Home Assistant, open **Settings → Devices & services → Kellie's Voice →
   your TV**. The same existing **Screensaver dashboard URL** text entity
   (previously labeled Frigate web URL) accepts a local URL, for example
   `http://192.168.1.10:8123/tv-wall/wall?kiosk`.
2. Optionally turn on **Restrict HTTP screensaver to private LAN** on the TV's
   Home Assistant device or in the TV's **Settings → Screensaver**. OFF by
   default. **Manage layout from Home Assistant** must be on for HA edits
   to sync while the main app is closed.
3. For HTTP, keep **Secure HA dashboard sign-in OFF**. The HTTPS-only
   owner-approved V3 refresh token is **never delivered** to an HTTP page.
   Standard browser login and Home Assistant trusted_networks may work,
   but a "Please wait" login stall remains unverified (#261, Grok #274).
4. Test **Preview screensaver**, then the real idle Android TV dream.
   Clear dashboard URL to restore the native four-live-camera grid.

## Guard behavior and limitations

- In the opt-in restricted HTTP mode, require a **numeric RFC1918 IPv4**
  destination: 10/8, 172.16/12 or 192.168/16. Reject public IPs,
  DNS names, loopback, CGNAT, link-local, IPv6 and malformed numeric
  hosts. This choice avoids DNS rebinding via hostnames in this initial
  QA design, but does not replace network firewalls.
- Main-frame same-origin navigation checks scheme, host and port. A
  best-effort `WebViewClient.shouldInterceptRequest` guard blocks
  ordinary off-origin HTTP(S) subresource requests where WebView
  exposes them. Not all network traffic is interceptable here:
  **WebSockets, service workers, some redirects or apps with external
  intents may bypass these checks**. A user should not rely on this
  switch as an absolute LAN-only firewall. For strict containment,
  use LAN firewall/VLAN egress restrictions at the router.
- The existing unrestricted Frigate/HTTP WebView continues to work when
  the restriction switch is OFF. Authenticated HTTPS uses the separate
  origin-locked `externalAppV2` bridge and is unchanged.
- HTTP traffic is **not confidential or cryptographically authenticated**.
  Local HTTP frontends may independently store a browser login token
  visible to an on-path attacker. Do not put access/refresh tokens in
  URLs or grant a trusted_networks login to an admin user on untrusted
  networks. Fully local HTTPS does not require cloud or port forwarding.
- On/off is per TV and opt-in, carries only a boolean preference over
  HA's existing `kellie_generation`/device-identity settings channel;
  no new microphone permission, owner credential or token fallback.

## QA gates

- [ ] Android JVM tests and signed APK compile; HA/HACS CI green.
- [ ] Verify per-TV HA switch and TV switch sync both directions.
- [ ] Local HTTP private IPv4 opens when guard ON; hostname/public
      IP is rejected and shows a clear TV message.
- [ ] Same-origin page navigation works and off-origin HTTP(S)
      resource/navigation attempts are blocked where WebView exposes them.
- [ ] Cross-host WebSocket/redirect/service worker attempts investigated,
      with accurate limits and no security guarantees based on UI checks.
- [ ] No native credential or owner token is sent to HTTP WebView.
- [ ] Legacy Frigate WebView, four live cameras, HTTPS signed-in HA page,
      floating voice overlay and Quick Actions still work.
- [ ] Original #261 HTTP-only WebView "Please wait" tester case checked
      separately; do not mark resolved without a real hardware pass.
