package com.alexbatalov.fallout2ce;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 32, qualifiers = "w1000dp-h800dp-land-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class LauncherSettingsTests {
    private static final String CONFIG = "; Keep this comment\n[screen]\nresolution_x=1000\nresolution_y=640\nscale=1\nwindowed=0\nmouse_lock=0\ncustom_value=42\n[preferences]\nsubtitles=1\n";
    private final List<ActivityController<? extends Activity>> controllers = new ArrayList<>();
    private Context context;

    @Before public void fixtures() throws Exception {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("library", Context.MODE_PRIVATE).edit().clear().commit();
        for (GameProfile profile : GameProfile.values()) {
            File dir = new SettingsRepository(context, profile).gameDirectory();
            dir.mkdirs();
            for (String name : new String[]{"master.dat", "critter.dat", "ce.dat"})
                Files.write(new File(dir, name).toPath(), new byte[]{1});
            Files.write(new File(dir, "fallout2.cfg").toPath(), CONFIG.getBytes(StandardCharsets.UTF_8));
        }
    }

    <T extends Activity> ActivityController<T> start(Class<T> type, Intent intent) {
        ActivityController<T> controller = Robolectric.buildActivity(type, intent).setup().visible();
        controllers.add(controller);
        layout(controller.get(), 1000, 800);
        return controller;
    }

    Intent intent(Class<?> type, String profile) {
        return new Intent(context, type).putExtra(GameProfiles.EXTRA, profile);
    }

    SettingsEditorActivity editor(String profile, String section, boolean raw) {
        return start(SettingsEditorActivity.class, intent(SettingsEditorActivity.class, profile)
                .putExtra("file", "fallout2.cfg").putExtra("section", section)
                .putExtra("title", section.equals("screen") ? "Display" : "Gameplay and controls")
                .putExtra("raw", raw)).get();
    }

    View root(Activity activity) { return activity.findViewById(android.R.id.content); }
    View find(Activity activity, String tag) {
        View view = root(activity).findViewWithTag(tag);
        assertNotNull("Missing " + tag, view);
        return view;
    }
    void layout(Activity activity, int width, int height) {
        View root = root(activity);
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, width, height);
        shadowOf(Looper.getMainLooper()).idle();
    }
    void visible(Activity activity, String tag) {
        View view = find(activity, tag);
        Rect rect = new Rect();
        assertTrue(tag + " must be visible", view.getGlobalVisibleRect(rect));
        assertEquals(tag + " must not be clipped vertically", view.getHeight(), rect.height());
        assertTrue(view.getHeight() >= 48);
    }
    String read(GameProfile profile) throws Exception {
        return new SettingsRepository(context, profile).read("fallout2.cfg");
    }

    @After public void finish() {
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        if (dialog != null && dialog.isShowing()) dialog.dismiss();
        for (ActivityController<?> controller : controllers) controller.pause().stop().destroy();
    }

    @Test @Config(qualifiers = "w400dp-h640dp-port-mdpi")
    public void libraryActionsStayVisibleAndSelectionKeepsScrollAndProfile() {
        LauncherActivity activity = start(LauncherActivity.class, intent(LauncherActivity.class, "rpu")).get();
        layout(activity, 400, 640);
        ScrollView scroll = (ScrollView)find(activity, "library-scroll");
        scroll.scrollTo(0, 300);
        find(activity, "game:nevada").performClick();
        layout(activity, 400, 640);
        scroll = (ScrollView)find(activity, "library-scroll");
        assertEquals(300, scroll.getScrollY());
        assertTrue(find(activity, "game:nevada").isSelected());
        assertEquals(GameProfile.NEVADA, GameProfiles.current(activity));
        visible(activity, "play"); visible(activity, "settings"); visible(activity, "saves");
        activity.onWindowFocusChanged(false);
        activity.onWindowFocusChanged(true);
        assertSame(scroll, find(activity, "library-scroll"));
        find(activity, "settings").performClick();
        assertEquals("nevada", shadowOf(activity).getNextStartedActivity().getStringExtra(GameProfiles.EXTRA));
    }

    @Test public void runningGameLocksSwitchingAndEditingButCanResume() throws Exception {
        try (GameSession lock = GameSession.tryAcquire(context.getFilesDir())) {
            assertNotNull(lock);
            LauncherActivity activity = start(LauncherActivity.class, intent(LauncherActivity.class, "rpu")).get();
            assertFalse(find(activity, "game:sonora").isEnabled());
            assertFalse(find(activity, "settings").isEnabled());
            assertFalse(find(activity, "saves").isEnabled());
            assertEquals("Resume game", ((TextView)find(activity, "play")).getText().toString());
            find(activity, "play").performClick();
            Intent opened = shadowOf(activity).getNextStartedActivity();
            assertEquals(MainActivity.class.getName(), opened.getComponent().getClassName());
            assertEquals("rpu", opened.getStringExtra(GameProfiles.EXTRA));
        }
    }

    @Test public void searchFindsSettingsAndAdvancedCategoriesRemainAccessible() throws Exception {
        SettingsActivity activity = start(SettingsActivity.class, intent(SettingsActivity.class, "sonora")).get();
        EditText search = (EditText)find(activity, "settings-search");
        search.setText("subtitles");
        find(activity, "search:preferences:subtitles").performClick();
        Intent opened = shadowOf(activity).getNextStartedActivity();
        assertEquals("subtitles", opened.getStringExtra("focus_key"));
        assertEquals("sonora", opened.getStringExtra(GameProfiles.EXTRA));
        search.setText("no such preference xyz");
        assertNull(root(activity).findViewWithTag("section:screen"));
        find(activity, "clear-search").performClick();
        assertNull(root(activity).findViewWithTag("rpu-options"));
        find(activity, "advanced-settings").performClick();
        org.json.JSONArray schema = SettingsSchema.load(activity);
        for (int i = 0; i < schema.length(); i++)
            find(activity, "section:" + schema.getJSONObject(i).getString("section"));
        find(activity, "configuration-files").performClick();
        activity.onBackPressed();
        assertFalse(activity.isFinishing());
        find(activity, "settings-search");
    }

    @Test public void searchBackNavigationAndDraftSurviveRecreation() {
        ActivityController<SettingsActivity> controller = start(SettingsActivity.class, intent(SettingsActivity.class, "rpu"));
        SettingsActivity activity = controller.get();
        ((EditText)find(activity, "settings-search")).setText("hero");
        find(activity, "rpu-options").performClick();
        controller.recreate();
        activity = controller.get();
        activity.onBackPressed();
        assertEquals("hero", ((EditText)find(activity, "settings-search")).getText().toString());
        assertFalse(activity.isFinishing());

        ActivityController<SettingsEditorActivity> edit = start(SettingsEditorActivity.class,
                intent(SettingsEditorActivity.class, "rpu").putExtra("file", "fallout2.cfg")
                .putExtra("section", "screen").putExtra("title", "Display"));
        ((EditText)find(edit.get(), "field:resolution_x")).setText("1280");
        edit.recreate();
        assertEquals("1280", ((EditText)find(edit.get(), "field:resolution_x")).getText().toString());
        edit.get().onBackPressed();
        assertTrue(ShadowAlertDialog.getLatestAlertDialog().isShowing());
    }

    @Test public void untouchedEditorDoesNotWriteOrAskToDiscard() throws Exception {
        SettingsEditorActivity activity = editor("rpu", "screen", false);
        activity.onBackPressed();
        assertTrue(activity.isFinishing());
        assertEquals(CONFIG, read(GameProfile.RPU));
        assertNull(ShadowAlertDialog.getLatestAlertDialog());
    }

    @Test public void dirtyBackCanKeepEditingOrDiscardWithoutWriting() throws Exception {
        SettingsEditorActivity activity = editor("rpu", "screen", false);
        ((EditText)find(activity, "field:resolution_x")).setText("1280");
        find(activity, "navigate-back").performClick();
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        assertFalse(activity.isFinishing());
        assertEquals("1280", ((EditText)find(activity, "field:resolution_x")).getText().toString());
        find(activity, "cancel-settings").performClick();
        ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue(activity.isFinishing());
        assertEquals(CONFIG, read(GameProfile.RPU));
    }

    @Test public void rawDraftAlsoRequiresExplicitDiscard() throws Exception {
        SettingsEditorActivity activity = editor("nevada", "", true);
        ((EditText)find(activity, "raw-config")).append("\n; draft");
        activity.onBackPressed();
        assertFalse(activity.isFinishing());
        ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(CONFIG, read(GameProfile.NEVADA));
    }

    @Test public void presetsValidateAndSaveOnlyTheSelectedGame() throws Exception {
        for (int[] display : new int[][]{{3200, 2000}, {1920, 1080}, {800, 1280}, {0, 0}, {4000, 800}}) {
            for (int height : new int[]{480, 640, 800}) {
                int[] size = DisplayPresets.size(display[0], display[1], height);
                SettingsValidation.validateScreen(new IniDocument("[screen]\nresolution_x=" + size[0] +
                        "\nresolution_y=" + size[1] + "\nscale=1\n"));
            }
        }
        SettingsEditorActivity activity = editor("sonora", "screen", false);
        find(activity, "display-preset:2").performClick();
        assertEquals(CONFIG, read(GameProfile.SONORA)); // A preset is only a draft.
        String width = ((EditText)find(activity, "field:resolution_x")).getText().toString();
        ((EditText)find(activity, "field:scale")).setText("4");
        assertTrue(((TextView)find(activity, "display-summary")).getText().toString().contains("Check display"));
        find(activity, "save-settings").performClick();
        assertFalse(activity.isFinishing());
        assertEquals(CONFIG, read(GameProfile.SONORA));
        ShadowAlertDialog.getLatestAlertDialog().dismiss();
        ((EditText)find(activity, "field:scale")).setText("1");
        find(activity, "save-settings").performClick();
        assertTrue(activity.isFinishing());
        IniDocument saved = new IniDocument(read(GameProfile.SONORA));
        assertEquals(width, saved.get("screen", "resolution_x", ""));
        assertEquals("800", saved.get("screen", "resolution_y", ""));
        assertEquals("42", saved.get("screen", "custom_value", ""));
        assertTrue(read(GameProfile.SONORA).contains("; Keep this comment"));
        assertEquals(CONFIG, read(GameProfile.RPU));
        assertEquals(CONFIG, read(GameProfile.NEVADA));
    }

    @Test public void searchTargetScrollsIntoViewAndSaveStaysAccessible() {
        SettingsEditorActivity activity = start(SettingsEditorActivity.class,
                intent(SettingsEditorActivity.class, "rpu").putExtra("file", "fallout2.cfg")
                .putExtra("section", "preferences").putExtra("title", "Gameplay and controls")
                .putExtra("focus_key", "subtitles")).get();
        assertTrue(((ScrollView)find(activity, "page-scroll")).getScrollY() > 0);
        visible(activity, "field:subtitles");
        layout(activity, 560, 320); // Landscape with little space / open keyboard.
        visible(activity, "save-settings"); visible(activity, "cancel-settings");
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void renderLibrarySettingsAndDisplayForReview() throws Exception {
        LauncherActivity library = start(LauncherActivity.class, intent(LauncherActivity.class, "rpu")).get();
        capture(library, "library", 1000, 800);
        SettingsActivity settings = start(SettingsActivity.class, intent(SettingsActivity.class, "rpu")).get();
        capture(settings, "settings", 1000, 800);
        SettingsEditorActivity display = editor("rpu", "screen", false);
        capture(display, "display", 1000, 800);
        capture(display, "display-small", 560, 320);
    }

    @Test @Config(qualifiers = "w400dp-h640dp-port-mdpi")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void renderNarrowLibraryAndLargeText() throws Exception {
        RuntimeEnvironment.setFontScale(1.3f);
        LauncherActivity library = start(LauncherActivity.class, intent(LauncherActivity.class, "rpu")).get();
        capture(library, "library-narrow", 400, 640);
        visible(library, "play"); visible(library, "settings"); visible(library, "saves");
        SettingsActivity settings = start(SettingsActivity.class, intent(SettingsActivity.class, "rpu")).get();
        capture(settings, "settings-narrow", 400, 640);
    }

    void capture(Activity activity, String name, int width, int height) throws Exception {
        layout(activity, width, height);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        root(activity).draw(new Canvas(bitmap));
        File dir = new File("build/reports/launcher-preview");
        dir.mkdirs();
        try (FileOutputStream stream = new FileOutputStream(new File(dir, name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        }
        bitmap.recycle();
    }
}
