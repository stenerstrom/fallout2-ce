package com.alexbatalov.fallout2ce;

import android.content.Context;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class BundledGame {
    static boolean available(Context context) {
        try (InputStream ignored = context.getAssets().open("bundled-game/manifest.json")) { return true; }
        catch (IOException absent) { return false; }
    }

    static List<BundledGameExtractor.Entry> entries(Context context) throws IOException {
        try (InputStream input = context.getAssets().open("bundled-game/manifest.json");
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int length;
            while ((length = input.read(buffer)) != -1) output.write(buffer, 0, length);
            JSONObject manifest = new JSONObject(output.toString("UTF-8"));
            JSONArray files = manifest.getJSONArray("files");
            List<BundledGameExtractor.Entry> result = new ArrayList<>();
            java.util.Set<String> names = new java.util.HashSet<>();
            for (int i = 0; i < files.length(); i++) {
                JSONObject file = files.getJSONObject(i);
                String path = file.getString("path");
                names.add(path);
                result.add(new BundledGameExtractor.Entry(path, file.getLong("size"), file.getString("sha256")));
            }
            for (String required : new String[]{"master.dat", "critter.dat", "ce.dat", "fallout2.cfg"}) {
                if (!names.contains(required)) throw new IOException("Spelpaketet saknar " + required);
            }
            return result;
        } catch (JSONException malformed) { throw new IOException("Spelpaketets filförteckning kunde inte läsas.", malformed); }
    }
}
