package ru.ggpk.schedule;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class SettingsActivity extends Activity {

    private static final int BG = 0xFF08080A;
    private static final int CARD = 0xFF111113;
    private static final int LINE = 0xFF262626;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFF8B8B8B;

    private LinearLayout soundBox;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        Notify.ensureChannels(this);
        setContentView(build());
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private View card() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1), LINE);
        box.setBackground(bg);
        box.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        box.setLayoutParams(lp);
        return box;
    }

    private TextView title(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(TEXT);
        t.setTextSize(15);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView note(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(MUTED);
        t.setTextSize(12.5f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(4);
        t.setLayoutParams(lp);
        return t;
    }

    private Button chip(String label, boolean on, View.OnClickListener click) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTextColor(on ? Color.BLACK : TEXT);
        b.setBackgroundColor(on ? Color.WHITE : CARD);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(on ? Color.WHITE : 0xFF151515);
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), on ? Color.WHITE : LINE);
        b.setBackground(bg);
        b.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.rightMargin = dp(8);
        b.setLayoutParams(lp);
        return b;
    }

    private View build() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(20), dp(18), dp(30));
        scroll.addView(root);

        TextView head = new TextView(this);
        head.setText("Настройки");
        head.setTextColor(TEXT);
        head.setTextSize(26);
        head.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(head);
        TextView sub = note("");
        root.addView(sub);

        // уведомления
        LinearLayout notifCard = (LinearLayout) card();
        LinearLayout notifTop = new LinearLayout(this);
        notifTop.setOrientation(LinearLayout.HORIZONTAL);
        notifTop.setGravity(Gravity.CENTER_VERTICAL);
        TextView nt = title("Уведомления");
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        notifTop.addView(nt, tlp);
        final Switch sw = new Switch(this);
        sw.setChecked(Prefs.notifyOn(this));
        sw.setOnCheckedChangeListener((btn, checked) -> {
            Prefs.setNotify(SettingsActivity.this, checked);
            if (checked) {
                askPermission();
                Notify.reschedule(SettingsActivity.this);
            } else {
                Notify.reschedule(SettingsActivity.this);
            }
        });
        notifTop.addView(sw);
        notifCard.addView(notifTop);
        notifCard.addView(note("Начало пары и перемены — те же сообщения, что в боте. "
                + "Работают по расписанию вашей группы."));

        LinearLayout updRow = new LinearLayout(this);
        updRow.setOrientation(LinearLayout.HORIZONTAL);
        updRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ulp.topMargin = dp(12);
        updRow.setLayoutParams(ulp);
        TextView ut = new TextView(this);
        ut.setText("Новое расписание");
        ut.setTextColor(TEXT);
        ut.setTextSize(14.5f);
        updRow.addView(ut, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final Switch usw = new Switch(this);
        usw.setChecked(Prefs.updateOn(this));
        usw.setOnCheckedChangeListener((btn, checked) -> {
            Prefs.setUpdateOn(SettingsActivity.this, checked);
            if (checked) Notify.checkUpdate(SettingsActivity.this, null);
        });
        updRow.addView(usw);
        notifCard.addView(updRow);
        notifCard.addView(note("Приложение раз в полчаса проверяет сайт и сообщает, "
                + "когда расписание обновилось."));
        root.addView(notifCard);

        // фоновый режим
        LinearLayout bgCard = (LinearLayout) card();
        bgCard.addView(title("Фоновый режим"));
        bgCard.addView(note("Приложение само проверяет обновления и присылает уведомления, "
                + "даже когда закрыто. При включении Android спросит разрешение на работу в фоне."));
        final Button bgBtn = new Button(this);
        bgBtn.setAllCaps(false);
        bgBtn.setTextSize(14.5f);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = dp(12);
        bgBtn.setLayoutParams(blp);
        styleBg(bgBtn);
        bgBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (Prefs.background(SettingsActivity.this)) {
                    Prefs.setBackground(SettingsActivity.this, false);
                    Notify.cancelChecks(SettingsActivity.this);
                } else {
                    Dialogs.enableBackground(SettingsActivity.this);
                }
                styleBg(bgBtn);
            }
        });
        bgCard.addView(bgBtn);
        root.addView(bgCard);

        // звук
        LinearLayout soundCard = (LinearLayout) card();
        soundCard.addView(title("Звук уведомления"));
        soundBox = new LinearLayout(this);
        soundBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = dp(8);
        soundBox.setLayoutParams(slp);
        soundCard.addView(soundBox);
        drawSounds();
        root.addView(soundCard);

        // кнопки
        Button test = chip("Проверить уведомление", false, new View.OnClickListener() {
            public void onClick(View v) {
                if (!Prefs.notifyOn(SettingsActivity.this)) {
                    Toast.makeText(SettingsActivity.this, "Сначала включите уведомления",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                Notify.test(SettingsActivity.this);
            }
        });
        root.addView(test);

        return scroll;
    }

    private void drawSounds() {
        soundBox.removeAllViews();
        int cur = Prefs.sound(this);
        String[] names = {"Тон", "iMessage", "Доставлено"};
        for (int i = 1; i <= 3; i++) {
            final int idx = i;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.topMargin = dp(6);
            row.setLayoutParams(rlp);
            row.setBackgroundColor(i == cur ? 0xFF1B1B1B : Color.TRANSPARENT);
            row.setPadding(dp(8), dp(8), dp(8), dp(8));

            TextView name = new TextView(this);
            name.setText((i == cur ? "● " : "○ ") + names[i - 1]);
            name.setTextColor(i == cur ? TEXT : MUTED);
            name.setTextSize(14);
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            row.addView(name, nlp);

            Button play = new Button(this);
            play.setText("▶");
            play.setTextSize(13);
            play.setTextColor(TEXT);
            GradientDrawable pb = new GradientDrawable();
            pb.setColor(0xFF151515);
            pb.setCornerRadius(dp(10));
            pb.setStroke(dp(1), LINE);
            play.setBackground(pb);
            play.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    playSound(idx);
                }
            });
            row.addView(play);

            row.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Prefs.setSound(SettingsActivity.this, idx);
                    drawSounds();
                }
            });
            soundBox.addView(row);
        }
    }

    private void styleBg(Button b) {
        boolean on = Prefs.background(this);
        b.setText(on ? "Выключить фоновый режим" : "Включить фоновый режим");
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        if (on) {
            bg.setColor(0xFF151515);
            bg.setStroke(dp(1), LINE);
            b.setTextColor(TEXT);
        } else {
            bg.setColor(Color.WHITE);
            b.setTextColor(Color.BLACK);
        }
        b.setBackground(bg);
        b.setPadding(0, dp(13), 0, dp(13));
    }

    private void playSound(int idx) {
        try {
            android.media.Ringtone r = android.media.RingtoneManager.getRingtone(this,
                    Uri.parse("android.resource://" + getPackageName() + "/raw/sound" + idx));
            if (r != null) r.play();
        } catch (Exception ignored) {
        }
    }

    private void askPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 11);
            }
        }
        if (Build.VERSION.SDK_INT >= 31) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null && !nm.canUseFullScreenIntent()) {
                // не критично
            }
            android.app.AlarmManager am =
                    (android.app.AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (am != null && !am.canScheduleExactAlarms()) {
                try {
                    startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:" + getPackageName())));
                } catch (Exception ignored) {
                }
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
    }
}
