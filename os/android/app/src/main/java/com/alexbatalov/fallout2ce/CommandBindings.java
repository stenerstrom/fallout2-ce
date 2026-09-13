package com.alexbatalov.fallout2ce;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

// DIK -> Android key code, joined through SDL 2.32.8's scancode tables.
final class CommandBindings {
    private static final int[] ANDROID_KEYS = {
        -1, 111, 8, 9, 10, 11, 12, 13, 14, 15, 16, 7, 69, 70, 67, 61,
        45, 51, 33, 46, 48, 53, 49, 37, 43, 44, 71, 72, 66, 113, 29, 47,
        32, 34, 35, 36, 38, 39, 40, 74, 75, 68, 59, 73, 54, 52, 31, 50,
        30, 42, 41, 55, 56, 76, 60, 155, 57, 62, 115, 131, 132, 133, 134, 135,
        136, 137, 138, 139, 140, -1, 116, 151, 152, 153, 156, 148, 149, 150, 157, 145,
        146, 147, 144, 158, -1, -1, -1, 141, 142, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 161, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 160, 114, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, 159, -1, 76, -1, -1, 58, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, 122, 19, 92, -1, 21, -1, 22, -1, 123,
        20, 93, 124, 112, -1, -1, -1, -1, -1, -1, -1, 117, 118, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1
    };

    static int[] parse(String text) {
        if (text == null || text.trim().isEmpty() || text.trim().equals("0")) return new int[0];
        LinkedHashSet<Integer> keys = new LinkedHashSet<>();
        for (String part : text.split("\\+", -1)) {
            int dik;
            try {
                String value = part.trim();
                dik = value.startsWith("0x") || value.startsWith("0X")
                        ? Integer.parseInt(value.substring(2), 16) : Integer.parseInt(value);
            } catch (NumberFormatException invalid) {
                throw new IllegalArgumentException("Invalid key combination: " + text);
            }
            if (dik <= 0 || dik >= ANDROID_KEYS.length || ANDROID_KEYS[dik] < 0) {
                throw new IllegalArgumentException("This key is not supported on Android: " + part);
            }
            keys.add(ANDROID_KEYS[dik]);
        }
        // A modifier must already be held when the primary key reaches the RPU hook.
        List<Integer> sorted = new ArrayList<>();
        for (int key : keys) if (modifier(key)) sorted.add(key);
        for (int key : keys) if (!modifier(key)) sorted.add(key);
        int[] result = new int[sorted.size()];
        for (int i = 0; i < result.length; i++) result[i] = sorted.get(i);
        return result;
    }

    private static boolean modifier(int key) {
        return key == 57 || key == 58 || key == 59 || key == 60
                || key == 113 || key == 114 || key == 117 || key == 118;
    }
}
