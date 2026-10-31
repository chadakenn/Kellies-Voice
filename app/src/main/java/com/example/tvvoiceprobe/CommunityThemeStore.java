package com.example.tvvoiceprobe;

import android.content.Context;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Community theme packs are small DATA files, never extensions or executable
 * scripts. Files are copied from Android's system document picker into
 * app-private storage. No network fetching, external file paths or ZIPs.
 */
public final class CommunityThemeStore {
    private static final int MAX_BYTES = 16 * 1024;
    private static final int MAX_PACKS = 20;
    private static final String DIR = "community-themes";
    public static final String[] COLOR_KEYS = {
        "screen", "panel", "card", "cardFocused", "accent",
        "accentSecondary", "text", "textMuted", "stroke", "focus",
        "success", "warning"
    };
    private static final String[] SHAPE_KEYS = {
        "panelRadiusDp", "cardRadiusDp", "focusBorderDp", "spacingDp"
    };

    private CommunityThemeStore() { }

    public static final class Theme {
        public final String id, name, author, base;
        public final JSONObject colors;
        public final int panelRadiusDp, cardRadiusDp, focusBorderDp, spacingDp;
        public final float animationScale;

        private Theme(String id, String name, String author, String base, JSONObject colors,
                      int panel, int card, int focus, int spacing, float animation) {
            this.id = id;
            this.name = name;
            this.author = author;
            this.base = base;
            this.colors = colors;
            this.panelRadiusDp = panel;
            this.cardRadiusDp = card;
            this.focusBorderDp = focus;
            this.spacingDp = spacing;
            this.animationScale = animation;
        }

        public int color(String key) {
            try {
                if (!allowed(COLOR_KEYS, key)) throw new IllegalArgumentException("Unknown color");
                return parseColor(colors.getString(key));
            } catch (JSONException e) {
                throw new IllegalArgumentException("Missing color: " + key, e);
            }
        }
    }

    private static boolean allowed(String[] values, String candidate) {
        for (String item : values) if (item.equals(candidate)) return true;
        return false;
    }

    private static void keysOnly(JSONObject object, String... allowedKeys) throws JSONException {
        Iterator<String> keys = object.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (!allowed(allowedKeys, key)) {
                throw new IllegalArgumentException("Unsupported theme property: " + key);
            }
        }
    }

    private static int parseColor(String value) {
        if (value == null || !value.matches("#[0-9a-fA-F]{6}")) {
            throw new IllegalArgumentException("Colors must use #RRGGBB");
        }
        return 0xff000000 | Integer.parseInt(value.substring(1), 16);
    }

    private static int bounded(JSONObject shapes, String key, int low, int high) throws JSONException {
        Object raw = shapes.get(key);
        if (!(raw instanceof Integer)) throw new IllegalArgumentException("Invalid " + key);
        int value = (Integer) raw;
        if (value < low || value > high) throw new IllegalArgumentException("Out of range: " + key);
        return value;
    }

    private static String label(JSONObject json, String key, int max) throws JSONException {
        String val = json.getString(key).trim();
        if (val.isEmpty() || val.length() > max) {
            throw new IllegalArgumentException("Invalid " + key);
        }
        for (int i = 0; i < val.length(); i++) {
            char c = val.charAt(i);
            if (Character.isISOControl(c) || c == '<' || c == '>') {
                throw new IllegalArgumentException("Unsafe " + key);
            }
        }
        return val;
    }

    private static double brightness(int argb) {
        double[] rgb = {
            ((argb >>> 16) & 255) / 255.0,
            ((argb >>> 8) & 255) / 255.0,
            (argb & 255) / 255.0
        };
        for (int i = 0; i < 3; i++) {
            rgb[i] = rgb[i] <= 0.04045 ? rgb[i] / 12.92 :
                    Math.pow((rgb[i] + 0.055) / 1.055, 2.4);
        }
        return rgb[0] * 0.2126 + rgb[1] * 0.7152 + rgb[2] * 0.0722;
    }

    private static double contrast(int a, int b) {
        double x = brightness(a), y = brightness(b);
        return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05);
    }

    public static Theme parse(String source) {
        try {
            if (source == null || source.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
                throw new IllegalArgumentException("Theme exceeds 16 KB");
            }
            JSONObject json = new JSONObject(source);
            keysOnly(json, "format", "id", "name", "author", "base", "colors", "shapes",
                    "animationScale");
            if (json.getInt("format") != 1) throw new IllegalArgumentException("Unsupported theme format");
            String id = label(json, "id", 42);
            if (!id.matches("community\\.[a-z0-9][a-z0-9-]{2,31}")) {
                throw new IllegalArgumentException("Theme ID must start with community.");
            }
            String name = label(json, "name", 40);
            String author = label(json, "author", 40);
            String base = json.getString("base");
            if (!("glass".equals(base) || "classic".equals(base) ||
                    "signal".equals(base) || "aurora".equals(base) ||
                    "obsidian".equals(base) || "terminal".equals(base))) {
                throw new IllegalArgumentException("Unknown base theme");
            }
            JSONObject colors = json.getJSONObject("colors");
            keysOnly(colors, COLOR_KEYS);
            for (String key : COLOR_KEYS) parseColor(colors.getString(key));
            if (contrast(parseColor(colors.getString("text")),
                    parseColor(colors.getString("screen"))) < 4.5 ||
                    contrast(parseColor(colors.getString("text")),
                    parseColor(colors.getString("panel"))) < 4.5 ||
                    contrast(parseColor(colors.getString("text")),
                    parseColor(colors.getString("card"))) < 4.5 ||
                    contrast(parseColor(colors.getString("focus")),
                    parseColor(colors.getString("card"))) < 3.0) {
                throw new IllegalArgumentException("Text/focus contrast is too low for TV");
            }
            JSONObject shapes = json.getJSONObject("shapes");
            keysOnly(shapes, SHAPE_KEYS);
            int panel = bounded(shapes, "panelRadiusDp", 0, 36);
            int card = bounded(shapes, "cardRadiusDp", 0, 36);
            int focus = bounded(shapes, "focusBorderDp", 1, 6);
            int spacing = bounded(shapes, "spacingDp", 4, 28);
            double animation = json.getDouble("animationScale");
            if (!Double.isFinite(animation) || animation < 0 || animation > 1.5) {
                throw new IllegalArgumentException("Invalid animationScale");
            }
            return new Theme(id, name, author, base, colors, panel, card, focus,
                    spacing, (float) animation);
        } catch (JSONException e) {
            throw new IllegalArgumentException("Invalid community theme JSON", e);
        }
    }

    private static File dir(Context context) {
        return new File(context.getApplicationContext().getFilesDir(), DIR);
    }

    public static boolean isId(String id) {
        return id != null && id.matches("community\\.[a-z0-9][a-z0-9-]{2,31}");
    }

    /** Never join arbitrary user-supplied paths, only validated IDs. */
    private static File path(Context context, String id) {
        if (!isId(id)) throw new IllegalArgumentException("Invalid theme ID");
        return new File(dir(context), id + ".json");
    }

    public static Theme get(Context context, String id) {
        if (!isId(id)) return null;
        File path = path(context, id);
        if (!path.isFile() || path.length() > MAX_BYTES) return null;
        try (FileInputStream input = new FileInputStream(path)) {
            byte[] raw = readLimited(input);
            Theme theme = parse(new String(raw, StandardCharsets.UTF_8));
            return theme.id.equals(id) ? theme : null;
        } catch (Exception invalid) {
            return null;
        }
    }

    public static List<Theme> list(Context context) {
        File[] files = dir(context).listFiles((folder, name) -> name.endsWith(".json"));
        if (files == null) return Collections.emptyList();
        List<Theme> themes = new ArrayList<>();
        for (File file : files) {
            if (!file.getName().startsWith("community.")) continue;
            Theme theme = get(context, file.getName().substring(0, file.getName().length() - 5));
            if (theme != null) themes.add(theme);
        }
        themes.sort(Comparator.comparing(x -> x.name.toLowerCase(java.util.Locale.ROOT)));
        return themes;
    }

    private static byte[] readLimited(InputStream input) throws java.io.IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[2048];
        int len;
        while ((len = input.read(buffer)) != -1) {
            if (output.size() + len > MAX_BYTES) throw new IllegalArgumentException("Theme exceeds 16 KB");
            output.write(buffer, 0, len);
        }
        return output.toByteArray();
    }

    /** Parses before writing; failed imports cannot replace a working theme. */
    public static synchronized Theme importTheme(Context context, InputStream stream)
            throws java.io.IOException {
        Theme pack = parse(new String(readLimited(stream), StandardCharsets.UTF_8));
        File folder = dir(context);
        if (!folder.isDirectory() && !folder.mkdirs()) {
            throw new java.io.IOException("Theme storage unavailable");
        }
        File target = path(context, pack.id);
        if (!target.exists() && list(context).size() >= MAX_PACKS) {
            throw new IllegalArgumentException("Maximum 20 community themes");
        }
        File temp = File.createTempFile("theme-", ".tmp", folder);
        try {
            // Normalize all validated content instead of preserving unknown bytes.
            JSONObject json = new JSONObject();
            json.put("format", 1);
            json.put("id", pack.id);
            json.put("name", pack.name);
            json.put("author", pack.author);
            json.put("base", pack.base);
            json.put("colors", pack.colors);
            JSONObject shapes = new JSONObject();
            shapes.put("panelRadiusDp", pack.panelRadiusDp);
            shapes.put("cardRadiusDp", pack.cardRadiusDp);
            shapes.put("focusBorderDp", pack.focusBorderDp);
            shapes.put("spacingDp", pack.spacingDp);
            json.put("shapes", shapes);
            json.put("animationScale", (double) pack.animationScale);
            try (FileOutputStream output = new FileOutputStream(temp)) {
                output.write(json.toString().getBytes(StandardCharsets.UTF_8));
                output.getFD().sync();
            }
            if (target.exists() && !target.delete()) {
                throw new java.io.IOException("Cannot replace old theme");
            }
            if (!temp.renameTo(target)) throw new java.io.IOException("Theme save failed");
            return pack;
        } catch (JSONException e) {
            throw new IllegalArgumentException("Could not encode theme", e);
        } finally {
            if (temp.exists()) temp.delete();
        }
    }
}
