package com.lpmmga.controlejornada;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;


import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.util.Calendar;
import java.util.TimeZone;

public class MainActivity extends android.app.Activity {
    public static final String CHANNEL_ID = "jornada_alertas";
    public static final int REQ_NOTIFICATIONS = 1001;
    private WebView webView;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        createNotificationChannel();
        requestNotificationPermission();
        requestExactAlarmPermission();

        webView = new WebView(this);
        webView.setBackgroundColor(0xFF111827);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setAllowFileAccess(true);
        webView.getSettings().setAllowContentAccess(true);
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new AndroidBridge(this), "AndroidBridge");
        setContentView(webView);
        webView.loadUrl("file:///android_asset/index.html");
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
        }
    }

    private void requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= 31) {
            AlarmManager am = (AlarmManager)getSystemService(ALARM_SERVICE);
            if (am != null && !am.canScheduleExactAlarms()) {
                try {
                    Intent i = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                    i.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(i);
                } catch (Exception ignored) {}
            }
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Alertas de jornada", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Alertas preventivos antes de completar 2 horas extras");
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 300, 180, 300, 180, 500});
            channel.setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI, null);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    public static class AndroidBridge {
        private final Context context;
        AndroidBridge(Context c) { context = c.getApplicationContext(); }

        @JavascriptInterface
        public void scheduleJourneyAlarms(String dbJson) {
            context.getSharedPreferences("jornada_native", MODE_PRIVATE)
                    .edit().putString("db", dbJson).apply();
            AlarmScheduler.schedule(context, dbJson);
        }
    }

    static class AlarmScheduler {
        static final int REQUEST_10 = 21010;
        static final int REQUEST_5 = 21005;

        static void cancel(Context context) {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            for (int req : new int[]{REQUEST_10, REQUEST_5}) {
                PendingIntent pi = PendingIntent.getBroadcast(context, req,
                        new Intent(context, JourneyAlarmReceiver.class),
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                am.cancel(pi);
            }
        }

        static void schedule(Context context, String json) {
            cancel(context);
            try {
                JSONObject db = new JSONObject(json);
                JSONObject days = db.optJSONObject("days");
                if (days == null) return;
                String key = LocalDate.now().toString();
                JSONObject day = days.optJSONObject(key);
                if (day == null) return;
                String type = day.optString("type", "normal");
                if (!"normal".equals(type)) return;
                int dow = LocalDate.now().getDayOfWeek().getValue(); // Mon=1..Sun=7
                int normal = dow == 6 ? 240 : 480;
                if (dow >= 7) return;
                JSONArray events = day.optJSONArray("events");
                if (events == null) return;
                String entry = null;
                int lunch = 0, ints = 0;
                for (int i=0;i<events.length();i++) {
                    JSONObject e=events.optJSONObject(i); if(e==null) continue;
                    String et=e.optString("type"); String tm=e.optString("time");
                    if("entry".equals(et)) entry=tm;
                }
                if (entry == null || entry.length() < 5) return;

                // Pair lunch and interruption events by chronological order, matching the web app's logic.
                java.util.ArrayList<Integer> lunchStarts = new java.util.ArrayList<>();
                java.util.ArrayList<Integer> lunchEnds = new java.util.ArrayList<>();
                java.util.ArrayList<Integer> intStarts = new java.util.ArrayList<>();
                java.util.ArrayList<Integer> intEnds = new java.util.ArrayList<>();
                for (int i=0;i<events.length();i++) {
                    JSONObject e=events.optJSONObject(i); if(e==null) continue;
                    int m=parseTime(e.optString("time"));
                    switch(e.optString("type")) {
                        case "lunch_start": lunchStarts.add(m); break;
                        case "lunch_end": lunchEnds.add(m); break;
                        case "int_start": intStarts.add(m); break;
                        case "int_end": intEnds.add(m); break;
                    }
                }
                java.util.Collections.sort(lunchStarts); java.util.Collections.sort(lunchEnds);
                java.util.Collections.sort(intStarts); java.util.Collections.sort(intEnds);
                lunch=pairTotal(lunchStarts,lunchEnds);
                ints=pairTotal(intStarts,intEnds);

                int entryMin=parseTime(entry);
                long base = entryMin + normal + lunch + ints;
                scheduleOne(context, REQUEST_10, 10, base + 110);
                scheduleOne(context, REQUEST_5, 5, base + 115);
            } catch (Exception ignored) {}
        }

        static int pairTotal(java.util.ArrayList<Integer> starts, java.util.ArrayList<Integer> ends) {
            int total=0,j=0;
            for(int s:starts){ while(j<ends.size() && ends.get(j)<=s) j++; if(j<ends.size()){ total += ends.get(j)-s; j++; } }
            return total;
        }
        static int parseTime(String s) { try { return Integer.parseInt(s.substring(0,2))*60+Integer.parseInt(s.substring(3,5)); } catch(Exception e){return -1;} }

        static void scheduleOne(Context context, int requestCode, int level, int targetMinutes) {
            if (targetMinutes < 0) return;
            int dayOffset = targetMinutes / 1440;
            int target = targetMinutes % 1440;
            Calendar cal = Calendar.getInstance(TimeZone.getDefault());
            cal.set(Calendar.HOUR_OF_DAY, target / 60);
            cal.set(Calendar.MINUTE, target % 60);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            if (dayOffset > 0) cal.add(Calendar.DAY_OF_MONTH, dayOffset);
            if (cal.getTimeInMillis() <= System.currentTimeMillis()) return;
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            Intent i = new Intent(context, JourneyAlarmReceiver.class).putExtra("level", level);
            PendingIntent pi = PendingIntent.getBroadcast(context, requestCode, i,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            if (Build.VERSION.SDK_INT >= 31 && am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
            }
        }

    }
}
