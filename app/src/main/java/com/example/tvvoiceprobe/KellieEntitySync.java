package com.example.tvvoiceprobe;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds this TV's Kellie controls without keeping a subscription to every
 * Home Assistant entity. Discovery still uses one get_states result. After
 * that, only the matched entity ids stay subscribed.
 */
final class KellieEntitySync {
    private KellieEntitySync() { }

    static boolean marked(JSONObject state) {
        if (state == null) return false;
        JSONObject attributes = state.optJSONObject("attributes");
        return attributes != null && attributes.has("kellie_device_id");
    }

    static JSONArray matchingIds(JSONArray states, String deviceId) {
        JSONArray ids = new JSONArray();
        if (states == null || deviceId == null || deviceId.isEmpty()) return ids;
        for (int i = 0; i < states.length(); i++) {
            JSONObject state = states.optJSONObject(i);
            if (!marked(state)) continue;
            JSONObject attributes = state.optJSONObject("attributes");
            if (!deviceId.equals(attributes.optString("kellie_device_id"))) continue;
            String id = state.optString("entity_id");
            if (!id.isEmpty()) ids.put(id);
        }
        return ids;
    }

    static JSONObject subscribe(int id, JSONArray entityIds) throws Exception {
        JSONObject command = new JSONObject();
        command.put("id", id);
        command.put("type", "subscribe_entities");
        command.put("entity_ids", entityIds);
        return command;
    }

    /** Applies a subscribe_entities event and returns added or changed states. */
    static final class Cache {
        private final Map<String, JSONObject> states = new LinkedHashMap<>();

        List<JSONObject> apply(JSONObject event) {
            List<JSONObject> changed = new ArrayList<>();
            if (event == null) return changed;
            JSONObject added = event.optJSONObject("a");
            if (added != null) {
                JSONArray names = added.names();
                if (names != null) {
                    for (int i = 0; i < names.length(); i++) {
                        String name = names.optString(i);
                        JSONObject full = expand(name, added.optJSONObject(name));
                        if (full == null) continue;
                        states.put(name, full);
                        changed.add(full);
                    }
                }
            }
            JSONObject updates = event.optJSONObject("c");
            if (updates != null) {
                JSONArray names = updates.names();
                if (names != null) {
                    for (int i = 0; i < names.length(); i++) {
                        String name = names.optString(i);
                        JSONObject current = states.get(name);
                        if (current == null) current = expand(name, null);
                        if (current == null) continue;
                        applyChange(current, updates.optJSONObject(name));
                        states.put(name, current);
                        changed.add(current);
                    }
                }
            }
            JSONArray removed = event.optJSONArray("r");
            if (removed != null) {
                for (int i = 0; i < removed.length(); i++) states.remove(removed.optString(i));
            }
            return changed;
        }

        void clear() { states.clear(); }

        private static JSONObject expand(String entityId, JSONObject compressed) {
            if (entityId == null || entityId.isEmpty()) return null;
            try {
                JSONObject state = new JSONObject();
                state.put("entity_id", entityId);
                state.put("state", compressed == null ? "" : compressed.optString("s", ""));
                JSONObject attributes = compressed == null ? null : compressed.optJSONObject("a");
                state.put("attributes", attributes == null
                        ? new JSONObject() : new JSONObject(attributes.toString()));
                return state;
            } catch (Exception ignored) {
                return null;
            }
        }

        private static void applyChange(JSONObject state, JSONObject delta) {
            if (delta == null) return;
            try {
                JSONObject plus = delta.optJSONObject("+");
                if (plus != null) {
                    if (plus.has("s")) state.put("state", plus.optString("s"));
                    JSONObject extra = plus.optJSONObject("a");
                    if (extra != null) {
                        JSONObject attributes = state.optJSONObject("attributes");
                        if (attributes == null) {
                            attributes = new JSONObject();
                            state.put("attributes", attributes);
                        }
                        JSONArray names = extra.names();
                        if (names != null) {
                            for (int i = 0; i < names.length(); i++) {
                                String key = names.optString(i);
                                attributes.put(key, extra.get(key));
                            }
                        }
                    }
                }
                JSONObject minus = delta.optJSONObject("-");
                JSONArray removed = minus == null ? null : minus.optJSONArray("a");
                JSONObject attributes = state.optJSONObject("attributes");
                if (removed != null && attributes != null) {
                    for (int i = 0; i < removed.length(); i++) attributes.remove(removed.optString(i));
                }
            } catch (Exception ignored) { }
        }
    }
}
