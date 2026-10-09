package com.rahul.leecoglasslauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.json.JSONObject;

public class MainActivity extends Activity {

    static final int BG = Color.rgb(238, 242, 245);
    static final int INK = Color.rgb(23, 33, 43);
    static final int MUTED = Color.rgb(102, 116, 130);
    static final int ACC = Color.rgb(77, 113, 143);

    // >>> Put the address of YOUR update.json here (GitHub raw link, your own website, etc.)
    static final String UPDATE_URL = "https://raw.githubusercontent.com/rahuldharuman01-blip/leeco-launcher/main/update.json";

    static class AppItem {
        String label, pkg;
        ComponentName cn;
        Drawable icon;
    }

    LinearLayout content;
    TextView clock, date;
    SharedPreferences prefs;
    List<AppItem> apps;
    boolean appsDirty = true;
    String screen = "home";

    final Handler handler = new Handler(Looper.getMainLooper());
    final Runnable ticker = new Runnable() {
        @Override public void run() {
            updateClock();
            handler.postDelayed(this, 15000);
        }
    };
    final BroadcastReceiver pkgReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            appsDirty = true;
            if (screen.equals("apps") || screen.equals("favs")) go(screen);
        }
    };

    // ---------------------------------------------------------------- lifecycle

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("launcher", MODE_PRIVATE);
        buildShell();
        go("home");
    }

    @Override protected void onResume() {
        super.onResume();
        handler.post(ticker);
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addDataScheme("package");
        registerReceiver(pkgReceiver, f);
    }

    @Override protected void onPause() {
        super.onPause();
        handler.removeCallbacks(ticker);
        try { unregisterReceiver(pkgReceiver); } catch (IllegalArgumentException ignored) { }
    }

    // Home button pressed while the launcher is already open
    @Override protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        if (Intent.ACTION_MAIN.equals(i.getAction())) go("home");
    }

    // A launcher must never close itself
    @Override public void onBackPressed() {
        if (!"home".equals(screen)) go("home");
    }

    // MENU key on the remote = add/remove favorite for the highlighted app
    @Override public boolean onKeyDown(int keyCode, KeyEvent e) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            View f = getCurrentFocus();
            if (f != null && f.getTag() instanceof AppItem) { f.performLongClick(); return true; }
        }
        return super.onKeyDown(keyCode, e);
    }

    // ---------------------------------------------------------------- UI helpers

    int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + .5f); }

    TextView tv(String s, float sp) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(INK);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    LinearLayout vbox() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    LinearLayout row() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        return l;
    }

    LinearLayout.LayoutParams cell(int heightDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(heightDp), 1);
        p.setMargins(dp(6), dp(6), dp(6), dp(6));
        return p;
    }

    ScrollView scroll(View v) {
        ScrollView s = new ScrollView(this);
        s.setVerticalScrollBarEnabled(false);
        s.setClipToPadding(false);
        s.setPadding(dp(8), dp(6), dp(8), dp(6));
        s.addView(v, new ViewGroup.LayoutParams(-1, -2));
        return s;
    }

    void addRow(LinearLayout parent, LinearLayout r) {
        parent.addView(r, new LinearLayout.LayoutParams(-1, -2));
    }

    LinearLayout card(Drawable icon, String title, String sub) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.HORIZONTAL);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setBackgroundResource(R.drawable.glass_card);
        c.setPadding(dp(16), dp(8), dp(16), dp(8));
        c.setFocusable(true);
        c.setClickable(true);
        if (icon != null) {
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(icon);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(48), dp(48));
            p.rightMargin = dp(14);
            c.addView(iv, p);
        }
        LinearLayout t = vbox();
        t.setGravity(Gravity.CENTER_VERTICAL);
        TextView a = tv(title, 20);
        a.setTypeface(null, Typeface.BOLD);
        a.setSingleLine(true);
        a.setEllipsize(TextUtils.TruncateAt.END);
        t.addView(a);
        TextView b = tv(sub == null ? "" : sub, 15);
        b.setTextColor(MUTED);
        b.setSingleLine(true);
        b.setEllipsize(TextUtils.TruncateAt.END);
        if (sub == null || sub.length() == 0) b.setVisibility(View.GONE);
        t.addView(b);
        c.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        c.setOnFocusChangeListener((v, f) ->
                v.animate().scaleX(f ? 1.04f : 1f).scaleY(f ? 1.04f : 1f).setDuration(120).start());
        return c;
    }

    void setSub(LinearLayout card, String s) {
        LinearLayout t = (LinearLayout) card.getChildAt(card.getChildCount() - 1);
        TextView b = (TextView) t.getChildAt(1);
        b.setText(s);
        b.setVisibility(View.VISIBLE);
    }

    TextView title(String s) {
        TextView t = tv(s, 30);
        t.setTypeface(null, Typeface.BOLD);
        t.setPadding(dp(16), dp(6), 0, dp(10));
        return t;
    }

    LinearLayout backCard() {
        LinearLayout b = card(null, "Back", "Return home");
        b.setOnClickListener(v -> go("home"));
        return b;
    }

    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    boolean safeStart(Intent i) {
        try { startActivity(i); return true; } catch (Exception e) { return false; }
    }

    // ---------------------------------------------------------------- shell

    void buildShell() {
        LinearLayout root = vbox();
        root.setBackgroundColor(BG);
        root.setPadding(dp(34), dp(20), dp(34), dp(16));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setBackgroundResource(R.drawable.glass_panel);
        TextView brand = tv("LEECO  \u2022  GLASS HOME", 22);
        brand.setTypeface(null, Typeface.BOLD);
        top.addView(brand, new LinearLayout.LayoutParams(0, dp(60), 1));
        LinearLayout info = vbox();
        info.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        clock = tv("", 20);
        clock.setTypeface(null, Typeface.BOLD);
        clock.setGravity(Gravity.END);
        date = tv("", 14);
        date.setTextColor(MUTED);
        date.setGravity(Gravity.END);
        info.addView(clock);
        info.addView(date);
        top.addView(info, new LinearLayout.LayoutParams(dp(230), dp(60)));
        root.addView(top, new LinearLayout.LayoutParams(-1, -2));

        content = vbox();
        content.setPadding(0, dp(12), 0, 0);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        updateClock();
    }

    void updateClock() {
        if (clock == null) return;
        Date d = new Date();
        clock.setText(new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(d));
        date.setText(new SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault()).format(d));
    }

    void go(String s) {
        screen = s;
        content.removeAllViews();
        if (s.equals("apps")) showApps(false);
        else if (s.equals("favs")) showApps(true);
        else if (s.equals("settings")) showSettings();
        else showHome();
    }

    // ---------------------------------------------------------------- apps data

    List<AppItem> loadApps() {
        PackageManager pm = getPackageManager();
        List<AppItem> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String[] cats = {Intent.CATEGORY_LAUNCHER, "android.intent.category.LEANBACK_LAUNCHER"};
        for (String c : cats) {
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(c);
            List<ResolveInfo> list = pm.queryIntentActivities(q, 0);
            if (list == null) continue;
            for (ResolveInfo ri : list) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(getPackageName()) || !seen.add(pkg)) continue;
                AppItem a = new AppItem();
                a.pkg = pkg;
                a.label = String.valueOf(ri.loadLabel(pm));
                a.cn = new ComponentName(pkg, ri.activityInfo.name);
                a.icon = ri.loadIcon(pm);
                out.add(a);
            }
        }
        Collections.sort(out, (x, y) -> x.label.compareToIgnoreCase(y.label));
        return out;
    }

    // loads on a background thread (old TVs are slow), then runs 'done' on the UI thread
    void withApps(final Runnable done) {
        if (apps != null && !appsDirty) { done.run(); return; }
        new Thread(() -> {
            final List<AppItem> l = loadApps();
            runOnUiThread(() -> { apps = l; appsDirty = false; done.run(); });
        }).start();
    }

    Set<String> favs() { return prefs.getStringSet("fav", new HashSet<String>()); }

    boolean toggleFav(AppItem a) {
        Set<String> s = new HashSet<>(favs());
        boolean now;
        if (s.contains(a.pkg)) { s.remove(a.pkg); now = false; } else { s.add(a.pkg); now = true; }
        prefs.edit().putStringSet("fav", s).apply();
        return now;
    }

    void launch(AppItem a) {
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_LAUNCHER);
        i.setComponent(a.cn);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        if (!safeStart(i)) toast("Couldn't open " + a.label);
    }

    // try known package names first, then fall back to searching installed app names
    void launchApp(final String[] pkgs, final String[] hints, final String name) {
        PackageManager pm = getPackageManager();
        for (String p : pkgs) {
            Intent i = pm.getLaunchIntentForPackage(p);
            if (i != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (safeStart(i)) return;
            }
        }
        withApps(() -> {
            for (String h : hints)
                for (AppItem a : apps)
                    if (a.label.toLowerCase(Locale.US).contains(h)) { launch(a); return; }
            toast(name + " is not installed on this TV.");
        });
    }

    View appCard(final AppItem a, final String ctx) {
        final LinearLayout c = card(a.icon, a.label, favs().contains(a.pkg) ? "Favorite" : "Open");
        c.setTag(a);
        c.setOnClickListener(v -> launch(a));
        c.setOnLongClickListener(v -> {
            boolean now = toggleFav(a);
            toast(a.label + (now ? " added to favorites" : " removed from favorites"));
            if (ctx.equals("apps")) setSub(c, now ? "Favorite" : "Open");
            else go(ctx);
            return true;
        });
        return c;
    }

    // ---------------------------------------------------------------- screens

    String greeting() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return h < 12 ? "Good morning" : h < 17 ? "Good afternoon" : "Good evening";
    }

    void showHome() {
        LinearLayout page = vbox();
        content.addView(scroll(page), new LinearLayout.LayoutParams(-1, 0, 1));

        TextView hero = tv(greeting() + "\nYour TV, simplified.", 30);
        hero.setTypeface(null, Typeface.BOLD);
        hero.setPadding(dp(16), dp(4), dp(16), dp(4));
        page.addView(hero, new LinearLayout.LayoutParams(-1, -2));

        TextView st = tv("TV READY     \u2022     HDMI / AV / USB INPUTS AVAILABLE", 15);
        st.setTextColor(ACC);
        st.setPadding(dp(16), dp(4), 0, dp(6));
        page.addView(st, new LinearLayout.LayoutParams(-1, -2));

        // inputs
        String[][] src = {{"USB", "USB media"}, {"HDMI 1", "Main HDMI"}, {"HDMI 2", "Second HDMI"}, {"AV / Analog", "Composite input"}};
        LinearLayout r1 = row();
        View first = null;
        for (String[] s : src) {
            final String name = s[0];
            LinearLayout c = card(null, s[0], s[1]);
            c.setOnClickListener(v -> selectInput(name));
            r1.addView(c, cell(92));
            if (first == null) first = c;
        }
        addRow(page, r1);

        // quick apps
        LinearLayout r2 = row();
        LinearLayout yt = card(null, "YouTube", "Open app");
        yt.setOnClickListener(v -> launchApp(new String[]{"com.google.android.youtube.tv", "com.google.android.youtube", "com.google.android.youtube.tvunplugged"}, new String[]{"youtube"}, "YouTube"));
        LinearLayout nf = card(null, "Netflix", "Open app");
        nf.setOnClickListener(v -> launchApp(new String[]{"com.netflix.ninja", "com.netflix.mediaclient"}, new String[]{"netflix"}, "Netflix"));
        LinearLayout hs = card(null, "JioHotstar", "Open app");
        hs.setOnClickListener(v -> launchApp(new String[]{"in.startv.hotstar.dplus.tv", "in.startv.hotstar", "in.startv.hotstar.dplus"}, new String[]{"hotstar", "jio"}, "JioHotstar"));
        LinearLayout all = card(null, "All apps", "Everything installed");
        all.setOnClickListener(v -> go("apps"));
        r2.addView(yt, cell(92));
        r2.addView(nf, cell(92));
        r2.addView(hs, cell(92));
        r2.addView(all, cell(92));
        addRow(page, r2);

        // favorites (filled in once the app list is ready)
        final LinearLayout favBox = vbox();
        page.addView(favBox, new LinearLayout.LayoutParams(-1, -2));
        withApps(() -> {
            if (!"home".equals(screen)) return;
            Set<String> f = favs();
            List<AppItem> fl = new ArrayList<>();
            for (AppItem a : apps) if (f.contains(a.pkg) && fl.size() < 4) fl.add(a);
            favBox.removeAllViews();
            if (fl.isEmpty()) return;
            TextView h = tv("Favorites", 18);
            h.setTextColor(MUTED);
            h.setPadding(dp(16), dp(8), 0, 0);
            favBox.addView(h, new LinearLayout.LayoutParams(-1, -2));
            LinearLayout r = row();
            for (AppItem a : fl) r.addView(appCard(a, "home"), cell(92));
            for (int k = fl.size(); k < 4; k++) r.addView(new View(this), cell(92));
            addRow(favBox, r);
        });

        // nav
        LinearLayout nav = row();
        String[] n = {"Home", "Apps", "Favorites", "Settings"};
        for (String x : n) {
            final String name = x;
            LinearLayout c = card(null, x, "");
            c.setOnClickListener(v -> {
                if (name.equals("Apps")) go("apps");
                else if (name.equals("Favorites")) go("favs");
                else if (name.equals("Settings")) go("settings");
                else go("home");
            });
            nav.addView(c, cell(64));
        }
        addRow(page, nav);

        if (first != null) first.requestFocus();
    }

    void showApps(final boolean favOnly) {
        final String ctx = favOnly ? "favs" : "apps";
        content.addView(title(favOnly ? "Favorites" : "All apps"), new LinearLayout.LayoutParams(-1, -2));
        final LinearLayout list = vbox();
        content.addView(scroll(list), new LinearLayout.LayoutParams(-1, 0, 1));
        list.addView(tv("Loading apps\u2026", 18));

        withApps(() -> {
            if (!ctx.equals(screen)) return;
            list.removeAllViews();
            Set<String> f = favs();
            List<AppItem> shown = new ArrayList<>();
            for (AppItem a : apps) if (!favOnly || f.contains(a.pkg)) shown.add(a);
            if (shown.isEmpty()) {
                TextView e = tv(favOnly
                        ? "No favorites yet.\nOpen All apps, highlight an app and long-press OK (or press Menu) to add it."
                        : "No apps found.", 18);
                e.setPadding(dp(16), dp(8), dp(16), dp(16));
                list.addView(e, new LinearLayout.LayoutParams(-1, -2));
            }
            View first = null;
            LinearLayout r = null;
            int col = 0;
            for (AppItem a : shown) {
                if (col == 0) { r = row(); addRow(list, r); }
                View c = appCard(a, ctx);
                r.addView(c, cell(84));
                if (first == null) first = c;
                col = (col + 1) % 4;
            }
            if (r != null) while (col != 0 && col < 4) { r.addView(new View(this), cell(84)); col++; }
            LinearLayout back = backCard();
            LinearLayout br = row();
            br.addView(back, cell(64));
            for (int k = 0; k < 3; k++) br.addView(new View(this), cell(64));
            addRow(list, br);
            (first != null ? first : back).requestFocus();
        });
    }

    void showSettings() {
        content.addView(title("Settings"), new LinearLayout.LayoutParams(-1, -2));
        LinearLayout list = vbox();
        content.addView(scroll(list), new LinearLayout.LayoutParams(-1, 0, 1));
        String[][] items = {
                {"wifi", "Network & Wi-Fi", "Connection and TV IP"},
                {"display", "Display", "Picture and screen"},
                {"sound", "Sound", "Volume and audio"},
                {"lang", "Language & Input", "Keyboard, language, region"},
                {"storage", "Storage", "USB devices and storage"},
                {"home", "Default launcher", "Set this as your Home app"},
                {"update", "Launcher update", "Check for a newer version"},
                {"fw", "TV system update", "Open the TV's own updater"},
                {"info", "Device information", "Model, Android, IP, storage"},
                {"system", "All TV settings", "Open the original settings app"}
        };
        View first = null;
        for (int i = 0; i < items.length; i += 2) {
            LinearLayout r = row();
            for (int j = i; j < Math.min(i + 2, items.length); j++) {
                final String id = items[j][0];
                LinearLayout c = card(null, items[j][1], items[j][2]);
                c.setOnClickListener(v -> settingAction(id));
                r.addView(c, cell(76));
                if (first == null) first = c;
            }
            addRow(list, r);
        }
        LinearLayout br = row();
        br.addView(backCard(), cell(64));
        br.addView(new View(this), cell(64));
        addRow(list, br);
        if (first != null) first.requestFocus();
    }

    // ---------------------------------------------------------------- actions

    void openSetting(String action) {
        if (safeStart(new Intent(action))) return;
        if (safeStart(new Intent(Settings.ACTION_SETTINGS))) {
            toast("That section isn't available on its own - opened main settings.");
        } else {
            toast("The TV's settings app was not found.");
        }
    }

    void settingAction(String id) {
        switch (id) {
            case "wifi": openSetting(Settings.ACTION_WIFI_SETTINGS); break;
            case "display": openSetting(Settings.ACTION_DISPLAY_SETTINGS); break;
            case "sound": openSetting(Settings.ACTION_SOUND_SETTINGS); break;
            case "lang": openSetting(Settings.ACTION_LOCALE_SETTINGS); break;
            case "storage": openSetting(Settings.ACTION_INTERNAL_STORAGE_SETTINGS); break;
            case "system": openSetting(Settings.ACTION_SETTINGS); break;
            case "info": showInfo(); break;
            case "update": checkUpdate(); break;
            case "fw": tvFirmwareUpdate(); break;
            case "home": homeHelp(); break;
            default: break;
        }
    }

    void homeHelp() {
        new AlertDialog.Builder(this)
                .setTitle("Set as default launcher")
                .setMessage("Press the Home button on your remote. If Android asks which Home app to use, choose LeEco Glass Launcher and tap \"Always\".")
                .setPositiveButton("Try Home settings", (d, w) -> {
                    if (!safeStart(new Intent("android.settings.HOME_SETTINGS")))
                        toast("Not available on this TV - use the Home button method.");
                })
                .setNegativeButton("Close", null)
                .show();
    }

    String ip() {
        try {
            for (NetworkInterface n : Collections.list(NetworkInterface.getNetworkInterfaces()))
                for (InetAddress a : Collections.list(n.getInetAddresses()))
                    if (!a.isLoopbackAddress() && a instanceof Inet4Address) return a.getHostAddress();
        } catch (Exception ignored) { }
        return "not connected";
    }

    String storage() {
        try {
            StatFs s = new StatFs(Environment.getDataDirectory().getPath());
            return Formatter.formatFileSize(this, s.getAvailableBytes()) + " free of "
                    + Formatter.formatFileSize(this, s.getTotalBytes());
        } catch (Exception e) { return "unknown"; }
    }

    void showInfo() {
        String ver = "";
        try { ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception ignored) { }
        String msg = "Model: " + Build.MODEL
                + "\nManufacturer: " + Build.MANUFACTURER
                + "\nAndroid: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")"
                + "\nBuild: " + Build.DISPLAY
                + "\nIP address: " + ip()
                + "\nStorage: " + storage()
                + "\nLauncher: " + ver;
        new AlertDialog.Builder(this).setTitle("Device information").setMessage(msg)
                .setPositiveButton("OK", null).show();
    }

    // ---------------------------------------------------------------- updates

    int myVersionCode() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionCode; } catch (Exception e) { return 0; }
    }

    void checkUpdate() {
        if (false) {
            new AlertDialog.Builder(this).setTitle("Update not configured")
                    .setMessage("Open MainActivity.java and set UPDATE_URL to the address of your update.json, then rebuild.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        toast("Checking for updates...");
        new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(UPDATE_URL).openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(10000);
                BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
                r.close();
                final JSONObject j = new JSONObject(sb.toString());
                runOnUiThread(() -> offerUpdate(j));
            } catch (Exception e) {
                runOnUiThread(() -> toast("Couldn't check for updates. Check the TV's internet connection."));
            }
        }).start();
    }

    void offerUpdate(JSONObject j) {
        final int code = j.optInt("versionCode", 0);
        final String name = j.optString("versionName", "?");
        final String url = j.optString("apkUrl", "");
        String notes = j.optString("notes", "");
        if (code <= myVersionCode() || url.length() == 0) {
            new AlertDialog.Builder(this).setTitle("Up to date")
                    .setMessage("You have the latest version.").setPositiveButton("OK", null).show();
            return;
        }
        new AlertDialog.Builder(this).setTitle("Update available: " + name)
                .setMessage(notes.length() > 0 ? notes : "A newer version of the launcher is available.")
                .setPositiveButton("Download & install", (d, w) -> downloadApk(url))
                .setNegativeButton("Later", null).show();
    }

    void downloadApk(String url) {
        try {
            final DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Request rq = new DownloadManager.Request(Uri.parse(url));
            rq.setTitle("LeEco Glass Launcher update");
            rq.setDestinationInExternalFilesDir(this, null, "update.apk");
            final long id = dm.enqueue(rq);
            toast("Downloading update... you'll be asked to install when it finishes.");
            registerReceiver(new BroadcastReceiver() {
                @Override public void onReceive(Context c, Intent i) {
                    if (i.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) != id) return;
                    try { unregisterReceiver(this); } catch (Exception ignored) { }
                    Uri u = dm.getUriForDownloadedFile(id);
                    if (u == null) { toast("Download failed."); return; }
                    Intent inst = new Intent(Intent.ACTION_VIEW);
                    inst.setDataAndType(u, "application/vnd.android.package-archive");
                    inst.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    if (!safeStart(inst)) toast("Couldn't open the installer. Install update.apk from the file manager.");
                }
            }, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        } catch (Exception e) {
            toast("Download couldn't start.");
        }
    }

    void tvFirmwareUpdate() {
        if (safeStart(new Intent("android.settings.SYSTEM_UPDATE_SETTINGS"))) return;
        new AlertDialog.Builder(this).setTitle("TV system update")
                .setMessage("This TV doesn't expose its updater to apps. Open the TV's own Settings and look for About / System update.")
                .setPositiveButton("Open settings", (d, w) -> openSetting(Settings.ACTION_SETTINGS))
                .setNegativeButton("Close", null).show();
    }

    // Honest input switching: only works if this firmware exposes TV inputs to apps.
    void selectInput(String name) {
        if (name.equals("USB")) {
            launchApp(new String[0], new String[]{"usb", "file", "media"}, "A file / media app");
            return;
        }
        if (Build.VERSION.SDK_INT >= 21 && Tif.open(this, name)) return;
        new AlertDialog.Builder(this)
                .setTitle(name)
                .setMessage("This TV's firmware doesn't let apps switch sources directly.\n\nPress the SOURCE / INPUT button on your remote and pick " + name + ".")
                .setPositiveButton("OK", null)
                .show();
    }

    // kept in its own class so Android 4.4 never loads the API-21 TV classes
    static class Tif {
        static boolean open(Activity act, String want) {
            try {
                TvInputManager m = (TvInputManager) act.getSystemService(Context.TV_INPUT_SERVICE);
                if (m == null) return false;
                for (TvInputInfo info : m.getTvInputList()) {
                    if (!info.isPassthroughInput()) continue;
                    String text = (String.valueOf(info.loadLabel(act)) + " " + info.getId()).toLowerCase(Locale.US);
                    if (match(want, text)) {
                        Intent i = new Intent(Intent.ACTION_VIEW,
                                TvContract.buildChannelUriForPassthroughInput(info.getId()));
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        act.startActivity(i);
                        return true;
                    }
                }
            } catch (Throwable ignored) { }
            return false;
        }

        static boolean match(String want, String t) {
            if (want.startsWith("HDMI")) {
                String n = want.substring(want.length() - 1);
                return t.contains("hdmi") && (t.contains("hdmi " + n) || t.contains("hdmi" + n) || t.endsWith(n));
            }
            return t.contains("composite") || t.contains("cvbs") || t.contains("analog") || t.contains("av");
        }
    }
}
