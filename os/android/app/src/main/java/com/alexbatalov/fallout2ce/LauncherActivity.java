package com.alexbatalov.fallout2ce;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.IOException;

public class LauncherActivity extends Activity {
    private SettingsRepository repository;
    private TextView status;
    private Button play, settings;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        repository = new SettingsRepository(this);
        LinearLayout body = LauncherUi.page(this, "Fallout 2 RPU CE");
        LauncherUi.note(this, body, "Välj inställningar innan du startar spelet.");
        status = LauncherUi.text(this, body, "", 18, LauncherUi.GOLD);
        play = LauncherUi.button(this, body, "Spela", view -> startGame());
        play.setTag("play");
        settings = LauncherUi.button(this, body, "Inställningar", view ->
                startActivity(new Intent(this, SettingsActivity.class)));
        settings.setTag("settings");
        LauncherUi.note(this, body, "Efter att du har avslutat spelet kan du öppna appen igen för att ändra inställningar. Sparningarna finns kvar.");
    }

    @Override protected void onResume() {
        super.onResume();
        try {
            if (BundledGame.available(this) && !repository.hasGameData()) {
                startActivity(new Intent(this, BundledInstallActivity.class));
                return;
            }
        } catch (IOException failure) { LauncherUi.error(this, failure.getMessage()); }
        refresh();
        String error = getIntent().getStringExtra("launch_error");
        if (error != null) {
            getIntent().removeExtra("launch_error");
            LauncherUi.error(this, error);
        }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (focused && repository != null) refresh();
    }

    private void refresh() {
        try {
            boolean running = repository.gameRunning();
            boolean installed = repository.hasGameData();
            settings.setEnabled(installed && !running);
            play.setText(running ? "Fortsätt spela" : installed ? "Spela" : "Välj spelfiler");
            if (running) {
                status.setText("Spelet är öppet. Avsluta via spelets meny innan du ändrar inställningarna.");
            } else if (!installed) {
                status.setText("Välj din Fallout 2-mapp med RPU för att komma igång.");
            } else {
                IniDocument config = new IniDocument(repository.read("fallout2.cfg"));
                String width = config.get("screen", "resolution_x", "640");
                String height = config.get("screen", "resolution_y", "480");
                status.setText("Redo att spela · " + width + " × " + height);
            }
        } catch (IOException error) {
            settings.setEnabled(false);
            status.setText(error.getMessage());
        }
    }

    private void startGame() {
        try {
            if (!repository.hasGameData()) {
                startActivity(new Intent(this, BundledGame.available(this) ? BundledInstallActivity.class : ImportActivity.class));
                return;
            }
            if (!repository.gameRunning()) {
                SettingsValidation.validateScreen(new IniDocument(repository.read("fallout2.cfg")));
            }
            startActivity(new Intent(this, MainActivity.class));
        } catch (IOException error) {
            LauncherUi.error(this, error.getMessage());
        }
    }
}
