package com.kharcha.diary;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Alarms are cleared when the phone restarts or the clock changes, so schedule them again. */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String json = context.getSharedPreferences(Scheduler.PREFS, Context.MODE_PRIVATE).getString("json", null);
        if (json != null) {
            Scheduler.schedule(context, json);
        }
    }
}
