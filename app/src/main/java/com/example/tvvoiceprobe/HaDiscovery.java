package com.example.tvvoiceprobe;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.provider.Settings;

/**
 * Advertises this TV so Home Assistant can offer "Add". Nothing listens on the
 * advertised port. Layout changes travel back over the existing Assist login.
 */
public final class HaDiscovery {
    static final String SERVICE_TYPE = "_kellies-voice._tcp.";
    private static final int UNUSED_PORT = 45600;
    private static int holders;
    private static boolean wanted;
    private static NsdManager.RegistrationListener registration;

    private HaDiscovery() {}

    public static synchronized void setWanted(Context context, boolean value) {
        wanted = value;
        update(context.getApplicationContext());
    }

    public static synchronized void acquire(Context context) {
        holders++;
        update(context.getApplicationContext());
    }

    public static synchronized void release(Context context) {
        if (holders > 0) holders--;
        update(context.getApplicationContext());
    }

    private static void update(Context context) {
        boolean should = wanted && holders > 0;
        if (!should) {
            unregister(context);
            return;
        }
        if (registration != null) return;
        NsdManager nsd = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
        if (nsd == null) return;
        OverlayPreferences prefs = new OverlayPreferences(context);
        NsdServiceInfo info = new NsdServiceInfo();
        info.setServiceName(serviceName(context, prefs.deviceId()));
        info.setServiceType(SERVICE_TYPE);
        info.setPort(UNUSED_PORT);
        info.setAttribute("id", prefs.deviceId());
        info.setAttribute("name", displayName(context));
        NsdManager.RegistrationListener listener = new NsdManager.RegistrationListener() {
            @Override public void onServiceRegistered(NsdServiceInfo serviceInfo) { }
            @Override public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                synchronized (HaDiscovery.class) {
                    if (registration == this) registration = null;
                }
            }
            @Override public void onServiceUnregistered(NsdServiceInfo serviceInfo) { }
            @Override public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) { }
        };
        try {
            nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener);
            registration = listener;
        } catch (Exception ignored) {
            registration = null;
        }
    }

    private static void unregister(Context context) {
        NsdManager.RegistrationListener listener = registration;
        registration = null;
        if (listener == null) return;
        NsdManager nsd = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
        if (nsd == null) return;
        try {
            nsd.unregisterService(listener);
        } catch (Exception ignored) { }
    }

    private static String displayName(Context context) {
        String name = Settings.Global.getString(context.getContentResolver(), Settings.Global.DEVICE_NAME);
        name = name == null ? "Kellie's Voice" : name.trim();
        if (name.isEmpty()) name = "Kellie's Voice";
        // Distinguish the permanent QA app from the installed release/debug
        // instances in the Home Assistant discovery list.
        return BuildConfig.IS_QA ? name + " (QA)" : name;
    }

    private static String serviceName(Context context, String deviceId) {
        String safe = displayName(context).replaceAll("[^A-Za-z0-9 ._-]", "").trim();
        if (safe.isEmpty()) safe = "Kellies Voice";
        if (safe.length() > 28) safe = safe.substring(0, 28).trim();
        String suffix = deviceId == null || deviceId.length() < 4 ? "tv" : deviceId.substring(0, 4);
        return safe + " " + suffix;
    }
}
