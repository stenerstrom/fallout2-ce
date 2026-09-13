package com.alexbatalov.fallout2ce;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public final class SettingsTests {
    private static int assertions;

    private static void equal(Object expected, Object actual) {
        assertions++;
        if (!expected.equals(actual)) throw new AssertionError("Expected " + expected + " but got " + actual);
    }

    private static void rejects(CheckedAction action) throws Exception {
        assertions++;
        try { action.run(); }
        catch (IOException | IllegalArgumentException expected) { return; }
        throw new AssertionError("Invalid setting was accepted");
    }

    private interface CheckedAction { void run() throws Exception; }

    public static void main(String[] args) throws Exception {
        if (args.length == 2 && args[0].equals("probe")) {
            try (GameSession session = GameSession.tryAcquire(new File(args[1]))) {
                System.exit(session == null ? 2 : 0);
            }
        }
        String original = "\uFEFF; RPU settings\r\n[Screen]\r\n Resolution_X = 960  ; keep me\r\n"
                + "resolution_y=600\r\nscale=1\r\n\r\n[mods]\r\nunknown_key = x[y] # preserve\r\n";
        IniDocument document = new IniDocument(original);
        equal(original, document.toString());
        equal("960", document.get("screen", "RESOLUTION_X", ""));
        equal("x[y]", document.get("mods", "unknown_key", ""));
        document.set("screen", "resolution_x", "960");
        equal(original, document.toString());
        document.set("screen", "resolution_x", "1500");
        equal(original.replace("960  ;", "1500  ;"), document.toString());
        document.set("screen", "resolution_y", "960");
        equal("960", new IniDocument(document.toString()).get("screen", "resolution_y", ""));
        document.set("screen", "windowed", "0");
        document.set("new_section", "new_key", "new value");
        document.set("", "root_key", "root");
        IniDocument roundTrip = new IniDocument(document.toString());
        equal("0", roundTrip.get("screen", "windowed", ""));
        equal("new value", roundTrip.get("new_section", "new_key", ""));
        equal("root", roundTrip.get("", "root_key", ""));
        equal("x[y]", roundTrip.get("mods", "unknown_key", ""));
        equal(true, document.toString().contains("; keep me\r\n"));
        equal(false, document.toString().replace("\r\n", "").contains("\n"));

        IniDocument duplicate = new IniDocument("[screen]\nresolution_x=640\n[SCREEN]\nRESOLUTION_X=1280 # last\n");
        equal("1280", duplicate.get("screen", "resolution_x", ""));
        duplicate.set("screen", "resolution_x", "1500");
        equal("[screen]\nresolution_x=1500\n[SCREEN]\nRESOLUTION_X=1500 # last\n", duplicate.toString());
        equal(1, duplicate.entries().size());

        IniDocument blank = new IniDocument("[test]\n; explanation\nempty=  ; preserved\nhash=a#b; engine uses semicolon first\n");
        equal("explanation", blank.entries().get(0).description);
        equal("a#b", blank.get("test", "hash", ""));
        blank.set("test", "empty", "value");
        equal("value", new IniDocument(blank.toString()).get("test", "empty", ""));
        equal(true, blank.toString().contains("; preserved"));
        rejects(() -> blank.set("test", "key", "first\ninjected=1"));
        rejects(() -> blank.set("bad]", "key", "1"));
        rejects(() -> blank.set("test", "key", "1;hidden"));

        for (String dimensions : new String[]{"640,480,1", "1500,960,1", "3000,1920,2", "7680,4320,4"}) {
            SettingsValidation.validateScreen(screen(dimensions));
        }
        SettingsValidation.validateScreen(new IniDocument(""));
        for (String dimensions : new String[]{"639,480,1", "640,479,1", "7681,480,1", "640,4321,1",
                "640,480,0", "640,480,5", "960,600,2", "1280,959,2", "foo,600,1"}) {
            rejects(() -> SettingsValidation.validateScreen(screen(dimensions)));
        }

        File directory = Files.createTempDirectory("force-settings-test").toFile();
        try {
            try (GameSession held = GameSession.tryAcquire(directory)) {
                equal(true, held != null);
                equal(null == GameSession.tryAcquire(directory), true);
                equal(2, probe(directory));
            }
            equal(0, probe(directory));
            try (GameSession reacquired = GameSession.tryAcquire(directory)) {
                equal(true, reacquired != null);
            }
        } finally {
            new File(directory, "game-session.lock").delete();
            directory.delete();
        }
        System.out.println("Settings tests passed: " + assertions + " assertions; INI preservation, validation and process locking.");
    }

    private static IniDocument screen(String values) {
        String[] parts = values.split(",");
        return new IniDocument("[screen]\nresolution_x=" + parts[0] + "\nresolution_y=" + parts[1] + "\nscale=" + parts[2]);
    }

    private static int probe(File directory) throws Exception {
        return new ProcessBuilder(new File(System.getProperty("java.home"), "bin/java").getPath(),
                "-cp", System.getProperty("java.class.path"), SettingsTests.class.getName(),
                "probe", directory.getPath()).inheritIO().start().waitFor();
    }
}
