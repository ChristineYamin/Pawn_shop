package com.example.pawn_ledger;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.text.NumberFormat;
import java.util.Locale;

public class RecordsActivity extends OwnerActivity {

    private LedgerDb ledgerDb;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!OwnerSession.isUnlocked()) {
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
            return;
        }

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_records);

        ledgerDb = new LedgerDb(this);

        Button backButton =
                findViewById(R.id.backFromRecordsButton);
        backButton.setOnClickListener(v -> finish());

        int padding = Math.round(
                16 * getResources().getDisplayMetrics().density
        );

        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.recordsMain),
                (view, insets) -> {
                    Insets bars = insets.getInsets(
                            WindowInsetsCompat.Type.systemBars()
                    );

                    view.setPadding(
                            padding + bars.left,
                            padding + bars.top,
                            padding + bars.right,
                            padding + bars.bottom
                    );

                    return insets;
                }
        );

        refreshRecordsTable();
    }

    private void refreshRecordsTable() {
        TableLayout table =
                findViewById(R.id.dailyRecordsTable);

        table.removeAllViews();

        addRecordRow(
                table,
                true,
                "Date", "+ Pawn", "− Redeem", "Net", "Interest"
        );

        NumberFormat formatter =
                NumberFormat.getIntegerInstance(Locale.US);

        try (Cursor cursor = ledgerDb.getAllDailyTotals()) {
            while (cursor.moveToNext()) {
                String date = cursor.getString(0);
                String displayDate = java.time.LocalDate.parse(date)
                        .format(
                                java.time.format.DateTimeFormatter
                                        .ofPattern("d-M-yyyy")
                        );
                long pawn = cursor.getLong(1);
                long redeem = cursor.getLong(2);
                long interest = cursor.getLong(3);
                long net = pawn - redeem;

                String netText =
                        (net > 0 ? "+" : "") +
                                formatter.format(net);

                addRecordRow(
                        table,
                        false,
                        displayDate,
                        formatter.format(pawn),
                        formatter.format(redeem),
                        netText,
                        formatter.format(interest)
                );
            }
        }
    }

    private void addRecordRow(
            TableLayout table,
            boolean header,
            String... values
    ) {
        TableRow row = new TableRow(this);

        int padding = Math.round(
                12 * getResources().getDisplayMetrics().density
        );

        for (String value : values) {
            TextView cell = new TextView(this);
            cell.setText(value);
            cell.setTextSize(16);
            cell.setPadding(padding, padding, padding, padding);

            if (header) {
                cell.setTypeface(
                        null,
                        android.graphics.Typeface.BOLD
                );
            }

            row.addView(cell);
        }

        table.addView(row);
    }

    @Override
    protected void onDestroy() {
        if (ledgerDb != null) {
            ledgerDb.close();
        }

        super.onDestroy();
    }
}