package com.kharcha.diary;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;

/**
 * One notification channel per reminder type. A channel's sound cannot be changed once it exists,
 * so changing a sound creates a new channel (version number goes up) and removes the old one.
 */
public final class Sounds {

    static final String[] KINDS = {
            "SIP", "NPS", "LIC premium", "Mediclaim",
            "Car insurance", "Bike insurance", "Education fee", "Other"
    };

    private Sounds() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("sounds", Context.MODE_PRIVATE);
    }

    static String slug(String kind) {
        if (kind == null) return "other";
        switch (kind) {
            case "SIP": return "sip";
            case "NPS": return "nps";
            case "LIC premium": return "lic";
            case "Mediclaim": return "mediclaim";
            case "Car insurance": return "car";
            case "Bike insurance": return "bike";
            case "Education fee": return "education";
            default: return "other";
        }
    }

    static Uri uri(Context c, String kind) {
        String saved = prefs(c).getString("uri_" + slug(kind), null);
        if (saved != null) return Uri.parse(saved);
        int res = c.getResources().getIdentifier("tone_" + slug(kind), "raw", c.getPackageName());
        return Uri.parse("android.resource://" + c.getPackageName() + "/" + res);
    }

    static boolean isCustom(Context c, String kind) {
        return prefs(c).contains("uri_" + slug(kind));
    }

    static String name(Context c, String kind) {
        if (!isCustom(c, kind)) return "Kharcha chime";
        try {
            Ringtone r = RingtoneManager.getRingtone(c, uri(c, kind));
            if (r != null) {
                String t = r.getTitle(c);
                if (t != null && !t.isEmpty()) return t;
            }
        } catch (Exception ignored) {
        }
        return "Custom sound";
    }

    static String channelId(Context c, String kind) {
        return "kd_" + slug(kind) + "_" + prefs(c).getInt("ver_" + slug(kind), 0);
    }

    static void ensureChannel(Context c, String kind) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        String id = channelId(c, kind);
        if (nm.getNotificationChannel(id) == null) {
            NotificationChannel ch = new NotificationChannel(id, kind + " reminders", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Payment reminders for " + kind);
            AudioAttributes aa = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            ch.setSound(uri(c, kind), aa);
            ch.enableVibration(true);
            ch.setVibrationPattern(new long[]{0, 300, 150, 300});
            ch.setShowBadge(true);
            nm.createNotificationChannel(ch);
        }
        String prefix = "kd_" + slug(kind) + "_";
        for (NotificationChannel x : nm.getNotificationChannels()) {
            if (x.getId().startsWith(prefix) && !x.getId().equals(id)) {
                nm.deleteNotificationChannel(x.getId());
            }
        }
    }

    static void set(Context c, String kind, Uri u) {
        String s = slug(kind);
        SharedPreferences p = prefs(c);
        p.edit().putString("uri_" + s, u.toString()).putInt("ver_" + s, p.getInt("ver_" + s, 0) + 1).apply();
        ensureChannel(c, kind);
    }

    static void reset(Context c, String kind) {
        String s = slug(kind);
        SharedPreferences p = prefs(c);
        p.edit().remove("uri_" + s).putInt("ver_" + s, p.getInt("ver_" + s, 0) + 1).apply();
        ensureChannel(c, kind);
    }
}
