package com.bloqueo.mobile;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Set;
import java.util.Calendar;

final class Prefs {
    private static final String STORE = "bloqueo_mobile_0_1";
    static final String END = "session_end";
    static final String START = "session_start";
    static final String DARK = "dark";
    static final String COMPANION = "companion";
    static final String COMPANION_NAME = "companion_name";
    static final String PENDING_USAGE_SELECTION = "pending_usage_selection";
    static final String ONBOARDED = "onboarded";
    static final String MODE = "mode";
    static final String DURATION = "duration";
    static final String SCHEDULE = "schedule";
    static final String START_MINUTE = "start_minute";
    static final String END_MINUTE = "end_minute";
    static final String DAYS = "schedule_days";
    static final String ALLOW_CHAT = "allow_chat";
    private static final String BLOCKED = "blocked_packages";

    private Prefs() { }

    static SharedPreferences of(Context c) {
        return c.getSharedPreferences(STORE, Context.MODE_PRIVATE);
    }

    static Set<String> blocked(Context c) {
        return new HashSet<>(of(c).getStringSet(BLOCKED, new HashSet<>()));
    }

    static void setBlocked(Context c, Set<String> ids) {
        of(c).edit().putStringSet(BLOCKED, new HashSet<>(ids)).apply();
    }

    static boolean active(Context c) {
        SharedPreferences prefs = of(c);
        if (!prefs.getBoolean(ONBOARDED, false)) return false;
        String mode = prefs.getString(SCHEDULE, "session");
        if ("always".equals(mode)) return true;
        Calendar cal = Calendar.getInstance();
        int day = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7; // Mon=0 ... Sun=6
        if ("weekdays".equals(mode)) return day < 5;
        if ("custom".equals(mode)) {
            int start = prefs.getInt(START_MINUTE, 8 * 60);
            int end = prefs.getInt(END_MINUTE, 22 * 60);
            int mask = prefs.getInt(DAYS, 0x7f);
            int minute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
            if (start == end) return false;
            if (start < end) return (mask & (1 << day)) != 0 && minute >= start && minute < end;
            // Overnight window: the after-midnight portion belongs to the previous day.
            if (minute >= start) return (mask & (1 << day)) != 0;
            int previous = (day + 6) % 7;
            return minute < end && (mask & (1 << previous)) != 0;
        }
        return prefs.getLong(END, 0) > System.currentTimeMillis();
    }

    static boolean chatAllowed(Context c) {
        return of(c).getBoolean(ALLOW_CHAT, false);
    }

    static void setSchedule(Context c, String mode) {
        of(c).edit().putString(SCHEDULE, mode).putBoolean(ONBOARDED, true)
                .remove(START).remove(END).apply();
    }

    static void start(Context c, int minutes) {
        long now = System.currentTimeMillis();
        of(c).edit().putLong(START, now).putLong(END, now + minutes * 60_000L)
            .putInt(DURATION, minutes).putString(SCHEDULE, "session")
            .putBoolean(ONBOARDED, true).apply();
    }

    static void stop(Context c) {
        of(c).edit().remove(END).remove(START).putString(SCHEDULE, "paused").apply();
    }
}
