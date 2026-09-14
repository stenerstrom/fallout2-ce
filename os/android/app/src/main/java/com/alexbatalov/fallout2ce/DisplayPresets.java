package com.alexbatalov.fallout2ce;

final class DisplayPresets {
    private DisplayPresets() {}

    static int[] size(int screenWidth, int screenHeight, int mapHeight) {
        double aspect = Math.max(screenWidth, screenHeight) / (double)Math.max(1, Math.min(screenWidth, screenHeight));
        aspect = Math.max(4.0 / 3.0, Math.min(3.0, aspect));
        int height = Math.max(480, Math.min(4320, mapHeight));
        int width = Math.max(640, Math.min(7680, (int)Math.round(height * aspect / 8) * 8));
        return new int[]{width, height};
    }
}
