"""Authenticated Home Assistant MP4 proxy for one authorized Frigate event.

No public Frigate media URL is sent to the TV. Every HTTP request (including
video seeks with Range headers) uses normal Home Assistant bearer auth *plus*
the already deployed per-TV and camera read gates. Frigate's existing HA
integration client provides its authorization headers. No new credential store.
"""
from __future__ import annotations

import re
import time
from urllib.parse import urlsplit

from aiohttp import ClientError, ClientTimeout, web
from homeassistant.components.http import HomeAssistantView
from homeassistant.helpers import entity_registry as er
from homeassistant.helpers.aiohttp_client import async_get_clientsession
from yarl import URL

from .const import DOMAIN
from .frigate_replay import valid_event_id
from .frigate_replay_access import ReplayAccessDenied, authorize_replay_request
from .frigate_replay_client import (
    FrigateReplayUnavailable, fetch_recorded_event_metadata,
)

_REGISTERED = "frigate_replay_clip_view_registered"
_MAX_CLIP_BYTES = 128 * 1024 * 1024
_RANGE = re.compile(r"bytes=(?:[0-9]{1,12})?-(?:[0-9]{1,12})?\Z", re.ASCII)


def safe_range_header(value: object) -> str | None:
    """Allow only simple bounded byte ranges; reject multipart or injections."""
    if value is None:
        return None
    if not isinstance(value, str) or _RANGE.fullmatch(value) is None:
        raise web.HTTPBadRequest(text="Invalid byte range")
    if value == "bytes=-":
        raise web.HTTPBadRequest(text="Invalid byte range")
    return value


def resolve_frigate_clip_origin(raw: object) -> URL:
    """Frigate host comes ONLY from the configured HA Frigate integration."""
    if not isinstance(raw, str):
        raise web.HTTPNotFound()
    value = urlsplit(raw)
    if (value.scheme not in ("https", "http") or not value.hostname
            or value.username or value.password or value.query or value.fragment
            or value.path not in ("", "/")):
        raise web.HTTPNotFound()
    return URL(raw)


class FrigateReplayClipView(HomeAssistantView):
    """Proxy a single verified recording with HA-authenticated HTTP range IO."""

    url = "/api/kellies_voice/replay_clip/{tv_id}/{entity_id}/{event_id}"
    name = "api:kellies_voice:replay_clip"
    requires_auth = True

    def __init__(self, hass):
        self.hass = hass

    async def get(self, request, tv_id: str, entity_id: str, event_id: str):
        user = request.get("hass_user")
        if user is None or not valid_event_id(event_id):
            raise web.HTTPForbidden(text="Replay unavailable")
        hass = self.hass
        try:
            scope = authorize_replay_request(
                hass, user, tv_id, entity_id, er.async_get(hass)
            )
        except ReplayAccessDenied:
            raise web.HTTPForbidden(text="Replay unavailable") from None
        frigate_entry = next((
            entry for entry in hass.config_entries.async_entries("frigate")
            if entry.entry_id == scope.frigate_entry_id
        ), None)
        loaded = hass.data.get("frigate", {}).get(scope.frigate_entry_id)
        if not frigate_entry or not isinstance(loaded, dict):
            raise web.HTTPNotFound(text="Recording unavailable")
        api = loaded.get("client")
        config = loaded.get("config")
        cameras = config.get("cameras") if isinstance(config, dict) else None
        if not isinstance(cameras, dict) or scope.frigate_camera not in cameras:
            raise web.HTTPNotFound(text="Recording unavailable")

        try:
            matches = await fetch_recorded_event_metadata(
                api, configured_cameras=cameras,
                frigate_camera=scope.frigate_camera,
                now=time.time(), requested_event_id=event_id,
                limit=1,
            )
        except (ValueError, FrigateReplayUnavailable):
            raise web.HTTPNotFound(text="Recording unavailable") from None
        if len(matches) != 1 or matches[0].event_id != event_id:
            raise web.HTTPNotFound(text="Recording unavailable")

        # Permission or mapping can change during the asynchronous metadata
        # lookup. Validate again before any video bytes leave the server.
        try:
            if authorize_replay_request(
                hass, user, tv_id, entity_id, er.async_get(hass)
            ) != scope:
                raise ReplayAccessDenied()
        except ReplayAccessDenied:
            raise web.HTTPForbidden(text="Replay unavailable") from None

        # All media requests remain scoped to this known Frigate instance.
        # There is no user-controllable outbound URL, host or redirect.
        origin = resolve_frigate_clip_origin(frigate_entry.data.get("url"))
        media_url = origin / "api" / "events" / event_id / "clip.mp4"
        try:
            headers = await api.get_auth_headers()
        except Exception:
            raise web.HTTPBadGateway(text="Recording unavailable") from None
        if not isinstance(headers, dict):
            raise web.HTTPBadGateway(text="Recording unavailable")
        upstream_headers = dict(headers)
        byte_range = safe_range_header(request.headers.get("Range"))
        if byte_range:
            upstream_headers["Range"] = byte_range
        session = async_get_clientsession(hass)
        try:
            async with session.get(
                media_url, headers=upstream_headers,
                ssl=api.validate_ssl,
                allow_redirects=False,
                timeout=ClientTimeout(total=None, sock_connect=12, sock_read=30),
            ) as source:
                if source.status in (404, 410):
                    raise web.HTTPNotFound(text="Recording unavailable")
                if source.status == 416:
                    raise web.HTTPRequestRangeNotSatisfiable()
                if source.status not in (200, 206):
                    raise web.HTTPBadGateway(text="Recording unavailable")
                if source.content_type not in ("video/mp4", "application/octet-stream"):
                    raise web.HTTPBadGateway(text="Recording unavailable")
                # Cap advertised payload sizes before committing the response.
                declared = source.headers.get("Content-Length", "")
                if declared:
                    if not declared.isdecimal() or int(declared) > _MAX_CLIP_BYTES:
                        raise web.HTTPRequestEntityTooLarge(
                            max_size=_MAX_CLIP_BYTES, actual_size=int(declared) if declared.isdecimal() else 0
                        )
                output_headers = {
                    "Content-Type": "video/mp4",
                    "Cache-Control": "no-store",
                    "X-Content-Type-Options": "nosniff",
                    "Accept-Ranges": "bytes",
                }
                if declared:
                    output_headers["Content-Length"] = declared
                if source.status == 206 and "Content-Range" in source.headers:
                    output_headers["Content-Range"] = source.headers["Content-Range"]
                target = web.StreamResponse(status=source.status, headers=output_headers)
                await target.prepare(request)
                transferred = 0
                try:
                    async for chunk in source.content.iter_chunked(64 * 1024):
                        transferred += len(chunk)
                        if transferred > _MAX_CLIP_BYTES:
                            # Kill an unbounded stream rather than disclosing more
                            # video than the configured per-request cap.
                            break
                        await target.write(chunk)
                    await target.write_eof()
                except (ConnectionResetError, OSError):
                    pass  # TV pressed Back or changed its seek position.
                return target
        except web.HTTPException:
            raise
        except (ClientError, TimeoutError):
            raise web.HTTPBadGateway(text="Recording unavailable") from None


def async_register_replay_media(hass):
    """Register a non-public, authenticated HA HTTP view once."""
    state = hass.data.setdefault(DOMAIN, {})
    if state.get(_REGISTERED):
        return
    hass.http.register_view(FrigateReplayClipView(hass))
    state[_REGISTERED] = True
