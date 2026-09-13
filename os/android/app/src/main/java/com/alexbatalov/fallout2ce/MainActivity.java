package com.alexbatalov.fallout2ce;

import android.content.Intent;
import android.os.Bundle;
import org.libsdl.app.SDLActivity;
import java.io.IOException;

public class MainActivity extends SDLActivity {
    private GameSession gameSession;

    @Override protected void onCreate(Bundle savedInstanceState) {
        String error = null;
        try {
            gameSession = GameSession.tryAcquire(getFilesDir());
            if (gameSession == null) error = "Inställningarna håller på att sparas. Försök starta spelet igen.";
        } catch (IOException failure) {
            error = "Spelets inställningar kunde inte låsas: " + failure.getMessage();
        }
        if (error != null) {
            // This activity runs in :game. Return to the launcher before loading SDL.
            startActivity(new Intent(this, LauncherActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra("launch_error", error));
            android.os.Process.killProcess(android.os.Process.myPid());
            return;
        }
        super.onCreate(savedInstanceState);
    }

    @Override protected void onDestroy() {
        // SDL joins the native thread, including its final config write, first.
        super.onDestroy();
        if (gameSession != null) {
            try { gameSession.close(); } catch (IOException ignored) {}
            gameSession = null;
        }
        // Keep native engine lifecycle isolated from the settings/launcher process.
        System.exit(0);
    }

    @Override protected String[] getLibraries() {
        return new String[]{"fallout2-ce"};
    }
}
