package com.example.pawn_ledger;

import android.database.Cursor;
import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
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
                "SELECT pawn_amount, redeem_amount, interest_amount " +
                        "FROM daily_totals WHERE date = ?",
                new String[]{date}
        )) {
            if (cursor.moveToFirst()) {
                return new long[]{
                        cursor.getLong(0),
                        cursor.getLong(1),
                        cursor.getLong(2)
                };
            }

            return null;
        }
    }

    public Cursor getAllDailyTotals() {
        SQLiteDatabase db = getReadableDatabase();

        return db.rawQuery(
                "SELECT date, pawn_amount, redeem_amount, interest_amount " +
                        "FROM daily_totals ORDER BY date ASC",
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

    public long getMonthlyInterest(int year, int month) {
        java.time.LocalDate start =
                java.time.LocalDate.of(year, month, 1);

        String startDate = start.toString();
        String nextMonthDate = start.plusMonths(1).toString();

        SQLiteDatabase db = getReadableDatabase();

        try (Cursor cursor = db.rawQuery(
                "SELECT COALESCE(SUM(interest_amount), 0) " +
                        "FROM daily_totals WHERE date >= ? AND date < ?",
                new String[]{startDate, nextMonthDate}
        )) {
            if (cursor.moveToFirst()) {
                return cursor.getLong(0);
            }

            return 0;
        }
    }
    public Long getMonthEndPawnBalance(int year, int month) {
        Long openingBalance = getOpeningBalance();

        if (openingBalance == null) {
            return null;
        }

        String nextMonthDate = java.time.LocalDate
                .of(year, month, 1)
                .plusMonths(1)
                .toString();

        SQLiteDatabase db = getReadableDatabase();

        try (Cursor cursor = db.rawQuery(
                "SELECT COALESCE(SUM(pawn_amount - redeem_amount), 0) " +
                        "FROM daily_totals WHERE date < ?",
                new String[]{nextMonthDate}
        )) {
            if (cursor.moveToFirst()) {
                return openingBalance + cursor.getLong(0);
            }

            return openingBalance;
        }
    }
    public String createBackupJson() throws JSONException {
        JSONObject backup = new JSONObject();
        backup.put("format", "pawn_ledger");
        backup.put("version", 1);

        Long openingBalance = getOpeningBalance();
        backup.put(
                "opening_balance",
                openingBalance == null ? JSONObject.NULL : openingBalance
        );

        JSONArray records = new JSONArray();

        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT date, pawn_amount, redeem_amount, interest_amount " +
                        "FROM daily_totals ORDER BY date",
                null
        )) {
            while (cursor.moveToNext()) {
                JSONObject record = new JSONObject();

                record.put("date", cursor.getString(0));
                record.put("pawn_amount", cursor.getLong(1));
                record.put("redeem_amount", cursor.getLong(2));
                record.put("interest_amount", cursor.getLong(3));

                records.put(record);
            }
        }

        backup.put("daily_totals", records);
        return backup.toString(2);
    }
    private long readBackupAmount(
            JSONObject object,
            String field
    ) throws JSONException {
        Object value = object.get(field);

        if (!(value instanceof Integer) &&
                !(value instanceof Long)) {
            throw new JSONException(
                    "Invalid whole-number amount: " + field
            );
        }

        long amount = ((Number) value).longValue();

        if (amount < 0) {
            throw new JSONException(
                    "Amount cannot be negative: " + field
            );
        }

        return amount;
    }
    public void restoreBackupJson(String json) throws JSONException {
        JSONObject backup = new JSONObject(json);

        if (!"pawn_ledger".equals(backup.getString("format")) ||
                readBackupAmount(backup, "version") != 1) {
            throw new JSONException("Unsupported backup file");
        }

        Object openingValue = backup.get("opening_balance");
        Long openingBalance = openingValue == JSONObject.NULL
                ? null
                : readBackupAmount(backup, "opening_balance");

        JSONArray records = backup.getJSONArray("daily_totals");

        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();

        try {
            db.delete("daily_totals", null, null);
            db.delete("settings", null, null);

            if (openingBalance != null) {
                ContentValues settings = new ContentValues();
                settings.put("id", 1);
                settings.put("opening_balance", openingBalance);

                db.insertOrThrow("settings", null, settings);
            }

            for (int i = 0; i < records.length(); i++) {
                JSONObject record = records.getJSONObject(i);
                String date = record.getString("date");

                try {
                    java.time.LocalDate parsedDate =
                            java.time.LocalDate.parse(date);

                    if (!parsedDate.toString().equals(date)) {
                        throw new JSONException("Invalid record date");
                    }
                } catch (java.time.format.DateTimeParseException e) {
                    throw new JSONException("Invalid record date");
                }

                ContentValues values = new ContentValues();
                values.put("date", date);
                values.put(
                        "pawn_amount",
                        readBackupAmount(record, "pawn_amount")
                );
                values.put(
                        "redeem_amount",
                        readBackupAmount(record, "redeem_amount")
                );
                values.put(
                        "interest_amount",
                        readBackupAmount(record, "interest_amount")
                );

                db.insertOrThrow("daily_totals", null, values);
            }

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }
    public boolean saveStaffDailyTotals(
            String date,
            long pawnAmount,
            long redeemAmount
    ) {
        if (pawnAmount < 0 || redeemAmount < 0) {
            return false;
        }

        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();

        try {
            ContentValues values = new ContentValues();
            values.put("pawn_amount", pawnAmount);
            values.put("redeem_amount", redeemAmount);

            int updatedRows = db.update(
                    "daily_totals",
                    values,
                    "date = ?",
                    new String[]{date}
            );

            if (updatedRows == 0) {
                values.put("date", date);

                // New records start with interest of 0.
                // Existing records keep their saved interest.
                db.insertOrThrow("daily_totals", null, values);
            }

            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }
    public boolean saveDailyInterest(String date, long interestAmount) {
        if (interestAmount < 0) {
            return false;
        }

        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();

        try {
            ContentValues values = new ContentValues();
            values.put("interest_amount", interestAmount);

            int updatedRows = db.update(
                    "daily_totals",
                    values,
                    "date = ?",
                    new String[]{date}
            );

            if (updatedRows == 0) {
                values.put("date", date);
                values.put("pawn_amount", 0);
                values.put("redeem_amount", 0);

                db.insertOrThrow("daily_totals", null, values);
            }

            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    public void startFresh(long openingBalance) {
        if (openingBalance < 0) {
            throw new IllegalArgumentException(
                    "Opening balance cannot be negative"
            );
        }

        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();

        try {
            db.delete("daily_totals", null, null);
            db.delete("settings", null, null);

            ContentValues values = new ContentValues();
            values.put("id", 1);
            values.put("opening_balance", openingBalance);

            db.insertOrThrow("settings", null, values);

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }













}
