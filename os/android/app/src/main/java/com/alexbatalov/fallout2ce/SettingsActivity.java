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
    private String page = "home", currentFile = "", query = "";
    private boolean advanced, childOpened;
    private LinearLayout results;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        repository = new SettingsRepository(this);
        if (state != null) {
            page = state.getString("page", "home");
            currentFile = state.getString("current-file", "");
            query = state.getString("query", "");
            advanced = state.getBoolean("advanced");
        }
        renderPage();
    }

    private void renderPage() {
        switch (page) {
            case "rpu": showRpuOptions(); break;
            case "files": showFiles(); break;
            case "file": showFile(currentFile); break;
            default: showSections();
        }
    }

    private void showSections() {
        page = "home";
        LinearLayout body = LauncherUi.page(this, GameProfiles.current(this).title + " · Settings");
        LauncherUi.note(this, body, "Changes apply on the next game start. Saved games may restore some gameplay and sound preferences.");
        android.widget.EditText search = new android.widget.EditText(this);
        search.setSingleLine(true);
        search.setHint("Search settings · resolution, sound, subtitles…");
        search.setContentDescription("Search settings");
        search.setTag("settings-search");
        search.setTextSize(16);
        search.setMinHeight(LauncherUi.dp(this, 56));
        search.setText(query);
        body.addView(search, new LinearLayout.LayoutParams(-1, -2));
        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        body.addView(results, new LinearLayout.LayoutParams(-1, -2));
        search.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                query = s.toString(); showResults();
            }
            public void afterTextChanged(android.text.Editable s) {}
        });
        showResults();
    }

    private void showResults() {
        results.removeAllViews();
        try {
            JSONArray sections = SettingsSchema.load(this);
            String term = query.trim().toLowerCase(java.util.Locale.ROOT);
            if (!term.isEmpty()) {
                int count = 0;
                for (int i = 0; i < sections.length(); i++) {
                    JSONObject section = sections.getJSONObject(i);
                    String key = section.getString("section"), title = section.getString("title");
                    JSONArray fields = section.getJSONArray("fields");
                    for (int j = 0; j < fields.length(); j++) {
                        JSONObject field = fields.getJSONObject(j);
                        String fieldKey = field.getString("key"), label = field.optString("label", fieldKey);
                        String searchable = (title + " " + key + " " + label + " " + fieldKey + " " +
                                field.optString("description")).toLowerCase(java.util.Locale.ROOT);
                        boolean match = true;
                        for (String word : term.split("\\s+")) if (!searchable.contains(word)) match = false;
                        if (!match) continue;
                        count++;
                        LauncherUi.button(this, results, label + "\n" + title, v ->
                                openEditor("fallout2.cfg", key, title, false, fieldKey))
                                .setTag("search:" + key + ":" + fieldKey);
                    }
                }
                if (GameProfiles.current(this) == GameProfile.RPU &&
                        ("restoration project hero appearance model style faster walking goris virtual file explosions ammo damage merchant restocking".contains(term))) {
                    LauncherUi.button(this, results, "Restoration Project options", v -> showRpuOptions()).setTag("rpu-options");
                    count++;
                }
                if (count == 0) LauncherUi.note(this, results, "No matching settings. Try a shorter name, or clear the search to browse categories.");
                LauncherUi.button(this, results, "Clear search", v -> { query = ""; showSections(); }).setTag("clear-search");
                return;
            }
            IniDocument config = new IniDocument(repository.read("fallout2.cfg"));
            LauncherUi.text(this, results, "MAKE IT YOURS", 12, LauncherUi.GOLD);
            boolean wide = getResources().getConfiguration().screenWidthDp >= 600;
            LinearLayout row = null;
            int index = 0;
            for (int i = 0; i < sections.length(); i++) {
                JSONObject section = sections.getJSONObject(i);
                String key = section.getString("section"), title = section.getString("title");
                if (key.equals("system") || key.equals("debug")) continue;
                if (!wide || index++ % 2 == 0) {
                    row = new LinearLayout(this);
                    results.addView(row, new LinearLayout.LayoutParams(-1, -2));
                }
                android.widget.Button button = LauncherUi.action(this, title + "\n" + summary(key, config), false, LauncherUi.GOLD);
                button.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
                button.setMinHeight(LauncherUi.dp(this, 84));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
                params.setMargins(0, LauncherUi.dp(this, 8), wide && row.getChildCount() == 0 ? LauncherUi.dp(this, 8) : 0, 0);
                row.addView(button, params);
                button.setTag("section:" + key);
                button.setOnClickListener(v -> openEditor("fallout2.cfg", key, title, false));
            }
            if (GameProfiles.current(this) == GameProfile.RPU)
                LauncherUi.button(this, results, "Restoration Project options", v -> showRpuOptions()).setTag("rpu-options");
            LauncherUi.button(this, results, "Saved games · Export and restore", v ->
                    startActivity(GameProfiles.intent(this, SaveGamesActivity.class))).setTag("saved-games");
            LauncherUi.button(this, results, advanced ? "Advanced settings ▴" : "Advanced settings ▾", v -> {
                advanced = !advanced; showResults();
            }).setTag("advanced-settings");
            if (advanced) {
                LauncherUi.note(this, results, "System paths, diagnostics and individual mod configuration files.");
                for (int i = 0; i < sections.length(); i++) {
                    JSONObject section = sections.getJSONObject(i);
                    String key = section.getString("section"), title = section.getString("title");
                    if (!key.equals("system") && !key.equals("debug")) continue;
                    LauncherUi.button(this, results, title, v -> openEditor("fallout2.cfg", key, title, false)).setTag("section:" + key);
                }
                LauncherUi.button(this, results, "Mods and all configuration files", v -> showFiles()).setTag("configuration-files");
            }
        } catch (Exception error) { LauncherUi.error(this, error.getMessage()); }
    }

    private String summary(String key, IniDocument config) throws IOException {
        switch (key) {
            case "screen":
                IniDocument legacy = new IniDocument(repository.read("f2_res.ini"));
                return config.get("screen", "resolution_x", legacy.get("MAIN", "SCR_WIDTH", "640")) + " × " +
                        config.get("screen", "resolution_y", legacy.get("MAIN", "SCR_HEIGHT", "480")) + " · Size and scaling";
            case "ui": return "Menus, inventory and touch controls";
            case "preferences": return "Difficulty, subtitles and game speed";
            case "sound": return "Music, speech and volume";
            case "gameplay": return "Perk selection";
            case "qol": return "Convenience and interface behavior";
            default: return "Game preferences";
        }
    }

    private void showRpuOptions() {
        page = "rpu";
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
        page = "files";
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
        page = "file"; currentFile = file;
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
        openEditor(file, section, title, raw, "");
    }

    private void openEditor(String file, String section, String title, boolean raw, String focusKey) {
        childOpened = true;
        startActivity(GameProfiles.intent(this, SettingsEditorActivity.class)
                .putExtra("file", file).putExtra("section", section).putExtra("title", title)
                .putExtra("raw", raw).putExtra("focus_key", focusKey));
    }

    @Override public void onConfigurationChanged(android.content.res.Configuration configuration) {
        super.onConfigurationChanged(configuration);
        if (page.equals("home")) showSections();
    }

    @Override public void onBackPressed() {
        if (page.equals("file")) showFiles();
        else if (!page.equals("home")) showSections();
        else super.onBackPressed();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("page", page);
        state.putString("current-file", currentFile);
        state.putString("query", query);
        state.putBoolean("advanced", advanced);
    }

    @Override protected void onResume() {
        super.onResume();
        if (childOpened) { childOpened = false; renderPage(); }
        try {
            if (repository.gameRunning()) {
                android.widget.Toast.makeText(this, "Exit the game before changing settings.", android.widget.Toast.LENGTH_LONG).show();
                finish();
            }
        } catch (IOException error) { LauncherUi.error(this, error.getMessage()); finish(); }
    }
}
