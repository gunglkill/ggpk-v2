package ru.ggpk.schedule;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Публикация расписания с телефона: сообщество ВК → сайт ggpk-v2.
 *
 * Без авторизации в ВК: читаем открытые страницы сообщества, находим
 * закреплённый основной файл («Расписание N семестр …») и изменения
 * «дд.мм.гггг.pdf» на сегодня и завтра, скачиваем и коммитим в репозиторий
 * сайта через GitHub API (ключ — в настройках приложения).
 */
public class PublishActivity extends Activity {

    private static final String OWNER = "216202806";
    private static final String API =
            "https://api.github.com/repos/gunglkill/ggpk-v2/contents/";

    private static final int BG = 0xFF08080A;
    private static final int CARD = 0xFF111113;
    private static final int LINE = 0xFF262626;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFF8B8B8B;

    private static final Pattern DOC_ID = Pattern.compile("(?:doc|docs)-?" + OWNER + "_(\\d+)");
    private static final Pattern TITLE = Pattern.compile("\"([^\"]{3,180}\\.pdf)\"",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ANY_URL = Pattern.compile("(https?:(?:\\\\?/)+[^\"'<>]{8,600})");
    private static final Pattern DATE = Pattern.compile("(\\d{2}\\.\\d{2}\\.\\d{4})");

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final StringBuilder logBuf = new StringBuilder();
    private TextView logView;
    private Button runBtn;
    private volatile boolean running = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        setContentView(build());
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private View build() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(20), dp(18), dp(30));
        scroll.addView(root);

        TextView head = new TextView(this);
        head.setText("Публикация изменений");
        head.setTextColor(TEXT);
        head.setTextSize(26);
        head.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(head);
        TextView sub = new TextView(this);
        sub.setText("Сообщество ВК → сайт. Берём основной файл и изменения на сегодня и завтра, "
                + "загружаем на GitHub (ключ задаётся в настройках).");
        sub.setTextColor(MUTED);
        sub.setTextSize(13);
        sub.setPadding(0, dp(8), 0, dp(14));
        root.addView(sub);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), LINE);
        runBtn = new Button(this);
        runBtn.setText("Обновить расписание");
        runBtn.setAllCaps(false);
        runBtn.setTextSize(15);
        runBtn.setTextColor(Color.BLACK);
        runBtn.setTypeface(Typeface.DEFAULT_BOLD);
        runBtn.setBackground(bg);
        runBtn.setPadding(0, dp(14), 0, dp(14));
        runBtn.setOnClickListener(v -> start());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(14);
        runBtn.setLayoutParams(lp);
        root.addView(runBtn);

        logView = new TextView(this);
        logView.setTextColor(MUTED);
        logView.setTextSize(13);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setLineSpacing(dp(3), 1f);
        logView.setText("Готово к работе.\n");
        root.addView(logView);

        return scroll;
    }

    private void append(String s) {
        ui.post(() -> {
            logBuf.append(s).append('\n');
            logView.setText(logBuf.toString());
            final ScrollView parent = (ScrollView) logView.getParent();
            parent.post(() -> parent.fullScroll(View.FOCUS_DOWN));
        });
    }

    // ---------------- сеть ----------------

    private static String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        return out.toString("UTF-8");
    }

    /** GET на GitHub API; null — файл не найден (404). */
    private String ghGet(String path, String auth) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(API + path + "?ref=main").openConnection();
        c.setConnectTimeout(20000);
        c.setReadTimeout(30000);
        c.setRequestProperty("Authorization", auth);
        c.setRequestProperty("User-Agent", Http.UA);
        c.setRequestProperty("Accept", "application/vnd.github+json");
        int code = c.getResponseCode();
        if (code == 404) {
            c.disconnect();
            return null;
        }
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        String text = in != null ? readAll(in) : "";
        c.disconnect();
        if (code >= 400) {
            try {
                text = new JSONObject(text).optString("message", text);
            } catch (Exception ignored) {
            }
            throw new IllegalStateException("GitHub: HTTP " + code + " — " + text);
        }
        return text;
    }

    private void ghPut(String path, byte[] data, String message, String auth, String sha)
            throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("message", message);
        payload.put("content", Base64.encodeToString(data, Base64.NO_WRAP));
        payload.put("branch", "main");
        if (sha != null) payload.put("sha", sha);
        byte[] body = payload.toString().getBytes("UTF-8");
        HttpURLConnection c = (HttpURLConnection) new URL(API + path).openConnection();
        c.setRequestMethod("PUT");
        c.setConnectTimeout(20000);
        c.setReadTimeout(60000);
        c.setDoOutput(true);
        c.setFixedLengthStreamingMode(body.length);
        c.setRequestProperty("Authorization", auth);
        c.setRequestProperty("User-Agent", Http.UA);
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        c.setRequestProperty("Accept", "application/vnd.github+json");
        c.getOutputStream().write(body);
        int code = c.getResponseCode();
        String text = readAll(code >= 400 ? c.getErrorStream() : c.getInputStream());
        c.disconnect();
        if (code >= 400) {
            try {
                text = new JSONObject(text).optString("message", text);
            } catch (Exception ignored) {
            }
            throw new IllegalStateException("GitHub: HTTP " + code + " — " + text);
        }
    }

    private static boolean isPdf(byte[] data) {
        return data != null && data.length > 800 && data[0] == '%' && data[1] == 'P';
    }

    private boolean isOnlineSafe() {
        return true; // проверка сети не критична: ошибки логируются
    }

    // ---------------- ВК ----------------

    private static String unescape(String s) {
        if (s == null) return "";
        s = s.replace("\\/", "/").replace("\\\"", "\"").replace("\\\\", "\\")
                .replace("&quot;", "\"").replace("&amp;", "&").replace("&lt;", "<")
                .replace("&gt;", ">").replace("&nbsp;", " ").replace("\\u0026", "&");
        return s;
    }

    private Map<Long, String> scanVk() throws Exception {
        Map<Long, String> found = new LinkedHashMap<>();
        String lastErr = "";
        for (String url : vkPages()) {
            try {
                String html = unescape(Http.text(url));
                Matcher m = DOC_ID.matcher(html);
                while (m.find()) {
                    long id = Long.parseLong(m.group(1));
                    if (found.containsKey(id)) continue;
                    String win = html.substring(m.start(),
                            Math.min(html.length(), m.end() + 1000));
                    Matcher t = TITLE.matcher(win);
                    if (t.find()) found.put(id, unescape(t.group(1)));
                }
                if (!found.isEmpty()) {
                    append("ВК: " + host(url) + " → документов найдено: " + found.size());
                    return found;
                }
                lastErr = host(url) + ": документы сообщества не видны гостям";
            } catch (Exception e) {
                lastErr = host(url) + ": " + e.getMessage();
            }
        }
        throw new IllegalStateException(lastErr);
    }

    private static String[] vkPages() {
        return new String[]{
                "https://vk.ru/docs-" + OWNER,
                "https://vk.com/docs-" + OWNER,
                "https://m.vk.com/docs-" + OWNER,
                "https://vk.ru/club" + OWNER,
                "https://m.vk.com/club" + OWNER,
        };
    }

    private static String host(String url) {
        return url.replaceAll("^https?://(m\\.)?", "").split("/")[0];
    }

    /** Скачивает PDF по номеру документа (через страницу документа). */
    private byte[] downloadDoc(long id) throws Exception {
        String page = unescape(Http.text("https://vk.ru/doc-" + OWNER + "_" + id));
        List<String> links = new ArrayList<>();
        Matcher m = ANY_URL.matcher(page);
        while (m.find()) {
            String u = m.group(1);
            if (u.contains("// ")) continue;
            u = u.replace(" ", "");
            String low = u.toLowerCase(Locale.ROOT);
            boolean pdf = low.contains(".pdf");
            boolean docLink = low.contains("/doc") || low.contains("token=")
                    || low.contains("occ=") || low.contains("exp=");
            if (!pdf && !docLink) continue;
            if (!links.contains(u)) links.add(u);
        }
        // сначала те, что похожи на прямые ссылки на файл
        List<String> pdfFirst = new ArrayList<>();
        for (String u : links) {
            if (u.toLowerCase(Locale.ROOT).contains(".pdf")) pdfFirst.add(u);
        }
        for (String u : pdfFirst) links.remove(u);
        links.addAll(0, pdfFirst);

        Exception err = null;
        for (String u : links) {
            try {
                byte[] data = Http.bytes(u);
                if (isPdf(data)) {
                    append("ВК: файл получен (" + data.length + " Б)");
                    return data;
                }
                err = new IllegalStateException("не PDF (" + data.length + " Б)");
            } catch (Exception e) {
                err = e;
            }
        }
        if (err != null) throw err;
        throw new IllegalStateException("ВК не дал гостю ссылку на файл");
    }

    // ---------------- публикация ----------------

    private void start() {
        if (running) return;
        running = true;
        runBtn.setEnabled(false);
        ui.post(() -> {
            logBuf.setLength(0);
            logView.setText("");
        });
        new Thread(this::publish, "ggpk-publish").start();
    }

    private void publish() {
        SimpleDateFormat dmy = new SimpleDateFormat("dd.MM.yyyy", Locale.US);
        Calendar cal = Calendar.getInstance();
        List<String> want = new ArrayList<>();
        for (int i = 0; i <= 1; i++) {
            cal.setTime(new Date());
            cal.add(Calendar.DAY_OF_YEAR, i);
            want.add(dmy.format(cal.getTime()));
        }
        try {
            String about = Prefs.ghToken(this);
            if (about == null || about.trim().isEmpty()) {
                throw new IllegalStateException("нет ключа GitHub — задайте в настройках");
            }
            final String auth = "token " + about.trim();
            append("GitHub: ключ есть ✓");

            append("ВК: ищу документы сообщества…");
            Map<Long, String> docs = scanVk();
            if (docs.isEmpty()) throw new IllegalStateException("документы не найдены");

            int ok = 0, same = 0, skip = 0;
            StringBuilder loaded = new StringBuilder();

            for (Map.Entry<Long, String> e : docs.entrySet()) {
                String title = e.getValue() == null ? "" : e.getValue();
                String path = targetPath(title, want);
                if (path == null) continue;
                try {
                    append("Скачиваю: " + title);
                    byte[] data = downloadDoc(e.getKey());
                    Info i = ghInfo(path, auth);
                    if (i != null && i.b64 != null && i.b64.length() > 0) {
                        byte[] old;
                        try {
                            old = Base64.decode(i.b64.replace("\n", ""), Base64.DEFAULT);
                        } catch (Exception ex) {
                            old = new byte[0];
                        }
                        if (Arrays.equals(old, data)) {
                            same++;
                            append("  без изменений — пропускаю ✓");
                            continue;
                        }
                    }
                    String msg = path.equals("main.pdf")
                            ? "расписание: основной файл (телефон)"
                            : "расписание: изменения на "
                            + path.substring("changes/".length(), "changes/".length() + 10)
                            + " (телефон)";
                    ghPut(path, data, msg, auth, i == null ? null : i.sha);
                    ok++;
                    if (loaded.length() > 0) loaded.append(", ");
                    loaded.append(path);
                    append("  загружено ✓ (" + data.length + " Б)");
                } catch (Exception ex) {
                    skip++;
                    append("  ошибка: " + ex.getMessage());
                }
            }

            append("Итог: загружено " + ok + ", без изменений " + same + ", ошибок " + skip);
            if (ok > 0) {
                append("Сайт пересоберётся автоматически (1-2 минуты).");
                ui.post(() -> Toast.makeText(PublishActivity.this,
                        "Опубликовали: " + loaded, Toast.LENGTH_LONG).show());
            } else if (same > 0) {
                ui.post(() -> Toast.makeText(PublishActivity.this,
                        "Обновлений нет: всё уже на сайте", Toast.LENGTH_LONG).show());
            }
        } catch (Exception e) {
            append("Не удалось: " + e.getMessage());
            append("Если сообщество всё же требует вход в ВК — скажите, включим вход "
                    + "через WebView (без обмена токенами).");
            ui.post(() -> Toast.makeText(this, "Публикация не удалась",
                    Toast.LENGTH_SHORT).show());
        } finally {
            ui.post(() -> {
                running = false;
                runBtn.setEnabled(true);
            });
        }
    }

    static final class Info {
        final String sha;
        final String b64;

        Info(String sha, String b64) {
            this.sha = sha;
            this.b64 = b64;
        }
    }

    private Info ghInfo(String path, String auth) {
        try {
            String resp = ghGet(path, auth);
            if (resp == null) return null;
            JSONObject j = new JSONObject(resp);
            return new Info(j.optString("sha"), j.optString("content"));
        } catch (Exception e) {
            return null;
        }
    }

    /** Куда положить файл по названию документа. null — это не наш файл. */
    private static String targetPath(String title, List<String> want) {
        if (title == null) return null;
        String low = unescape(title).toLowerCase(Locale.ROOT).trim();
        if (!low.endsWith(".pdf")) return null;
        if (low.contains("дежурство")) return null;
        Matcher d = DATE.matcher(low);
        if (d.find()) {
            String dt = d.group(1);
            if (!want.contains(dt)) return null;
            String iso = dt.substring(6) + "-" + dt.substring(3, 5) + "-" + dt.substring(0, 2);
            return "changes/" + iso + ".pdf";
        }
        if (low.contains("расписание") && (low.contains("семестр") || low.contains("а3"))) {
            return "main.pdf";
        }
        return null;
    }
}
