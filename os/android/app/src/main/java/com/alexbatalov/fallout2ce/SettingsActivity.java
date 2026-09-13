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
        LinearLayout body = LauncherUi.page(this, "Inställningar");
        LauncherUi.note(this, body, "Ändringarna används nästa gång du startar spelet. Vissa spel- och ljudval kan även följa med en sparning.");
        try {
            JSONArray sections = SettingsSchema.load(this);
            for (int i = 0; i < sections.length(); i++) {
                JSONObject section = sections.getJSONObject(i);
                String key = section.getString("section");
                String title = section.getString("title");
                LauncherUi.button(this, body, title, view -> openEditor("fallout2.cfg", key, title, false))
                        .setTag("section:" + key);
            }
            LauncherUi.button(this, body, "Moddar och alla konfigurationsfiler", view -> showFiles());
            LauncherUi.note(this, body, "Modval kan kräva funktioner som FOR:CE ännu saknar. Snabbare gång och Goris-animationen bör vara avstängda i den nuvarande RPU-installationen.");
            LauncherUi.button(this, body, "Tillbaka", view -> finish());
        } catch (Exception error) { LauncherUi.error(this, error.getMessage()); }
    }

    private void showFiles() {
        LinearLayout body = LauncherUi.page(this, "Alla konfigurationsfiler");
        LauncherUi.note(this, body, "Här finns samtliga importerade INI- och CFG-filer i spelroten, mods och data/config. Bildinställningar för CE ändras i fallout2.cfg. Äldre f2_res.ini används främst vid första migreringen.");
        try {
            for (String file : repository.configFiles()) {
                LauncherUi.button(this, body, file, view -> showFile(file));
            }
        } catch (IOException error) { LauncherUi.error(this, error.getMessage()); }
        LauncherUi.button(this, body, "Tillbaka till inställningar", view -> showSections());
    }

    private void showFile(String file) {
        LinearLayout body = LauncherUi.page(this, file);
        try {
            LinkedHashSet<String> sections = new LinkedHashSet<>();
            for (IniDocument.Entry entry : new IniDocument(repository.read(file)).entries()) sections.add(entry.section);
            for (String section : sections) {
                String title = section.isEmpty() ? "Allmänt" : section;
                LauncherUi.button(this, body, title, view -> openEditor(file, section, title, false));
            }
            LauncherUi.button(this, body, "Redigera hela filtexten", view -> openEditor(file, "", file, true));
        } catch (IOException error) { LauncherUi.error(this, error.getMessage()); }
        LauncherUi.button(this, body, "Tillbaka till filer", view -> showFiles());
    }

    private void openEditor(String file, String section, String title, boolean raw) {
        startActivity(new Intent(this, SettingsEditorActivity.class)
                .putExtra("file", file).putExtra("section", section).putExtra("title", title).putExtra("raw", raw));
    }

    @Override protected void onResume() {
        super.onResume();
        try {
            if (repository.gameRunning()) {
                android.widget.Toast.makeText(this, "Avsluta spelet innan du ändrar inställningar.", android.widget.Toast.LENGTH_LONG).show();
                finish();
            }
        } catch (IOException error) { LauncherUi.error(this, error.getMessage()); finish(); }
    }
}
