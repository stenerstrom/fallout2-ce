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
        LinearLayout body = LauncherUi.page(this, "Preparing " + GameProfiles.current(this).title);
        LauncherUi.note(this, body, "This adventure is included in the app. Its files are prepared once, with separate settings and saved games.");
        status = LauncherUi.text(this, body, "Starting…", 18, LauncherUi.GOLD);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        body.addView(progress, new LinearLayout.LayoutParams(-1, LauncherUi.dp(this, 32)));
        retry = LauncherUi.button(this, body, "Retry", view -> install());
        LauncherUi.note(this, body, "Keep the app open. If preparation is interrupted, it will resume the next time you open the app.");
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
                if (session == null) throw new IOException("The game is running. Exit the game and try again.");
                BundledGame.install(this, this::updateProgress);
            } catch (Exception error) {
                failure = error.getMessage();
            }
            String message = failure;
            runOnUiThread(() -> {
                working = false;
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                if (isFinishing() || isDestroyed()) return;
                if (message == null) {
                    startActivity(GameProfiles.intent(this, LauncherActivity.class));
                    finish();
                } else {
                    status.setText("Could not prepare the game: " + message);
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
        if (working) Toast.makeText(this, "The game files are being prepared. Please wait until preparation is complete.", Toast.LENGTH_SHORT).show();
        else moveTaskToBack(true);
    }
}
