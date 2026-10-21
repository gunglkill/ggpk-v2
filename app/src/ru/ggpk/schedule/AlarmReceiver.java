package ru.ggpk.schedule;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        if (i != null && i.getBooleanExtra("check", false)) {
            final PendingResult pr = goAsync();
            Notify.checkUpdate(c, new Runnable() {
                public void run() {
                    pr.finish();
                }
            });
            return;
        }
        if (i != null && i.getBooleanExtra("daily", false)) {
            Notify.reschedule(c);
            return;
        }
        String title = i == null ? null : i.getStringExtra("title");
        String text = i == null ? "" : i.getStringExtra("text");
        int id = i == null ? 0 : i.getIntExtra("id", 0);
        if (title != null) {
            Notify.show(c, title, text == null ? "" : text, id);
        }
    }
}
