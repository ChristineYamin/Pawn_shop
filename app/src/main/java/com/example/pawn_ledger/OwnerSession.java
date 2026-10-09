package com.example.pawn_ledger;

public final class OwnerSession {

    private static boolean unlocked = false;

    private OwnerSession() {
    }

    public static void unlock() {
        unlocked = true;
    }

    public static void lock() {
        unlocked = false;
    }

    public static boolean isUnlocked() {
        return unlocked;
    }
}