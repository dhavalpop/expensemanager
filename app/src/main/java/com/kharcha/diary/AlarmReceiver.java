package com.kharcha.diary;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AlarmReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String id = intent.getStringExtra("id");
        String name = intent.getStringExtra("name");
        String kind = intent.getStringExtra("kind");
        String due = intent.getStringExtra("due");
        double amt = intent.getDoubleExtra("amt", 0);
        int lead = intent.getIntExtra("lead", 0);
        if (name == null) name = "Payment reminder";

        String when;
        if (lead <= 0) when = "Due today";
        else if (lead == 1) when = "Due tomorrow";
        else when = "Due in " + lead + " days";

        StringBuilder text = new StringBuilder(when);
        if (amt > 0) {
            NumberFormat nf = NumberFormat.getNumberInstance(new Locale("en", "IN"));
            nf.setMaximumFractionDigits(0);
            text.append(" · ₹").append(nf.format(amt));
        }
        if (due != null) {
            try {
                Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(due);
                if (d != null) {
                    text.append(" · ").append(new SimpleDateFormat("d MMM yyyy", Locale.ENGLISH).format(d));
                }
            } catch (ParseException ignored) {
            }
        }

        int notifId = (id + "|" + due + "|" + lead).hashCode();
        Notifier.show(context, kind, name, text.toString(), notifId);
    }
}
