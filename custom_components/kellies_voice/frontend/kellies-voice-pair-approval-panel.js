/* Kellie's Voice owner-only pairing approval preview for Home Assistant.
 * NO OAuth, token issuance, browser persistence, credentials or TV login.
 * HA supplies "hass" and authenticated connection in a custom panel.
 * All untrusted data is inserted using textContent, never innerHTML.
 */
class KelliesVoicePairApprovalPanel extends HTMLElement {
  constructor() {
    super();
    this.attachShadow({ mode: "open" });
    this._hass = null;
    this._loading = false;
    this._approved = false;
    this._built = false;
  }

  set hass(value) {
    const firstAuthenticatedState = !this._hass && !!value?.connection;
    this._hass = value;
    if (this.isConnected) {
      if (!this._built) this._render();
      // HA can attach the custom element before supplying the hass prop.
      // Fetch once when the authenticated connection first arrives.
      if (firstAuthenticatedState) this._refresh();
    }
  }

  connectedCallback() {
    if (!this._built) {
      this._render();
      if (this._hass) this._refresh();
    }
  }

  _render() {
    if (this._built) return;
    this._built = true;
    const root = this.shadowRoot;
    root.innerHTML = `
      <style>
        :host { display:block; background:var(--primary-background-color,#171b18); color:var(--primary-text-color,#f3f3ef); min-height:100vh; font:inherit; }
        main { max-width:680px; margin:0 auto; padding:36px 20px; }
        h1 { font-size:1.65rem; margin:0 0 14px; }
        h2 { font-size:1.1rem; }
        p { line-height:1.55; }
        .sub { color:var(--secondary-text-color,#b8b8b8); }
        .card { border:1px solid var(--divider-color,#787878); border-radius:14px; padding:18px; margin:16px 0; background:var(--card-background-color,#232722); }
        button { font:inherit; border:0; padding:12px 20px; border-radius:8px; background:var(--primary-color,#84a877); color:var(--text-primary-color,#fff); cursor:pointer; }
        button:disabled { cursor:wait; opacity:.55; }
        button:focus-visible { outline:3px solid var(--primary-color,#91b783); outline-offset:3px; }
        .warning { font-weight:600; }
        #error { color:var(--error-color,#ec8a8a); }
        .small { font-size:.86rem; overflow-wrap:anywhere; }
      </style>
      <main>
        <h1>Kellie's Voice · TV pairing</h1>
        <p>Approve a TV that you've already verified using the matching eight-digit code and its physical remote.</p>
        <p class="warning">Security preview only. This will NOT sign the TV into Home Assistant or change your existing login.</p>
        <button id="refresh" type="button">Refresh available TVs</button>
        <p id="status" role="status">Checking for verified TVs…</p>
        <p id="error" role="alert"></p>
        <section id="list" aria-live="polite"></section>
        <p class="sub">Only the signed-in Home Assistant owner can approve a TV. If nothing appears, begin the two-minute pairing process on the TV and compare both codes.</p>
      </main>`;
    root.getElementById("refresh").addEventListener("click", () => this._refresh());
  }

  async _refresh() {
    if (!this._hass?.connection || this._loading) return;
    this._loading = true;
    const root = this.shadowRoot;
    const status = root.getElementById("status");
    const list = root.getElementById("list");
    const err = root.getElementById("error");
    root.getElementById("refresh").disabled = true;
    err.textContent = "";
    status.textContent = "Checking your Home Assistant pairing sessions…";
    list.replaceChildren();
    try {
      const result = await this._hass.connection.sendMessagePromise({
        type: "kellies_voice/pair/list_verified"
      });
      const sessions = Array.isArray(result?.pending) ? result.pending : [];
      if (!sessions.length) {
        status.textContent = this._approved
          ? "Owner approval recorded. Return to the Home Assistant pairing dialog."
          : "No verified TVs are awaiting owner approval.";
      } else {
        status.textContent = sessions.length + " TV pairing request" +
          (sessions.length === 1 ? "" : "s") + " awaiting your approval.";
        for (const session of sessions) this._addTV(session, list);
      }
    } catch (_) {
      status.textContent = "Could not check available TVs.";
      err.textContent = "Make sure you are signed in as your Home Assistant owner and the pairing preview is installed.";
    } finally {
      this._loading = false;
      root.getElementById("refresh").disabled = false;
    }
  }

  _addTV(pair, list) {
    if (typeof pair?.flow_id !== "string" || typeof pair?.tv_session !== "string"
        || typeof pair?.tv_uuid !== "string") return;
    const box = document.createElement("article");
    box.className = "card";
    const heading = document.createElement("h2");
    heading.textContent = "Verified TV · " + pair.tv_uuid.slice(-8);
    const detail = document.createElement("p");
    detail.className = "small sub";
    detail.textContent = "TV device ID: " + pair.tv_uuid;
    const instruction = document.createElement("p");
    instruction.textContent = "Approve ONLY if you initiated setup and confirmed that all eight digits matched on your TV and Home Assistant.";
    const ttl = document.createElement("p");
    ttl.className = "small sub";
    ttl.textContent = "Approval window: " + Math.max(0, Number(pair.seconds_remaining) || 0) + " seconds left";
    const button = document.createElement("button");
    button.type = "button";
    button.textContent = "Approve this TV";
    button.addEventListener("click", async () => {
      button.disabled = true;
      button.textContent = "Approving…";
      try {
        const reply = await this._hass.connection.sendMessagePromise({
          type: "kellies_voice/pair/claim_verified_owner",
          flow_id: pair.flow_id,
          tv_session: pair.tv_session
        });
        if (reply?.owner_verified !== true) throw Error("Approval rejected");
        this._approved = true;
        this.shadowRoot.getElementById("status").textContent =
          "Owner approval recorded. Return to the original Home Assistant setup dialog.";
        box.remove();
      } catch (_) {
        this.shadowRoot.getElementById("error").textContent =
          "Approval expired or was denied. Restart TV pairing and compare a new code.";
        button.disabled = false;
        button.textContent = "Approve this TV";
      }
    });
    box.append(heading, detail, instruction, ttl, button);
    list.append(box);
  }
}
customElements.define("kellies-voice-pair-approval-panel", KelliesVoicePairApprovalPanel);
