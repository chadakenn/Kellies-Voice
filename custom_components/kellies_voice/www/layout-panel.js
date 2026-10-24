const SECTIONS = [
  {
    title: "Voice overlay",
    note: "Where the assistant card sits while you watch something else.",
    rows: [
      ["position", "Position", "select"],
      ["size", "Size", "select"],
      ["style", "Style", "select"],
      ["auto_hide", "Auto-hide", "select"],
      ["auto_listen", "Auto-listen", "switch"],
    ],
  },
  {
    title: "Announcements and cameras",
    note: "These move alert cards only. They do not move the voice overlay.",
    rows: [
      ["alert_position", "Position", "select"],
      ["alert_size", "Size", "select"],
    ],
  },
  {
    title: "Microphone",
    note: "This changes how a pause is detected. It does not turn the microphone on.",
    rows: [["noise", "Sensitivity", "select"]],
  },
  {
    title: "Forecast",
    note: "Shown only when a forecast is requested.",
    rows: [
      ["weather_position", "Position", "select"],
      ["weather_size", "Size", "select"],
      ["weather_duration", "Duration", "select"],
      ["weather_source", "Weather entity", "select"],
    ],
  },
  {
    title: "Screensaver",
    note: "Small and Medium leave the rest of the screen clear. A Frigate page address is still set on the TV.",
    rows: [
      ["screensaver_size", "Size", "select"],
      ["screensaver_cameras", "Cameras", "cameras"],
    ],
  },
];

class KelliesVoiceLayout extends HTMLElement {
  set hass(hass) {
    this._hass = hass;
    const signature = this._signature();
    if (signature !== this._signatureValue) {
      this._signatureValue = signature;
      this._render();
    } else {
      this._sync();
    }
  }

  _signature() {
    const hass = this._hass;
    if (!hass || !hass.states) return "";
    const controls = [];
    const cameras = [];
    Object.keys(hass.states).forEach((id) => {
      const attrs = hass.states[id].attributes || {};
      if (attrs.kellie_setting && attrs.kellie_device_id) {
        const options = attrs.options ? attrs.options.join("|") : "";
        controls.push(attrs.kellie_device_id + ":" + attrs.kellie_setting + ":" + id + ":" + options);
      }
      if (id.indexOf("camera.") === 0) cameras.push(id);
    });
    controls.sort();
    cameras.sort();
    return controls.join(",") + "#" + cameras.join(",");
  }

  _tvs() {
    const hass = this._hass;
    const byDevice = new Map();
    Object.keys(hass.states).forEach((id) => {
      const state = hass.states[id];
      const attrs = state.attributes || {};
      if (!attrs.kellie_setting || !attrs.kellie_device_id) return;
      if (!byDevice.has(attrs.kellie_device_id)) byDevice.set(attrs.kellie_device_id, []);
      byDevice.get(attrs.kellie_device_id).push({ id, state, setting: attrs.kellie_setting });
    });
    return Array.from(byDevice.entries()).map(([deviceId, controls]) => ({
      deviceId,
      name: this._tvName(controls[0].id, deviceId),
      controls,
    }));
  }

  _tvName(entityId, deviceId) {
    const hass = this._hass;
    const entity = hass.entities && hass.entities[entityId];
    const device = entity && hass.devices && hass.devices[entity.device_id];
    return (device && (device.name_by_user || device.name)) || "Kellie's Voice TV";
  }

  _control(tv, setting) {
    return tv.controls.find((item) => item.setting === setting);
  }

  _render() {
    const tvs = this._hass ? this._tvs() : [];
    const body = tvs.length
      ? tvs.map((tv) => this._tvCard(tv)).join("")
      : `<section class="card empty">Add the TV under Settings → Devices & services, then turn <strong>Home Assistant layout</strong> on in the Kellie's Voice app.</section>`;
    this.innerHTML = `<style>${PAGE_CSS}</style>
      <main>
        <header>
          <p class="eyebrow">Layout</p>
          <h1>Kellie's Voice</h1>
          <p class="intro">Dropdowns here are the same controls as the device page, in one place. The TV applies them while Home Assistant layout is on. Nothing on this page starts the microphone.</p>
        </header>
        ${body}
        <p class="status"></p>
      </main>`;
    this._bind();
  }

  _tvCard(tv) {
    const sections = SECTIONS.map((section) => {
      const rows = section.rows
        .map(([setting, label, kind]) => this._row(tv, setting, label, kind))
        .filter(Boolean)
        .join("");
      if (!rows) return "";
      return `<section class="card"><h2>${section.title}</h2><p class="note">${section.note}</p>${rows}</section>`;
    }).join("");
    return `<h2 class="tv">${this._escape(tv.name)}</h2>${sections}`;
  }

  _row(tv, setting, label, kind) {
    const control = this._control(tv, setting);
    if (!control && kind !== "cameras") return "";
    if (kind === "switch") {
      const on = control.state.state === "on";
      return `<div class="row"><label for="${control.id}">${label}</label>
        <input class="toggle" id="${control.id}" data-kind="switch" data-entity="${control.id}" type="checkbox" ${on ? "checked" : ""}></div>`;
    }
    if (kind === "cameras") {
      return this._cameraRow(tv, control, label);
    }
    const options = (control.state.attributes && control.state.attributes.options) || [];
    const current = control.state.state;
    const choices = options.map((option) =>
      `<option value="${this._escape(option)}" ${option === current ? "selected" : ""}>${this._escape(option)}</option>`
    ).join("");
    return `<div class="row"><label for="${control.id}">${label}</label>
      <span class="select-wrap"><select id="${control.id}" data-kind="select" data-entity="${control.id}">${choices}</select></span></div>`;
  }

  _cameraRow(tv, control, label) {
    if (!control) return "";
    const selected = new Set(
      String(control.state.state || "")
        .split(",")
        .map((item) => item.trim())
        .filter((item) => item && item !== "none" && item !== "unknown" && item !== "unavailable")
    );
    const cameras = Object.keys(this._hass.states)
      .filter((id) => id.indexOf("camera.") === 0)
      .sort();
    const summary = selected.size ? Array.from(selected).map(this._short).join(", ") : "None";
    const boxes = cameras.map((id) => {
      const name = (this._hass.states[id].attributes || {}).friendly_name || id;
      return `<label class="check"><input type="checkbox" value="${this._escape(id)}" ${selected.has(id) ? "checked" : ""}>
        <span>${this._escape(name)}<small>${this._escape(id)}</small></span></label>`;
    }).join("");
    const empty = cameras.length ? boxes : `<p class="note">No camera entities found in Home Assistant.</p>`;
    return `<div class="row cameras" data-kind="cameras" data-entity="${control.id}">
      <span>${label}</span>
      <span class="menu">
        <button type="button" class="menu-button">${this._escape(summary)}</button>
        <span class="menu-panel" hidden>${empty}</span>
      </span>
    </div>`;
  }

  _bind() {
    this.querySelectorAll("select[data-kind='select']").forEach((select) => {
      select.addEventListener("change", () => this._saveSelect(select));
    });
    this.querySelectorAll("input[data-kind='switch']").forEach((input) => {
      input.addEventListener("change", () => this._saveSwitch(input));
    });
    this.querySelectorAll(".menu-button").forEach((button) => {
      button.addEventListener("click", () => {
        const panel = button.parentElement.querySelector(".menu-panel");
        const open = panel.hasAttribute("hidden");
        this.querySelectorAll(".menu-panel").forEach((item) => item.setAttribute("hidden", ""));
        if (open) panel.removeAttribute("hidden");
      });
    });
    this.querySelectorAll(".cameras input").forEach((box) => {
      box.addEventListener("change", () => this._saveCameras(box.closest(".cameras")));
    });
  }

  _sync() {
    if (!this._hass) return;
    this.querySelectorAll("select[data-entity]").forEach((select) => {
      if (document.activeElement === select) return;
      const state = this._hass.states[select.dataset.entity];
      if (state && select.value !== state.state) select.value = state.state;
    });
    this.querySelectorAll("input[data-kind='switch']").forEach((input) => {
      const state = this._hass.states[input.dataset.entity];
      if (!state) return;
      input.checked = state.state === "on";
    });
  }

  async _saveSelect(select) {
    await this._call("select", "select_option", {
      entity_id: select.dataset.entity,
      option: select.value,
    });
  }

  async _saveSwitch(input) {
    await this._call("switch", input.checked ? "turn_on" : "turn_off", {
      entity_id: input.dataset.entity,
    });
  }

  async _saveCameras(row) {
    const boxes = Array.from(row.querySelectorAll("input[type='checkbox']"));
    const picked = boxes.filter((box) => box.checked).map((box) => box.value);
    if (picked.length > 4) {
      boxes.forEach((box) => {
        if (picked.indexOf(box.value) > 3) box.checked = false;
      });
      this._status("Up to four cameras.");
    }
    const value = boxes.filter((box) => box.checked).map((box) => box.value);
    const label = value.length ? value.map(this._short).join(", ") : "None";
    row.querySelector(".menu-button").textContent = label;
    await this._call("text", "set_value", {
      entity_id: row.dataset.entity,
      value: value.length ? value.join(",") : "none",
    });
  }

  async _call(domain, service, data) {
    try {
      await this._hass.callService(domain, service, data);
      this._status("Sent to the TV.");
    } catch (error) {
      this._status("Home Assistant did not accept that change.");
    }
  }

  _status(text) {
    const node = this.querySelector(".status");
    if (!node) return;
    node.textContent = text;
  }

  _short(entityId) {
    return String(entityId).replace(/^camera\./, "").replace(/_/g, " ");
  }

  _escape(value) {
    return String(value)
      .replace(/&/g, "&")
      .replace(/</g, "<")
      .replace(/"/g, """);
  }
}

const PAGE_CSS = `
:host { display: block; background: var(--primary-background-color); color: var(--primary-text-color); min-height: 100%; }
main { max-width: 880px; margin: 0 auto; padding: 28px 20px 48px; box-sizing: border-box; }
.eyebrow { margin: 0; letter-spacing: .14em; text-transform: uppercase; font-size: 12px; color: var(--primary-color); }
h1 { margin: 4px 0 8px; font-weight: 600; font-size: 34px; }
.intro, .note { color: var(--secondary-text-color, var(--primary-text-color)); line-height: 1.45; }
.intro { margin: 0 0 22px; max-width: 62ch; }
.tv { margin: 8px 0 12px; font-size: 22px; }
.card { background: var(--card-background-color); border-radius: 18px; padding: 8px 18px 10px; margin: 0 0 16px; box-shadow: var(--ha-card-box-shadow, 0 1px 2px rgba(0,0,0,.18)); }
.card h2 { margin: 14px 0 0; font-size: 18px; }
.note { margin: 4px 0 8px; font-size: 13px; }
.row { display: flex; justify-content: space-between; align-items: center; gap: 16px; padding: 11px 0; border-top: 1px solid var(--divider-color); }
.row label, .row > span:first-child { font-size: 15px; }
.select-wrap { position: relative; }
.select-wrap::after { content: ""; position: absolute; right: 14px; top: 46%; border: 5px solid transparent; border-top-color: var(--primary-text-color); pointer-events: none; }
select, .menu-button { appearance: none; background: var(--input-fill-color, var(--secondary-background-color, transparent)); color: var(--primary-text-color); border: 1px solid var(--divider-color); border-radius: 12px; min-width: 240px; max-width: 420px; padding: 10px 34px 10px 12px; font: inherit; text-align: left; }
select:focus, .menu-button:focus, .toggle:focus { outline: 2px solid var(--primary-color); outline-offset: 2px; }
.menu { position: relative; }
.menu-panel { position: absolute; right: 0; z-index: 4; display: block; width: min(420px, 80vw); max-height: 280px; overflow: auto; padding: 8px; border-radius: 12px; background: var(--card-background-color); border: 1px solid var(--divider-color); box-shadow: var(--ha-card-box-shadow, 0 8px 24px rgba(0,0,0,.2)); }
.menu-panel[hidden] { display: none; }
.check { display: flex; gap: 10px; align-items: flex-start; padding: 8px; border-radius: 8px; }
.check small { display: block; color: var(--secondary-text-color, var(--primary-text-color)); }
.toggle { width: 46px; height: 26px; accent-color: var(--primary-color); }
.status { min-height: 1.2em; color: var(--secondary-text-color, var(--primary-text-color)); }
.empty { padding: 18px; }
`;

customElements.define("kellies-voice-layout", KelliesVoiceLayout);
