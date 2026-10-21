package ru.ggpk.schedule;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Уведомления о начале пар и переменах по фактическому расписанию дня.
 * Тексты повторяют бота.
 */
public final class Notify {

    static final String BASE = "https://gunglkill.github.io/ggpk/data/g/";
    private static final int MAX_ALARMS = 60;

    private Notify() {
    }

    // ---------- каналы ----------
    static String channelId(int sound) {
        return "ggpk_s" + Math.max(1, Math.min(3, sound));
    }

    static void ensureChannels(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        for (int i = 1; i <= 3; i++) {
            String id = channelId(i);
            if (nm.getNotificationChannel(id) != null) continue;
            NotificationChannel ch = new NotificationChannel(id, "Звонки · звук " + i,
                    NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Начало пары и перемены");
            ch.enableVibration(true);
            Uri snd = Uri.parse("android.resource://" + c.getPackageName() + "/raw/sound" + i);
            ch.setSound(snd, new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            nm.createNotificationChannel(ch);
        }
    }

    static void show(Context c, String title, String text, int id) {
        ensureChannels(c);
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        Intent open = new Intent(c, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, id, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(c, channelId(Prefs.sound(c)))
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build();
        nm.notify(1000 + id, n);
    }

    // ---------- время ----------
    private static int toMin(String t) {
        try {
            String[] p = t.replace("\u2013", "-").split(":");
            return Integer.parseInt(p[0].trim()) * 60 + Integer.parseInt(p[1].trim());
        } catch (Exception e) {
            return -1;
        }
    }

    static String plural(int n, String one, String few, String many) {
        int m = Math.abs(n) % 100;
        if (m >= 11 && m <= 19) return many;
        m %= 10;
        if (m == 1) return one;
        if (m >= 2 && m <= 4) return few;
        return many;
    }

    // ---------- события дня ----------
    static final class Ev {
        final long at;
        final String title;
        final String text;

        Ev(long at, String title, String text) {
            this.at = at;
            this.title = title;
            this.text = text;
        }
    }

    /** Разбирает "8:30–9:15<br>9:20–10:05" в массив пар начало/конец. */
    private static List<int[]> slots(String raw) {
        List<int[]> out = new ArrayList<>();
        if (raw == null) return out;
        String[] parts = raw.replace("\u2013", "-").split("<br>");
        for (String p : parts) {
            String[] se = p.trim().split("-");
            if (se.length < 2) continue;
            int a = toMin(se[0]);
            int b = toMin(se[1]);
            if (a >= 0 && b >= 0) out.add(new int[]{a, b});
        }
        return out;
    }

    static List<Ev> dayEvents(JSONObject day, long baseMillis) {
        List<Ev> ev = new ArrayList<>();
        try {
            JSONArray rows = day.getJSONArray("rows");
            List<Object[]> list = new ArrayList<>();
            for (int i = 0; i < rows.length(); i++) {
                JSONArray r = rows.getJSONArray(i);
                int num;
                try {
                    num = Integer.parseInt(r.getString(0));
                } catch (Exception e) {
                    continue;
                }
                if (num < 1) continue;
                String room = r.isNull(3) ? "" : r.getString(3);
                List<int[]> sl = slots(r.getString(1));
                if (sl.isEmpty()) continue;
                list.add(new Object[]{num, sl, room});
            }
            for (int i = 0; i < list.size(); i++) {
                int num = (Integer) list.get(i)[0];
                @SuppressWarnings("unchecked")
                List<int[]> sl = (List<int[]>) list.get(i)[1];
                String room = (String) list.get(i)[2];
                String body = room == null || room.isEmpty() ? "" : "\n\n📍 " + room;

                int s1 = sl.get(0)[0], e1 = sl.get(0)[1];
                ev.add(new Ev(baseMillis + s1 * 60000L,
                        "🔔 " + num + " пара · " + hhmm(s1), body.trim()));

                int lastEnd = e1;
                if (sl.size() > 1) {
                    int s2 = sl.get(1)[0], e2 = sl.get(1)[1];
                    int d = s2 - e1;
                    if (d > 0) {
                        ev.add(new Ev(baseMillis + e1 * 60000L,
                                "🕒 Перемена · " + d + " " + plural(d, "минута", "минуты", "минут"),
                                "♦️ Следующий урок в " + hhmm(s2)));
                    }
                    ev.add(new Ev(baseMillis + s2 * 60000L,
                            "🔔 " + num + " пара · 2 урок · " + hhmm(s2), body.trim()));
                    lastEnd = e2;
                }
                if (i + 1 < list.size()) {
                    @SuppressWarnings("unchecked")
                    List<int[]> nxt = (List<int[]>) list.get(i + 1)[1];
                    int ns = nxt.get(0)[0];
                    int d = ns - lastEnd;
                    if (d > 0) {
                        ev.add(new Ev(baseMillis + lastEnd * 60000L,
                                "🕒 Перемена · " + d + " " + plural(d, "минута", "минуты", "минут"),
                                "♦️ Следующая пара в " + hhmm(ns)));
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return ev;
    }

    private static String hhmm(int min) {
        return String.format(Locale.US, "%d:%02d", min / 60, min % 60);
    }

    // ---------- сеть ----------
    private static String fetch(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setRequestProperty("User-Agent", "GGPKApp/1.0");
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
        } finally {
            c.disconnect();
        }
        return sb.toString();
    }

    // ---------- планирование ----------
    static void reschedule(final Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        ensureChannels(ctx);
        for (int i = 0; i < MAX_ALARMS; i++) {
            am.cancel(alarmPi(ctx, i));
        }
        if (!Prefs.notifyOn(ctx)) return;
        final String group = Prefs.group(ctx);
        if (group.isEmpty()) return;

        new Thread(new Runnable() {
            public void run() {
                try {
                    String json = fetch(BASE + group + ".json?v=" + System.currentTimeMillis());
                    JSONObject root = new JSONObject(json);
                    JSONObject days = root.getJSONObject("days");
                    SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                    List<Ev> all = new ArrayList<>();
                    for (int d = 0; d <= 1; d++) {
                        Calendar cal = Calendar.getInstance();
                        cal.add(Calendar.DAY_OF_YEAR, d);
                        cal.set(Calendar.HOUR_OF_DAY, 0);
                        cal.set(Calendar.MINUTE, 0);
                        cal.set(Calendar.SECOND, 0);
                        cal.set(Calendar.MILLISECOND, 0);
                        String key = f.format(cal.getTime());
                        if (!days.has(key)) continue;
                        all.addAll(dayEvents(days.getJSONObject(key), cal.getTimeInMillis()));
                    }
                    AlarmManager am2 = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
                    if (am2 == null) return;
                    long now = System.currentTimeMillis();
                    int id = 0;
                    for (Ev e : all) {
                        if (e.at <= now + 5000) continue;
                        if (id >= MAX_ALARMS) break;
                        Intent it = new Intent(ctx, AlarmReceiver.class);
                        it.putExtra("title", e.title);
                        it.putExtra("text", e.text);
                        it.putExtra("id", id);
                        PendingIntent pi = PendingIntent.getBroadcast(ctx, id, it,
                                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                        try {
                            am2.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, e.at, pi);
                        } catch (SecurityException se) {
                            am2.set(AlarmManager.RTC_WAKEUP, e.at, pi);
                        }
                        id++;
                    }
                } catch (Exception ignored) {
                }
            }
        }, "ggpk-notify").start();
    }

    static PendingIntent alarmPi(Context ctx, int id) {
        Intent it = new Intent(ctx, AlarmReceiver.class);
        return PendingIntent.getBroadcast(ctx, id, it,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void test(Context c) {
        show(c, "🔔 1 пара · 8:30", "📍 222Ш", 0);
    }

    // ---------- проверка нового расписания ----------
    static final String VERSION_URL = "https://gunglkill.github.io/ggpk/version.txt";

    /** Раз в полчаса: если метка сборки на сайте изменилась — пуш о новом расписании. */
    static void checkUpdate(final Context ctx, final Runnable done) {
        if (!Prefs.background(ctx)) {
            if (done != null) done.run();
            return;
        }
        final String last = Prefs.version(ctx);
        new Thread(new Runnable() {
            public void run() {
                try {
                    String v = fetch(VERSION_URL + "?t=" + System.currentTimeMillis()).trim();
                    if (!v.isEmpty() && !v.equals(last)) {
                        Prefs.setVersion(ctx, v);
                        if (!last.isEmpty() && Prefs.notifyOn(ctx) && Prefs.updateOn(ctx)) {
                            show(ctx, "🔄 Новое расписание",
                                    "Расписание обновилось. Откройте приложение, чтобы посмотреть.", 900);
                        }
                        if (!last.isEmpty()) reschedule(ctx);
                    }
                } catch (Exception ignored) {
                } finally {
                    if (done != null) done.run();
                }
            }
        }, "ggpk-version").start();
    }

    /** Ставит периодическую проверку обновлений (раз в 30 минут). */
    static void cancelChecks(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent it = new Intent(c, AlarmReceiver.class);
        it.putExtra("check", true);
        PendingIntent pi = PendingIntent.getBroadcast(c, 8000, it,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.cancel(pi);
    }

    static void scheduleChecks(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        if (!Prefs.background(c)) {
            cancelChecks(c);
            return;
        }
        Intent it = new Intent(c, AlarmReceiver.class);
        it.putExtra("check", true);
        PendingIntent pi = PendingIntent.getBroadcast(c, 8000, it,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 5 * 60 * 1000L,
                30 * 60 * 1000L, pi);
    }
}
