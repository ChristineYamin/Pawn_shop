package com.example.pawn_ledger;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.text.NumberFormat;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import androidx.appcompat.app.AlertDialog;
import android.widget.LinearLayout;
import android.widget.NumberPicker;

public class MonthlyDashboardActivity extends OwnerActivity {

    private LedgerDb ledgerDb;

    private YearMonth selectedMonth =
            YearMonth.now(ZoneId.of("Asia/Yangon"));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_monthly_dashboard);

        ledgerDb = new LedgerDb(this);
        Button monthButton = findViewById(R.id.selectMonthButton);
        monthButton.setOnClickListener(v -> showMonthPicker());

        Button backButton = findViewById(R.id.backToLedgerButton);
        backButton.setOnClickListener(v -> finish());

        int padding = Math.round(
                24 * getResources().getDisplayMetrics().density
        );

        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.dashboardMain),
                (v, insets) -> {
                    Insets bars = insets.getInsets(
                            WindowInsetsCompat.Type.systemBars()
                    );

                    v.setPadding(
                            padding + bars.left,
                            padding + bars.top,
                            padding + bars.right,
                            padding + bars.bottom
                    );

                    return insets;
                }
        );

        refreshDashboard();
    }
    private void showMonthPicker() {
        YearMonth currentMonth =
                YearMonth.now(ZoneId.of("Asia/Yangon"));

        NumberPicker monthPicker = new NumberPicker(this);
        monthPicker.setMinValue(1);
        monthPicker.setMaxValue(12);
        monthPicker.setDisplayedValues(new String[]{
                "January", "February", "March", "April",
                "May", "June", "July", "August",
                "September", "October", "November", "December"
        });
        monthPicker.setValue(selectedMonth.getMonthValue());
        monthPicker.setWrapSelectorWheel(false);

        NumberPicker yearPicker = new NumberPicker(this);
        yearPicker.setMinValue(2000);
        yearPicker.setMaxValue(currentMonth.getYear());
        yearPicker.setValue(selectedMonth.getYear());
        yearPicker.setWrapSelectorWheel(false);

        LinearLayout pickerLayout = new LinearLayout(this);
        pickerLayout.setOrientation(LinearLayout.HORIZONTAL);

        pickerLayout.addView(monthPicker, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
        ));
        pickerLayout.addView(yearPicker, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
        ));

        new AlertDialog.Builder(this)
                .setTitle("Choose month and year")
                .setView(pickerLayout)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Show", (dialog, which) -> {
                    pickerLayout.clearFocus();

                    selectedMonth = YearMonth.of(
                            yearPicker.getValue(),
                            monthPicker.getValue()
                    );

                    refreshDashboard();
                })
                .show();
    }

    private void refreshDashboard() {
        Button monthButton = findViewById(R.id.selectMonthButton);
        TextView balanceText = findViewById(R.id.monthEndBalanceText);
        TextView interestText = findViewById(R.id.monthlyInterestText);

        monthButton.setText(
                selectedMonth.format(
                        DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)
                )
        );

        int year = selectedMonth.getYear();
        int month = selectedMonth.getMonthValue();

        Long balance = ledgerDb.getMonthEndPawnBalance(year, month);
        long interest = ledgerDb.getMonthlyInterest(year, month);

        NumberFormat formatter =
                NumberFormat.getIntegerInstance(Locale.US);

        if (balance == null) {
            balanceText.setText(R.string.month_end_balance_placeholder);
        } else {
            balanceText.setText(
                    getString(
                            R.string.month_end_balance_value,
                            formatter.format(balance)
                    )
            );
        }

        interestText.setText(
                getString(
                        R.string.monthly_interest_value,
                        formatter.format(interest)
                )
        );
    }

    @Override
    protected void onDestroy() {
        if (ledgerDb != null) {
            ledgerDb.close();
        }
        super.onDestroy();
    }
}