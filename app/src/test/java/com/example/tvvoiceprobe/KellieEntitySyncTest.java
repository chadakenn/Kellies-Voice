package com.example.tvvoiceprobe;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class KellieEntitySyncTest {
    @Test public void discoveryIgnoresEntitiesThatAreNotKellieControls() throws Exception {
        JSONArray states = new JSONArray()
                .put(entity("light.kitchen", "on", null))
                .put(entity("select.other_tv", "Top", "other-tv"))
                .put(entity("select.this_tv", "Bottom", "this-tv"));
        JSONArray ids = KellieEntitySync.matchingIds(states, "this-tv");
        assertEquals(1, ids.length());
        assertEquals("select.this_tv", ids.getString(0));
        assertFalse(KellieEntitySync.marked(entity("sensor.temp", "70", null)));
    }

    @Test public void entitySubscriptionKeepsUnchangedAttributes() throws Exception {
        KellieEntitySync.Cache cache = new KellieEntitySync.Cache();
        JSONObject added = new JSONObject("{\"a\":{\"select.this_tv\":{\"s\":\"Bottom\","
                + "\"a\":{\"kellie_device_id\":\"this-tv\",\"kellie_setting\":\"alert_position\","
                + "\"kellie_generation\":2}}}}");
        List<JSONObject> first = cache.apply(added);
        assertEquals("Bottom", first.get(0).optString("state"));
        JSONObject changed = new JSONObject("{\"c\":{\"select.this_tv\":{\"+\":{\"s\":\"Top\","
                + "\"a\":{\"kellie_generation\":3}}}}}");
        List<JSONObject> second = cache.apply(changed);
        assertEquals("Top", second.get(0).optString("state"));
        assertEquals("this-tv", second.get(0).getJSONObject("attributes").getString("kellie_device_id"));
        assertEquals(3, second.get(0).getJSONObject("attributes").getInt("kellie_generation"));
        JSONObject command = KellieEntitySync.subscribe(4, new JSONArray().put("select.this_tv"));
        assertEquals("subscribe_entities", command.getString("type"));
        assertEquals("select.this_tv", command.getJSONArray("entity_ids").getString(0));
        assertTrue(command.getInt("id") == 4);
    }

    private static JSONObject entity(String id, String state, String device) throws Exception {
        JSONObject entity = new JSONObject();
        entity.put("entity_id", id);
        entity.put("state", state);
        JSONObject attributes = new JSONObject();
        if (device != null) attributes.put("kellie_device_id", device);
        entity.put("attributes", attributes);
        return entity;
    }
}
