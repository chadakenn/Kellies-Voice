"""Canonical TV UUIDv4 validation only. No HA entities, users or credentials."""
from uuid import UUID

def clean_tv_device_id(value: object) -> str:
    if type(value) is not str:
        raise ValueError("TV UUID invalid")
    try:
        parsed = UUID(value)
    except (ValueError, TypeError, AttributeError):
        raise ValueError("TV UUID invalid") from None
    if (parsed.version != 4
            or parsed.variant != "specified in RFC 4122"
            or str(parsed) != value):
        raise ValueError("TV UUID must be canonical UUIDv4")
    return value
