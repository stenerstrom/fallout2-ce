package com.alexbatalov.fallout2ce;

import java.util.Arrays;

public final class CommandBindingsTests {
    private static int assertions;
    private static void expect(String text, int... expected) {
        assertions++;
        if (!Arrays.equals(expected, CommandBindings.parse(text))) throw new AssertionError(text);
    }
    private static void rejects(String text) {
        assertions++;
        try { CommandBindings.parse(text); }
        catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Accepted " + text);
    }
    public static void main(String[] args) {
        expect("34", 35); // G: loot
        expect("33", 34); // F: healing
        expect("35", 36); // H: holster
        expect("19", 46); // R: regroup
        expect("45", 52); // X: scatter
        expect("20", 48); // T: pickup
        expect("11", 7); // 0: pickup/loot mode
        expect("48+29", 113, 30); // Ctrl before B, despite config's reversed order
        expect("29+48", 113, 30);
        expect("48+29+42", 113, 59, 30); // Ctrl and Shift before primary key
        expect("48+29+29", 113, 30); // no stuck duplicate modifiers
        expect("0x22", 35);
        expect(" 34 ", 35);
        expect("0"); // disabled
        expect("");
        expect("62", 134); // F4: save dialog
        expect("63", 135); // F5: load dialog
        expect("2", 8); // 1: sneak
        expect("9", 15); // 8: repair
        expect("15", 61); // Tab: map
        expect("1", 111); // actual Escape, not Android Back
        for (String bad : new String[]{"256","-2","48+","34+0","hello","84","34++29"}) rejects(bad);
        System.out.println("Command bindings tests passed: " + assertions + " assertions.");
    }
}
