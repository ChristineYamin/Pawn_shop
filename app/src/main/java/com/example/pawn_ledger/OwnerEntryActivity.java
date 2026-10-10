package com.example.pawn_ledger;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class OwnerEntryActivity extends OwnerActivity {

    private LedgerDb ledgerDb;
    private boolean interestMode;

    private LocalDate selectedDate =
            LocalDate.now(ZoneId.of("Asia/Yangon"));

    private Button dateButton;
    private EditText pawnInput;
    private EditText redeemInput;
    private EditText interestInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!OwnerSession.isUnlocked()) {
            Intent intent = new Intent(this, StaffEntryActivity.class);
            intent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK |
                            Intent.FLAG_ACTIVITY_CLEAR_TASK
            );
            startActivity(intent);
            finish();
            return;
        }

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_owner_entry);

        ledgerDb = new LedgerDb(this);
        interestMode = "interest".equals(
                getIntent().getStringExtra("entry_mode")
        );

        if (savedInstanceState != null) {
            selectedDate = LocalDate.parse(
                    savedInstanceState.getString(
                            "selected_date",
                            selectedDate.toString()
                    )
            );
        }

        dateButton = findViewById(R.id.entryDateButton);
        pawnInput = findViewById(R.id.entryPawnInput);
        redeemInput = findViewById(R.id.entryRedeemInput);
        interestInput = findViewById(R.id.entryInterestInput);

        TextView title = findViewById(R.id.ownerEntryTitle);
        title.setText(
                interestMode
                        ? R.string.record_interest
                        : R.string.edit_pawn_redeem
        );

        pawnInput.setVisibility(
                interestMode ? View.GONE : View.VISIBLE
        );
        redeemInput.setVisibility(
                interestMode ? View.GONE : View.VISIBLE
        );
        interestInput.setVisibility(
                interestMode ? View.VISIBLE : View.GONE
        );

        dateButton.setOnClickListener(v -> chooseDate());

        Button saveButton = findViewById(R.id.entrySaveButton);
        saveButton.setOnClickListener(v -> saveEntry());

        Button backButton = findViewById(R.id.backFromEntryButton);
        backButton.setOnClickListener(v -> finish());

        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.ownerEntryMain),
                (view, insets) -> {
                    Insets bars = insets.getInsets(
                            WindowInsetsCompat.Type.systemBars()
                    );
                    view.setPadding(
                            bars.left, bars.top,
                            bars.right, bars.bottom
                    );
                    return insets;
                }
        );

        loadSelectedDate();
    }

    private void chooseDate() {
        new DatePickerDialog(
                this,
                (view, year, month, day) -> {
                    LocalDate chosen =
                            LocalDate.of(year, month + 1, day);

                    if (chosen.isAfter(
                            LocalDate.now(ZoneId.of("Asia/Yangon"))
                    )) {
                        Toast.makeText(
                                this,
                                "Choose today or an earlier date",
                                Toast.LENGTH_SHORT
                        ).show();
                        return;
                    }

                    selectedDate = chosen;
                    loadSelectedDate();
                },
                selectedDate.getYear(),
                selectedDate.getMonthValue() - 1,
                selectedDate.getDayOfMonth()
        ).show();
    }

    private void loadSelectedDate() {
        dateButton.setText(
                selectedDate.format(
                        DateTimeFormatter.ofPattern("d-M-yyyy")
                )
        );

        long[] amounts =
                ledgerDb.getDailyTotals(selectedDate.toString());

        pawnInput.setError(null);
        redeemInput.setError(null);
        interestInput.setError(null);

        pawnInput.setText(
                amounts == null ? "" : String.valueOf(amounts[0])
        );
        redeemInput.setText(
                amounts == null ? "" : String.valueOf(amounts[1])
        );
        interestInput.setText(
                amounts == null ? "" : String.valueOf(amounts[2])
        );
    }

    private Long readAmount(EditText input) {
        try {
            long amount = Long.parseLong(
                    input.getText().toString().trim()
            );

            if (amount < 0) {
                input.setError("Enter 0 or more");
                return null;
            }

            return amount;
        } catch (NumberFormatException e) {
            input.setError(
                    "Enter a whole number without commas, or 0"
            );
            return null;
        }
    }

    private void saveEntry() {
        Long first = readAmount(
                interestMode ? interestInput : pawnInput
        );
        if (first == null) {
            return;
        }

        Long second = interestMode ? 0L : readAmount(redeemInput);
        if (second == null) {
            return;
        }

        String date = selectedDate.toString();

        try {
            Runnable save = () -> {
                try {
                    boolean saved = interestMode
                            ? ledgerDb.saveDailyInterest(date, first)
                            : ledgerDb.saveStaffDailyTotals(
                            date, first, second
                    );

                    Toast.makeText(
                            this,
                            saved
                                    ? "Record saved"
                                    : "Could not save the record",
                            Toast.LENGTH_SHORT
                    ).show();

                    if (saved) {
                        loadSelectedDate();
                    }
                } catch (android.database.sqlite.SQLiteException e) {
                    Toast.makeText(
                            this,
                            "Could not save the record",
                            Toast.LENGTH_LONG
                    ).show();
                }
            };

            if (ledgerDb.getDailyTotals(date) != null) {
                new AlertDialog.Builder(this)
                        .setTitle("Update saved record?")
                        .setMessage(
                                "Replace " +
                                        (interestMode
                                                ? "interest"
                                                : "pawn and redeem amounts") +
                                        " for " +
                                        selectedDate.format(
                                                DateTimeFormatter.ofPattern(
                                                        "d-M-yyyy"
                                                )
                                        ) + "?"
                        )
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton(
                                "Update",
                                (dialog, which) ->
                                        runWithOwnerAccess(save)
                        )
                        .show();
            } else {
                runWithOwnerAccess(save);
            }
        } catch (android.database.sqlite.SQLiteException e) {
            Toast.makeText(
                    this,
                    "Could not read the record",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString("selected_date", selectedDate.toString());
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        if (ledgerDb != null) {
            ledgerDb.close();
        }
        super.onDestroy();
    }
}