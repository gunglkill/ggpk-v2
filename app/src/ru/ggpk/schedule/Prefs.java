package ru.ggpk.schedule;

import android.content.Context;
import android.content.SharedPreferences;

public final class Prefs {
    private static final String FILE = "ggpk_prefs";

    public static SharedPreferences get(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static boolean notifyOn(Context c) {
        return get(c).getBoolean("notify", false);
    }

    public static void setNotify(Context c, boolean v) {
        get(c).edit().putBoolean("notify", v).apply();
    }

    public static int sound(Context c) {
        return get(c).getInt("sound", 1);
    }

    public static void setSound(Context c, int v) {
        get(c).edit().putInt("sound", v).apply();
    }

    public static String theme(Context c) {
        return get(c).getString("theme", "dark");
    }

    public static void setTheme(Context c, String v) {
        get(c).edit().putString("theme", v).apply();
    }

    public static String group(Context c) {
        return get(c).getString("group", "");
    }

    public static void setGroup(Context c, String v) {
        get(c).edit().putString("group", v).apply();
    }

    public static boolean updateOn(Context c) {
        return get(c).getBoolean("update_notify", true);
    }

    public static void setUpdateOn(Context c, boolean v) {
        get(c).edit().putBoolean("update_notify", v).apply();
    }

    public static String version(Context c) {
        return get(c).getString("version", "");
    }

    public static void setVersion(Context c, String v) {
        get(c).edit().putString("version", v).apply();
    }

    public static boolean onboarded(Context c) {
        return get(c).getBoolean("onboarded", false);
    }

    public static void setOnboarded(Context c, boolean v) {
        get(c).edit().putBoolean("onboarded", v).apply();
    }

    public static boolean background(Context c) {
        return get(c).getBoolean("background", false);
    }

    public static void setBackground(Context c, boolean v) {
        get(c).edit().putBoolean("background", v).apply();
    }

    public static String dlSize(Context c) {
        return get(c).getString("dl_size", "");
    }

    public static void setDlSize(Context c, String v) {
        get(c).edit().putString("dl_size", v).apply();
    }

    public static String prefetchDay(Context c) {
        return get(c).getString("prefetch_day", "");
    }

    public static void setPrefetchDay(Context c, String v) {
        get(c).edit().putString("prefetch_day", v).apply();
    }
}
