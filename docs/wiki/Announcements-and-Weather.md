# Announcements and Weather

## Text announcements

Home Assistant can send quiet on-screen text popups with `kellies_voice.announce`. These are **not** spoken TTS broadcasts — they are visual only.

Example and placement notes are in the [YAML Cookbook](YAML-Cookbook#announcements).

Enable **Settings → TV announcements & cameras** on the TV and ensure the alert receiver is connected.

## Seven-day weather card

`kellies_voice.show_forecast` displays a seven-day forecast card using a Home Assistant weather entity that supports daily forecasts. It is on-demand only (not automatic). The card does not start the microphone or change TV audio.

Set a default **Forecast source** on the Kellie's Voice device page, or pass `entity_id` in the action. Position, size, and duration of the weather card are controlled separately from the voice overlay and camera popups.

Full script and action examples: [YAML Cookbook](YAML-Cookbook#seven-day-weather-forecast) and the repository's [docs/WEATHER_FORECAST.md](https://github.com/chadakenn/Kellies-Voice/blob/main/docs/WEATHER_FORECAST.md).

Confirmed working on the maintainer's onn 4K Pro. Other weather integrations and TV models need testing.

## Next

- [YAML Cookbook](YAML-Cookbook)
- [TV App and Permissions](TV-App-and-Permissions)
