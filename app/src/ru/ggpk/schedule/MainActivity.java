package ru.ggpk.schedule;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final String START_URL = "https://gunglkill.github.io/ggpk/";
    private static final int BG = 0xFF000000;

    private WebView web;
    private View splash;
    private long backAt = 0;
    private long lastSchedule = 0;
    private boolean prefetching = false;
    private boolean themeChecked = false;
    private boolean themeReloaded = false;

    private static final String NET_JS =
            "(function(){try{" +
            "function svgNet(ok){" +
            "var c=ok?'#34c759':'#ff453a';" +
            "var cross=ok?'':'<path d=\"M5 5l22 22\" stroke=\"#ff453a\" stroke-width=\"2.6\" stroke-linecap=\"round\"/>';" +
            "return '<svg viewBox=\"0 0 32 32\" width=\"32\" height=\"32\">'+" +
            "'<circle cx=\"16\" cy=\"16\" r=\"12\" fill=\"none\" stroke=\"'+c+'\" stroke-width=\"2.3\"/>'+" +
            "'<ellipse cx=\"16\" cy=\"16\" rx=\"5.6\" ry=\"12\" fill=\"none\" stroke=\"'+c+'\" stroke-width=\"2.3\"/>'+" +
            "'<path d=\"M4.6 12.4h22.8M4.6 19.6h22.8\" stroke=\"'+c+'\" stroke-width=\"2.3\" stroke-linecap=\"round\"/>'+" +
            "cross+'</svg>';}" +
            "function paint(){var m=document.querySelector('.brand .mark');if(!m)return;m.innerHTML=svgNet(navigator.onLine);}" +
            "function toast(t){var el=document.getElementById('toast');if(!el)return;" +
            "el.textContent=t;el.classList.add('on');" +
            "if(window.__nt)clearTimeout(window.__nt);" +
            "window.__nt=setTimeout(function(){el.classList.remove('on');},5000);}" +
            "document.addEventListener('click',function(e){" +
            "var m=e.target.closest?e.target.closest('.brand .mark'):null;" +
            "if(m){e.preventDefault();e.stopPropagation();" +
            "toast(navigator.onLine?'Вы подключены к сети':'Нет подключения к сети');}},true);" +
            "window.addEventListener('online',paint);window.addEventListener('offline',paint);" +
            "var st=document.getElementById('st');if(st)st.style.display='none';" +
            "paint();setInterval(paint,3000);" +
            "}catch(e){}})();";

    private static final String INJECT =
            "(function(){try{" +
            "var t=document.querySelector('.tools');" +
            "if(t&&!document.getElementById('appset')){" +
            "var b=document.createElement('button');b.id='appset';b.className='ic';b.title='Настройки';" +
            "b.innerHTML='<svg viewBox=\"0 0 24 24\" width=\"18\" height=\"18\" fill=\"none\" " +
            "stroke=\"currentColor\" stroke-width=\"1.7\" stroke-linecap=\"round\" stroke-linejoin=\"round\">" +
            "<circle cx=\"12\" cy=\"12\" r=\"3.1\"/><path d=\"M19.4 15a1.7 1.7 0 0 0 .3 1.9l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1" +
            "a1.7 1.7 0 0 0-1.9-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.9.3" +
            "l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.9 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1" +
            "a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.9l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.9.3H9" +
            "a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.9-.3l.1-.1a2 2 0 1 1 2.8 2.8" +
            "l-.1.1a1.7 1.7 0 0 0-.3 1.9V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z\"/></svg>';" +
            "b.onclick=function(){Android.openSettings();};t.appendChild(b);}" +
            "var g=localStorage.getItem('ggpk.group')||'';Android.setGroup(g);" +
            "}catch(e){}})();";

    private static final String PREFETCH_JS =
            "(function(){try{" +
            "if(!('caches' in window)){Android.prefetchDone('нет кеша');return;}" +
            "var BASE='/ggpk/',CACHE='ggpk-v1';" +
            "var done=0,bytes=0,list=[],seen={};" +
            "function norm(u){var a=document.createElement('a');a.href=u;return a.origin+a.pathname;}" +
            "function getText(u){return fetch(u,{cache:'reload'}).then(function(r){return r.text();});}" +
            "function purge(cache){try{cache.keys().then(function(reqs){" +
            "var now=new Date().getTime(),limit=3*86400000,i=0;" +
            "function step(){if(i>=reqs.length)return;var req=reqs[i++];" +
            "cache.match(req).then(function(res){if(!res)return;var d=res.headers.get('date');" +
            "var t=d?Date.parse(d):0;if(t&&(now-t)>limit)return cache.delete(req);})" +
            ".catch(function(){}).then(step);}" +
            "step();});}catch(e){}}" +
            "getText(BASE+'index.html').then(function(idx){" +
            "return getText(BASE+'prep.html').then(function(pre){" +
            "var d1=new DOMParser().parseFromString(idx,'text/html');" +
            "var d2=new DOMParser().parseFromString(pre,'text/html');" +
            "var groups=[].map.call(d1.querySelectorAll('a[href^=\"g/\"]'),function(a){return a.getAttribute('href').split('?')[0];});" +
            "var teachers=[].map.call(d2.querySelectorAll('a[href^=\"p/\"]'),function(a){return a.getAttribute('href').split('?')[0];});" +
            "list.push(BASE,BASE+'index.html',BASE+'prep.html',BASE+'fonts.css',BASE+'version.txt');" +
            "groups.forEach(function(g){list.push(BASE+g);list.push(BASE+g.replace('g/','data/g/').replace('.html','.json'));});" +
            "teachers.forEach(function(t){list.push(BASE+t);list.push(BASE+t.replace('p/','data/p/').replace('.html','.json'));});" +
            "return getText(BASE+'fonts.css').then(function(css){" +
            "var m=css.match(/fonts\\/[^)\"']+\\.woff2/g)||[];" +
            "m.forEach(function(f){list.push(BASE+f);});" +
            "}).catch(function(){}).then(function(){" +
            "return caches.open(CACHE).then(function(cache){" +
            "var i=0;" +
            "function worker(){" +
            "if(i>=list.length)return Promise.resolve();" +
            "var u=list[i++];" +
            "if(seen[u])return worker();" +
            "seen[u]=1;" +
            "return fetch(u,{cache:'reload'}).then(function(r){" +
            "if(!r.ok)return;" +
            "done++;" +
            "var cl=r.headers.get('content-length');" +
            "if(cl){bytes+=parseInt(cl,10)||0;}" +
            "return cache.put(new Request(norm(u)),r.clone()).then(function(){" +
            "if(!cl){return r.clone().text().then(function(t){bytes+=t.length;});}" +
            "});" +
            "}).catch(function(){}).then(worker);" +
            "}" +
            "var ws=[];for(var k=0;k<6;k++)ws.push(worker());" +
            "return Promise.all(ws).then(function(){purge(cache);return {n:done,all:list.length,b:bytes};});" +
            "});" +
            "});" +
            "});" +
            "}).then(function(res){Android.prefetchDone(res.n+' из '+res.all+'|'+res.b);})" +
            ".catch(function(e){Android.prefetchDone('ошибка');});" +
            "}catch(e){}})();";

    private class Bridge {
        @JavascriptInterface
        public void openSettings() {
            runOnUiThread(new Runnable() {
                public void run() {
                    startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                }
            });
        }

        @JavascriptInterface
        public void setGroup(String g) {
            if (g != null) Prefs.setGroup(MainActivity.this, g);
        }

        @JavascriptInterface
        public void prefetchDone(String result) {
            final String r = result == null ? "" : result;
            String[] parts = r.split("\\|");
            String count = parts.length > 0 ? parts[0] : r;
            long bytes = 0;
            if (parts.length > 1) {
                try {
                    bytes = Long.parseLong(parts[1].trim());
                } catch (Exception ignored) {
                }
            }
            if (bytes > 0) Prefs.setDlSize(MainActivity.this, human(bytes));
            final String c = count;
            runOnUiThread(new Runnable() {
                public void run() {
                    prefetching = false;
                    if (c.contains(" из ")) {
                        Toast.makeText(MainActivity.this, "Расписание сохранено: " + c,
                                Toast.LENGTH_LONG).show();
                    }
                }
            });
        }

        @JavascriptInterface
        public void downloadAll() {
            runOnUiThread(new Runnable() {
                public void run() {
                    if (prefetching) {
                        Toast.makeText(MainActivity.this, "Уже скачиваю…", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!isOnline()) {
                        Toast.makeText(MainActivity.this, "Нет подключения к сети",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    prefetching = true;
                    Toast.makeText(MainActivity.this, "Скачиваю всё расписание…",
                            Toast.LENGTH_SHORT).show();
                    web.evaluateJavascript(PREFETCH_JS, null);
                }
            });
        }
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setUserAgentString(s.getUserAgentString() + " GGPKApp/1.0");
        if (Build.VERSION.SDK_INT >= 21) {
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        }
        web.setBackgroundColor(BG);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.addJavascriptInterface(new Bridge(), "Android");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                return handleExternal(req.getUrl().toString());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView v, String url) {
                return handleExternal(url);
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                hideSplash();
                v.evaluateJavascript(INJECT, null);
                v.evaluateJavascript(NET_JS, null);
                injectDownloadRow();
                captureTheme();
                maybePrefetch();
            }

        });
        web.loadUrl(START_URL);

        root.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        splash = buildSplash();
        root.addView(splash, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);
        if (!Prefs.onboarded(this)) {
            root.postDelayed(new Runnable() {
                public void run() {
                    Dialogs.offerBackground(MainActivity.this);
                }
            }, 900);
        }
    }

    /** Запоминаем тему, которую выбрал пользователь на главном экране. */
    private void captureTheme() {
        web.evaluateJavascript("localStorage.getItem('ggpk.theme') || ''", value -> {
            if (value == null) return;
            String cur = value.replace("\"", "").trim();
            if (!cur.isEmpty()) Prefs.setTheme(this, cur);
        });
    }

    private static String human(long b) {
        if (b < 1024) return b + " Б";
        if (b < 1024 * 1024) return String.format(java.util.Locale.US, "%.0f КБ", b / 1024.0);
        return String.format(java.util.Locale.US, "%.1f МБ", b / (1024.0 * 1024.0));
    }

    /** Кнопка «Скачать все расписание» в блоке «Ещё» на главной. */
    private void injectDownloadRow() {
        String size = Prefs.dlSize(this);
        if (size == null || size.isEmpty()) size = "—";
        String js = "(function(){try{"
                + "var p=location.pathname;"
                + "if(p.indexOf('index.html')<0 && p.slice(-6)!=='/ggpk/')return;"
                + "var rows=document.querySelector('.rows');"
                + "if(!rows||document.getElementById('dlall'))return;"
                + "var a=document.createElement('a');"
                + "a.className='row';a.id='dlall';a.href='#';"
                + "a.innerHTML='Скачать все расписание<span>__SIZE__</span>';"
                + "a.onclick=function(e){e.preventDefault();Android.downloadAll();};"
                + "rows.appendChild(a);"
                + "}catch(e){}})();";
        web.evaluateJavascript(js.replace("__SIZE__", size), null);
    }

    /** Телеграм — в приложение (или браузер), донат — во внешний браузер. */
    private boolean handleExternal(String url) {
        if (url == null) return false;
        String low = url.toLowerCase();
        boolean tg = low.contains("t.me/") || low.contains("telegram.me/") || low.startsWith("tg:");
        boolean donate = low.contains("dalink.to/");
        if (!tg && !donate) return false;
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void maybePrefetch() {
        if (prefetching || !isOnline()) return;
        String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                .format(new java.util.Date());
        if (today.equals(Prefs.prefetchDay(this))) return;
        Prefs.setPrefetchDay(this, today);
        prefetching = true;
        Toast.makeText(this, "Скачиваю расписание всех групп для офлайна…", Toast.LENGTH_SHORT).show();
        web.evaluateJavascript(PREFETCH_JS, null);
    }

    private View buildSplash() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackgroundColor(BG);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.mipmap.ic_launcher);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(96), dp(96));
        ip.bottomMargin = dp(18);
        box.addView(icon, ip);

        TextView name = new TextView(this);
        name.setText("Расписание ГГПК");
        name.setTextColor(0xFFFFFFFF);
        name.setTextSize(19f);
        name.setGravity(Gravity.CENTER);
        box.addView(name);

        TextView sub = new TextView(this);
        sub.setText("Губкинский горно-политехнический колледж");
        sub.setTextColor(0xFF8B8B8B);
        sub.setTextSize(12f);
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.topMargin = dp(6);
        box.addView(sub, sp);

        return box;
    }

    private void hideSplash() {
        if (splash == null || splash.getVisibility() != View.VISIBLE) return;
        AlphaAnimation fade = new AlphaAnimation(1f, 0f);
        fade.setDuration(260);
        fade.setFillAfter(true);
        splash.startAnimation(fade);
        splash.postDelayed(new Runnable() {
            public void run() {
                splash.setVisibility(View.GONE);
            }
        }, 280);
    }

    private boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return true;
        Network n = cm.getActiveNetwork();
        if (n == null) return false;
        NetworkCapabilities c = cm.getNetworkCapabilities(n);
        return c != null && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private void askNotifications() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 11);
            }
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onResume() {
        super.onResume();
        long now = System.currentTimeMillis();
        if (now - lastSchedule > 15 * 60 * 1000L) {
            lastSchedule = now;
            Notify.reschedule(this);
            if (Prefs.background(this)) {
                Notify.scheduleChecks(this);
                Notify.checkUpdate(this, null);
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) {
            web.goBack();
            return;
        }
        long now = System.currentTimeMillis();
        if (now - backAt < 2000) {
            super.onBackPressed();
            return;
        }
        backAt = now;
        Toast.makeText(this, "Нажмите ещё раз, чтобы выйти", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
