package com.alexbatalov.fallout2ce;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.Locale;
import org.libsdl.app.SDLActivity;

// A focusable, touch-modal popup consumes touches outside it instead of clicking the map.
final class GameCommandHud {
    interface GameInput {
        int speed();
        void speed(int percent);
        void down(int key);
        void up(int key);
    }
    private static native int nativeGetSpeed();
    private static native void nativeSetSpeed(int percent);
    private static final GameInput SDL_INPUT = new GameInput() {
        public int speed() { return nativeGetSpeed(); }
        public void speed(int percent) { nativeSetSpeed(percent); }
        public void down(int key) { SDLActivity.onNativeKeyDown(key); }
        public void up(int key) { SDLActivity.onNativeKeyUp(key); }
    };

    private final Activity activity;
    private final ViewGroup root;
    private final View gameView;
    private final Button handle;
    private final SharedPreferences preferences;
    private final GameInput input;
    private final boolean rpu;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final View.OnLayoutChangeListener layoutListener;
    private final Map<Integer, Button> speeds = new LinkedHashMap<>();
    private final Map<String, Button> tabs = new LinkedHashMap<>();
    private Set<String> favorites;
    private String section;
    private IniDocument partyConfig;
    private PopupWindow popup;
    private Rect popupBounds;
    private int popupX, popupAnchorY;
    private ScrollView scroll;
    private LinearLayout body;
    private TextView speedStatus;
    private int[] heldKeys = new int[0];
    private boolean sending, disposed;
    private float touchX, touchY, startX, startY;
    private boolean dragging;

    GameCommandHud(Activity activity, ViewGroup root, View gameView) {
        this(activity, root, gameView, GameProfiles.current(activity) == GameProfile.RPU,
                activity.getSharedPreferences(GameProfiles.current(activity) == GameProfile.RPU
                        ? "command-hud" : "command-hud-" + GameProfiles.current(activity).id, Context.MODE_PRIVATE),
                SDL_INPUT);
    }

    // The same panel can be exercised by Android UI tests without loading the game engine.
    GameCommandHud(Activity activity, ViewGroup root, View gameView, boolean rpu,
                   SharedPreferences preferences, GameInput input) {
        this.activity = activity; this.root = root; this.gameView = gameView;
        this.rpu = rpu; this.preferences = preferences; this.input = input;
        favorites = CommandCatalog.favorites(rpu, preferences.contains("favorites")
                ? preferences.getStringSet("favorites", null) : null);
        section = CommandCatalog.section(rpu, preferences.getString("section", "quick"));
        handle = button("≡", this::show);
        handle.setTag("hud:handle");
        handle.setTextSize(18);
        handle.setTextColor(LauncherUi.GOLD);
        handle.setContentDescription("Open quick commands. Drag to move the button.");
        handle.setPadding(0, 0, 0, 0);
        root.addView(handle, new RelativeLayout.LayoutParams(dp(76), dp(48)));
        updateSpeed();
        handle.setOnTouchListener((view, event) -> drag(event));
        layoutListener = (view, l, t, r, b, ol, ot, or, ob) -> {
            if (!disposed && (r-l != or-ol || b-t != ob-ot)) {
                closeMenu();
                position();
            }
        };
        root.addOnLayoutChangeListener(layoutListener);
        root.post(this::position);
    }

    private int dp(int value) { return LauncherUi.dp(activity, value); }

    private GradientDrawable background(int color, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(8));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private void updateSpeed() {
        int speed = input.speed();
        handle.setText(speed == 100 ? "≡" : "≡ " + speedLabel(speed));
        if (speedStatus != null) speedStatus.setText("Game speed · " + speedLabel(speed));
        for (Map.Entry<Integer, Button> item : speeds.entrySet()) {
            boolean selected = item.getKey() == speed;
            item.getValue().setSelected(selected);
            item.getValue().setTextColor(selected ? Color.BLACK : Color.WHITE);
            item.getValue().setContentDescription("Game speed " + speedLabel(item.getKey())
                    + (selected ? ", selected" : ""));
        }
    }

    private static String speedLabel(int percent) {
        return String.format(Locale.ROOT, percent % 100 == 0 ? "%.0f×" : "%.1f×", percent / 100.0);
    }

    private void position() {
        if (disposed) return;
        move(preferences.getFloat("x", 1f) * Math.max(0, root.getWidth() - handle.getWidth() - dp(8)),
                preferences.getFloat("y", 0f) * Math.max(0, root.getHeight() - handle.getHeight() - dp(8)));
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
        if (disposed || sending || closeMenu()) return;
        if (root.getWidth() < dp(240) || root.getHeight() < dp(240)) return;
        partyConfig = null;
        if (rpu) {
            try { partyConfig = new IniDocument(new SettingsRepository(activity).read("mods/party_orders.ini")); }
            catch (IOException ignored) { /* Party commands explain the unavailable binding when tapped. */ }
        }
        LinearLayout panel = new LinearLayout(activity);
        panel.setTag("hud:panel");
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(8), dp(12), dp(8));
        panel.setBackground(background(Color.argb(250, 23, 27, 24), LauncherUi.GOLD));
        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(activity);
        title.setText("Quick commands"); title.setTextColor(LauncherUi.GOLD); title.setTextSize(20);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        Button close = button("×", () -> closeMenu());
        close.setContentDescription("Close quick commands");
        close.setTag("hud:close");
        header.addView(close, new LinearLayout.LayoutParams(dp(48), dp(48)));
        panel.addView(header);

        speedStatus = new TextView(activity);
        speedStatus.setTextColor(LauncherUi.GOLD);
        speedStatus.setTextSize(14);
        panel.addView(speedStatus);
        HorizontalScrollView speedScroll = new HorizontalScrollView(activity);
        speedScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout speedRow = new LinearLayout(activity);
        speeds.clear();
        for (int speed : new int[]{50, 100, 150, 200, 300, 400}) {
            Button choice = button(speedLabel(speed), () -> { input.speed(speed); updateSpeed(); });
            choice.setTag("speed:" + speed);
            speeds.put(speed, choice);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(58), dp(48));
            params.setMargins(dp(2), dp(4), dp(2), dp(6));
            speedRow.addView(choice, params);
        }
        speedScroll.addView(speedRow);
        panel.addView(speedScroll);
        updateSpeed();

        tabs.clear();
        LinearLayout tabRow = new LinearLayout(activity);
        for (String id : rpu ? new String[]{"quick", "party", "game", "skills"} : new String[]{"quick", "game", "skills"}) {
            Button tab = button(id.substring(0, 1).toUpperCase(Locale.ROOT) + id.substring(1), () -> {
                section = id;
                preferences.edit().putString("section", section).apply();
                renderCommands();
                scroll.scrollTo(0, 0);
            });
            tab.setTag("tab:" + id);
            tabs.put(id, tab);
            tabRow.addView(tab, cell(48));
        }
        panel.addView(tabRow);
        scroll = new ScrollView(activity);
        body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body);
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        renderCommands();

        Rect visible = new Rect();
        root.getWindowVisibleDisplayFrame(visible);
        int[] origin = new int[2];
        root.getLocationOnScreen(origin);
        int left = Math.max(origin[0], visible.left), top = Math.max(origin[1], visible.top);
        int right = Math.min(origin[0] + root.getWidth(), visible.right);
        int bottom = Math.min(origin[1] + root.getHeight(), visible.bottom);
        int width = Math.min(dp(420), right - left - dp(16));
        int height = Math.min(preferredHeight(), bottom - top - dp(16));
        if (width < dp(220) || height < dp(220)) return;
        int x = Math.max(left + dp(8), Math.min(origin[0] + (int)handle.getX(), right - width - dp(8)));
        int y = Math.max(top + dp(8), Math.min(origin[1] + (int)handle.getY() + handle.getHeight(), bottom - height - dp(8)));
        popupBounds = new Rect(left, top, right, bottom);
        popupX = x;
        popupAnchorY = origin[1] + (int)handle.getY() + handle.getHeight();
        popup = new PopupWindow(panel, width, height, true);
        popup.setBackgroundDrawable(background(Color.rgb(23, 27, 24), LauncherUi.GOLD));
        popup.setOutsideTouchable(true);
        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        popup.setElevation(dp(8));
        popup.setOnDismissListener(() -> {
            popup = null; body = null; scroll = null; speedStatus = null;
            tabs.clear(); speeds.clear();
            gameView.requestFocus();
        });
        popup.showAtLocation(root, Gravity.TOP | Gravity.LEFT, x, y);
    }

    private void renderCommands() {
        body.removeAllViews();
        for (Map.Entry<String, Button> tab : tabs.entrySet()) {
            boolean selected = tab.getKey().equals(section);
            tab.getValue().setSelected(selected);
            tab.getValue().setTextColor(selected ? Color.BLACK : Color.WHITE);
        }
        if (section.equals("quick"))
            note(favorites.isEmpty() ? "Your Quick tab is empty. Hold a command in another tab to add it."
                    : "Hold any command to add or remove it from Quick. Up to 6 favorites.");
        if (section.equals("party"))
            note("Orders need available companions. Pick up / loot also controls your character. Toggle auto-loot changes whether your character loots bodies.");
        if (section.equals("skills")) note("Sneak toggles directly. For other skills, choose a target in the game.");
        LinearLayout row = null;
        int count = 0;
        for (CommandCatalog.Command command : CommandCatalog.commands(rpu)) {
            if (!(section.equals("quick") ? favorites.contains(command.id) : section.equals(command.section))) continue;
            if (count++ % 2 == 0) {
                row = new LinearLayout(activity);
                body.addView(row, new LinearLayout.LayoutParams(-1, -2));
            }
            addCommand(row, command);
        }
        if (count % 2 != 0) row.addView(new View(activity), cell(56));
        if (section.equals("game")) note("Save game and Load game open the slot menu.");
        note("Drag ≡ to move this panel's button. Speed resets to 1× after restarting the game.");
        if (popup != null) {
            int height = Math.min(preferredHeight(), popupBounds.height() - dp(16));
            int y = Math.max(popupBounds.top + dp(8), Math.min(popupAnchorY, popupBounds.bottom - height - dp(8)));
            popup.update(popupX, y, popup.getWidth(), height);
        }
    }

    private int preferredHeight() {
        if (section.equals("quick")) return dp(300 + 60 * ((favorites.size() + 1) / 2));
        return dp(section.equals("party") ? 610 : section.equals("game") ? 580 : 550);
    }

    private void note(String text) {
        TextView note = new TextView(activity);
        note.setText(text); note.setTextColor(Color.rgb(175, 187, 173)); note.setTextSize(13);
        note.setPadding(dp(2), dp(8), dp(2), dp(8));
        body.addView(note);
    }

    private LinearLayout.LayoutParams cell(int height) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(height), 1);
        params.setMargins(dp(2), dp(2), dp(2), dp(2));
        return params;
    }

    private Button button(String label, Runnable action) {
        Button button = new Button(activity);
        button.setText(label); button.setTextSize(14); button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, background(Color.rgb(93, 107, 79), LauncherUi.GOLD));
        states.addState(new int[]{android.R.attr.state_selected}, background(LauncherUi.GOLD, LauncherUi.GOLD));
        states.addState(new int[]{}, background(Color.rgb(47, 57, 48), Color.rgb(83, 98, 80)));
        button.setBackground(states);
        button.setPadding(dp(4), 0, dp(4), 0);
        button.setMinWidth(0); button.setMinimumWidth(0);
        button.setMinHeight(0); button.setMinimumHeight(0);
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private void addCommand(LinearLayout row, CommandCatalog.Command command) {
        int[] keys = new int[0];
        String problem = null;
        try {
            keys = command.keys(partyConfig);
            if (keys.length == 0) problem = "This command is turned off in Party Orders settings.";
        } catch (IllegalArgumentException invalid) { problem = invalid.getMessage(); }
        final int[] binding = keys;
        final String error = problem;
        String label = (favorites.contains(command.id) ? "★ " : "") + command.label;
        Button choice = button(label + (error == null ? "" : "\nUnavailable"), () -> {
            if (error != null) Toast.makeText(activity, error, Toast.LENGTH_LONG).show();
            else send(binding, command.label);
        });
        choice.setTag("command:" + command.id);
        choice.setTextColor(error == null ? Color.WHITE : Color.rgb(157, 166, 152));
        choice.setContentDescription(command.label + (favorites.contains(command.id) ? ", favorite" : "")
                + (error == null ? "" : ", unavailable") + ". Hold to change favorites.");
        choice.setOnLongClickListener(view -> {
            boolean removed = favorites.remove(command.id);
            if (!removed) {
                if (favorites.size() >= CommandCatalog.MAX_FAVORITES) {
                    Toast.makeText(activity, "Quick holds 6 favorites. Remove one first by holding it.", Toast.LENGTH_SHORT).show();
                    return true;
                }
                favorites.add(command.id);
            }
            preferences.edit().putStringSet("favorites", new java.util.HashSet<>(favorites)).apply();
            int y = scroll.getScrollY();
            renderCommands();
            scroll.post(() -> { if (scroll != null) scroll.scrollTo(0, y); });
            Toast.makeText(activity, removed ? "Removed from Quick" : "Added to Quick", Toast.LENGTH_SHORT).show();
            return true;
        });
        row.addView(choice, cell(error == null ? 56 : 72));
    }

    private void send(int[] keys, String label) {
        if (sending || disposed || keys.length == 0) return;
        closeMenu();
        gameView.requestFocus();
        sending = true;
        // Let SDL resume after losing focus before sending a real key press.
        handler.postDelayed(() -> pressWhenFocused(keys, label, 0), 200);
    }

    private void pressWhenFocused(int[] keys, String label, int attempts) {
        if (disposed) return;
        if (!activity.hasWindowFocus()) {
            if (attempts < 10) handler.postDelayed(() -> pressWhenFocused(keys, label, attempts + 1), 100);
            else {
                sending = false;
                Toast.makeText(activity, "Command not sent. Reopen Quick commands and try again.", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        heldKeys = keys;
        for (int key : keys) input.down(key);
        Toast.makeText(activity, "Sent: " + label, Toast.LENGTH_SHORT).show();
        handler.postDelayed(this::releaseKeys, 100);
    }

    private void releaseKeys() {
        for (int i = heldKeys.length - 1; i >= 0; i--) input.up(heldKeys[i]);
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
        root.removeOnLayoutChangeListener(layoutListener);
        root.removeView(handle);
    }
}
