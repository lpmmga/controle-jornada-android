package com.lpmmga.controlejornada;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;


public class JourneyAlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        int level = intent.getIntExtra("level", 5);
        String title;
        String text;
        if (level == 10) {
            title = "⚠️ Faltam 10 minutos";
            text = "Faltam 10 minutos para atingir 2h de hora extra. Vá se preparar para bater o ponto.";
        } else {
            title = "🚨 Faltam 5 minutos";
            text = "Faltam 5 minutos para atingir 2h de hora extra. Atenção para bater o ponto.";
        }

        Intent open = new Intent(context, MainActivity.class);
        PendingIntent content = PendingIntent.getActivity(context, 3000 + level, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        android.app.Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            b = new android.app.Notification.Builder(context, MainActivity.CHANNEL_ID);
        } else {
            b = new android.app.Notification.Builder(context);
        }
        b.setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new android.app.Notification.BigTextStyle().bigText(text))
                .setPriority(android.app.Notification.PRIORITY_HIGH)
                .setCategory(android.app.Notification.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(content)
                .setDefaults(android.app.Notification.DEFAULT_SOUND | android.app.Notification.DEFAULT_VIBRATE);

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(4000 + level, b.build());

        Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null && v.hasVibrator()) {
            long[] pattern = level == 10 ? new long[]{0,300,180,300} : new long[]{0,500,200,500,200,500};
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createWaveform(pattern, -1));
            else v.vibrate(pattern, -1);
        }
    }
}
