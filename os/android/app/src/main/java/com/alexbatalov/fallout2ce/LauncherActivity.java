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
        LauncherUi.note(this, body, "Choose your settings before starting the game.");
        status = LauncherUi.text(this, body, "", 18, LauncherUi.GOLD);
        play = LauncherUi.button(this, body, "Play", view -> startGame());
        play.setTag("play");
        settings = LauncherUi.button(this, body, "Settings", view ->
                startActivity(new Intent(this, SettingsActivity.class)));
        settings.setTag("settings");
        LauncherUi.note(this, body, "After exiting the game, reopen the app to change settings. Your saves are kept.");
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
            play.setText(running ? "Resume game" : installed ? "Play" : "Choose game files");
            if (running) {
                status.setText("The game is running. Exit through the game menu before changing settings.");
            } else if (!installed) {
                status.setText("Select your Fallout 2 folder with RPU to get started.");
            } else {
                IniDocument config = new IniDocument(repository.read("fallout2.cfg"));
                String width = config.get("screen", "resolution_x", "640");
                String height = config.get("screen", "resolution_y", "480");
                status.setText("Ready to play · " + width + " × " + height);
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
