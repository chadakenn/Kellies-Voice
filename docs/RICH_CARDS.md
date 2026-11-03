# Rich interactive Assist cards (v0.18 design)

Temporary floating cards for lists, status, and (later) media.
Reuses the existing one-use action button system.

## Event

`kellies_voice_rich_card`

```yaml
title: Shopping list
type: list          # list | card
rows:
  - label: Milk
    value: "2"
actions:
  - id: clear
    label: Clear
duration_seconds: 25
announcement_id: <32-hex>   # only when actions present
```

## Service

`kellies_voice.show_rich_card`

See services.py for the schema.

## Status

Basic end-to-end path implemented:

- HA service registration and event firing
- Sanitized alert transport
- Android overlay rendering (title, subtitle, rows, optional action buttons)
- Auto-dismiss and Back to close

Camera snapshot rendering via `image_entity` is implemented. Media-player images and full hardware QA are still pending.


## Example

A minimal automation that shows a shopping-list card:

```yaml
action:
  - service: kellies_voice.show_rich_card
    data:
      title: Shopping list
      subtitle: Tonight
      type: list
      duration_seconds: 25
      rows:
        - label: Milk
          value: "2"
        - label: Eggs
          value: "1 dozen"
      actions:
        - id: clear
          label: Clear
```

See also:
- `docs/examples/rich_card_example.yaml` — full automation you can paste into Developer Tools
- `custom_components/kellies_voice/blueprints/rich_card_test.yaml` — importable blueprint
