package com.alexbatalov.fallout2ce;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.Toast;
import java.io.IOException;
import java.util.Locale;
import org.libsdl.app.SDLActivity;

// Android overlay: tapping commands never sends a click through to the game map.
final class GameCommandHud {
    private final Activity activity;
    private final ViewGroup root;
    private final View gameView;
    private final Button handle;
    private final SharedPreferences preferences;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private PopupWindow popup;
    private int[] heldKeys = new int[0];
    private boolean sending, disposed;
    private float touchX, touchY, startX, startY;
    private boolean dragging;

    private static native int nativeGetSpeed();
    private static native void nativeSetSpeed(int percent);

    GameCommandHud(Activity activity, ViewGroup root, View gameView) {
        this.activity = activity; this.root = root; this.gameView = gameView;
        preferences = activity.getSharedPreferences("command-hud", Context.MODE_PRIVATE);
        handle = new Button(activity);
        handle.setAllCaps(false);
        handle.setTextSize(18);
        handle.setTextColor(LauncherUi.GOLD);
        handle.setContentDescription("Öppna snabbkommandon. Dra för att flytta knappen.");
        handle.setPadding(0, 0, 0, 0);
        handle.setMinWidth(0);
        handle.setMinimumWidth(0);
        handle.setBackground(panel());
        root.addView(handle, new RelativeLayout.LayoutParams(dp(66), dp(44)));
        updateHandle();
        handle.setOnClickListener(view -> show());
        handle.setOnTouchListener((view, event) -> drag(event));
        root.addOnLayoutChangeListener((view, l, t, r, b, ol, ot, or, ob) -> {
            if (!disposed && (r-l != or-ol || b-t != ob-ot)) position();
        });
        root.post(this::position);
    }

    private int dp(int value) { return LauncherUi.dp(activity, value); }

    private GradientDrawable panel() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.argb(240, 23, 27, 24));
        drawable.setCornerRadius(dp(10));
        drawable.setStroke(dp(1), Color.rgb(137, 115, 61));
        return drawable;
    }

    private void updateHandle() {
        int speed = nativeGetSpeed();
        handle.setText(speed == 100 ? "≡" : "≡ " + speedLabel(speed));
    }

    private static String speedLabel(int percent) {
        return String.format(Locale.ROOT, percent % 100 == 0 ? "%.0f×" : "%.1f×", percent / 100.0);
    }

    private void position() {
        if (disposed) return;
        float x = preferences.getFloat("x", 1f) * Math.max(0, root.getWidth() - handle.getWidth() - dp(8));
        float y = preferences.getFloat("y", 0f) * Math.max(0, root.getHeight() - handle.getHeight() - dp(8));
        move(x, y);
    }

    private void move(float x, float y) {
        handle.setX(Math.max(dp(8), Math.min(x, root.getWidth() - handle.getWidth() - dp(8))));
        handle.setY(Math.max(dp(8), Math.min(y, root.getHeight() - handle.getHeight() - dp(8))));
    }

    private boolean drag(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                touchX = event.getRawX(); touchY = event.getRawY();
                startX = handle.getX(); startY = handle.getY(); dragging = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getRawX() - touchX, dy = event.getRawY() - touchY;
                if (Math.abs(dx) + Math.abs(dy) > dp(8)) dragging = true;
                if (dragging) move(startX + dx, startY + dy);
                return true;
            case MotionEvent.ACTION_UP:
                if (dragging) {
                    preferences.edit()
                            .putFloat("x", handle.getX() / Math.max(1, root.getWidth() - handle.getWidth() - dp(8)))
                            .putFloat("y", handle.getY() / Math.max(1, root.getHeight() - handle.getHeight() - dp(8))).apply();
                } else handle.performClick();
                return true;
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                return true;
            default:
                return true;
        }
    }

    private void show() {
        if (disposed || sending) return;
        if (closeMenu()) return;
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(8), dp(12), dp(12));
        panel.setBackground(panel());
        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        android.widget.TextView title = new android.widget.TextView(activity);
        title.setText("Snabbkommandon");
        title.setTextColor(LauncherUi.GOLD);
        title.setTextSize(20);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button close = button("×", () -> closeMenu());
        close.setContentDescription("Stäng snabbkommandon");
        header.addView(close, new LinearLayout.LayoutParams(dp(48), dp(48)));
        panel.addView(header);

        ScrollView scroll = new ScrollView(activity);
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body);
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        heading(body, "Spelhastighet");
        LinearLayout row = null;
        int index = 0;
        for (int speed : new int[]{50, 100, 150, 200, 300, 400}) {
            if (index++ % 3 == 0) row = row(body);
            Button choice = button(speedLabel(speed), () -> {
                nativeSetSpeed(speed);
                updateHandle();
                closeMenu();
                gameView.requestFocus();
            });
            if (nativeGetSpeed() == speed) choice.setTextColor(LauncherUi.GOLD);
            row.addView(choice, cell());
        }

        heading(body, "Följeslagare och RPU");
        LauncherUi.note(activity, body, "Orderna används i spelet. Följeslagarna måste kunna utföra ordern.");
        try {
            IniDocument config = new IniDocument(new SettingsRepository(activity).read("mods/party_orders.ini"));
            String[][] orders = {
                {"Loota", "SETTINGS", "LootingOrderKey", "34"},
                {"Läk gruppen", "SETTINGS", "HealingOrderKey", "33"},
                {"Samla gruppen", "SETTINGS", "RegroupOrderKey", "19"},
                {"Sprid ut", "SETTINGS", "SpreadOrderKey", "45"},
                {"Hölstra vapen", "SETTINGS", "HolsterOrderKey", "35"},
                {"Plocka upp / loota", "SETTINGS", "PickUpKey", "20"},
                {"Växla egen lootning", "SETTINGS", "SwitchKey", "11"},
                {"Byt ammunition", "SETTINGS", "AmmoTypeOrderKey", "48+29"},
                {"Tillåt / stoppa salvor", "BURST_CONTROL", "burst_key", "32"}
            };
            index = 0;
            for (String[] order : orders) {
                if (index++ % 2 == 0) row = row(body);
                String binding = config.get(order[1], order[2], order[3]);
                if (binding.trim().isEmpty() || binding.trim().equals("-1")) binding = order[3];
                addCommand(row, order[0], binding);
            }
        } catch (IOException error) {
            LauncherUi.note(activity, body, "Kunde inte läsa Party Orders: " + error.getMessage());
        }

        heading(body, "Vanliga kommandon");
        String[][] common = {
            {"Inventarie", "23"}, {"Karaktär", "46"}, {"Pip-Boy", "25"}, {"Karta", "15"},
            {"Spara", "62"}, {"Ladda", "63"}, {"Byt hand", "48"}, {"Byt vapenläge", "49"},
            {"Avsluta tur", "57"}, {"Meny / Esc", "1"}
        };
        index = 0;
        for (String[] command : common) {
            if (index++ % 2 == 0) row = row(body);
            addCommand(row, command[0], command[1]);
        }
        heading(body, "Färdigheter");
        String[] skills = {"Smyga", "Dyrka lås", "Stjäla", "Fällor", "Första hjälpen", "Läkare", "Vetenskap", "Reparera"};
        for (int i = 0; i < skills.length; i++) {
            if (i % 2 == 0) row = row(body);
            addCommand(row, skills[i], Integer.toString(i + 2));
        }
        LauncherUi.note(activity, body, "Dra ≡-knappen för att flytta den. Tangentbindningar följer mods/party_orders.ini. Hastigheten återgår till 1× när appens spelprocess startas om.");

        int width = Math.min(dp(380), root.getWidth() - dp(16));
        int height = Math.min(dp(680), root.getHeight() - dp(64));
        popup = new PopupWindow(panel, width, height, true);
        popup.setBackgroundDrawable(panel());
        popup.setOutsideTouchable(true);
        popup.setElevation(dp(8));
        popup.setOnDismissListener(() -> { popup = null; gameView.requestFocus(); });
        popup.showAtLocation(root, Gravity.TOP | Gravity.RIGHT, dp(8), dp(52));
    }

    private void heading(LinearLayout body, String label) {
        LauncherUi.text(activity, body, label, 17, LauncherUi.GOLD);
    }

    private LinearLayout row(LinearLayout body) {
        LinearLayout row = new LinearLayout(activity);
        body.addView(row, new LinearLayout.LayoutParams(-1, -2));
        return row;
    }

    private LinearLayout.LayoutParams cell() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(50), 1);
        params.setMargins(dp(2), dp(2), dp(2), dp(2));
        return params;
    }

    private Button button(String label, Runnable action) {
        Button button = new Button(activity);
        button.setText(label);
        button.setTextSize(14);
        button.setTextColor(Color.rgb(228, 230, 223));
        button.setAllCaps(false);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(47, 57, 48));
        background.setCornerRadius(dp(6));
        background.setStroke(dp(1), Color.rgb(83, 98, 80));
        button.setBackground(background);
        button.setPadding(dp(4), 0, dp(4), 0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private void addCommand(LinearLayout row, String label, String binding) {
        try {
            int[] keys = CommandBindings.parse(binding);
            Button command = button(label, () -> send(keys));
            command.setEnabled(keys.length > 0);
            if (keys.length == 0) command.setText(label + " (av)");
            row.addView(command, cell());
        } catch (IllegalArgumentException invalid) {
            Button command = button(label + " (?)", () ->
                    Toast.makeText(activity, invalid.getMessage(), Toast.LENGTH_LONG).show());
            row.addView(command, cell());
        }
    }

    private void send(int[] keys) {
        if (sending || disposed || keys.length == 0) return;
        closeMenu();
        gameView.requestFocus();
        sending = true;
        // Return focus to SDL before pressing keys; this also covers its 125 ms
        // lost-focus wait. A genuine SDL press updates key_pressed and HOOK_KEYPRESS.
        handler.postDelayed(() -> pressWhenFocused(keys, 0), 200);
    }

    private void pressWhenFocused(int[] keys, int attempts) {
        if (disposed) return;
        if (!activity.hasWindowFocus()) {
            if (attempts < 10) handler.postDelayed(() -> pressWhenFocused(keys, attempts + 1), 100);
            else sending = false;
            return;
        }
        heldKeys = keys;
        for (int key : keys) SDLActivity.onNativeKeyDown(key);
        handler.postDelayed(this::releaseKeys, 100);
    }

    private void releaseKeys() {
        for (int i = heldKeys.length - 1; i >= 0; i--) SDLActivity.onNativeKeyUp(heldKeys[i]);
        heldKeys = new int[0];
        sending = false;
    }

    boolean closeMenu() {
        if (popup == null) return false;
        popup.dismiss();
        popup = null;
        return true;
    }

    void pause() {
        handler.removeCallbacksAndMessages(null);
        releaseKeys();
        closeMenu();
    }

    void dispose() {
        pause();
        disposed = true;
        root.removeView(handle);
    }
}
