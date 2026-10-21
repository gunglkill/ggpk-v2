package ru.ggpk.schedule;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.Calendar;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        Notify.reschedule(c);
        Notify.scheduleChecks(c);
        Notify.checkUpdate(c, null);
        try {
            android.app.AlarmManager am =
                    (android.app.AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            Calendar next = Calendar.getInstance();
            next.add(Calendar.DAY_OF_YEAR, 1);
            next.set(Calendar.HOUR_OF_DAY, 5);
            next.set(Calendar.MINUTE, 30);
            next.set(Calendar.SECOND, 0);
            Intent daily = new Intent(c, AlarmReceiver.class);
            daily.putExtra("daily", true);
            android.app.PendingIntent pi = android.app.PendingIntent.getBroadcast(c, 9999, daily,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT
                            | android.app.PendingIntent.FLAG_IMMUTABLE);
            am.set(android.app.AlarmManager.RTC_WAKEUP, next.getTimeInMillis(), pi);
        } catch (Exception ignored) {
        }
    }
}
