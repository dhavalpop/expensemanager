package com.kharcha.diary;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Receives the reminder list from the web page and sets one exact alarm for every
 * "remind me N days before" choice of every upcoming due date.
 */
public final class Scheduler {

    static final String PREFS = "sched";
    private static final int MAX_ALARMS = 400;

    private Scheduler() {}

    static int hour(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("hour", 9);
    }

    static void setHour(Context c, int h) {
        SharedPreferences sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        sp.edit().putInt("hour", h).apply();
        String json = sp.getString("json", null);
        if (json != null) schedule(c, json);
    }

    static void schedule(Context c, String json) {
        SharedPreferences sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        sp.edit().putString("json", json).apply();
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        cancelAll(c, sp, am);

        JSONArray arr;
        try {
            arr = new JSONArray(json);
        } catch (JSONException e) {
            return;
        }

        int hour = sp.getInt("hour", 9);
        long now = System.currentTimeMillis();
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        JSONArray codes = new JSONArray();

        for (int i = 0; i < arr.length(); i++) {
            JSONObject r = arr.optJSONObject(i);
            if (r == null) continue;
            String id = r.optString("id");
            String name = r.optString("name");
            String kind = r.optString("kind", "Other");
            double amt = r.optDouble("amt", 0);
            JSONArray leads = r.optJSONArray("leads");
            JSONArray occ = r.optJSONArray("occ");
            if (leads == null || occ == null) continue;

            for (int o = 0; o < occ.length(); o++) {
                String d = occ.optString(o);
                Date due;
                try {
                    due = fmt.parse(d);
                } catch (Exception e) {
                    continue;
                }
                if (due == null) continue;

                for (int l = 0; l < leads.length(); l++) {
                    int lead = leads.optInt(l, -1);
                    if (lead < 0) continue;
                    if (codes.length() >= MAX_ALARMS) break;

                    Calendar cal = Calendar.getInstance();
                    cal.setTime(due);
                    cal.add(Calendar.DAY_OF_MONTH, -lead);
                    cal.set(Calendar.HOUR_OF_DAY, hour);
                    cal.set(Calendar.MINUTE, 0);
                    cal.set(Calendar.SECOND, 0);
                    cal.set(Calendar.MILLISECOND, 0);
                    long when = cal.getTimeInMillis();
                    if (when <= now) continue;

                    int code = (id + "|" + d + "|" + lead).hashCode();
                    Intent in = new Intent(c, AlarmReceiver.class);
                    in.putExtra("id", id);
                    in.putExtra("name", name);
                    in.putExtra("kind", kind);
                    in.putExtra("amt", amt);
                    in.putExtra("due", d);
                    in.putExtra("lead", lead);
                    PendingIntent pi = PendingIntent.getBroadcast(c, code, in,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                    setAlarm(am, when, pi);
                    codes.put(code);
                }
            }
        }
        sp.edit().putString("codes", codes.toString()).apply();
    }

    private static void setAlarm(AlarmManager am, long when, PendingIntent pi) {
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
            }
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
        }
    }

    private static void cancelAll(Context c, SharedPreferences sp, AlarmManager am) {
        String saved = sp.getString("codes", null);
        if (saved == null) return;
        try {
            JSONArray codes = new JSONArray(saved);
            for (int i = 0; i < codes.length(); i++) {
                int code = codes.getInt(i);
                PendingIntent pi = PendingIntent.getBroadcast(c, code, new Intent(c, AlarmReceiver.class),
                        PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
                if (pi != null) {
                    am.cancel(pi);
                    pi.cancel();
                }
            }
        } catch (JSONException ignored) {
        }
    }
}
