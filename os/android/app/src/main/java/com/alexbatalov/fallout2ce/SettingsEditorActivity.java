package com.alexbatalov.fallout2ce;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Point;
import android.os.Bundle;
import android.text.InputType;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import org.json.JSONArray;
import org.json.JSONObject;

public class SettingsEditorActivity extends Activity {
    private SettingsRepository repository;
    private String file, section;
    private EditText rawEditor;
    private final List<Field> fields = new ArrayList<>();

    private static final class Field {
        String key, original;
        JSONObject metadata;
        View control;
        Supplier<String> value;
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        repository = new SettingsRepository(this);
        file = getIntent().getStringExtra("file");
        section = getIntent().getStringExtra("section");
        if (file == null || section == null) { finish(); return; }
        LinearLayout body = LauncherUi.page(this, getIntent().getStringExtra("title"));
        try {
            String text = repository.read(file);
            if (getIntent().getBooleanExtra("raw", false)) {
                LauncherUi.note(this, body, "Avancerad filredigering. Här kan du även lägga till eller aktivera kommenterade inställningar. Behåll sektioner, nyckelnamn och modkrav.");
                rawEditor = new EditText(this);
                rawEditor.setTag("raw-config");
                rawEditor.setText(state == null ? text : state.getString("raw", text));
                rawEditor.setTextSize(15);
                rawEditor.setTypeface(android.graphics.Typeface.MONOSPACE);
                rawEditor.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                        | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
                rawEditor.setMinLines(12);
                body.addView(rawEditor, new LinearLayout.LayoutParams(-1, -2));
            } else {
                IniDocument document = new IniDocument(text);
                JSONObject schema = file.equals("fallout2.cfg") ? SettingsSchema.section(this, section) : null;
                if (file.equals("fallout2.cfg") && section.equals("screen")) {
                    LauncherUi.note(this, body, "Högre upplösning visar mer av kartan men gör text och knappar mindre. Lägre upplösning eller högre skalning gör dem större.");
                    LauncherUi.button(this, body, "Välj en upplösning", view -> chooseResolution()).setTag("resolution-presets");
                }
                List<String> known = new ArrayList<>();
                if (schema != null) {
                    JSONArray items = schema.getJSONArray("fields");
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject item = items.getJSONObject(i);
                        String key = item.getString("key");
                        known.add(key.toLowerCase(Locale.ROOT));
                        String initial = document.get(section, key, defaultValue(item));
                        addField(body, key, initial, item, state);
                    }
                }
                for (IniDocument.Entry entry : document.entries()) {
                    if (!entry.section.equalsIgnoreCase(section) || known.contains(entry.key.toLowerCase(Locale.ROOT))) continue;
                    JSONObject metadata = new JSONObject();
                    metadata.put("label", entry.key);
                    metadata.put("type", "text");
                    metadata.put("description", entry.description);
                    addField(body, entry.key, entry.value, metadata, state);
                }
                if (fields.isEmpty()) LauncherUi.note(this, body, "Inga aktiva inställningar i den här sektionen. Använd filtextredigeringen för att lägga till värden.");
            }
            LauncherUi.button(this, body, "Spara inställningar", view -> save()).setTag("save-settings");
            LauncherUi.button(this, body, "Avbryt", view -> finish()).setTag("cancel-settings");
        } catch (Exception error) {
            LauncherUi.error(this, error.getMessage());
        }
    }

    private String defaultValue(JSONObject item) throws IOException {
        String key = item.optString("key");
        String fallback = item.optString("default");
        if (section.equals("screen")) {
            IniDocument legacy = new IniDocument(repository.read("f2_res.ini"));
            if (key.equals("resolution_x")) return legacy.get("MAIN", "SCR_WIDTH", fallback);
            if (key.equals("resolution_y")) return legacy.get("MAIN", "SCR_HEIGHT", fallback);
            if (key.equals("windowed")) return legacy.get("MAIN", "WINDOWED", fallback);
            if (key.equals("scale")) {
                try { return Integer.toString(Integer.parseInt(legacy.get("MAIN", "SCALE_2X", "0")) + 1); }
                catch (NumberFormatException ignored) { return fallback; }
            }
        }
        return fallback;
    }

    private void addField(LinearLayout body, String key, String initial, JSONObject metadata, Bundle state) {
        Field field = new Field();
        field.key = key;
        field.original = initial;
        field.metadata = metadata;
        String current = state == null ? initial : state.getString("field:" + key, initial);
        String type = metadata.optString("type", "text");
        String label = metadata.optString("label", key);
        JSONArray choices = metadata.optJSONArray("choices");
        if (type.equals("bool")) {
            Switch control = new Switch(this);
            control.setText(label);
            control.setTextSize(18);
            control.setMinHeight(LauncherUi.dp(this, 56));
            control.setChecked(isTrue(current));
            body.addView(control, new LinearLayout.LayoutParams(-1, -2));
            field.control = control;
            field.value = () -> control.isChecked() == isTrue(initial) ? initial : control.isChecked() ? "1" : "0";
        } else if (choices != null) {
            LauncherUi.text(this, body, label, 18, LauncherUi.GOLD);
            Spinner control = new Spinner(this);
            List<String> labels = new ArrayList<>();
            for (int i = 0; i < choices.length(); i++) labels.add(choices.optString(i));
            int selected;
            try { selected = Integer.parseInt(current); } catch (NumberFormatException invalid) { selected = -1; }
            if (selected < 0 || selected >= choices.length()) {
                selected = choices.length();
                labels.add("Eget värde: " + current);
            }
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            control.setAdapter(adapter);
            control.setSelection(selected);
            control.setMinimumHeight(LauncherUi.dp(this, 52));
            body.addView(control, new LinearLayout.LayoutParams(-1, -2));
            field.control = control;
            int originalSelection = selected;
            field.value = () -> control.getSelectedItemPosition() == originalSelection ? current : Integer.toString(control.getSelectedItemPosition());
        } else if (file.equals("fallout2.cfg") && section.equals("sound") && key.endsWith("_volume")) {
            TextView heading = LauncherUi.text(this, body, label, 18, LauncherUi.GOLD);
            SeekBar control = new SeekBar(this);
            control.setMax(100);
            int volume;
            try { volume = Math.max(0, Math.min(32767, Integer.parseInt(current))); }
            catch (NumberFormatException invalid) { volume = 22281; }
            int progress = Math.round(volume * 100f / 32767f);
            control.setProgress(progress);
            heading.setText(label + " · " + progress + " %");
            control.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                public void onStartTrackingTouch(SeekBar bar) {}
                public void onStopTrackingTouch(SeekBar bar) {}
                public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                    heading.setText(label + " · " + value + " %");
                }
            });
            control.setMinimumHeight(LauncherUi.dp(this, 48));
            body.addView(control, new LinearLayout.LayoutParams(-1, -2));
            field.control = control;
            field.value = () -> control.getProgress() == progress ? current : Integer.toString(Math.round(control.getProgress() * 32767f / 100f));
        } else {
            LauncherUi.text(this, body, label, 18, LauncherUi.GOLD);
            EditText control = new EditText(this);
            control.setSingleLine(true);
            control.setTextSize(18);
            control.setMinHeight(LauncherUi.dp(this, 52));
            if (type.equals("integer") || type.equals("decimal")) {
                control.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED
                        | (type.equals("decimal") ? InputType.TYPE_NUMBER_FLAG_DECIMAL : 0));
            } else {
                control.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            }
            control.setText(current);
            body.addView(control, new LinearLayout.LayoutParams(-1, -2));
            field.control = control;
            field.value = () -> control.getText().toString().trim();
            if (metadata.has("min") && metadata.has("max")) {
                LauncherUi.note(this, body, "Tillåtet: " + metadata.optString("min") + "–" + metadata.optString("max"));
            }
        }
        field.control.setTag("field:" + key);
        field.control.setContentDescription(label);
        if (!metadata.optString("description").isEmpty()) LauncherUi.note(this, body, metadata.optString("description"));
        fields.add(field);
    }

    private static boolean isTrue(String value) {
        try { return Integer.decode(value.trim()) != 0; } catch (NumberFormatException invalid) { return false; }
    }

    private void chooseResolution() {
        Point screen = new Point();
        getWindowManager().getDefaultDisplay().getRealSize(screen);
        List<int[]> sizes = new ArrayList<>();
        sizes.add(new int[]{640,480});
        sizes.add(new int[]{800,600});
        sizes.add(new int[]{960,600});
        sizes.add(new int[]{1000,640});
        sizes.add(new int[]{1200,768});
        sizes.add(new int[]{1280,800});
        sizes.add(new int[]{1500,960});
        sizes.add(new int[]{1920,1200});
        int width = Math.max(screen.x, screen.y), height = Math.min(screen.x, screen.y);
        if (width >= 640 && width <= 7680 && height >= 480 && height <= 4320) sizes.add(new int[]{width,height});
        String[] labels = new String[sizes.size()];
        for (int i = 0; i < sizes.size(); i++) labels[i] = sizes.get(i)[0] + " × " + sizes.get(i)[1];
        new AlertDialog.Builder(this).setTitle("Spelupplösning")
                .setItems(labels, (dialog, index) -> {
                    for (Field field : fields) {
                        if (!(field.control instanceof EditText)) continue;
                        if (field.key.equals("resolution_x")) ((EditText)field.control).setText(Integer.toString(sizes.get(index)[0]));
                        if (field.key.equals("resolution_y")) ((EditText)field.control).setText(Integer.toString(sizes.get(index)[1]));
                        if (field.key.equals("scale")) ((EditText)field.control).setText("1");
                    }
                }).setNegativeButton("Avbryt", null).show();
    }

    private void save() {
        try {
            if (rawEditor != null) {
                repository.saveText(file, rawEditor.getText().toString());
            } else {
                List<IniDocument.Entry> changes = new ArrayList<>();
                for (Field field : fields) {
                    String value = field.value.get();
                    // Commit the complete screen tuple when editing it so absent defaults
                    // (including legacy values) cannot leave width/height/scale inconsistent.
                    if (value.equals(field.original)
                            && !(file.equals("fallout2.cfg") && section.equals("screen"))) continue;
                    validate(field, value);
                    changes.add(new IniDocument.Entry(section, field.key, value, ""));
                }
                if (!changes.isEmpty()) repository.saveChanges(file, changes);
            }
            Toast.makeText(this, "Sparat. Inställningarna används vid nästa spelstart.", Toast.LENGTH_LONG).show();
            finish();
        } catch (IOException | IllegalArgumentException error) {
            LauncherUi.error(this, error.getMessage());
        }
    }

    private void validate(Field field, String value) throws IOException {
        JSONObject metadata = field.metadata;
        String type = metadata.optString("type");
        if (!(type.equals("integer") || type.equals("decimal"))) return;
        try {
            double number = type.equals("integer") ? Integer.parseInt(value) : Double.parseDouble(value);
            if (!Double.isFinite(number)
                    || metadata.has("min") && number < metadata.optDouble("min")
                    || metadata.has("max") && number > metadata.optDouble("max")) throw new NumberFormatException();
        } catch (NumberFormatException invalid) {
            field.control.requestFocus();
            throw new IOException("Ogiltigt värde för " + metadata.optString("label", field.key) + ".");
        }
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        if (rawEditor != null) state.putString("raw", rawEditor.getText().toString());
        for (Field field : fields) state.putString("field:" + field.key, field.value.get());
    }

    @Override protected void onResume() {
        super.onResume();
        if (repository == null) return;
        try {
            if (repository.gameRunning()) {
                Toast.makeText(this, "Avsluta spelet innan du ändrar inställningar.", Toast.LENGTH_LONG).show();
                finish();
            }
        } catch (IOException error) { LauncherUi.error(this, error.getMessage()); finish(); }
    }
}
