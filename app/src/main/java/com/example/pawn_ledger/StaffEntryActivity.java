package com.example.pawn_ledger;

import android.os.Bundle;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import android.app.DatePickerDialog;
import android.widget.Toast;
import java.time.LocalDate;
import java.time.ZoneId;

import android.widget.EditText;
import androidx.appcompat.app.AlertDialog;

import android.content.Intent;
import android.text.InputFilter;
import android.text.InputType;
import android.widget.LinearLayout;


public class StaffEntryActivity extends AppCompatActivity {

    private LedgerDb ledgerDb;

    private LocalDate selectedDate =
            LocalDate.now(ZoneId.of("Asia/Yangon"));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_staff_entry);
        ledgerDb = new LedgerDb(this);

        Button saveButton = findViewById(R.id.staffSaveButton);
        saveButton.setOnClickListener(v -> saveStaffEntry());
        Button dateButton = findViewById(R.id.staffDateButton);
        dateButton.setText(selectedDate.toString());
        dateButton.setOnClickListener(v -> {
            DatePickerDialog picker = new DatePickerDialog(
                    this,
                    (view, year, month, day) -> {
                        LocalDate chosenDate =
                                LocalDate.of(year, month + 1, day);

                        if (chosenDate.isAfter(
                                LocalDate.now(ZoneId.of("Asia/Yangon"))
                        )) {
                            Toast.makeText(
                                    this,
                                    "Choose today or an earlier date",
                                    Toast.LENGTH_SHORT
                            ).show();
                            return;
                        }

                        selectedDate = chosenDate;
                        dateButton.setText(selectedDate.toString());
                    },
                    selectedDate.getYear(),
                    selectedDate.getMonthValue() - 1,
                    selectedDate.getDayOfMonth()
            );

            picker.show();
        });
        Button ownerButton = findViewById(R.id.staffOwnerButton);
        ownerButton.setOnClickListener(v -> showOwnerPinDialog());


        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.staffMain),
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
    }
    private void saveStaffEntry() {
        EditText pawnInput = findViewById(R.id.staffPawnInput);
        EditText redeemInput = findViewById(R.id.staffRedeemInput);

        String pawnText = pawnInput.getText().toString().trim();
        String redeemText = redeemInput.getText().toString().trim();

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

            if (pawnAmount < 0 || redeemAmount < 0) {
                Toast.makeText(
                        this,
                        "Enter amounts of 0 or more",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }

            String recordDate = selectedDate.toString();

            if (ledgerDb.getDailyTotals(recordDate) != null) {
                new AlertDialog.Builder(this)
                        .setTitle("Update saved record?")
                        .setMessage(
                                "Replace pawn and redeem amounts for " +
                                        recordDate + "?"
                        )
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Update", (dialog, which) ->
                                saveStaffAmounts(
                                        recordDate,
                                        pawnAmount,
                                        redeemAmount
                                )
                        )
                        .show();
            } else {
                saveStaffAmounts(
                        recordDate,
                        pawnAmount,
                        redeemAmount
                );
            }

        } catch (NumberFormatException e) {
            Toast.makeText(
                    this,
                    "Enter whole numbers without commas; the amount may be too large",
                    Toast.LENGTH_LONG
            ).show();
        } catch (android.database.sqlite.SQLiteException e) {
            Toast.makeText(
                    this,
                    "Could not read the saved record. Please try again.",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void saveStaffAmounts(
            String date,
            long pawnAmount,
            long redeemAmount
    ) {
        try {
            boolean saved = ledgerDb.saveStaffDailyTotals(
                    date,
                    pawnAmount,
                    redeemAmount
            );

            Toast.makeText(
                    this,
                    saved
                            ? "Record saved for " + date
                            : "Could not save the record",
                    Toast.LENGTH_LONG
            ).show();

        } catch (android.database.sqlite.SQLiteException e) {
            Toast.makeText(
                    this,
                    "Could not save the record. Please try again.",
                    Toast.LENGTH_LONG
            ).show();
        }
    }
        private void showOwnerPinDialog() {
            OwnerPinStore pinStore = new OwnerPinStore(this);
            boolean settingUp = !pinStore.hasPin();

            LinearLayout layout = new LinearLayout(this);
            layout.setOrientation(LinearLayout.VERTICAL);

            int padding = Math.round(
                    24 * getResources().getDisplayMetrics().density
            );
            layout.setPadding(padding, 0, padding, 0);

            EditText pinInput = new EditText(this);
            pinInput.setHint("6-digit PIN");
            pinInput.setInputType(
                    InputType.TYPE_CLASS_NUMBER |
                            InputType.TYPE_NUMBER_VARIATION_PASSWORD
            );
            pinInput.setFilters(new InputFilter[]{
                    new InputFilter.LengthFilter(6)
            });
            layout.addView(pinInput);

            EditText confirmInput = new EditText(this);

            if (settingUp) {
                confirmInput.setHint("Confirm PIN");
                confirmInput.setInputType(
                        InputType.TYPE_CLASS_NUMBER |
                                InputType.TYPE_NUMBER_VARIATION_PASSWORD
                );
                confirmInput.setFilters(new InputFilter[]{
                        new InputFilter.LengthFilter(6)
                });
                layout.addView(confirmInput);
            }

            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setTitle(settingUp ? "Set owner PIN" : "Owner access")
                    .setView(layout)
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton(settingUp ? "Set PIN" : "Unlock", null)
                    .create();

            dialog.setOnShowListener(ignored ->
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                            .setOnClickListener(view -> {
                                String pin = pinInput.getText().toString();

                                if (!pin.matches("[0-9]{6}")) {
                                    pinInput.setError("Enter exactly 6 digits");
                                    return;
                                }

                                if (settingUp &&
                                        !pin.equals(
                                                confirmInput.getText().toString()
                                        )) {
                                    confirmInput.setError("PINs do not match");
                                    return;
                                }

                                try {
                                    boolean accepted = settingUp
                                            ? pinStore.setPin(pin)
                                            : pinStore.verifyPin(pin);

                                    if (!accepted) {
                                        pinInput.setError(
                                                settingUp
                                                        ? "Could not save PIN"
                                                        : "Incorrect PIN"
                                        );
                                        return;
                                    }

                                    OwnerSession.unlock();
                                    dialog.dismiss();

                                    startActivity(new Intent(
                                            this,
                                            MainActivity.class
                                    ));

                                } catch (IllegalStateException |
                                         IllegalArgumentException e) {
                                    pinInput.setError(
                                            "Could not check the PIN. Please try again."
                                    );
                                }
                            })
            );

            dialog.show();
        }

    }

