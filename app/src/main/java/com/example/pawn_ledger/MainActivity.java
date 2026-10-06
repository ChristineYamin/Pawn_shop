package com.example.pawn_ledger;

import android.os.Bundle;

import java.time.LocalDate;
import java.time.ZoneId;

import android.app.DatePickerDialog;

import android.widget.EditText;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import android.widget.Button;
import android.widget.Toast;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {
    private LocalDate selectedDate =
            LocalDate.now(ZoneId.of("Asia/Yangon"));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        LedgerDb ledgerDb = new LedgerDb(this);
        refreshRecordsTable(ledgerDb);
        Button dateButton = findViewById(R.id.selectDateButton);
        dateButton.setText(selectedDate.toString());

        dateButton.setOnClickListener(v -> {
            DatePickerDialog picker = new DatePickerDialog(
                    MainActivity.this,
                    (view, year, month, day) -> {
                        LocalDate chosenDate =
                                LocalDate.of(year, month + 1, day);

                        if (chosenDate.isAfter(
                                LocalDate.now(ZoneId.of("Asia/Yangon"))
                        )) {
                            Toast.makeText(
                                    MainActivity.this,
                                    "Choose today or an earlier date",
                                    Toast.LENGTH_SHORT
                            ).show();
                            return;
                        }

                        selectedDate = chosenDate;
                        dateButton.setText(selectedDate.toString());
                        loadSelectedDate(ledgerDb);
                        refreshCurrentBalance(ledgerDb);
                        refreshRecordsTable(ledgerDb);
                    },
                    selectedDate.getYear(),
                    selectedDate.getMonthValue() - 1,
                    selectedDate.getDayOfMonth()
            );

            picker.show();
        });

        EditText openingInput = findViewById(R.id.openingBalanceInput);
        Button openingButton = findViewById(R.id.setOpeningBalanceButton);

        Long savedOpeningBalance = ledgerDb.getOpeningBalance();

        if (savedOpeningBalance != null) {
            openingInput.setText(String.valueOf(savedOpeningBalance));
            openingInput.setEnabled(false);
            openingButton.setEnabled(false);
        }

        openingButton.setOnClickListener(v -> {
            String amountText = openingInput.getText().toString().trim();

            if (amountText.isEmpty()) {
                openingInput.setError("Enter your opening balance");
                return;
            }

            try {
                long amount = Long.parseLong(amountText);

                if (amount < 0) {
                    openingInput.setError("Enter 0 or more");
                    return;
                }

                boolean saved = ledgerDb.saveOpeningBalance(amount);

                if (saved) {
                    openingInput.setEnabled(false);
                    openingButton.setEnabled(false);
                    refreshCurrentBalance(ledgerDb);

                    Toast.makeText(
                            MainActivity.this,
                            "Opening balance saved",
                            Toast.LENGTH_SHORT
                    ).show();
                } else {
                    Toast.makeText(
                            MainActivity.this,
                            "Not saved. An opening balance may already exist.",
                            Toast.LENGTH_LONG
                    ).show();
                }

            } catch (NumberFormatException e) {
                openingInput.setError("Enter a whole number without commas; it may be too large");
            }
        });
        Button saveButton = findViewById(R.id.saveButton);

        saveButton.setOnClickListener(v -> {
                    android.widget.EditText pawnInput =
                            findViewById(R.id.pawnAmountInput);

                    android.widget.EditText redeemInput =
                            findViewById(R.id.redeemAmountInput);
                    EditText interestInput =
                            findViewById(R.id.interestAmountInput);

                    String pawnText = pawnInput.getText().toString().trim();
                    String redeemText = redeemInput.getText().toString().trim();

                    String interestText =
                            interestInput.getText().toString().trim();

                    if (interestText.isEmpty()) {
                        interestInput.setError("Enter interest, or 0");
                        return;
                    }

                    if (pawnText.isEmpty()) {
                        pawnInput.setError("Enter an amount, or 0");
                        return;
                    }

                    if (redeemText.isEmpty()) {
                        redeemInput.setError("Enter an amount, or 0");
                        return;
                    }

                    try {
                        long pawnAmount = Long.parseLong(pawnText);
                        long redeemAmount = Long.parseLong(redeemText);
                        long interestAmount = Long.parseLong(interestText);

                        if (interestAmount < 0) {
                            interestInput.setError("Enter 0 or more");
                            return;
                        }

                        if (pawnAmount < 0 || redeemAmount < 0) {
                            Toast.makeText(
                                    MainActivity.this,
                                    "Enter amounts of 0 or more",
                                    Toast.LENGTH_SHORT
                            ).show();
                            return;
                        }
                        String recordDate = selectedDate.toString();

                        boolean saved = ledgerDb.saveDailyTotals(
                                recordDate,
                                pawnAmount,
                                redeemAmount,
                                interestAmount
                        );


                        if (!saved) {

                            if (ledgerDb.getDailyTotals(recordDate) == null) {
                                Toast.makeText(
                                        MainActivity.this,
                                        "Could not save the record",
                                        Toast.LENGTH_LONG
                                ).show();
                                return;
                            }

                            new androidx.appcompat.app.AlertDialog.Builder(this)
                                    .setTitle("Update saved record?")
                                    .setMessage(
                                            "Replace the amounts for " + recordDate + "?"
                                    )
                                    .setNegativeButton("Cancel", null)
                                    .setPositiveButton("Update", (dialog, which) -> {
                                        boolean updated = ledgerDb.updateDailyTotals(
                                                recordDate,
                                                pawnAmount,
                                                redeemAmount,
                                                interestAmount
                                        );

                                        if (updated) {
                                            refreshCurrentBalance(ledgerDb);
                                            refreshRecordsTable(ledgerDb);
                                            loadSelectedDate(ledgerDb);

                                            Toast.makeText(
                                                    MainActivity.this,
                                                    "Record updated",
                                                    Toast.LENGTH_SHORT
                                            ).show();
                                        } else {
                                            Toast.makeText(
                                                    MainActivity.this,
                                                    "Could not update the record",
                                                    Toast.LENGTH_LONG
                                            ).show();
                                        }
                                    })
                                    .show();

                            return;
                        }

                        Toast.makeText(
                                MainActivity.this,
                                "Today's totals saved",
                                Toast.LENGTH_SHORT
                        ).show();

                        refreshCurrentBalance(ledgerDb);
                        long net = pawnAmount - redeemAmount;
                        String netText = net > 0 ? "+" + net : String.valueOf(net);

                        TextView netResultText = findViewById(R.id.netResultText);

                        netResultText.setText(
                                getString(R.string.daily_net_result, netText)
                        );

                    } catch (NumberFormatException e) {
                        Toast.makeText(
                                MainActivity.this,
                                "Enter whole numbers without commas; the amount may be too large",
                                Toast.LENGTH_LONG
                        ).show();
                    }
                });

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }
    private void refreshCurrentBalance(LedgerDb ledgerDb) {
        TextView balanceText = findViewById(R.id.currentBalanceText);

        Long openingBalance = ledgerDb.getOpeningBalance();

        if (openingBalance == null) {
            balanceText.setText(R.string.current_balance_placeholder);
            return;
        }

        long currentBalance = openingBalance + ledgerDb.getTotalNet();

        String formattedBalance = java.text.NumberFormat
                .getIntegerInstance(java.util.Locale.US)
                .format(currentBalance);

        balanceText.setText(
                getString(R.string.current_balance_value, formattedBalance)
        );
    }
    private void loadSelectedDate(LedgerDb ledgerDb) {
        EditText pawnInput = findViewById(R.id.pawnAmountInput);
        EditText redeemInput = findViewById(R.id.redeemAmountInput);
        TextView netText = findViewById(R.id.netResultText);

        long[] amounts = ledgerDb.getDailyTotals(
                selectedDate.toString()
        );

        pawnInput.setError(null);
        redeemInput.setError(null);

        if (amounts == null) {
            pawnInput.setText("");
            redeemInput.setText("");
            netText.setText(R.string.daily_net_placeholder);
            return;
        }

        pawnInput.setText(String.valueOf(amounts[0]));
        redeemInput.setText(String.valueOf(amounts[1]));

        long net = amounts[0] - amounts[1];
        String formattedNet = net > 0 ? "+" + net : String.valueOf(net);

        netText.setText(
                getString(R.string.daily_net_result, formattedNet)
        );



    }
    private void refreshRecordsTable(LedgerDb ledgerDb) {
        android.widget.TableLayout table =
                findViewById(R.id.recordsTable);

        table.removeAllViews();

        addRecordRow(table, "Date", "+ Pawn", "− Redeem", "Net");

        java.text.NumberFormat formatter =
                java.text.NumberFormat.getIntegerInstance(
                        java.util.Locale.US
                );

        try (android.database.Cursor cursor =
                     ledgerDb.getAllDailyTotals()) {

            while (cursor.moveToNext()) {
                String date = cursor.getString(0);
                long pawn = cursor.getLong(1);
                long redeem = cursor.getLong(2);
                long net = pawn - redeem;

                String netText =
                        (net > 0 ? "+" : "") + formatter.format(net);

                addRecordRow(
                        table,
                        date,
                        formatter.format(pawn),
                        formatter.format(redeem),
                        netText
                );
            }
        }
    }

    private void addRecordRow(
            android.widget.TableLayout table,
            String... values
    ) {
        android.widget.TableRow row =
                new android.widget.TableRow(this);

        int padding = Math.round(
                8 * getResources().getDisplayMetrics().density
        );

        for (String value : values) {
            TextView cell = new TextView(this);
            cell.setText(value);
            cell.setTextSize(14);
            cell.setPadding(padding, padding, padding, padding);
            row.addView(cell);
        }

        table.addView(row);
    }
}