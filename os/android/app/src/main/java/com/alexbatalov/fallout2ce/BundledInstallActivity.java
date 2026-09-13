package com.alexbatalov.fallout2ce;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import java.io.IOException;

public class BundledInstallActivity extends Activity {
    private volatile boolean working;
    private TextView status;
    private ProgressBar progress;
    private Button retry;
    private long lastProgress;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout body = LauncherUi.page(this, "Förbereder Fallout 2");
        LauncherUi.note(this, body, "Spelfilerna och RPU finns redan i appen. Första starten tar lite längre tid medan filerna förbereds.");
        status = LauncherUi.text(this, body, "Startar…", 18, LauncherUi.GOLD);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        body.addView(progress, new LinearLayout.LayoutParams(-1, LauncherUi.dp(this, 32)));
        retry = LauncherUi.button(this, body, "Försök igen", view -> install());
        LauncherUi.note(this, body, "Låt appen vara öppen. Om förberedelsen avbryts fortsätter den vid nästa start.");
        install();
    }

    private void install() {
        if (working) return;
        working = true;
        retry.setVisibility(android.view.View.GONE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        new Thread(() -> {
            String failure = null;
            try (GameSession session = GameSession.tryAcquire(getFilesDir())) {
                if (session == null) throw new IOException("Spelet är öppet. Avsluta det och försök igen.");
                SettingsRepository repository = new SettingsRepository(this);
                BundledGameExtractor.extract(repository.gameDirectory(), BundledGame.entries(this),
                        path -> getAssets().open("bundled-game/files/" + path), this::updateProgress);
            } catch (Exception error) {
                failure = error.getMessage();
            }
            String message = failure;
            runOnUiThread(() -> {
                working = false;
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                if (isFinishing() || isDestroyed()) return;
                if (message == null) {
                    startActivity(new Intent(this, LauncherActivity.class));
                    finish();
                } else {
                    status.setText("Kunde inte förbereda spelet: " + message);
                    retry.setVisibility(android.view.View.VISIBLE);
                }
            });
        }, "bundled-game-install").start();
    }

    private void updateProgress(long completed, long total) {
        long now = android.os.SystemClock.elapsedRealtime();
        if (now - lastProgress < 250 && completed != total) return;
        lastProgress = now;
        runOnUiThread(() -> {
            if (isDestroyed()) return;
            int percent = total == 0 ? 100 : (int)(completed * 100 / total);
            progress.setProgress(percent);
            status.setText(percent + " % · " + completed / (1024 * 1024) + " / " + total / (1024 * 1024) + " MB");
        });
    }

    @Override public void onBackPressed() {
        if (working) Toast.makeText(this, "Spelfilerna förbereds. Vänta tills det är klart.", Toast.LENGTH_SHORT).show();
        else moveTaskToBack(true);
    }
}
