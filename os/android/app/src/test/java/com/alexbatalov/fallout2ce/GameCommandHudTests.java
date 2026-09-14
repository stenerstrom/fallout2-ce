package com.alexbatalov.fallout2ce;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;
import android.widget.RelativeLayout;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 32, qualifiers = "w1000dp-h800dp-land-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class GameCommandHudTests {
    public static class Host extends Activity {
        boolean focused = true;
        @Override public boolean hasWindowFocus() { return focused; }
    }
    static class Input implements GameCommandHud.GameInput {
        int speed = 100;
        List<String> events = new ArrayList<>();
        public int speed() { return speed; }
        public void speed(int value) { speed = value; }
        public void down(int key) { events.add("down:" + key); }
        public void up(int key) { events.add("up:" + key); }
    }
    ActivityController<Host> controller;
    Host activity;
    RelativeLayout root;
    GameCommandHud hud;
    SharedPreferences prefs;
    Input input;

    void start(boolean rpu) {
        controller = Robolectric.buildActivity(Host.class).setup().visible();
        activity = controller.get();
        root = new RelativeLayout(activity);
        View game = new View(activity);
        game.setFocusableInTouchMode(true);
        root.addView(game, new RelativeLayout.LayoutParams(-1, -1));
        activity.setContentView(root);
        root.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 1000, 800);
        prefs = activity.getSharedPreferences("hud-ui-test", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        input = new Input();
        hud = new GameCommandHud(activity, root, game, rpu, prefs, input);
        shadowOf(Looper.getMainLooper()).idle();
    }
    PopupWindow popup() throws Exception {
        Field f = GameCommandHud.class.getDeclaredField("popup");
        f.setAccessible(true);
        return (PopupWindow)f.get(hud);
    }
    View panel() throws Exception {
        assertNotNull(popup());
        View p = popup().getContentView();
        p.measure(View.MeasureSpec.makeMeasureSpec(popup().getWidth(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(popup().getHeight(), View.MeasureSpec.EXACTLY));
        p.layout(0,0,popup().getWidth(),popup().getHeight());
        return p;
    }
    void open() throws Exception {
        root.findViewWithTag("hud:handle").performClick();
        assertNotNull(popup());
        panel();
    }
    void click(String tag) throws Exception {
        View view = panel().findViewWithTag(tag);
        assertNotNull("Missing " + tag, view);
        view.performClick();
    }
    boolean hasText(View view, String text) {
        if (view instanceof TextView && ((TextView)view).getText().toString().contains(text)) return true;
        if (view instanceof ViewGroup) for (int i=0; i<((ViewGroup)view).getChildCount(); i++)
            if (hasText(((ViewGroup)view).getChildAt(i), text)) return true;
        return false;
    }

    @After public void finish() {
        if (hud != null) hud.dispose();
        if (controller != null) controller.pause().stop().destroy();
    }

    @Test public void categoriesFavoritesAndSpeedStayUsable() throws Exception {
        start(true); open();
        assertFalse(hasText(panel(), "Appearance"));
        assertTrue(popup().isFocusable());
        click("speed:200");
        assertEquals(200, input.speed);
        assertNotNull(popup()); // Speed changes keep the panel open.
        assertTrue(panel().findViewWithTag("speed:200").isSelected());
        panel().findViewWithTag("command:loot").performLongClick();
        assertFalse(prefs.getStringSet("favorites", null).contains("loot"));
        click("tab:skills");
        panel().findViewWithTag("command:repair").performLongClick();
        click("tab:quick");
        assertNotNull(panel().findViewWithTag("command:repair"));
        assertNull(panel().findViewWithTag("command:loot"));
        hud.closeMenu(); open();
        assertNotNull(panel().findViewWithTag("command:repair"));
    }

    @Test public void nonRpuHasNoPartyAndRemembersTab() throws Exception {
        start(false); open();
        assertNull(panel().findViewWithTag("tab:party"));
        assertNull(panel().findViewWithTag("command:loot"));
        click("tab:skills");
        hud.closeMenu(); open();
        assertTrue(panel().findViewWithTag("tab:skills").isSelected());
        assertNotNull(panel().findViewWithTag("command:repair"));
    }

    @Test public void pausesCancelPendingKeysAndReleaseHeldModifiers() throws Exception {
        start(true);
        open(); click("tab:party"); click("command:ammo");
        hud.pause();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));
        assertTrue(input.events.isEmpty()); // A backgrounded app must not receive the delayed command.
        open(); click("command:ammo");
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200));
        assertEquals(Arrays.asList("down:113", "down:30"), input.events);
        hud.pause();
        assertEquals(Arrays.asList("down:113", "down:30", "up:30", "up:113"), input.events);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));
        assertEquals(4, input.events.size());
    }

    @Test public void focusFailureNeverSendsKeysAndCanBeRetried() throws Exception {
        start(false); open();
        activity.focused = false;
        click("command:inventory");
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));
        assertTrue(input.events.isEmpty());
        activity.focused = true;
        open(); click("command:inventory");
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400));
        assertEquals(Arrays.asList("down:37", "up:37"), input.events);
    }

    @Test public void smallLandscapeKeepsTabsCloseAndCommandsInsidePopup() throws Exception {
        start(false);
        root.measure(View.MeasureSpec.makeMeasureSpec(560, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 560, 320);
        open();
        assertTrue(popup().getHeight() <= 304);
        View close = panel().findViewWithTag("hud:close");
        assertEquals(48, close.getHeight());
        click("tab:skills");
        assertTrue(panel().findViewWithTag("command:repair").getHeight() >= 48);
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void renderPanelForVisualReview() throws Exception {
        start(true); open();
        File dir = new File("build/reports/hud-preview");
        assertTrue(dir.isDirectory() || dir.mkdirs());
        for (String tab : new String[]{"quick", "party", "game", "skills"}) {
            click("tab:" + tab);
            View view = panel();
            Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
            view.draw(new Canvas(bitmap));
            try (FileOutputStream stream = new FileOutputStream(new File(dir, tab + ".png"))) {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream));
            }
            bitmap.recycle();
        }
    }
}
