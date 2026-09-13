package com.alexbatalov.fallout2ce;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.LinearLayout;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SaveGamesActivity extends Activity {
    private static final int EXPORT = 1, RESTORE = 2, EXPORT_BACKUP = 3;
    private boolean busy;
    private SettingsRepository repository;
    private GameProfile profile;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        profile = GameProfiles.current(this);
        repository = new SettingsRepository(this);
        render();
    }

    private File saveDirectory() throws IOException {
        String patches = new IniDocument(repository.read("fallout2.cfg")).get("system", "master_patches", "data");
        return SaveGameArchive.locate(repository.gameDirectory(), patches);
    }

    private void render() {
        LinearLayout body = LauncherUi.page(this, profile.title + " · Saved games");
        LauncherUi.note(this, body, busy ? "Working with your saved games…" :
                "Export a ZIP archive to keep a copy outside the app. Archives are kept separate for each game.");
        LauncherUi.button(this, body, "Export saved games", view -> chooseExport(EXPORT)).setEnabled(!busy);
        LauncherUi.button(this, body, "Restore from archive", view -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/zip");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(intent, RESTORE);
        }).setEnabled(!busy);
        try {
            if (SaveGameArchive.latestBackup(saveDirectory()) != null)
                LauncherUi.button(this, body, "Export saves from before the last restore",
                        view -> chooseExport(EXPORT_BACKUP)).setEnabled(!busy);
        } catch (IOException error) { LauncherUi.note(this, body, error.getMessage()); }
        LauncherUi.note(this, body, "Restoring replaces this game's current saves. A local backup of the previous saves is kept so you can export and restore it if needed.");
        LauncherUi.button(this, body, "Back", view -> finish()).setEnabled(!busy);
    }

    private void chooseExport(int request) {
        String date = new SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.ROOT).format(new Date());
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/zip");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_TITLE, "Wasteland-" + profile.id + (request == EXPORT_BACKUP ? "-previous" : "") + "-saves-" + date + ".zip");
        startActivityForResult(intent, request);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (request == RESTORE) {
            new AlertDialog.Builder(this).setTitle("Restore " + profile.title + " saves?")
                    .setMessage("This replaces the current saves for this game. A local backup of those saves will be kept.")
                    .setPositiveButton("Restore", (dialog, which) -> run(request, uri))
                    .setNegativeButton("Cancel", null).show();
        } else if (request == EXPORT || request == EXPORT_BACKUP) run(request, uri);
    }

    private void run(int request, Uri uri) {
        if (busy) return;
        busy = true;
        render();
        new Thread(() -> {
            String failure = null;
            try (GameSession lock = GameSession.tryAcquire(getFilesDir())) {
                if (lock == null) throw new IOException("Exit the game before exporting or restoring saves.");
                SaveGameArchive.recover(repository.gameDirectory());
                File saves = saveDirectory();
                if (request == RESTORE) {
                    InputStream input = getContentResolver().openInputStream(uri);
                    if (input == null) throw new IOException("Could not open the save archive.");
                    try (InputStream owned = input) { SaveGameArchive.restore(repository.gameDirectory(), saves, profile.id, owned); }
                } else {
                    if (request == EXPORT_BACKUP) {
                        saves = SaveGameArchive.latestBackup(saves);
                        if (saves == null) throw new IOException("No previous saves are available.");
                    }
                    OutputStream output = getContentResolver().openOutputStream(uri, "wt");
                    if (output == null) throw new IOException("Could not create the save archive.");
                    try (OutputStream owned = output) { SaveGameArchive.exportTo(saves, profile.id, owned); }
                }
            } catch (Exception error) { failure = error.getMessage() == null ? "Could not complete the save archive operation." : error.getMessage(); }
            String error = failure;
            runOnUiThread(() -> {
                busy = false;
                if (isFinishing()) return;
                render();
                if (error != null) LauncherUi.error(this, error);
                else new AlertDialog.Builder(this).setMessage(request == RESTORE ? "Saved games restored." : "Save archive exported.")
                        .setPositiveButton("Done", null).show();
            });
        }, "save-archive").start();
    }

    @Override public void onBackPressed() { if (!busy) super.onBackPressed(); }
}
