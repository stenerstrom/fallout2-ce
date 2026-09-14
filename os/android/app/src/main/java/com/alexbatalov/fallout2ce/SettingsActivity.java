package com.alexbatalov.fallout2ce;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import java.io.IOException;
import java.util.LinkedHashSet;
import org.json.JSONArray;
import org.json.JSONObject;

public class SettingsActivity extends Activity {
    private SettingsRepository repository;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        repository = new SettingsRepository(this);
        showSections();
    }

    private void showSections() {
        LinearLayout body = LauncherUi.page(this, GameProfiles.current(this).title + " · Settings");
        LauncherUi.note(this, body, "Changes take effect the next time you start the game. Some gameplay and sound preferences may also be restored from a save.");
        try {
            JSONArray sections = SettingsSchema.load(this);
            for (int i = 0; i < sections.length(); i++) {
                JSONObject section = sections.getJSONObject(i);
                String key = section.getString("section");
                String title = section.getString("title");
                LauncherUi.button(this, body, title, view -> openEditor("fallout2.cfg", key, title, false))
                        .setTag("section:" + key);
            }
            if (GameProfiles.current(this) == GameProfile.RPU)
                LauncherUi.button(this, body, "Restoration Project options", view -> showRpuOptions());
            LauncherUi.button(this, body, "Saved games · Export and restore", view ->
                    startActivity(GameProfiles.intent(this, SaveGamesActivity.class)));
            LauncherUi.button(this, body, "Mods and all configuration files", view -> showFiles());

            LauncherUi.button(this, body, "Back", view -> finish());
        } catch (Exception error) { LauncherUi.error(this, error.getMessage()); }
    }

    private void showRpuOptions() {
        LinearLayout body = LauncherUi.page(this, "Restoration Project options");
        LauncherUi.note(this, body, "Changes apply after you exit and restart the game. Existing choices are kept until you change them.");
        try {
            option(body, "Faster walking", "Speeds up the slow walking animations of selected robots, guards and other critters.",
                    "mods/upu.ini", "main", "critters_walk_faster", new String[]{"Off", "On"}, new String[]{"0", "1"}, "0");
            option(body, "Goris animation", "Choose the speed of Goris removing his robe. This is separate from overall game speed.",
                    "mods/upu.ini", "main", "goris_derobing_speed",
                    new String[]{"Off", "Original · 16 FPS", "25 FPS", "50 FPS", "75 FPS", "100 FPS"},
                    new String[]{"0", "16", "25", "50", "75", "100"}, "0");
            option(body, "Virtual file support", "Required for faster walking and the Goris animation. Keep this enabled when using those options.",
                    "ddraw.ini", "Misc", "UseFileSystemOverride", new String[]{"Off", "On"}, new String[]{"0", "1"}, "1");
            option(body, "Hero Appearance", "Enables MODEL / STYLE during character creation and appearance changes in EPA. Your appearance is saved with your character.",
                    "ddraw.ini", "Misc", "EnableHeroAppearanceMod", new String[]{"Off", "On"}, new String[]{"0", "1"}, "1");
            option(body, "Alternative explosions", "Uses the alternative explosion artwork included with Restoration Project.",
                    "ddraw.ini", "RPU", "AlternativeExplosions", new String[]{"Original", "Alternative"}, new String[]{"0", "1"}, "0");
            option(body, "Ammo damage formula", "Sets both the damage formula and its ammo adjustments. Choose one rule set for your playthrough.",
                    "ddraw.ini", "RPU", "DamageFormula", new String[]{"Default", "Glovz", "YAAM"}, new String[]{"0", "1", "5"},
                    new IniDocument(repository.read("ddraw.ini")).get("Misc", "DamageFormula", "0"));
            option(body, "Merchant restocking", "Refresh merchant inventories instead of keeping all previously sold items. Quest items are preserved.",
                    "mods/upu.ini", "main", "wipe_inventory", new String[]{"Keep sold items", "Refresh inventory"}, new String[]{"0", "1"}, "0");
        } catch (IOException error) { LauncherUi.error(this, error.getMessage()); }
        LauncherUi.button(this, body, "Back to settings", view -> showSections());
    }

    private void option(LinearLayout body, String title, String description, String file,
                        String section, String key, String[] labels, String[] values, String fallback) throws IOException {
        String value = new IniDocument(repository.read(file)).get(section, key, fallback);
        int selected = -1;
        for (int index = 0; index < values.length; index++) if (values[index].equals(value)) selected = index;
        String current = selected < 0 ? "Custom (" + value + ")" : labels[selected];
        LauncherUi.button(this, body, title + " · " + current, view ->
                new android.app.AlertDialog.Builder(this).setTitle(title)
                        .setItems(labels, (dialog, which) -> {
                            try {
                                java.util.List<IniDocument.Entry> changes = new java.util.ArrayList<>();
                                changes.add(new IniDocument.Entry(section, key, values[which], ""));
                                if (section.equals("RPU") && key.equals("DamageFormula"))
                                    changes.add(new IniDocument.Entry("Misc", key, values[which], ""));
                                repository.saveChanges(file, changes);
                                showRpuOptions();
                            } catch (IOException error) { LauncherUi.error(this, error.getMessage()); }
                        }).setNegativeButton("Cancel", null).show());
        LauncherUi.note(this, body, description);
    }

    private void showFiles() {
        LinearLayout body = LauncherUi.page(this, "All configuration files");
        LauncherUi.note(this, body, "All imported INI and CFG files in the game folder, mods and data/config are listed here. Change CE display settings in fallout2.cfg. The older f2_res.ini is mainly used during initial migration.");
        try {
            for (String file : repository.configFiles()) {
                LauncherUi.button(this, body, file, view -> showFile(file));
            }
        } catch (IOException error) { LauncherUi.error(this, error.getMessage()); }
        LauncherUi.button(this, body, "Back to settings", view -> showSections());
    }

    private void showFile(String file) {
        LinearLayout body = LauncherUi.page(this, file);
        try {
            LinkedHashSet<String> sections = new LinkedHashSet<>();
            for (IniDocument.Entry entry : new IniDocument(repository.read(file)).entries()) sections.add(entry.section);
            for (String section : sections) {
                String title = section.isEmpty() ? "General" : section;
                LauncherUi.button(this, body, title, view -> openEditor(file, section, title, false));
            }
            LauncherUi.button(this, body, "Edit full file", view -> openEditor(file, "", file, true));
        } catch (IOException error) { LauncherUi.error(this, error.getMessage()); }
        LauncherUi.button(this, body, "Back to files", view -> showFiles());
    }

    private void openEditor(String file, String section, String title, boolean raw) {
        startActivity(GameProfiles.intent(this, SettingsEditorActivity.class)
                .putExtra("file", file).putExtra("section", section).putExtra("title", title).putExtra("raw", raw));
    }

    @Override protected void onResume() {
        super.onResume();
        try {
            if (repository.gameRunning()) {
                android.widget.Toast.makeText(this, "Exit the game before changing settings.", android.widget.Toast.LENGTH_LONG).show();
                finish();
            }
        } catch (IOException error) { LauncherUi.error(this, error.getMessage()); finish(); }
    }
}
