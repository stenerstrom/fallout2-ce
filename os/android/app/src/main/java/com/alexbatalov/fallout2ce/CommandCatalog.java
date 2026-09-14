package com.alexbatalov.fallout2ce;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

// Stable IDs keep favorites separate from translated labels and custom key bindings.
final class CommandCatalog {
    static final int MAX_FAVORITES = 6;

    static final class Command {
        final String id, label, section, configSection, configKey, defaultBinding;
        Command(String id, String label, String section, String configSection, String configKey, String binding) {
            this.id = id; this.label = label; this.section = section;
            this.configSection = configSection; this.configKey = configKey; this.defaultBinding = binding;
        }
        boolean party() { return section.equals("party"); }
        int[] keys(IniDocument config) {
            if (party() && config == null) throw new IllegalArgumentException("Party Orders settings could not be read.");
            // Only an absent key gets a default. Explicit blank/0 stays disabled;
            // malformed values must never send a different, default command.
            return CommandBindings.parse(party() ? config.get(configSection, configKey, defaultBinding) : defaultBinding);
        }
    }

    private static Command party(String id, String label, String key, String binding) {
        return new Command(id, label, "party", "SETTINGS", key, binding);
    }
    private static Command game(String id, String label, String binding) {
        return new Command(id, label, "game", "", "", binding);
    }
    private static final List<Command> COMMANDS = Arrays.asList(
        party("loot", "Loot bodies", "LootingOrderKey", "34"),
        party("heal", "Heal party", "HealingOrderKey", "33"),
        party("regroup", "Regroup", "RegroupOrderKey", "19"),
        party("spread", "Spread out", "SpreadOrderKey", "45"),
        party("holster", "Holster weapons", "HolsterOrderKey", "35"),
        party("pickup", "Pick up / loot", "PickUpKey", "20"),
        party("auto_loot", "Toggle auto-loot", "SwitchKey", "11"),
        party("ammo", "Switch ammo", "AmmoTypeOrderKey", "48+29"),
        new Command("burst", "Toggle burst", "party", "BURST_CONTROL", "burst_key", "32"),
        game("inventory", "Inventory", "23"), game("character", "Character", "46"),
        game("pipboy", "Pip-Boy", "25"), game("map", "Map", "15"),
        game("save", "Save game", "62"), game("load", "Load game", "63"),
        game("hand", "Switch hand", "48"), game("weapon_mode", "Weapon mode", "49"),
        game("end_turn", "End turn", "57"), game("escape", "Menu / Esc", "1"),
        new Command("sneak", "Sneak", "skills", "", "", "2"),
        new Command("lockpick", "Lockpick", "skills", "", "", "3"),
        new Command("steal", "Steal", "skills", "", "", "4"),
        new Command("traps", "Traps", "skills", "", "", "5"),
        new Command("first_aid", "First Aid", "skills", "", "", "6"),
        new Command("doctor", "Doctor", "skills", "", "", "7"),
        new Command("science", "Science", "skills", "", "", "8"),
        new Command("repair", "Repair", "skills", "", "", "9")
    );

    static List<Command> commands(boolean rpu) {
        List<Command> result = new ArrayList<>();
        for (Command command : COMMANDS) if (rpu || !command.party()) result.add(command);
        return result;
    }

    static Set<String> favorites(boolean rpu, Set<String> saved) {
        Set<String> requested = saved == null
                ? new LinkedHashSet<>(Arrays.asList(rpu
                    ? new String[]{"loot", "heal", "regroup", "inventory", "save", "end_turn"}
                    : new String[]{"inventory", "character", "map", "save", "load", "end_turn"}))
                : saved;
        Set<String> result = new LinkedHashSet<>();
        for (Command command : commands(rpu))
            if (requested.contains(command.id) && result.size() < MAX_FAVORITES) result.add(command.id);
        return result;
    }

    static String section(boolean rpu, String saved) {
        return saved != null && (saved.equals("quick") || saved.equals("game")
                || saved.equals("skills") || rpu && saved.equals("party")) ? saved : "quick";
    }
}
