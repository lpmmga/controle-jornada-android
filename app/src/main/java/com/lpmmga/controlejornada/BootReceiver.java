package com.lpmmga.controlejornada;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String db = context.getSharedPreferences("jornada_native", Context.MODE_PRIVATE).getString("db", null);
        if (db != null) MainActivity.AlarmScheduler.schedule(context, db);
    }
}
