package com.alexbatalov.fallout2ce;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import java.io.File;
import java.io.IOException;

final class GameProfiles {
    static final String EXTRA="game_id";
    static GameProfile current(Context context) {
        if (context instanceof Activity) {
            String id=((Activity)context).getIntent().getStringExtra(EXTRA);
            if (id != null) return GameProfile.fromId(id);
        }
        String id=context.getSharedPreferences("library", Context.MODE_PRIVATE).getString("selected", "rpu");
        try { return GameProfile.fromId(id); } catch (IllegalArgumentException invalid) { return GameProfile.RPU; }
    }
    static void select(Activity activity, GameProfile profile) {
        activity.getIntent().putExtra(EXTRA, profile.id);
        activity.getSharedPreferences("library", Context.MODE_PRIVATE).edit().putString("selected", profile.id).apply();
    }
    static Intent intent(Context context, Class<?> target) {
        return new Intent(context, target).putExtra(EXTRA, current(context).id);
    }
    static File directory(Context context, GameProfile profile) throws IOException {
        return profile.directory(context.getApplicationContext().getExternalFilesDir(null));
    }
}
