package ru.ggpk.schedule;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Первый запуск: предложение включить фоновый режим. */
public final class Dialogs {

    private static final int CARD = 0xFF151515;
    private static final int LINE = 0xFF2E2E2E;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFF8B8B8B;

    private Dialogs() {
    }

    private static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    private static Dialog dialog(Activity a) {
        Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        if (d.getWindow() != null) {
            d.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        return d;
    }

    private static LinearLayout card(Context c) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(c, 20));
        bg.setStroke(dp(c, 1), LINE);
        box.setBackground(bg);
        box.setPadding(dp(c, 22), dp(c, 20), dp(c, 22), dp(c, 18));
        return box;
    }

    private static TextView title(Context c, String s) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextColor(TEXT);
        t.setTextSize(18);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private static TextView body(Context c, String s) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextColor(MUTED);
        t.setTextSize(13.5f);
        t.setLineSpacing(dp(c, 3), 1f);
        t.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, 10);
        lp.bottomMargin = dp(c, 18);
        t.setLayoutParams(lp);
        return t;
    }

    private static Button primary(Context c, String s) {
        Button b = new Button(c);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTextColor(Color.BLACK);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(c, 14));
        b.setBackground(bg);
        b.setPadding(0, dp(c, 13), 0, dp(c, 13));
        return b;
    }

    private static Button ghost(Context c, String s) {
        Button b = new Button(c);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTextColor(MUTED);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.TRANSPARENT);
        bg.setCornerRadius(dp(c, 14));
        bg.setStroke(dp(c, 1), LINE);
        b.setBackground(bg);
        b.setPadding(0, dp(c, 12), 0, dp(c, 12));
        return b;
    }

    /** Включает фоновый режим: уведомления, проверки, исключение из оптимизации батареи. */
    static void enableBackground(Activity a) {
        Prefs.setBackground(a, true);
        Prefs.setNotify(a, true);
        Notify.ensureChannels(a);
        Notify.scheduleChecks(a);
        Notify.reschedule(a);

        // 1) уведомления (Android 13+)
        if (Build.VERSION.SDK_INT >= 33) {
            if (a.checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                a.requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 11);
            }
        }

        // 2) точные будильники (Android 12+)
        if (Build.VERSION.SDK_INT >= 31) {
            android.app.AlarmManager am =
                    (android.app.AlarmManager) a.getSystemService(Context.ALARM_SERVICE);
            if (am != null && !am.canScheduleExactAlarms()) {
                try {
                    a.startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:" + a.getPackageName())));
                } catch (Exception ignored) {
                }
            }
        }

        // 3) работа в фоне без остановки системой
        if (Build.VERSION.SDK_INT >= 23) {
            try {
                PowerManager pm = (PowerManager) a.getSystemService(Context.POWER_SERVICE);
                if (pm != null && !pm.isIgnoringBatteryOptimizations(a.getPackageName())) {
                    Intent it = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    it.setData(Uri.parse("package:" + a.getPackageName()));
                    a.startActivity(it);
                }
            } catch (Exception e) {
                // если производитель заблокировал — открываем общий экран настроек батареи
                try {
                    Intent it = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                    a.startActivity(it);
                } catch (Exception ignored) {
                }
            }
        }
    }

    /** Первое окно: предложение. */
    public static void offerBackground(final Activity a) {
        final Dialog d = dialog(a);
        LinearLayout box = card(a);

        box.addView(title(a, "Фоновый режим"));
        box.addView(body(a, "Чтобы не пропустить новое расписание, приложению нужно работать в фоне: "
                + "проверять обновления и присылать уведомления о начале пар.\n\n"
                + "Включить фоновый режим?"));

        Button yes = primary(a, "Включить");
        yes.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Prefs.setOnboarded(a, true);
                enableBackground(a);
                d.dismiss();
            }
        });
        box.addView(yes, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button later = ghost(a, "Позже");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(a, 8);
        later.setLayoutParams(lp);
        later.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Prefs.setOnboarded(a, true);
                d.dismiss();
                showLater(a);
            }
        });
        box.addView(later);

        d.setContentView(box);
        d.setCancelable(false);
        d.show();
    }

    /** Второе окно: «можно включить позже», закрывается через 3 секунды. */
    private static void showLater(final Activity a) {
        final Dialog d = dialog(a);
        LinearLayout box = card(a);

        box.addView(title(a, "Можно включить позже"));
        box.addView(body(a, "Фоновый режим включается в любой момент:\nНастройки → Фоновый режим.\n\n"
                + "Пока он выключен, приложение не проверяет обновления, пока вы его не откроете."));

        final Button close = ghost(a, "Закрыть · 3");
        close.setEnabled(false);
        close.setTextColor(0xFF5A5A5A);
        box.addView(close);

        d.setContentView(box);
        d.setCancelable(false);
        d.show();

        final Handler h = new Handler(Looper.getMainLooper());
        final int[] left = {3};
        Runnable tick = new Runnable() {
            public void run() {
                left[0]--;
                if (left[0] <= 0) {
                    close.setText("Закрыть");
                    close.setTextColor(TEXT);
                    close.setEnabled(true);
                    return;
                }
                close.setText("Закрыть · " + left[0]);
                h.postDelayed(this, 1000);
            }
        };
        h.postDelayed(tick, 1000);

        close.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                d.dismiss();
            }
        });
    }
}
