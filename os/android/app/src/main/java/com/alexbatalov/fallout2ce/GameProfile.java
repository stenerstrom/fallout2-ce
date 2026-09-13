package com.alexbatalov.fallout2ce;

import java.io.File;
import java.io.IOException;

enum GameProfile {
    RPU("rpu", "Fallout 2", "Restoration Project", "Return to California with restored locations, quests and companions.", 0xFFD4BC83),
    SONORA("sonora", "Sonora", "A new frontier", "Explore the desert borderlands and the secrets of Dayglow.", 0xFFE0A473),
    NEVADA("nevada", "Nevada", "Before the legend", "Leave Vault 8 and discover the wasteland before the first Fallout.", 0xFF94C1B4);

    final String id, title, subtitle, description;
    final int accent;
    GameProfile(String id, String title, String subtitle, String description, int accent) {
        this.id=id; this.title=title; this.subtitle=subtitle; this.description=description; this.accent=accent;
    }
    static GameProfile fromId(String id) {
        if (id == null) return RPU;
        for (GameProfile profile : values()) if (profile.id.equals(id)) return profile;
        throw new IllegalArgumentException("Unknown game: " + id);
    }
    File directory(File externalRoot) throws IOException {
        if (externalRoot == null) throw new IOException("Game storage is unavailable.");
        File root=externalRoot.getCanonicalFile();
        // RPU retains its existing directory, including all pre-library saves.
        File result=this == RPU ? root : new File(root, "games/" + id);
        if (!result.getCanonicalPath().equals(result.getAbsolutePath()))
            throw new IOException("The game folder cannot be a symbolic link.");
        return result;
    }
}
