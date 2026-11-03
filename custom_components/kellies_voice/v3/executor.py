"""Offload pairing cryptography without blocking Home Assistant's event loop.

Production flows pass HomeAssistant and use its supported executor API.
Standalone protocol tests have no Home Assistant instance, so they use a
thread via asyncio.to_thread instead.
"""
import asyncio


async def async_crypto_job(hass, func, *args):
    """Execute synchronous P-256 operations outside the event loop."""
    executor = getattr(hass, "async_add_executor_job", None)
    if executor is not None:
        return await executor(func, *args)
    return await asyncio.to_thread(func, *args)
