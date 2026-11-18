package com.bloqueo.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class MainActivity extends Activity {
    private static final int COMPANION = 0, CHOOSE = 1, APPS = 2, SUMMARY = 3, TIME = 4, HOME = 5;
    private static final String[] COMPANIONS = {"Órbita", "Brisa", "Nexo", "Nova", "Pulso"};
    private static final String[] DESCRIPTIONS = {
        "Curioso y dinámico.", "Sereno y constante.", "Directo y decidido.",
        "Creativa y optimista.", "Tu impulso para avanzar."
    };
    private static final Set<String> MESSAGING = new HashSet<>(Arrays.asList(
        "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "org.telegram.messenger.web",
        "com.discord", "org.thoughtcrime.securesms", "com.facebook.orca", "jp.naver.line.android"
    ));
    private static final Set<String> RECOMMENDED = new HashSet<>(Arrays.asList(
        "com.instagram.android", "com.zhiliaoapp.musically", "com.ss.android.ugc.trill",
        "com.google.android.youtube", "com.facebook.katana", "com.twitter.android",
        "com.snapchat.android", "com.netflix.mediaclient", "com.reddit.frontpage",
        "com.pinterest", "com.discord", "com.google.android.apps.youtube.music"
    ));
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (screen == HOME && Prefs.active(MainActivity.this)) render();
            handler.postDelayed(this, 1000);
        }
    };
    private int screen = COMPANION;
    private boolean dark = true;
    private int companion = 0;
    private int minutes = 25;
    private final List<AppInfo> installedApps = new ArrayList<>();
    private LinearLayout root, body, footer;
    private int bg, fg, muted, surface, line, primary, onPrimary;

    static final class AppInfo {
        final String name, packageName;
        AppInfo(String name, String packageName) {
            this.name = name;
            this.packageName = packageName;
        }
    }

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        dark = Prefs.of(this).getBoolean(Prefs.DARK, true);
        companion = Prefs.of(this).getInt(Prefs.COMPANION, 0);
        minutes = Prefs.of(this).getInt(Prefs.DURATION, 25);
        screen = Prefs.of(this).getBoolean(Prefs.ONBOARDED, false) ? HOME : COMPANION;
        loadInstalledApps();
        render();
        handler.postDelayed(tick, 1000);
    }

    @Override protected void onResume() {
        super.onResume();
        if (root != null && screen == HOME) render();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(tick);
        super.onDestroy();
    }

    private int d(float dp) { return Math.round(dp * getResources().getDisplayMetrics().density); }

    private void theme() {
        bg = Color.parseColor(dark ? "#09090B" : "#FFFFFF");
        fg = Color.parseColor(dark ? "#FAFAFA" : "#111113");
        muted = Color.parseColor(dark ? "#A7A7AD" : "#64646B");
        surface = Color.parseColor(dark ? "#1C1C1F" : "#F4F4F5");
        line = Color.parseColor(dark ? "#3B3B40" : "#E4E4E7");
        primary = dark ? Color.WHITE : Color.BLACK;
        onPrimary = dark ? Color.BLACK : Color.WHITE;
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        getWindow().getDecorView().setSystemUiVisibility(dark ? 0 :
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    }

    private GradientDrawable shape(int color, int radius, int borderColor) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(d(radius));
        if (borderColor != Color.TRANSPARENT) g.setStroke(d(1), borderColor);
        return g;
    }
    private GradientDrawable circle(int color, int borderColor) {
        GradientDrawable g = shape(color, 200, borderColor);
        g.setShape(GradientDrawable.OVAL);
        return g;
    }
    private TextView text(String value, int size, boolean heavy, int color) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setIncludeFontPadding(false);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setTypeface(Typeface.create("sans-serif", heavy ? Typeface.BOLD : Typeface.NORMAL));
        return v;
    }
    private LinearLayout vertical(int spacing) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(d(spacing), d(spacing), d(spacing), d(spacing));
        return c;
    }
    private LinearLayout horizontal() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.HORIZONTAL);
        c.setGravity(Gravity.CENTER_VERTICAL);
        return c;
    }
    private void addSpace(LinearLayout to, int height) {
        View v = new View(this);
        to.addView(v, new LinearLayout.LayoutParams(1, d(height)));
    }
    private void addText(LinearLayout to, String s, int size, boolean bold, int color, int bottom) {
        TextView t = text(s, size, bold, color);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = d(bottom);
        to.addView(t, lp);
    }
    private TextView circlePlaceholder(int diameter) {
        TextView v = text("", 16, true, fg);
        v.setGravity(Gravity.CENTER);
        v.setBackground(circle(dark ? Color.WHITE : Color.WHITE, dark ? Color.parseColor("#5D5D62") : line));
        v.setLayoutParams(new LinearLayout.LayoutParams(d(diameter), d(diameter)));
        return v;
    }
    private void button(String label, Runnable action, boolean solid) {
        LinearLayout outer = horizontal();
        outer.setGravity(Gravity.CENTER);
        outer.setBackground(shape(solid ? primary : bg, 17, solid ? Color.TRANSPARENT : line));
        outer.setClickable(true);
        outer.setOnClickListener(v -> action.run());
        TextView t = text(label, 17, true, solid ? onPrimary : fg);
        outer.addView(t);
        if (label.toLowerCase(Locale.ROOT).contains("continuar") || label.contains("Empezar")) {
            TextView arrow = text("→", 25, false, solid ? onPrimary : fg);
            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-2, -2);
            alp.leftMargin = d(9);
            outer.addView(arrow, alp);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, d(56));
        lp.bottomMargin = d(10);
        footer.addView(outer, lp);
    }
    private void showScreen(int s) {
        screen = s;
        render();
    }
    private void header() {
        LinearLayout bar = horizontal();
        bar.setPadding(d(22), d(16), d(22), d(14));
        TextView brand = text("BLOQUEO", 18, true, fg);
        bar.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        String position = "Móvil · Beta";
        if (screen == CHOOSE || screen == SUMMARY) position = "1 de 3";
        if (screen == APPS) position = "2 de 3";
        if (screen == TIME) position = "3 de 3";
        TextView progress = text(position, 12, false, muted);
        bar.addView(progress);
        TextView toggle = text(dark ? "☀  Claro" : "☾  Oscuro", 13, true, fg);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, d(36));
        p.leftMargin = d(14);
        toggle.setGravity(Gravity.CENTER);
        toggle.setPadding(d(10), 0, d(10), 0);
        toggle.setBackground(shape(surface, 16, line));
        toggle.setOnClickListener(v -> {
            root.animate().alpha(0.55f).setDuration(120).withEndAction(() -> {
                dark = !dark;
                Prefs.of(this).edit().putBoolean(Prefs.DARK, dark).apply();
                render();
                root.setAlpha(0.55f);
                root.animate().alpha(1f).setDuration(160).start();
            }).start();
        });
        bar.addView(toggle, p);
        root.addView(bar);
    }
    private void screenTitle(String title, String subtitle) {
        addSpace(body, 12);
        addText(body, title, 30, true, fg, 9);
        if (!subtitle.isEmpty()) addText(body, subtitle, 15, false, muted, 16);
    }
    private void back(Runnable action) {
        TextView t = text("←  Volver", 14, false, muted);
        t.setPadding(0, d(8), 0, d(8));
        t.setOnClickListener(v -> action.run());
        body.addView(t);
        addSpace(body, 12);
    }

    private void render() {
        theme();
        root = vertical(0);
        root.setPadding(0, 0, 0, 0);
        root.setBackgroundColor(bg);
        setContentView(root);
        header();
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout.LayoutParams sc = new LinearLayout.LayoutParams(-1, 0, 1f);
        root.addView(scroll, sc);
        body = vertical(22);
        body.setPadding(d(22), d(5), d(22), d(20));
        scroll.addView(body);
        footer = vertical(22);
        footer.setPadding(d(22), d(10), d(22), d(12));
        root.addView(footer);
        switch (screen) {
            case COMPANION: renderCompanion(); break;
            case CHOOSE: renderChoose(); break;
            case APPS: renderApps(); break;
            case SUMMARY: renderSummary(); break;
            case TIME: renderTime(); break;
            default: renderHome(); break;
        }
    }

    private void renderCompanion() {
        screenTitle("Elige tu compañero", "Te acompañará en tus sesiones y metas.");
        addSpace(body, 20);
        LinearLayout chooser = horizontal();
        chooser.setGravity(Gravity.CENTER);
        TextView prev = text("‹", 40, false, fg);
        prev.setGravity(Gravity.CENTER);
        prev.setBackground(circle(surface, Color.TRANSPARENT));
        prev.setOnClickListener(v -> changeCompanion(-1));
        chooser.addView(prev, new LinearLayout.LayoutParams(d(54), d(54)));
        FrameLayout center = new FrameLayout(this);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, d(190), 1f);
        chooser.addView(center, clp);
        TextView avatar = circlePlaceholder(156);
        FrameLayout.LayoutParams avp = new FrameLayout.LayoutParams(d(156), d(156), Gravity.CENTER);
        center.addView(avatar, avp);
        TextView next = text("›", 40, false, fg);
        next.setGravity(Gravity.CENTER);
        next.setBackground(circle(surface, Color.TRANSPARENT));
        next.setOnClickListener(v -> changeCompanion(1));
        chooser.addView(next, new LinearLayout.LayoutParams(d(54), d(54)));
        body.addView(chooser);
        addSpace(body, 12);
        TextView name = text(COMPANIONS[companion], 26, true, fg);
        name.setGravity(Gravity.CENTER);
        body.addView(name);
        addSpace(body, 8);
        TextView desc = text(DESCRIPTIONS[companion], 15, false, muted);
        desc.setGravity(Gravity.CENTER);
        body.addView(desc);
        addSpace(body, 25);
        TextView dots = text("●  ●  ●  ●  ●", 15, false, muted);
        dots.setGravity(Gravity.CENTER);
        body.addView(dots);
        button("Continuar", () -> {
            Prefs.of(this).edit().putInt(Prefs.COMPANION, companion).apply();
            showScreen(CHOOSE);
        }, true);
    }

    private void changeCompanion(int dir) {
        companion = (companion + COMPANIONS.length + dir) % COMPANIONS.length;
        render();
        body.setAlpha(0.65f);
        body.setScaleX(0.985f);
        body.setScaleY(0.985f);
        body.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(160).start();
    }

    private void renderChoose() {
        back(() -> showScreen(COMPANION));
        FrameLayout frame = new FrameLayout(this);
        frame.setBackground(shape(surface, 38, line));
        TextView intro = text("Primero vamos a quitar algunas distracciones.", 25, true, fg);
        intro.setPadding(d(24), d(25), d(24), d(25));
        frame.addView(intro);
        body.addView(frame);
        addSpace(body, 25);
        TextView photo = circlePlaceholder(112);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(d(112), d(112));
        cp.gravity = Gravity.RIGHT;
        body.addView(photo, cp);
        addSpace(body, 22);
        addText(body, "¿Quieres que las elija por ti?", 23, true, fg, 9);
        addText(body, "Puedes cambiar tu selección después.", 14, false, muted, 0);
        addSpace(body, 12);
        button("Sí, usar recomendaciones", () -> {
            Set<String> selected = new HashSet<>();
            Prefs.of(this).edit().putBoolean(Prefs.ALLOW_CHAT, false).apply();
            for (AppInfo app : installedApps) {
                if (RECOMMENDED.contains(app.packageName)) selected.add(app.packageName);
            }
            Prefs.setBlocked(this, selected);
            Prefs.of(this).edit().putString(Prefs.MODE, "recommended").apply();
            showScreen(SUMMARY);
        }, true);
        button("Prefiero elegir", () -> {
            Prefs.setBlocked(this, new HashSet<>());
            Prefs.of(this).edit().putBoolean(Prefs.ALLOW_CHAT, false)
                .putString(Prefs.MODE, "manual").apply();
            showScreen(APPS);
        }, false);
    }

    private void loadInstalledApps() {
        installedApps.clear();
        PackageManager pm = getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN);
        query.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolved = pm.queryIntentActivities(query, 0);
        Map<String, AppInfo> uniques = new HashMap<>();
        for (ResolveInfo ri : resolved) {
            if (ri.activityInfo == null) continue;
            String packageName = ri.activityInfo.packageName;
            if (packageName.equals(getPackageName())) continue;
            try {
                ApplicationInfo a = pm.getApplicationInfo(packageName, 0);
                if ((a.flags & ApplicationInfo.FLAG_SYSTEM) != 0 && !RECOMMENDED.contains(packageName)) continue;
                String label = String.valueOf(ri.loadLabel(pm));
                if (label.trim().isEmpty()) label = packageName;
                uniques.put(packageName, new AppInfo(label, packageName));
            } catch (PackageManager.NameNotFoundException ignored) { }
        }
        installedApps.addAll(uniques.values());
        Collator collator = Collator.getInstance(new Locale("es", "PE"));
        installedApps.sort((a, b) -> collator.compare(a.name, b.name));
    }

    private void renderApps() {
        back(() -> showScreen(CHOOSE));
        screenTitle("Elige qué quieres dejar atrás", "Selecciona las aplicaciones que no quieres abrir durante tus sesiones.");
        Set<String> selected = Prefs.blocked(this);
        TextView counter = text(selected.size() + " aplicaciones seleccionadas", 13, true, muted);
        body.addView(counter);
        addSpace(body, 12);
        if (installedApps.isEmpty()) addText(body, "No se encontraron aplicaciones. Revisa los permisos de visibilidad del dispositivo.", 15, false, muted, 0);
        for (AppInfo app : installedApps) {
            LinearLayout card = horizontal();
            card.setPadding(d(14), d(11), d(14), d(11));
            card.setBackground(shape(surface, 14, line));
            TextView ico = circlePlaceholder(40);
            card.addView(ico);
            TextView label = text(app.name, 15, true, fg);
            LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(0, -2, 1);
            ll.leftMargin = d(12);
            card.addView(label, ll);
            TextView check = text(selected.contains(app.packageName) ? "✓" : "+", 18, true, selected.contains(app.packageName) ? primary : muted);
            card.addView(check);
            card.setOnClickListener(v -> {
                Set<String> current = Prefs.blocked(this);
                if (!current.add(app.packageName)) current.remove(app.packageName);
                Prefs.setBlocked(this, current);
                render();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, d(66));
            lp.bottomMargin = d(8);
            body.addView(card, lp);
        }
        addSpace(body, 10);
        addText(body, "Contenido adulto: el filtro de sitios web todavía no está disponible en esta beta Android. No se marcará como protegido hasta implementarlo.", 13, false, muted, 0);
        button("Continuar", () -> {
            if (Prefs.blocked(this).isEmpty()) {
                Toast.makeText(this, "Selecciona al menos una aplicación", Toast.LENGTH_SHORT).show();
                return;
            }
            showScreen(TIME);
        }, true);
    }

    private void renderSummary() {
        back(() -> showScreen(CHOOSE));
        screenTitle("Tu protección está lista.", "Revisa las aplicaciones recomendadas para bloquear.");
        Set<String> selected = Prefs.blocked(this);
        if (selected.isEmpty()) {
            addText(body, "No encontramos redes de entretenimiento entre tus apps instaladas. Elige algunas manualmente.", 16, false, muted, 16);
            button("Elegir aplicaciones", () -> showScreen(APPS), false);
        }
        for (AppInfo app : installedApps) {
            if (!selected.contains(app.packageName)) continue;
            LinearLayout card = horizontal();
            card.setPadding(d(14), d(10), d(14), d(10));
            card.setBackground(shape(surface, 14, line));
            card.addView(circlePlaceholder(40));
            TextView label = text(app.name, 15, true, fg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
            lp.leftMargin = d(12);
            card.addView(label, lp);
            TextView enabled = text("Bloqueada", 12, true, muted);
            card.addView(enabled);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, d(60));
            cp.bottomMargin = d(9);
            body.addView(card, cp);
        }
        addSpace(body, 16);
        LinearLayout chatRow = horizontal();
        chatRow.setPadding(d(14), d(15), d(14), d(15));
        chatRow.setBackground(shape(surface, 16, line));
        TextView chatLabel = text("Mantener aplicaciones de mensajería disponibles", 15, true, fg);
        chatRow.addView(chatLabel, new LinearLayout.LayoutParams(0, -2, 1));
        boolean allowed = Prefs.chatAllowed(this);
        TextView chatSwitch = text(allowed ? "ACTIVADO" : "DESACTIVADO", 12, true, allowed ? fg : muted);
        LinearLayout.LayoutParams swParams = new LinearLayout.LayoutParams(-2, -2);
        swParams.leftMargin = d(8);
        chatRow.addView(chatSwitch, swParams);
        chatRow.setOnClickListener(v -> {
            Prefs.of(this).edit().putBoolean(Prefs.ALLOW_CHAT, !Prefs.chatAllowed(this)).apply();
            render();
        });
        body.addView(chatRow);
        addSpace(body, 10);
        addText(body, "La opción de mensajería excluye las apps compatibles del bloqueo, aunque sigan seleccionadas. El filtrado web y de contenido adulto aún no está implementado.", 13, false, muted, 0);
        button("Editar selección", () -> showScreen(APPS), false);
        button("Confirmar y continuar", () -> {
            if (Prefs.blocked(this).isEmpty()) showScreen(APPS);
            else showScreen(TIME);
        }, true);
    }

    private void renderTime() {
        back(() -> showScreen(Prefs.of(this).getString(Prefs.MODE, "manual").equals("manual") ? APPS : SUMMARY));
        screenTitle("Protege tu tiempo sin negociar.", "Elige cuándo mantener bloqueadas tus distracciones.");
        addSpace(body, 6);
        scheduleOption("Protección permanente · 24/7", "Bloquea las aplicaciones elegidas todos los días.", () -> enableSchedule("always"));
        scheduleOption("Solo lunes a viernes", "Bloquea durante los cinco días laborables.", () -> enableSchedule("weekdays"));
        scheduleOption("Configurar horario", "Elige días y un intervalo de horas.", this::configureSchedule);
        addSpace(body, 18);
        addText(body, "O crea una sesión de enfoque", 19, true, fg, 8);
        addText(body, "También puedes usar BLOQUEO solo durante una sesión temporal.", 14, false, muted, 12);
        int[] choices = {25, 45, 60, 90};
        for (int length : choices) {
            LinearLayout card = horizontal();
            card.setBackground(shape(minutes == length ? primary : surface, 16, minutes == length ? Color.TRANSPARENT : line));
            card.setPadding(d(18), 0, d(18), 0);
            TextView label = text(length == 25 ? "25 minutos · Pomodoro" : length + " minutos", 16, true, minutes == length ? onPrimary : fg);
            card.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
            card.addView(text(minutes == length ? "✓" : "○", 20, true, minutes == length ? onPrimary : muted));
            card.setOnClickListener(v -> {
                minutes = length;
                Prefs.of(this).edit().putInt(Prefs.DURATION, minutes).apply();
                render();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, d(63));
            lp.bottomMargin = d(9);
            body.addView(card, lp);
        }
        addSpace(body, 10);
        addText(body, "La protección de apps requiere habilitar el servicio de Accesibilidad de Android. El contenido de páginas web todavía no se filtra.", 13, false, muted, 7);
        button("Empezar sesión", () -> {
            if (Prefs.blocked(this).isEmpty()) {
                Toast.makeText(this, "Primero selecciona aplicaciones", Toast.LENGTH_LONG).show();
                showScreen(APPS);
                return;
            }
            Prefs.start(this, minutes);
            showScreen(HOME);
        }, true);
    }

    private void scheduleOption(String heading, String description, Runnable action) {
        LinearLayout card = vertical(16);
        card.setBackground(shape(surface, 17, line));
        card.setPadding(d(17), d(17), d(17), d(17));
        addText(card, heading, 17, true, fg, 7);
        addText(card, description, 13, false, muted, 0);
        card.setClickable(true);
        card.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = d(11);
        body.addView(card, params);
    }

    private void enableSchedule(String mode) {
        if (Prefs.blocked(this).isEmpty()) {
            Toast.makeText(this, "Selecciona al menos una app antes de activar", Toast.LENGTH_LONG).show();
            showScreen(APPS);
            return;
        }
        Prefs.setSchedule(this, mode);
        showScreen(HOME);
    }

    private void configureSchedule() {
        final String[] days = {"Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"};
        final boolean[] chosen = new boolean[7];
        int oldMask = Prefs.of(this).getInt(Prefs.DAYS, 0x7f);
        for (int i = 0; i < chosen.length; i++) chosen[i] = (oldMask & (1 << i)) != 0;
        new AlertDialog.Builder(this)
            .setTitle("Elige los días")
            .setMultiChoiceItems(days, chosen, (dialog, which, checked) -> chosen[which] = checked)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Continuar", (dialog, which) -> {
                int mask = 0;
                for (int i = 0; i < chosen.length; i++) if (chosen[i]) mask |= (1 << i);
                if (mask == 0) {
                    Toast.makeText(this, "Selecciona al menos un día", Toast.LENGTH_SHORT).show();
                    return;
                }
                final int savedMask = mask;
                int currentStart = Prefs.of(this).getInt(Prefs.START_MINUTE, 8 * 60);
                new TimePickerDialog(this, (picker, sh, sm) -> {
                    int currentEnd = Prefs.of(this).getInt(Prefs.END_MINUTE, 22 * 60);
                    new TimePickerDialog(this, (endPicker, eh, em) -> {
                        int startMinute = sh * 60 + sm;
                        int endMinute = eh * 60 + em;
                        if (startMinute == endMinute) {
                            Toast.makeText(this, "El inicio y el fin deben ser distintos", Toast.LENGTH_LONG).show();
                            return;
                        }
                        Prefs.of(this).edit().putInt(Prefs.DAYS, savedMask)
                            .putInt(Prefs.START_MINUTE, startMinute).putInt(Prefs.END_MINUTE, endMinute).apply();
                        enableSchedule("custom");
                    }, currentEnd / 60, currentEnd % 60, true).show();
                }, currentStart / 60, currentStart % 60, true).show();
            }).show();
    }

    private boolean serviceEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) return false;
        String full = new ComponentName(this, BlockService.class).flattenToString();
        return enabled.toLowerCase(Locale.ROOT).contains(full.toLowerCase(Locale.ROOT));
    }

    private void renderHome() {
        screenTitle("Tu enfoque empieza aquí.", "Haz espacio para lo que sí importa.");
        addSpace(body, 4);
        long remaining = Math.max(0, Prefs.of(this).getLong(Prefs.END, 0) - System.currentTimeMillis());
        String schedule = Prefs.of(this).getString(Prefs.SCHEDULE, "session");
        boolean active = Prefs.active(this);
        boolean timed = "session".equals(schedule);
        LinearLayout hero = vertical(22);
        hero.setBackground(shape(surface, 25, line));
        addText(hero, active ? "PROTECCIÓN ACTIVADA" : "PROTECCIÓN EN PAUSA", 12, true, muted, 12);
        String clock;
        if ("always".equals(schedule)) clock = "24/7";
        else if ("weekdays".equals(schedule)) clock = "L–V";
        else if ("custom".equals(schedule)) {
            int start = Prefs.of(this).getInt(Prefs.START_MINUTE, 480);
            int end = Prefs.of(this).getInt(Prefs.END_MINUTE, 1320);
            clock = String.format(Locale.ROOT, "%02d:%02d–%02d:%02d", start / 60, start % 60, end / 60, end % 60);
        } else clock = timed && active ? String.format(Locale.ROOT, "%02d:%02d", remaining / 60000, (remaining / 1000) % 60) : "00:00";
        addText(hero, clock, "custom".equals(schedule) ? 29 : 49, true, fg, 12);
        addText(hero, active ? "Las aplicaciones elegidas se evitarán mientras esté activa la protección." :
                "Esta programación no está activa en este momento.", 14, false, muted, 0);
        body.addView(hero);
        addSpace(body, 18);
        boolean service = serviceEnabled();
        LinearLayout permission = vertical(18);
        permission.setBackground(shape(surface, 19, service ? line : Color.parseColor(dark ? "#756446" : "#D6B56A")));
        addText(permission, service ? "Protección Android habilitada" : "Activa el permiso de Accesibilidad", 17, true, fg, 7);
        addText(permission, service ? "BLOQUEO puede detectar el nombre técnico de la app abierta." :
            "Para cerrar apps seleccionadas, habilita voluntariamente el servicio BLOQUEO en Ajustes → Accesibilidad. No lee conversaciones ni pantallas. Puedes desactivarlo cuando quieras. En Android 13 o superior, quizá debas permitir antes los ajustes restringidos desde Información de la app.", 14, false, muted, 0);
        body.addView(permission);
        if (!service) {
            addSpace(body, 12);
            TextView grant = text("Abrir Ajustes de accesibilidad →", 15, true, fg);
            grant.setPadding(d(12), d(15), d(12), d(15));
            grant.setBackground(shape(surface, 14, line));
            grant.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
            body.addView(grant);
        }
        addSpace(body, 24);
        addText(body, "Aplicaciones seleccionadas: " + Prefs.blocked(this).size(), 15, true, fg, 10);
        for (AppInfo app : installedApps) {
            if (Prefs.blocked(this).contains(app.packageName)) addText(body, "· " + app.name, 14, false, muted, 7);
        }
        addSpace(body, 15);
        if (active || (!"session".equals(schedule) && !"paused".equals(schedule))) {
            button("Pausar protección", () -> new AlertDialog.Builder(this)
                .setTitle("¿Pausar BLOQUEO?")
                .setMessage("El bloqueo de aplicaciones dejará de estar activo hasta que vuelvas a iniciarlo.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Pausar", (dialog, which) -> {
                    Prefs.stop(this);
                    showScreen(HOME);
                }).show(), false);
        } else {
            button("Empezar otra sesión", () -> showScreen(TIME), true);
        }
        button("Cambiar horario", () -> showScreen(TIME), false);
        button("Cambiar aplicaciones", () -> showScreen(APPS), false);
    }

    @Override public void onBackPressed() {
        switch (screen) {
            case COMPANION: super.onBackPressed(); break;
            case CHOOSE: showScreen(COMPANION); break;
            case SUMMARY: showScreen(CHOOSE); break;
            case APPS: showScreen(Prefs.of(this).getBoolean(Prefs.ONBOARDED, false) ? HOME : CHOOSE); break;
            case TIME: showScreen(Prefs.of(this).getString(Prefs.MODE, "manual").equals("manual") ? APPS : SUMMARY); break;
            default: super.onBackPressed(); break;
        }
    }
}
