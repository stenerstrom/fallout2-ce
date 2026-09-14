package com.alexbatalov.fallout2ce;
import java.util.*;
public final class CommandCatalogTests {
    private static int assertions;
    private static void check(boolean value) { assertions++; if (!value) throw new AssertionError("Check " + assertions); }
    private static CommandCatalog.Command command(String id) {
        for (CommandCatalog.Command c : CommandCatalog.commands(true)) if (c.id.equals(id)) return c;
        throw new AssertionError(id);
    }
    public static void main(String[] args) {
        Set<String> ids = new HashSet<>();
        for (CommandCatalog.Command c : CommandCatalog.commands(true)) {
            check(ids.add(c.id));
            check(c.keys(new IniDocument("")).length > 0);
            check(!c.id.toLowerCase(Locale.ROOT).contains("appearance"));
        }
        check(CommandCatalog.commands(true).size() == 27);
        for (CommandCatalog.Command c : CommandCatalog.commands(false)) check(!c.party());
        check(CommandCatalog.commands(false).size() == 18);
        Set<String> defaults = CommandCatalog.favorites(true, null);
        check(defaults.size() == 6 && defaults.contains("loot"));
        Set<String> nonRpu = CommandCatalog.favorites(false, defaults);
        check(!nonRpu.contains("loot") && nonRpu.contains("inventory"));
        check(CommandCatalog.favorites(true, Collections.emptySet()).isEmpty());
        check(CommandCatalog.favorites(true, new HashSet<>(Arrays.asList("missing", "appearance", "save"))).equals(Collections.singleton("save")));
        check(CommandCatalog.favorites(true, ids).size() == 6);
        Set<String> saved = new HashSet<>(Arrays.asList("save", "loot"));
        CommandCatalog.favorites(false, saved).clear();
        check(saved.size() == 2); // SharedPreferences' set must not be mutated.
        check(CommandCatalog.section(false, "party").equals("quick"));
        check(CommandCatalog.section(true, "skills").equals("skills"));
        check(CommandCatalog.section(true, null).equals("quick"));
        IniDocument config = new IniDocument("[SETTINGS]\nLootingOrderKey=0\nHealingOrderKey=\nAmmoTypeOrderKey=48+29\n");
        check(command("loot").keys(config).length == 0);
        check(command("heal").keys(config).length == 0);
        check(Arrays.equals(command("ammo").keys(config), new int[]{113,30}));
        check(Arrays.equals(command("regroup").keys(config), new int[]{46})); // Missing only -> default.
        for (String bad : new String[]{"-1", "garbage", "999", "34+"}) {
            boolean rejected = false;
            try { command("loot").keys(new IniDocument("[SETTINGS]\nLootingOrderKey=" + bad)); }
            catch (IllegalArgumentException expected) { rejected = true; }
            check(rejected);
        }
        boolean unreadable = false;
        try { command("loot").keys(null); } catch (IllegalArgumentException expected) { unreadable = true; }
        check(unreadable);
        check(command("inventory").keys(null).length == 1);
        System.out.println("Command catalog passed: " + assertions + " assertions.");
    }
}
