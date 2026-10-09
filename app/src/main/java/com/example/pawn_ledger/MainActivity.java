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
import android.content.Intent;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends OwnerActivity{
    private LocalDate selectedDate =
            LocalDate.now(ZoneId.of("Asia/Yangon"));
    private final ActivityResultLauncher<String[]> restoreLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.OpenDocument(),
                    uri -> {
                        if (uri != null) {
                            runWithOwnerAccess(
                                    () -> readBackupForRestore(uri)
                            );
                        }
                    }
            );


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!OwnerSession.isUnlocked()) {
            android.content.Intent intent =
                    new android.content.Intent(
                            this,
                            StaffEntryActivity.class
                    );

            intent.addFlags(
                    android.content.Intent.FLAG_ACTIVITY_NEW_TASK |
                            android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
            );

            startActivity(intent);
            finish();
            return;
        }
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        LedgerDb ledgerDb = new LedgerDb(this);
        Button recordsButton = findViewById(R.id.openRecordsButton);

        recordsButton.setOnClickListener(v -> {
            Intent intent = new Intent(
                    this,
                    RecordsActivity.class
            );
            startActivity(intent);
        });
        Button lockButton = findViewById(R.id.lockOwnerButton);

        lockButton.setOnClickListener(v -> {
            OwnerSession.lock();

            Intent intent = new Intent(
                    this,
                    StaffEntryActivity.class
            );

            intent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK |
                            Intent.FLAG_ACTIVITY_CLEAR_TASK
            );

            startActivity(intent);
            finish();
        });
        Button settingsButton = findViewById(R.id.settingsButton);

        settingsButton.setOnClickListener(v -> {
            String[] options = {
                    getString(R.string.backup_records),
                    getString(R.string.restore_records)
            };

            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(R.string.settings)
                    .setItems(options, (dialog, which) -> {
                        if (which == 0) {
                            String fileName = "pawn-ledger-backup-" +
                                    LocalDate.now(ZoneId.of("Asia/Yangon")) +
                                    ".json";

                            backupLauncher.launch(fileName);
                        } else {
                            restoreLauncher.launch(
                                    new String[]{
                                            "application/json",
                                            "text/plain"
                                    }
                            );
                        }
                    })
                    .show();
        });



        Button dashboardButton = findViewById(R.id.openDashboardButton);

        dashboardButton.setOnClickListener(v -> {
            Intent intent = new Intent(
                    MainActivity.this,
                    MonthlyDashboardActivity.class
            );
            startActivity(intent);
        });
        refreshCurrentBalance(ledgerDb);
        loadSelectedDate(ledgerDb);
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
    private void restoreRecords(String backupJson) {
        LedgerDb restoreDb = new LedgerDb(this);

        try {
            restoreDb.restoreBackupJson(backupJson);

            Long openingBalance = restoreDb.getOpeningBalance();

            EditText openingInput =
                    findViewById(R.id.openingBalanceInput);
            Button openingButton =
                    findViewById(R.id.setOpeningBalanceButton);

            openingInput.setText(
                    openingBalance == null
                            ? ""
                            : String.valueOf(openingBalance)
            );

            openingInput.setEnabled(openingBalance == null);
            openingButton.setEnabled(openingBalance == null);
            openingInput.setError(null);

            refreshCurrentBalance(restoreDb);
            loadSelectedDate(restoreDb);

            Toast.makeText(
                    this,
                    "Records restored",
                    Toast.LENGTH_LONG
            ).show();

        } catch (org.json.JSONException |
                 android.database.sqlite.SQLiteException e) {
            Toast.makeText(
                    this,
                    "Restore failed. Check your backup file.",
                    Toast.LENGTH_LONG
            ).show();
        } finally {
            restoreDb.close();
        }
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
        EditText interestInput = findViewById(R.id.interestAmountInput);
        TextView netText = findViewById(R.id.netResultText);

        long[] amounts = ledgerDb.getDailyTotals(
                selectedDate.toString()
        );

        pawnInput.setError(null);
        redeemInput.setError(null);
        interestInput.setError(null);

        if (amounts == null) {
            pawnInput.setText("");
            redeemInput.setText("");
            interestInput.setText("");
            netText.setText(R.string.daily_net_placeholder);
            return;
        }

        pawnInput.setText(String.valueOf(amounts[0]));
        redeemInput.setText(String.valueOf(amounts[1]));
        interestInput.setText(String.valueOf(amounts[2]));

        long net = amounts[0] - amounts[1];
        String formattedNet = net > 0 ? "+" + net : String.valueOf(net);

        netText.setText(
                getString(R.string.daily_net_result, formattedNet)
        );



    }
    private final ActivityResultLauncher<String> backupLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.CreateDocument(
                            "application/json"
                    ),
                    uri -> {
                        if (uri == null) {
                            return;
                        }

                        runWithOwnerAccess(() -> {
                            LedgerDb backupDb = new LedgerDb(this);

                            try {
                                String backupJson =
                                        backupDb.createBackupJson();

                                try (OutputStream output =
                                             getContentResolver()
                                                     .openOutputStream(uri)) {

                                    if (output == null) {
                                        throw new java.io.IOException(
                                                "Cannot open backup file"
                                        );
                                    }

                                    output.write(
                                            backupJson.getBytes(
                                                    StandardCharsets.UTF_8
                                            )
                                    );
                                }

                                Toast.makeText(
                                        this,
                                        "Backup saved",
                                        Toast.LENGTH_LONG
                                ).show();

                            } catch (java.io.IOException |
                                     org.json.JSONException |
                                     SecurityException |
                                     android.database.sqlite.SQLiteException e) {
                                Toast.makeText(
                                        this,
                                        "Backup failed. Please try again.",
                                        Toast.LENGTH_LONG
                                ).show();
                            } finally {
                                backupDb.close();
                            }
                        });
                    }
            );




    private void readBackupForRestore(android.net.Uri uri) {
        try {
            StringBuilder text = new StringBuilder();

            try (java.io.InputStream input =
                         getContentResolver().openInputStream(uri)) {

                if (input == null) {
                    throw new java.io.IOException(
                            "Cannot open backup file"
                    );
                }

                try (java.io.InputStreamReader reader =
                             new java.io.InputStreamReader(
                                     input,
                                     StandardCharsets.UTF_8
                             )) {
                    char[] buffer = new char[4096];
                    int count;

                    while ((count = reader.read(buffer)) != -1) {
                        if (text.length() + count > 10_000_000) {
                            throw new java.io.IOException(
                                    "Backup file is too large"
                            );
                        }

                        text.append(buffer, 0, count);
                    }
                }
            }

            String backupJson = text.toString();

            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Restore records?")
                    .setMessage(
                            "This will replace your current records " +
                                    "and opening balance with the backup. " +
                                    "Entries added after that backup will be lost."
                    )
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Restore", (dialog, which) ->
                            runWithOwnerAccess(
                                    () -> restoreRecords(backupJson)
                            )
                    )
                    .show();

        } catch (java.io.IOException | SecurityException e) {
            Toast.makeText(
                    this,
                    "Could not read the backup file",
                    Toast.LENGTH_LONG
            ).show();
        }
    }
}