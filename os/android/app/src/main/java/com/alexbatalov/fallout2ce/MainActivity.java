package com.alexbatalov.fallout2ce;

import android.content.Intent;
import android.os.Bundle;
import org.libsdl.app.SDLActivity;
import java.io.IOException;

public class MainActivity extends SDLActivity {
    private GameSession gameSession;
    private GameProfile profile;
    private GameCommandHud commandHud;

    @Override protected void onCreate(Bundle savedInstanceState) {
        String error = null;
        try {
            profile = GameProfiles.current(this);
            gameSession = GameSession.tryAcquire(getFilesDir());
            if (gameSession == null) error = "Settings are being saved. Try starting the game again.";
            else if (BundledGame.needsInstall(this) || !new SettingsRepository(this).hasGameData())
                error = "Prepare this game from the library before playing.";
        } catch (IOException | IllegalArgumentException failure) {
            error = "Could not lock the game settings: " + failure.getMessage();
        }
        if (error != null) {
            // This activity runs in :game. Return to the launcher before loading SDL.
            startActivity(new Intent(this, LauncherActivity.class)
                    .putExtra(GameProfiles.EXTRA, profile == null ? "rpu" : profile.id)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra("launch_error", error));
            android.os.Process.killProcess(android.os.Process.myPid());
            return;
        }
        super.onCreate(savedInstanceState);
        if (mLayout != null && mSurface != null) commandHud = new GameCommandHud(this, mLayout, mSurface);
    }

    @Override protected void onPause() {
        if (commandHud != null) commandHud.pause();
        super.onPause();
    }

    @Override public void onBackPressed() {
        if (commandHud != null && commandHud.closeMenu()) return;
        super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (commandHud != null) { commandHud.dispose(); commandHud = null; }
        // SDL joins the native thread, including its final config write, first.
        super.onDestroy();
        if (gameSession != null) {
            try { gameSession.close(); } catch (IOException ignored) {}
            gameSession = null;
        }
        // Keep native engine lifecycle isolated from the settings/launcher process.
        System.exit(0);
    }

    // SDL calls getExternalFilesDir through JNI before choosing its working directory.
    // The game process is restarted between profiles, so SDL's cached path is isolated.
    @Override public java.io.File getExternalFilesDir(String type) {
        java.io.File root=super.getExternalFilesDir(type);
        if (type != null) return root;
        try { return (profile == null ? GameProfile.RPU : profile).directory(root); }
        catch (IOException invalid) { return null; }
    }

    @Override protected String[] getLibraries() {
        return new String[]{"fallout2-ce"};
    }
}
