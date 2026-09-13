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
            LauncherUi.button(this, body, "Mods and all configuration files", view -> showFiles());
            if (GameProfiles.current(this) == GameProfile.RPU) LauncherUi.note(this, body, "Mod options may require features that FOR:CE does not yet support. Faster walking and the Goris animation option should stay disabled in the current RPU installation.");
            LauncherUi.button(this, body, "Back", view -> finish());
        } catch (Exception error) { LauncherUi.error(this, error.getMessage()); }
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
