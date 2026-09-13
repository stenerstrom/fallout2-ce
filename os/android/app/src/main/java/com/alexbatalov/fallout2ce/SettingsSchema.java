package com.alexbatalov.fallout2ce;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

final class SettingsSchema {
    static JSONArray load(Context context) throws IOException {
        try (InputStream input = context.getAssets().open("engine-settings.json");
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int length;
            while ((length = input.read(buffer)) != -1) output.write(buffer, 0, length);
            return new JSONArray(new String(output.toByteArray(), StandardCharsets.UTF_8));
        } catch (JSONException invalid) {
            throw new IOException("Inställningsbeskrivningen kunde inte läsas.", invalid);
        }
    }

    static JSONObject section(Context context, String name) throws IOException {
        JSONArray sections = load(context);
        for (int i = 0; i < sections.length(); i++) {
            JSONObject section = sections.optJSONObject(i);
            if (section != null && section.optString("section").equals(name)) return section;
        }
        return null;
    }
}
