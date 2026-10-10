package com.example.pawn_ledger;

import android.content.Intent;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.json.JSONException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;

public class MainActivity extends OwnerActivity {

    private final ActivityResultLauncher<String> backupLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.CreateDocument(
                            "application/json"
                    ),
                    uri -> {
                        if (uri != null) {
                            runWithOwnerAccess(() -> saveBackup(uri));
                        }
                    }
            );

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
            returnToStaff();
            return;
        }

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        Button interestButton =
                findViewById(R.id.recordInterestButton);

        interestButton.setOnClickListener(v ->
                openEntry("interest")
        );

        Button editButton =
                findViewById(R.id.editPawnRedeemButton);

        editButton.setOnClickListener(v ->
                openEntry("pawn_redeem")
        );

        Button recordsButton =
                findViewById(R.id.openRecordsButton);

        recordsButton.setOnClickListener(v ->
                startActivity(
                        new Intent(this, RecordsActivity.class)
                )
        );

        Button dashboardButton =
                findViewById(R.id.openDashboardButton);

        dashboardButton.setOnClickListener(v ->
                startActivity(
                        new Intent(
                                this,
                                MonthlyDashboardActivity.class
                        )
                )
        );

        Button settingsButton =
                findViewById(R.id.settingsButton);

        settingsButton.setOnClickListener(v -> showSettings());

        Button lockButton =
                findViewById(R.id.lockOwnerButton);

        lockButton.setOnClickListener(v -> returnToStaff());

        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.main),
                (view, insets) -> {
                    Insets bars = insets.getInsets(
                            WindowInsetsCompat.Type.systemBars()
                    );

                    view.setPadding(
                            bars.left,
                            bars.top,
                            bars.right,
                            bars.bottom
                    );

                    return insets;
                }
        );

        refreshCurrentBalance();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!isFinishing() &&
                findViewById(R.id.currentBalanceText) != null) {
            refreshCurrentBalance();
        }
    }

    private void openEntry(String mode) {
        Intent intent = new Intent(
                this,
                OwnerEntryActivity.class
        );

        intent.putExtra("entry_mode", mode);
        startActivity(intent);
    }

    private void returnToStaff() {
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
    }

    private void refreshCurrentBalance() {
        LedgerDb db = new LedgerDb(this);

        try {
            TextView balanceText =
                    findViewById(R.id.currentBalanceText);

            Long openingBalance = db.getOpeningBalance();

            if (openingBalance == null) {
                balanceText.setText(
                        R.string.current_balance_placeholder
                );
                return;
            }

            long currentBalance =
                    openingBalance + db.getTotalNet();

            String formattedBalance = NumberFormat
                    .getIntegerInstance(Locale.US)
                    .format(currentBalance);

            balanceText.setText(
                    getString(
                            R.string.current_balance_value,
                            formattedBalance
                    )
            );

        } catch (SQLiteException e) {
            Toast.makeText(
                    this,
                    "Could not load the balance",
                    Toast.LENGTH_LONG
            ).show();
        } finally {
            db.close();
        }
    }

    private void showSettings() {
        String[] options = {
                getString(R.string.set_opening_balance),
                getString(R.string.backup_records),
                getString(R.string.restore_records),
                getString(R.string.start_fresh)
        };

        new AlertDialog.Builder(this)
                .setTitle(R.string.settings)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        runWithOwnerAccess(() -> showOpeningBalance());

                    } else if (which == 1) {
                        String fileName = "pawn-ledger-backup-" +
                                LocalDate.now(ZoneId.of("Asia/Yangon")) +
                                ".json";

                        backupLauncher.launch(fileName);

                    } else if (which == 2) {
                        restoreLauncher.launch(
                                new String[]{
                                        "application/json",
                                        "text/plain"
                                }
                        );

                    } else {
                        runWithOwnerAccess(() -> showStartFresh());
                    }
                })
                .show();
    }



    private void showOpeningBalance() {
        LedgerDb db = new LedgerDb(this);
        Long savedBalance;

        try {
            savedBalance = db.getOpeningBalance();
        } catch (SQLiteException e) {
            Toast.makeText(
                    this,
                    "Could not load the opening balance",
                    Toast.LENGTH_LONG
            ).show();
            return;
        } finally {
            db.close();
        }

        if (savedBalance != null) {
            String formatted = NumberFormat
                    .getIntegerInstance(Locale.US)
                    .format(savedBalance);

            new AlertDialog.Builder(this)
                    .setTitle(R.string.set_opening_balance)
                    .setMessage(
                            "Opening balance already set: " +
                                    formatted + " MMK"
                    )
                    .setPositiveButton("OK", null)
                    .show();

            return;
        }

        EditText input = new EditText(this);
        input.setHint(R.string.opening_balance_hint);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.set_opening_balance)
                .setMessage(
                        "Enter the starting pawn balance. " +
                                "This can be set only once."
                )
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();

        dialog.setOnShowListener(ignored ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setOnClickListener(view -> {
                            try {
                                long amount = Long.parseLong(
                                        input.getText()
                                                .toString()
                                                .trim()
                                );

                                if (amount < 0) {
                                    input.setError("Enter 0 or more");
                                    return;
                                }

                                runWithOwnerAccess(() -> {
                                    LedgerDb openingDb =
                                            new LedgerDb(this);

                                    try {
                                        boolean saved =
                                                openingDb.saveOpeningBalance(
                                                        amount
                                                );

                                        if (saved) {
                                            dialog.dismiss();
                                            refreshCurrentBalance();

                                            Toast.makeText(
                                                    this,
                                                    "Opening balance saved",
                                                    Toast.LENGTH_SHORT
                                            ).show();
                                        } else {
                                            input.setError(
                                                    "Not saved. An opening " +
                                                            "balance may already exist."
                                            );
                                        }
                                    } catch (SQLiteException e) {
                                        input.setError(
                                                "Could not save the balance"
                                        );
                                    } finally {
                                        openingDb.close();
                                    }
                                });

                            } catch (NumberFormatException e) {
                                input.setError(
                                        "Enter a whole number " +
                                                "without commas, or 0"
                                );
                            }
                        })
        );

        dialog.show();
    }

    private void saveBackup(Uri uri) {
        LedgerDb db = new LedgerDb(this);

        try {
            String backupJson = db.createBackupJson();

            try (OutputStream output =
                         getContentResolver().openOutputStream(uri)) {

                if (output == null) {
                    throw new IOException(
                            "Cannot open backup file"
                    );
                }

                output.write(
                        backupJson.getBytes(StandardCharsets.UTF_8)
                );
            }

            Toast.makeText(
                    this,
                    "Backup saved",
                    Toast.LENGTH_LONG
            ).show();

        } catch (IOException |
                 JSONException |
                 SecurityException |
                 SQLiteException e) {
            Toast.makeText(
                    this,
                    "Backup failed. Please try again.",
                    Toast.LENGTH_LONG
            ).show();
        } finally {
            db.close();
        }
    }

    private void readBackupForRestore(Uri uri) {
        try {
            StringBuilder text = new StringBuilder();

            try (InputStream input =
                         getContentResolver().openInputStream(uri)) {

                if (input == null) {
                    throw new IOException(
                            "Cannot open backup file"
                    );
                }

                try (InputStreamReader reader =
                             new InputStreamReader(
                                     input,
                                     StandardCharsets.UTF_8
                             )) {
                    char[] buffer = new char[4096];
                    int count;

                    while ((count = reader.read(buffer)) != -1) {
                        if (text.length() + count > 10_000_000) {
                            throw new IOException(
                                    "Backup file is too large"
                            );
                        }

                        text.append(buffer, 0, count);
                    }
                }
            }

            String backupJson = text.toString();

            new AlertDialog.Builder(this)
                    .setTitle("Restore records?")
                    .setMessage(
                            "This will replace your current records " +
                                    "and opening balance with the backup. " +
                                    "Entries added after that backup will be lost."
                    )
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton(
                            "Restore",
                            (dialog, which) ->
                                    runWithOwnerAccess(
                                            () -> restoreRecords(backupJson)
                                    )
                    )
                    .show();

        } catch (IOException | SecurityException e) {
            Toast.makeText(
                    this,
                    "Could not read the backup file",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void restoreRecords(String backupJson) {
        LedgerDb db = new LedgerDb(this);

        try {
            db.restoreBackupJson(backupJson);
            refreshCurrentBalance();

            Toast.makeText(
                    this,
                    "Records restored",
                    Toast.LENGTH_LONG
            ).show();

        } catch (JSONException | SQLiteException e) {
            Toast.makeText(
                    this,
                    "Restore failed. Check your backup file.",
                    Toast.LENGTH_LONG
            ).show();
        } finally {
            db.close();
        }
    }
    private void showStartFresh() {
        EditText input = new EditText(this);
        input.setHint(R.string.opening_balance_hint);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);

        AlertDialog amountDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.start_fresh)
                .setMessage(
                        "Enter the shop's real current pawn balance " +
                                "in MMK, without commas."
                )
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Continue", null)
                .create();

        amountDialog.setOnShowListener(ignored ->
                amountDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setOnClickListener(view -> {
                            try {
                                long amount = Long.parseLong(
                                        input.getText().toString().trim()
                                );

                                if (amount < 0) {
                                    input.setError("Enter 0 or more");
                                    return;
                                }

                                String formatted = NumberFormat
                                        .getIntegerInstance(Locale.US)
                                        .format(amount);

                                runWithOwnerAccess(() ->
                                        new AlertDialog.Builder(this)
                                                .setTitle("Delete all records?")
                                                .setMessage(
                                                        "All daily records and interest " +
                                                                "will be deleted.\n\n" +
                                                                "New opening pawn balance: " +
                                                                formatted + " MMK\n\n" +
                                                                "Your owner PIN will stay unchanged. " +
                                                                "This cannot be undone without a backup."
                                                )
                                                .setNegativeButton("Cancel", null)
                                                .setPositiveButton(
                                                        "Delete and start fresh",
                                                        (dialog, which) ->
                                                                runWithOwnerAccess(() -> {
                                                                    LedgerDb db =
                                                                            new LedgerDb(this);

                                                                    try {
                                                                        db.startFresh(amount);
                                                                        amountDialog.dismiss();
                                                                        refreshCurrentBalance();

                                                                        Toast.makeText(
                                                                                this,
                                                                                "New balance saved. Records cleared.",
                                                                                Toast.LENGTH_LONG
                                                                        ).show();

                                                                    } catch (SQLiteException e) {
                                                                        Toast.makeText(
                                                                                this,
                                                                                "Could not reset the ledger",
                                                                                Toast.LENGTH_LONG
                                                                        ).show();
                                                                    } finally {
                                                                        db.close();
                                                                    }
                                                                })
                                                )
                                                .show()
                                );

                            } catch (NumberFormatException e) {
                                input.setError(
                                        "Enter a whole number without commas, or 0"
                                );
                            }
                        })
        );

        amountDialog.show();
    }





}