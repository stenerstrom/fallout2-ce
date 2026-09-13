package com.alexbatalov.fallout2ce;

import java.io.IOException;

final class SettingsValidation {
    static void validateScreen(IniDocument document) throws IOException {
        try {
            int width = Integer.parseInt(document.get("screen", "resolution_x", "640"));
            int height = Integer.parseInt(document.get("screen", "resolution_y", "480"));
            int scale = Integer.parseInt(document.get("screen", "scale", "1"));
            if (width < 640 || width > 7680 || height < 480 || height > 4320
                    || scale < 1 || scale > 4 || width / scale < 640 || height / scale < 480) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException invalid) {
            throw new IOException("Choose 640–7680 × 480–4320 pixels and a scale of 1–4. At least 640 × 480 pixels must remain after scaling.");
        }
    }
}
