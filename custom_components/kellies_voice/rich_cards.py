"""Validation and helpers for rich Assist cards."""

import re

_ROW_LABEL = re.compile(r"^.{1,40}$")
_TYPES = {"list", "card"}

def clean_rows(value) -> list:
    if value is None:
        return []
    if not isinstance(value, list) or len(value) > 8:
        raise ValueError("Use at most 8 rows.")
    rows = []
    for item in value:
        if not isinstance(item, dict):
            raise ValueError("Each row must be an object.")
        label = " ".join(str(item.get("label", "")).split())
        if not label or len(label) > 40 or "://" in label.lower():
            raise ValueError("Row label must be 1-40 characters with no URL.")
        value_str = " ".join(str(item.get("value", "")).split())
        if len(value_str) > 20 or "://" in value_str.lower():
            raise ValueError("Row value must be at most 20 characters with no URL.")
        rows.append({"label": label, "value": value_str})
    return rows

def clean_card_type(value) -> str:
    if value not in _TYPES:
        raise ValueError("Card type must be 'list' or 'card'.")
    return value
