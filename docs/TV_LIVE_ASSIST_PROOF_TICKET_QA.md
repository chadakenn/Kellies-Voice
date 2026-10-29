# #193: One-use authenticated Assist + scoped-TV-alert proof, draft

The TV previously had a proof state that could be advanced by test code using
booleans, even when no Home Assistant WebSocket authentication had happened.
That pure state remains **test-only** in the synthetic four-message harness.

This branch adds a separate, trusted-result boundary:

- PairingAssistConnectionProbe.checkBound validates the pending OAuth candidate's
  exact TV UUID and pairing session, requires trusted HTTPS/WSS, then performs the
  existing one-shot auth_required → auth_ok → nonempty Assist pipelines →
  successful per-TV kellies_voice/subscribe_alerts result sequence.
- Only a successful real WebSocket sequence can construct
  PairingAssistConnectionProbe.VerifiedCapabilityReceipt. Its constructor is
  private; a failed, cancelled or timed-out probe delivers null.
- The receipt has no token material, is bound to the exact TV UUID and pairing
  session, expires 15 seconds after its monotonic issuance, and can be claimed
  only once. Wrong TV/session, negative time, late time, or replay fails closed.
- TvSealedHandoff.tvProofFromVerifiedSocket consumes that ticket before
  encrypting a TV proof receipt. A caller-supplied boolean or a bare
  PairingAssistProofState is **not accepted by this method**.
- Existing check(Result) and syntheticTvProof remain for draft compatibility
  and offline tests. They must not be mistaken for real verification.
- No code starts automatic pairing, installs credentials, changes the existing
  manual login, starts a microphone, or marks Connected.

The ticket unit tests directly exercise identity, one-use and monotonic
expiry rules. They are not a substitute for an instrumented/real Home
Assistant WSS test of auth_ok, a nonempty Assist list, subscription permission
and rejection of wrong responses. That hardware/integration QA remains
required before connecting a token.

No real HA exchange or transport handoff is wired. OAuth owner authorization,
refresh/revocation, restart-safe rollback, token/Keystore activation, privacy
and independent audit #214 remain blocking. Keep this PR draft, do not merge
to main/dev/#206.
