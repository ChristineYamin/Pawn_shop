package com.example.pawn_ledger;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class OwnerActivity extends AppCompatActivity {

    private AlertDialog pinDialog;
    private Runnable pendingAction;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_SECURE
        );
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (OwnerSession.isUnlocked()) {
            showOwnerContent(true);
        } else {
            showOwnerContent(false);
            requestOwnerUnlock();
        }
    }

    @Override
    protected void onPause() {
        showOwnerContent(false);
        super.onPause();
    }

    protected void runWithOwnerAccess(Runnable action) {
        if (OwnerSession.isUnlocked()) {
            action.run();
        } else {
            pendingAction = action;
            showOwnerContent(false);
            requestOwnerUnlock();
        }
    }

    private void showOwnerContent(boolean visible) {
        findViewById(android.R.id.content).setVisibility(
                visible ? View.VISIBLE : View.INVISIBLE
        );
    }

    private void requestOwnerUnlock() {
        if (isFinishing() || isDestroyed()) {
            return;
        }

        if (pinDialog != null && pinDialog.isShowing()) {
            return;
        }

        EditText pinInput = new EditText(this);
        pinInput.setHint("6-digit PIN");
        pinInput.setInputType(
                InputType.TYPE_CLASS_NUMBER |
                        InputType.TYPE_NUMBER_VARIATION_PASSWORD
        );
        pinInput.setFilters(new InputFilter[]{
                new InputFilter.LengthFilter(6)
        });

        pinDialog = new AlertDialog.Builder(this)
                .setTitle("Owner access")
                .setView(pinInput)
                .setNegativeButton(
                        "Cancel",
                        (dialog, which) -> returnToStaff()
                )
                .setPositiveButton("Unlock", null)
                .create();

        pinDialog.setCanceledOnTouchOutside(false);
        pinDialog.setOnCancelListener(
                dialog -> returnToStaff()
        );

        pinDialog.setOnShowListener(ignored ->
                pinDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setOnClickListener(view -> {
                            String pin =
                                    pinInput.getText().toString();

                            try {
                                OwnerPinStore store =
                                        new OwnerPinStore(this);

                                if (!store.verifyPin(pin)) {
                                    pinInput.setError(
                                            "Incorrect PIN. Enter 6 digits."
                                    );
                                    return;
                                }

                                OwnerSession.unlock();
                                pinDialog.dismiss();
                                showOwnerContent(true);

                                Runnable action = pendingAction;
                                pendingAction = null;

                                if (action != null) {
                                    action.run();
                                }

                            } catch (IllegalStateException |
                                     IllegalArgumentException e) {
                                pinInput.setError(
                                        "Could not check the PIN"
                                );
                            }
                        })
        );

        pinDialog.show();
    }

    private void returnToStaff() {
        OwnerSession.lock();
        pendingAction = null;

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

    @Override
    protected void onDestroy() {
        pendingAction = null;

        if (pinDialog != null) {
            pinDialog.dismiss();
        }

        super.onDestroy();
    }
}