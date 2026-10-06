package com.example.pawn_ledger;

import android.database.Cursor;
import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class LedgerDb extends SQLiteOpenHelper {

    public LedgerDb(Context context) {
        super(context, "pawn_ledger.db", null, 3);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE daily_totals (" +
                        "date TEXT PRIMARY KEY, " +
                        "pawn_amount INTEGER NOT NULL CHECK(pawn_amount >= 0), " +
                        "redeem_amount INTEGER NOT NULL CHECK(redeem_amount >= 0), " +
                        "interest_amount INTEGER NOT NULL DEFAULT 0 CHECK(interest_amount >= 0)" +
                        ")"
        );
        createSettingsTable(db);

    }


    @Override
    public void onUpgrade(
            SQLiteDatabase db,
            int oldVersion,
            int newVersion
    ) {
        if (oldVersion < 2) {
            createSettingsTable(db);
        }

        if (oldVersion < 3) {
            db.execSQL(
                    "ALTER TABLE daily_totals " +
                            "ADD COLUMN interest_amount INTEGER NOT NULL " +
                            "DEFAULT 0 CHECK(interest_amount >= 0)"
            );
        }
    }

    public boolean saveDailyTotals(
            String date,
            long pawnAmount,
            long redeemAmount,
            long interestAmount
    ) {
        ContentValues values = new ContentValues();
        values.put("date", date);
        values.put("pawn_amount", pawnAmount);
        values.put("redeem_amount", redeemAmount);
        values.put("interest_amount", interestAmount);

        SQLiteDatabase db = getWritableDatabase();

        long result = db.insertWithOnConflict(
                "daily_totals",
                null,
                values,
                SQLiteDatabase.CONFLICT_IGNORE
        );

        return result != -1;
    }

    public long getTotalNet() {
        SQLiteDatabase db = getReadableDatabase();

        try (Cursor cursor = db.rawQuery(
                "SELECT COALESCE(SUM(pawn_amount - redeem_amount), 0) " +
                        "FROM daily_totals",
                null
        )) {
            if (cursor.moveToFirst()) {
                return cursor.getLong(0);
            }

            return 0;
        }
    }

    private void createSettingsTable(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE IF NOT EXISTS settings (" +
                        "id INTEGER PRIMARY KEY CHECK(id = 1), " +
                        "opening_balance INTEGER NOT NULL " +
                        "CHECK(opening_balance >= 0)" +
                        ")"
        );
    }
    public boolean saveOpeningBalance(long amount) {
        if (amount < 0) {
            return false;
        }

        ContentValues values = new ContentValues();
        values.put("id", 1);
        values.put("opening_balance", amount);

        SQLiteDatabase db = getWritableDatabase();

        long result = db.insertWithOnConflict(
                "settings",
                null,
                values,
                SQLiteDatabase.CONFLICT_IGNORE
        );

        return result != -1;
    }

    public Long getOpeningBalance() {
        SQLiteDatabase db = getReadableDatabase();

        try (Cursor cursor = db.rawQuery(
                "SELECT opening_balance FROM settings WHERE id = 1",
                null
        )) {
            if (cursor.moveToFirst()) {
                return cursor.getLong(0);
            }

            return null;
        }
    }
    public long[] getDailyTotals(String date) {
        SQLiteDatabase db = getReadableDatabase();

        try (Cursor cursor = db.rawQuery(
                "SELECT pawn_amount, redeem_amount " +
                        "FROM daily_totals WHERE date = ?",
                new String[]{date}
        )) {
            if (cursor.moveToFirst()) {
                return new long[]{
                        cursor.getLong(0),
                        cursor.getLong(1)
                };
            }

            return null;
        }
    }

    public Cursor getAllDailyTotals() {
        SQLiteDatabase db = getReadableDatabase();

        return db.rawQuery(
                "SELECT date, pawn_amount, redeem_amount " +
                        "FROM daily_totals ORDER BY date DESC",
                null
        );
    }

    public boolean updateDailyTotals(
            String date,
            long pawnAmount,
            long redeemAmount,
            long interestAmount
    ) {
        if (pawnAmount < 0 || redeemAmount < 0 || interestAmount < 0) {
            return false;
        }

        ContentValues values = new ContentValues();
        values.put("pawn_amount", pawnAmount);
        values.put("redeem_amount", redeemAmount);

        SQLiteDatabase db = getWritableDatabase();

        int updatedRows = db.update(
                "daily_totals",
                values,
                "date = ?",
                new String[]{date}
        );

        return updatedRows == 1;
    }











}
