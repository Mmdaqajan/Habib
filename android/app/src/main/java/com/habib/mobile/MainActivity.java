package com.habib.mobile;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.ScrollView;
import android.widget.TextView;

import android.icu.util.Calendar;
import android.icu.util.ULocale;

import java.time.LocalDate;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final int REQUEST_NOTIFICATIONS = 51;
    private static final int REQUEST_BIRTHDAY_NOTIFICATIONS = 52;
    private static final String NOTIFICATION_CHANNEL = "habib_reminders";
    private static final int GREEN = 0xff10b981;
    private static final int BG = 0xff090d16;
    private static final int CARD = 0xff141a26;
    private static final int TEXT = 0xfff8fafc;
    private static final int MUTED = 0xff94a3b8;
    private static final String[] MOTIVATIONAL_QUOTES = {
            "عادت های تو هویتت و میزان اراده ات و آینده ات رو تعین میکنن . کلمات این جمله با دقت انتخاب شده !",
            "آدم‌های موفق مرز روشنی بین کارهای قابل مذاکره و غیرقابل مذاکره دارند.",
            "موفقیت هزاران قدم کوچیکه نه یک قدم بزرگ، این قدم میتونه سر زدن به حبیب باشه :)",
            "وقتی دربارهٔ برنامه‌هایت با دیگران حرف می‌زنی، گاهی انگیزهٔ انجامشان کمتر می‌شود.",
            "داستان بازنده هارو که بشنوی متوجه میشی اوناهم خیلی کارهارو شروع کردن ولی نتونستن نگهش دارن !"
    };
    private static final int[] EVENT_COLORS = {
            0xffef4444, 0xfff59e0b, 0xff10b981, 0xff3b82f6, 0xffa855f7, 0xff94a3b8
    };
    private DbHelper database;
    private LinearLayout root;
    private LinearLayout content;
    private String currentPage = "habits";
    private Uri pendingPhoto;
    private long selectedChartHabitId = -1;
    private int calendarMonthOffset;
    private int reminderFilter;
    private int quoteIndex;
    private String selectedCalendarDateKey = "";
    private final Handler quoteHandler = new Handler(Looper.getMainLooper());
    private Runnable quoteAdvance;
    private String pendingNotificationTitle;
    private String pendingNotificationText;
    private long pendingNotificationDelay;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        database = new DbHelper(this);
        showApp();
    }

    private String persianDigits(int value) {
        return String.valueOf(value).replace('0', '۰').replace('1', '۱').replace('2', '۲')
                .replace('3', '۳').replace('4', '۴').replace('5', '۵').replace('6', '۶')
                .replace('7', '۷').replace('8', '۸').replace('9', '۹');
    }

    private String persianMonthName(Calendar date) {
        String[] monthNames = {"فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
                "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"};
        int month = date.get(Calendar.MONTH);
        return month >= 0 && month < monthNames.length ? monthNames[month] : "";
    }

    private void adjustAmount(EditText amount, int min, int max, int delta) {
        int current = parse(amount.getText().toString(), min);
        amount.setText(String.valueOf(Math.max(min, Math.min(max, current + delta))));
        amount.setSelection(amount.getText().length());
        amount.requestFocus();
    }

    private boolean validAmount(String value, int min, int max) {
        if (value == null || value.trim().isEmpty()) return false;
        try {
            int amount = Integer.parseInt(value.trim());
            return amount >= min && amount <= max;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private void scheduleNotificationDialog() {
        LinearLayout form = form();
        EditText minutes = field("چند دقیقه دیگر؟", "1", InputType.TYPE_CLASS_NUMBER);
        EditText message = field("متن اعلان", "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        form.addView(minutes);
        form.addView(message, margin(0, 8, 0, 0));
        new AlertDialog.Builder(this).setTitle("زمان‌بندی اعلان")
                .setView(form)
                .setNegativeButton("انصراف", null)
                .setPositiveButton("زمان‌بندی", (dialog, which) -> {
                    int delay = parse(minutes.getText().toString(), 0);
                    String text = message.getText().toString().trim();
                    if (delay < 1 || text.isEmpty()) {
                        showMessage("زمان را حداقل یک دقیقه و متن اعلان را وارد کن.");
                        return;
                    }
                    ensureNotificationPermission("Habib", text, delay * 60_000L);
                }).show();
    }

    private void ensureNotificationPermission(String title, String text, long delayMillis) {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            pendingNotificationTitle = title;
            pendingNotificationText = text;
            pendingNotificationDelay = delayMillis;
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
            return;
        }
        if (!((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).areNotificationsEnabled()) {
            showMessage("اعلان‌های Habib در تنظیمات گوشی غیرفعال هستند.");
            return;
        }
        if (delayMillis == 0) postNotification(title, text);
        else scheduleNotification(title, text, delayMillis);
    }

    private void requestBirthdayNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, REQUEST_BIRTHDAY_NOTIFICATIONS);
        }
    }

    private void scheduleNotification(String title, String text, long delayMillis) {
        Intent intent = new Intent(this, NotificationReceiver.class);
        intent.putExtra("title", title);
        intent.putExtra("text", text);
        int requestCode = (int) System.currentTimeMillis();
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + delayMillis, pendingIntent);
        showMessage("اعلان برای " + persianDigits((int) (delayMillis / 60_000L)) + " دقیقهٔ دیگر زمان‌بندی شد.");
    }

    private void postNotification(String title, String text) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.createNotificationChannel(new NotificationChannel(NOTIFICATION_CHANNEL, "یادآوری‌های حبیب",
                NotificationManager.IMPORTANCE_DEFAULT));
        Notification notification = new Notification.Builder(this, NOTIFICATION_CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .build();
        manager.notify((int) System.currentTimeMillis(), notification);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BIRTHDAY_NOTIFICATIONS) {
            if (grantResults.length == 0 || grantResults[0] != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                showMessage("برای دریافت یادآوری تولد، مجوز اعلان را در تنظیمات گوشی فعال کن.");
            }
            return;
        }
        if (requestCode != REQUEST_NOTIFICATIONS) return;
        if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            ensureNotificationPermission(pendingNotificationTitle, pendingNotificationText, pendingNotificationDelay);
            pendingNotificationTitle = null;
            pendingNotificationText = null;
            pendingNotificationDelay = 0;
        } else {
            showMessage("برای ارسال اعلان، مجوز اعلان را از تنظیمات گوشی فعال کن.");
        }
    }

    private void showApp() {
        stopQuoteAdvance();
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackgroundColor(BG);
        setContentView(root);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(0, insets.getSystemWindowInsetTop(), 0, insets.getSystemWindowInsetBottom());
            return insets;
        });

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(20), dp(14), dp(20), dp(14));
        TextView title = text("حبیب  |  Habib", 21, TEXT);
        title.setTypeface(null, Typeface.BOLD);
        header.addView(title);
        header.addView(text("محل توسعهٔ عادت‌های من :)", 12, MUTED), margin(0, 3, 0, 0));
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        content.setPadding(dp(16), dp(6), dp(16), dp(20));
        scroll.addView(content);
        FrameLayout pageFrame = new FrameLayout(this);
        pageFrame.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        pageFrame.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        Button floatingAdd = button("+", GREEN);
        floatingAdd.setTextSize(28);
        floatingAdd.setPadding(0, 0, 0, dp(3));
        floatingAdd.setBackground(round(GREEN, 100, GREEN));
        floatingAdd.setElevation(dp(8));
        floatingAdd.setContentDescription("افزودن یادآوری");
        FrameLayout.LayoutParams addParams = new FrameLayout.LayoutParams(dp(58), dp(58), Gravity.BOTTOM | Gravity.END);
        addParams.setMargins(0, 0, dp(18), dp(18));
        pageFrame.addView(floatingAdd, addParams);
        floatingAdd.setVisibility("reminders".equals(currentPage) ? View.VISIBLE : View.GONE);
        floatingAdd.setOnClickListener(v -> addReminderDialog());
        root.addView(pageFrame, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout navigation = new LinearLayout(this);
        navigation.setGravity(Gravity.CENTER);
        navigation.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        navigation.setBackgroundColor(CARD);
        navigation.setPadding(dp(8), dp(5), dp(8), dp(5));
        addNav(navigation, "habits", "عادت‌ها", "✓");
        addNav(navigation, "reminders", "یادآوری‌ها", "◷");
        addNav(navigation, "profile", "تنظیمات", "⚙");
        root.addView(navigation, new LinearLayout.LayoutParams(-1, dp(76)));
        renderPage();
    }

    private void addNav(LinearLayout bar, String page, String label, String icon) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        int iconColor = page.equals(currentPage) ? GREEN : MUTED;
        TextView glyph = text(icon, 18, iconColor);
        glyph.setGravity(Gravity.CENTER);
        glyph.setBackground(round(page.equals(currentPage) ? 0xff17372f : 0xff1e293b, 12, 0xff263244));
        item.addView(glyph, new LinearLayout.LayoutParams(dp(34), dp(32)));
        TextView caption = text(label, 10, iconColor);
        caption.setGravity(Gravity.CENTER);
        item.addView(caption);
        item.setOnClickListener(v -> {
            currentPage = page;
            showApp();
        });
        bar.addView(item, new LinearLayout.LayoutParams(0, -1, 1));
    }

    private void renderPage() {
        stopQuoteAdvance();
        content.removeAllViews();
        if ("habits".equals(currentPage)) renderHabits();
        else if ("reminders".equals(currentPage)) renderReminders();
        else renderProfile();
    }

    private void renderHabits() {
        ArrayList<DbHelper.Habit> habits = database.habits();
        int completed = 0;
        for (DbHelper.Habit habit : habits) if (habit.completed) completed++;
        renderMotivationalQuote();
        TextView heading = text("عادت‌های امروز", 20, TEXT);
        heading.setTypeface(null, Typeface.BOLD);
        content.addView(heading);
        content.addView(text(completed + " از " + habits.size() + " عادت انجام شده", 14, GREEN), margin(0, 4, 0, 14));

        for (DbHelper.Habit habit : habits) {
            LinearLayout card = card();
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

            LinearLayout description = new LinearLayout(this);
            description.setOrientation(LinearLayout.VERTICAL);
            description.setGravity(Gravity.CENTER_VERTICAL);
            TextView title = text(habit.title, 16, TEXT);
            title.setTypeface(null, Typeface.BOLD);
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout titleRow = new LinearLayout(this);
            titleRow.setGravity(Gravity.CENTER_VERTICAL);
            titleRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            TextView emoji = text(habit.emoji, 28, TEXT);
            titleRow.addView(emoji, wrapMargin(0, 0, 8, 0));
            titleRow.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
            description.addView(titleRow, new LinearLayout.LayoutParams(-1, -2));
            String subtitle = habit.type.equals("quantitative")
                    ? "هدف: " + habit.min + " تا " + habit.max + " " + habit.unit
                    : (habit.description == null || habit.description.isEmpty() ? "عادت روزانه" : habit.description);
            description.addView(text(subtitle, 12, MUTED), margin(0, 5, 0, 0));
            EditText amount = null;
            LinearLayout amountControl = null;
            if (habit.type.equals("quantitative")) {
                amount = new EditText(this);
                amount.setSingleLine(true);
                amount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
                amount.setTextSize(15);
                amount.setTextColor(TEXT);
                amount.setHintTextColor(MUTED);
                amount.setHint("مقدار");
                amount.setText(habit.value);
                amount.setSelectAllOnFocus(true);
                amount.setGravity(Gravity.CENTER);
                amount.setPadding(dp(4), 0, dp(4), 0);
                amount.setBackground(round(0xff0b0f17, 8, 0xff334155));
                amount.setHint(habit.min + "-" + habit.max);
                amountControl = new LinearLayout(this);
                amountControl.setGravity(Gravity.CENTER);
                amountControl.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
                Button decrease = button("−", 0xff263244);
                Button increase = button("+", 0xff263244);
                final EditText numericAmount = amount;
                amountControl.addView(decrease, new LinearLayout.LayoutParams(dp(38), dp(42)));
                amountControl.addView(amount, new LinearLayout.LayoutParams(dp(62), dp(44)));
                amountControl.addView(increase, new LinearLayout.LayoutParams(dp(38), dp(42)));
                decrease.setOnClickListener(v -> adjustAmount(numericAmount, habit.min, habit.max, -1));
                increase.setOnClickListener(v -> adjustAmount(numericAmount, habit.min, habit.max, 1));
            }

            final EditText amountField = amount;
            final boolean[] amountWasEdited = {false};
            Button check = button(habit.completed ? "✓" : "○", habit.completed ? GREEN : 0xff334155);
            LinearLayout actions = new LinearLayout(this);
            actions.setGravity(Gravity.CENTER_VERTICAL);
            actions.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            LinearLayout.LayoutParams checkParams = new LinearLayout.LayoutParams(dp(44), dp(44));
            checkParams.setMargins(dp(5), 0, 0, 0);
            actions.addView(check, checkParams);
            check.setOnClickListener(v -> {
                boolean next = amountWasEdited[0] || !habit.completed;
                String value = amountField == null ? null : amountField.getText().toString();
                try {
                    if (amountField != null && !validAmount(value, habit.min, habit.max)) {
                        amountField.setError("مقدار باید بین " + habit.min + " و " + habit.max + " باشد");
                        return;
                    }
                    database.saveLog(habit.id, next, value);
                    renderPage();
                } catch (NumberFormatException exception) {
                    if (amountField != null) amountField.setError("مقدار باید عدد باشد");
                }
            });
            Button edit = button("✎", 0xff263244);
            LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(dp(40), dp(40));
            actions.addView(edit, editParams);
            edit.setOnClickListener(v -> editHabitDialog(habit));
            row.addView(description, new LinearLayout.LayoutParams(0, -2, 1));
            row.addView(actions, wrapMargin(8, 0, 0, 0));
            card.addView(row);
            if (amountControl != null) {
                LinearLayout amountRow = new LinearLayout(this);
                amountRow.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
                amountRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
                amountRow.addView(amountControl);
                card.addView(amountRow, margin(0, 8, 0, 0));
            }
            if (habit.type.equals("quantitative")) {
                amount.setOnClickListener(v -> {
                    amountWasEdited[0] = true;
                    check.setBackground(round(0xff334155, 10, 0xff334155));
                    check.setText("○");
                });
                amount.setOnFocusChangeListener((v, focused) -> {
                    if (focused) {
                        amountWasEdited[0] = true;
                        check.setBackground(round(0xff334155, 10, 0xff334155));
                        check.setText("○");
                    } else {
                        check.setBackground(round(amountField.getText().toString().equals(habit.value) ? GREEN : 0xff334155, 10, 0xff334155));
                        check.setText(amountField.getText().toString().equals(habit.value) ? "✓" : "○");
                    }
                });
            }
            content.addView(card, margin(0, 0, 0, 10));
        }

        Button add = button("+  افزودن عادت", GREEN);
        content.addView(add, margin(0, 2, 0, 14));
        add.setOnClickListener(v -> addHabitDialog());
        if (!habits.isEmpty()) {
            renderActivityGrid();
            renderHabitChart(habits);
        }
    }

    private void renderMotivationalQuote() {
        LinearLayout quoteCard = card();
        TextView label = text("✦  الهام‌بخش امروز", 12, GREEN);
        label.setTypeface(null, Typeface.BOLD);
        quoteCard.addView(label);

        LinearLayout quoteRow = new LinearLayout(this);
        quoteRow.setGravity(Gravity.CENTER_VERTICAL);
        quoteRow.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        Button previous = button("‹", 0xff263244);
        Button next = button("›", 0xff263244);
        previous.setTextSize(24);
        next.setTextSize(24);
        LinearLayout.LayoutParams arrowParams = new LinearLayout.LayoutParams(dp(40), dp(40));
        quoteRow.addView(previous, arrowParams);
        TextView quote = text(MOTIVATIONAL_QUOTES[quoteIndex], 14, TEXT);
        quote.setGravity(Gravity.CENTER);
        quote.setPadding(dp(2), dp(10), dp(2), dp(8));
        quote.setMaxLines(4);
        quote.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        quoteRow.addView(quote);
        quoteRow.addView(next, new LinearLayout.LayoutParams(dp(40), dp(40)));
        quoteCard.addView(quoteRow);
        previous.setOnClickListener(v -> moveQuote(-1));
        next.setOnClickListener(v -> moveQuote(1));
        GestureDetector swipeDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent event) {
                return true;
            }

            @Override
            public boolean onFling(MotionEvent start, MotionEvent end, float velocityX, float velocityY) {
                float distance = end.getX() - start.getX();
                if (Math.abs(distance) < dp(48) || Math.abs(velocityX) < dp(120)) return false;
                moveQuote(distance < 0 ? 1 : -1);
                return true;
            }
        });
        quote.setOnTouchListener((view, event) -> swipeDetector.onTouchEvent(event));
        LinearLayout dots = new LinearLayout(this);
        dots.setGravity(Gravity.CENTER);
        for (int i = 0; i < MOTIVATIONAL_QUOTES.length; i++) {
            final int index = i;
            TextView dot = text(i == quoteIndex ? "●" : "○", 12, i == quoteIndex ? GREEN : MUTED);
            dot.setGravity(Gravity.CENTER);
            dot.setPadding(dp(5), dp(2), dp(5), dp(2));
            dot.setOnClickListener(v -> {
                quoteIndex = index;
                renderPage();
            });
            dots.addView(dot);
        }
        quoteCard.addView(dots);
        content.addView(quoteCard, margin(0, 0, 0, 12));
        quoteAdvance = () -> {
            if (!"habits".equals(currentPage)) return;
            quoteIndex = (quoteIndex + 1) % MOTIVATIONAL_QUOTES.length;
            renderPage();
        };
        quoteHandler.postDelayed(quoteAdvance, 5000);
    }

    private void moveQuote(int offset) {
        quoteIndex = (quoteIndex + offset + MOTIVATIONAL_QUOTES.length) % MOTIVATIONAL_QUOTES.length;
        renderPage();
    }

    private void stopQuoteAdvance() {
        if (quoteAdvance != null) {
            quoteHandler.removeCallbacks(quoteAdvance);
            quoteAdvance = null;
        }
    }

    private void renderActivityGrid() {
        TextView title = text("تقویم پایداری عادت", 17, TEXT);
        title.setTypeface(null, Typeface.BOLD);
        content.addView(title, margin(0, 4, 0, 8));
        content.addView(text("فعالیت ۱۲۰ روز اخیر", 12, MUTED), margin(0, 0, 0, 8));

        HashMap<String, Integer> counts = database.activityCounts(120);
        LocalDate start = LocalDate.now().minusDays(119);
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(18);
        grid.setRowCount(7);
        grid.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        for (int day = 0; day < 120; day++) {
            LocalDate date = start.plusDays(day);
            int count = counts.containsKey(date.toString()) ? counts.get(date.toString()) : 0;
            int color = count == 0 ? 0xff1e293b : count == 1 ? 0xff064e3b : count == 2 ? 0xff047857 : count == 3 ? 0xff10b981 : 0xff34d399;
            View cell = new View(this);
            cell.setBackground(round(color, 3, 0xff263244));
            cell.setContentDescription(date.toString() + ": " + count);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = dp(11);
            params.height = dp(11);
            params.setMargins(dp(2), dp(2), dp(2), dp(2));
            grid.addView(cell, params);
        }
        HorizontalScrollView horizontal = new HorizontalScrollView(this);
        horizontal.setHorizontalScrollBarEnabled(false);
        horizontal.addView(grid);
        LinearLayout gridCard = card();
        gridCard.addView(horizontal);
        content.addView(gridCard, margin(0, 0, 0, 14));
    }

    private void renderHabitChart(ArrayList<DbHelper.Habit> habits) {
        TextView chartTitle = text("نمودار روند ۱۴ روز اخیر", 17, TEXT);
        chartTitle.setTypeface(null, Typeface.BOLD);
        content.addView(chartTitle, margin(0, 0, 0, 8));
        Spinner selector = new Spinner(this);
        ArrayList<String> labels = new ArrayList<>();
        int selectedPosition = 0;
        for (int i = 0; i < habits.size(); i++) {
            DbHelper.Habit habit = habits.get(i);
            labels.add(habit.emoji + "  " + habit.title + (habit.unit.isEmpty() ? "" : " (" + habit.unit + ")"));
            if (habit.id == selectedChartHabitId) selectedPosition = i;
        }
        selector.setAdapter(spinnerAdapter(labels.toArray(new String[0])));
        selector.setSelection(selectedPosition);
        selector.setBackground(round(0xff0b0f17, 10, 0xff334155));
        selector.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        selector.setPopupBackgroundDrawable(round(CARD, 10, 0xff334155));
        selector.setDropDownWidth(-1);
        LinearLayout chartCard = card();
        chartCard.addView(selector, margin(0, 0, 0, 8));
        DbHelper.Habit initial = habits.get(selectedPosition);
        ChartView chart = new ChartView(this, database.chartValues(initial.id), initial.min, initial.max,
                initial.type.equals("quantitative"));
        chartCard.addView(chart, new LinearLayout.LayoutParams(-1, dp(180)));
        content.addView(chartCard, margin(0, 0, 0, 12));
        selector.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                DbHelper.Habit selected = habits.get(position);
                selectedChartHabitId = selected.id;
                chart.setValues(database.chartValues(selected.id), selected.min, selected.max,
                        selected.type.equals("quantitative"));
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });
    }

    private void renderReminders() {
        renderCalendar();
        TextView heading = text("یادآوری‌ها", 20, TEXT);
        heading.setTypeface(null, Typeface.BOLD);
        content.addView(heading, margin(0, 0, 0, 12));
        String[] filters = {"روزانه", "هفتگی", "ماهانه", "سالانه"};
        LinearLayout filterOptions = new LinearLayout(this);
        filterOptions.setGravity(Gravity.CENTER_VERTICAL);
        filterOptions.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        for (int i = 0; i < filters.length; i++) {
            final int selected = i + 1;
            Button option = button(filters[i], reminderFilter == selected ? GREEN : 0xff263244);
            option.setTextSize(11);
            option.setMinHeight(dp(38));
            option.setPadding(dp(7), 0, dp(7), 0);
            LinearLayout.LayoutParams optionParams = new LinearLayout.LayoutParams(0, dp(38), 1);
            optionParams.setMargins(dp(2), 0, dp(2), 0);
            filterOptions.addView(option, optionParams);
            option.setOnClickListener(v -> {
                reminderFilter = selected;
                renderPage();
            });
        }
        content.addView(filterOptions, margin(0, 0, 0, 12));
        LinearLayout notificationActions = new LinearLayout(this);
        notificationActions.setOrientation(LinearLayout.HORIZONTAL);
        notificationActions.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        Button scheduleNotification = button("زمان‌بندی اعلان", GREEN);
        notificationActions.setGravity(Gravity.START);
        notificationActions.addView(scheduleNotification, wrapMargin(0, 0, 0, 0));
        content.addView(notificationActions, margin(0, 0, 0, 12));
        scheduleNotification.setOnClickListener(v -> scheduleNotificationDialog());
        if (reminderFilter == 0) return;
        ArrayList<DbHelper.Reminder> reminders = database.reminders();
        int displayedReminders = 0;
        for (DbHelper.Reminder reminder : reminders) {
            if (!matchesFrequency(reminder.frequency, reminderFilter)) continue;
            displayedReminders++;
            LinearLayout reminderCard = card();
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView icon = text(reminderIcon(reminder.frequency), 28, GREEN);
            row.addView(icon, wrapMargin(0, 0, 10, 0));
            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            info.addView(text(reminder.title, 16, TEXT));
            info.addView(text(reminder.frequency + (reminder.time.isEmpty() ? "" : " • " + reminder.time), 12, MUTED), margin(0, 4, 0, 0));
            row.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
            reminderCard.addView(row);
            reminderCard.setOnClickListener(v -> reminderDialog(reminder));
            content.addView(reminderCard, margin(0, 0, 0, 10));
        }
        if (displayedReminders == 0) content.addView(text("یادآوری‌ای در این بازه ثبت نشده است.", 14, MUTED));
    }

    private boolean matchesFrequency(String frequency, int filter) {
        String lower = frequency.toLowerCase(Locale.ROOT);
        if (filter == 1) return frequency.contains("روز") || lower.contains("daily");
        if (filter == 2) return frequency.contains("هفت") || lower.contains("weekly");
        if (filter == 3) return frequency.contains("ماه") || lower.contains("monthly");
        return frequency.contains("سال") || lower.contains("yearly");
    }

    private String reminderIcon(String frequency) {
        if (frequency.contains("روز") || frequency.toLowerCase(Locale.ROOT).contains("daily")) return "☀";
        if (frequency.contains("هفت") || frequency.toLowerCase(Locale.ROOT).contains("weekly")) return "📅";
        if (frequency.contains("ماه") || frequency.toLowerCase(Locale.ROOT).contains("monthly")) return "🗓";
        return "🎉";
    }

    private void renderCalendar() {
        Calendar month = Calendar.getInstance(new ULocale("fa_IR@calendar=persian"));
        month.add(Calendar.MONTH, calendarMonthOffset);
        month.set(Calendar.DAY_OF_MONTH, 1);
        LinearLayout calendarCard = card();
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        Button previous = button("‹", 0xff263244);
        Button next = button("›", 0xff263244);
        titleRow.addView(previous, new LinearLayout.LayoutParams(dp(42), dp(40)));
        TextView monthTitle = text(persianMonthName(month) + " " + persianDigits(month.get(Calendar.YEAR)), 16, TEXT);
        monthTitle.setGravity(Gravity.CENTER);
        titleRow.addView(monthTitle, new LinearLayout.LayoutParams(0, -2, 1));
        titleRow.addView(next, new LinearLayout.LayoutParams(dp(42), dp(40)));
        calendarCard.addView(titleRow);
        previous.setOnClickListener(v -> {
            calendarMonthOffset--;
            selectedCalendarDateKey = "";
            renderPage();
        });
        next.setOnClickListener(v -> {
            calendarMonthOffset++;
            selectedCalendarDateKey = "";
            renderPage();
        });

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(7);
        grid.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        String[] weekdays = {"ش", "ی", "د", "س", "چ", "پ", "ج"};
        for (String weekday : weekdays) {
            TextView label = text(weekday, 12, MUTED);
            label.setGravity(Gravity.CENTER);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = dp(32);
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            grid.addView(label, params);
        }
        int firstColumn = month.get(Calendar.DAY_OF_WEEK) % 7;
        int daysInMonth = month.getActualMaximum(Calendar.DAY_OF_MONTH);
        int cellCount = ((firstColumn + daysInMonth + 6) / 7) * 7;
        Calendar today = Calendar.getInstance(new ULocale("fa_IR@calendar=persian"));
        Calendar selectedDate = selectedCalendarDateKey.isEmpty()
                ? (Calendar) month.clone()
                : Calendar.getInstance(new ULocale("fa_IR@calendar=persian"));
        if (!selectedCalendarDateKey.isEmpty()) {
            LocalDate selectedLocalDate = LocalDate.parse(selectedCalendarDateKey);
            selectedDate.setTimeInMillis(selectedLocalDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli());
        } else if (calendarMonthOffset == 0) {
            selectedDate = (Calendar) today.clone();
        }
        if (selectedDate.get(Calendar.YEAR) != month.get(Calendar.YEAR)
                || selectedDate.get(Calendar.MONTH) != month.get(Calendar.MONTH)) {
            selectedDate = (Calendar) month.clone();
            selectedCalendarDateKey = "";
        }
        for (int cell = 0; cell < cellCount; cell++) {
            int day = cell - firstColumn + 1;
            View dateCell;
            if (day < 1 || day > daysInMonth) {
                dateCell = text("", 12, MUTED);
            } else {
                Calendar date = (Calendar) month.clone();
                date.set(Calendar.DAY_OF_MONTH, day);
                boolean isToday = date.get(Calendar.YEAR) == today.get(Calendar.YEAR)
                        && date.get(Calendar.MONTH) == today.get(Calendar.MONTH)
                        && day == today.get(Calendar.DAY_OF_MONTH);
                boolean isPast = date.before(today);
                String dateKey = calendarDateKey(date);
                boolean isSelected = dateKey.equals(selectedCalendarDateKey)
                        || (selectedCalendarDateKey.isEmpty() && date.get(Calendar.YEAR) == selectedDate.get(Calendar.YEAR)
                        && date.get(Calendar.MONTH) == selectedDate.get(Calendar.MONTH)
                        && day == selectedDate.get(Calendar.DAY_OF_MONTH));
                int textColor = isToday ? 0xff111827 : isPast ? 0xffe2e8f0 : 0xff111827;
                int fillColor = isToday ? 0xfff59e0b : isPast ? 0xff475569 : 0xffffffff;
                LinearLayout dayCell = new LinearLayout(this);
                dayCell.setOrientation(LinearLayout.VERTICAL);
                dayCell.setGravity(Gravity.CENTER);
                TextView dayNumber = text(persianDigits(day), 12, textColor);
                dayNumber.setGravity(Gravity.CENTER);
                dayCell.addView(dayNumber, new LinearLayout.LayoutParams(-1, 0, 1));
                LinearLayout eventDots = new LinearLayout(this);
                eventDots.setGravity(Gravity.CENTER);
                ArrayList<DbHelper.CalendarEvent> events = database.calendarEvents(dateKey);
                for (int i = 0; i < Math.min(events.size(), 4); i++) {
                    View dot = new View(this);
                    dot.setBackground(round(events.get(i).color, 50, events.get(i).color));
                    LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(5), dp(5));
                    dotParams.setMargins(dp(1), 0, dp(1), dp(2));
                    eventDots.addView(dot, dotParams);
                }
                dayCell.addView(eventDots, new LinearLayout.LayoutParams(-1, dp(7)));
                dayCell.setBackground(round(fillColor, 8, isSelected ? GREEN : fillColor));
                dayCell.setContentDescription(persianDigits(day) + "، " + events.size() + " رویداد");
                dayCell.setOnClickListener(v -> {
                    selectedCalendarDateKey = dateKey;
                    renderPage();
                });
                dateCell = dayCell;
            }
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = dp(44);
            params.setMargins(dp(2), dp(2), dp(2), dp(2));
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            grid.addView(dateCell, params);
        }
        calendarCard.addView(grid, margin(0, 8, 0, 0));
        content.addView(calendarCard, margin(0, 0, 0, 14));
        if (selectedCalendarDateKey.isEmpty()) selectedCalendarDateKey = calendarDateKey(selectedDate);
        renderSelectedCalendarDay(selectedDate, selectedCalendarDateKey);
    }

    private void renderSelectedCalendarDay(Calendar date, String dateKey) {
        LinearLayout dayCard = card();
        TextView dayTitle = text("رویدادهای " + persianDigits(date.get(Calendar.DAY_OF_MONTH)) + " "
                + persianMonthName(date) + " " + persianDigits(date.get(Calendar.YEAR)), 16, TEXT);
        dayTitle.setTypeface(null, Typeface.BOLD);
        dayCard.addView(dayTitle);
        ArrayList<DbHelper.CalendarEvent> events = database.calendarEvents(dateKey);
        if (events.isEmpty()) {
            dayCard.addView(text("برای این روز رویدادی ثبت نشده است.", 13, MUTED), margin(0, 8, 0, 4));
        } else {
            for (DbHelper.CalendarEvent event : events) {
                LinearLayout row = new LinearLayout(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
                View colorDot = new View(this);
                colorDot.setBackground(round(event.color, 50, event.color));
                row.addView(colorDot, new LinearLayout.LayoutParams(dp(12), dp(12)));
                TextView eventTitle = text(event.title, 14, event.color);
                eventTitle.setMaxLines(2);
                eventTitle.setEllipsize(TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams eventParams = new LinearLayout.LayoutParams(0, -2, 1);
                eventParams.setMargins(dp(8), 0, dp(8), 0);
                row.addView(eventTitle, eventParams);
                Button delete = button("حذف", 0xff7f1d1d);
                delete.setTextSize(10);
                row.addView(delete, new LinearLayout.LayoutParams(dp(54), dp(36)));
                delete.setOnClickListener(v -> {
                    database.deleteCalendarEvent(event.id);
                    renderPage();
                });
                dayCard.addView(row, margin(0, 8, 0, 0));
            }
        }
        EditText eventTitle = field("عنوان رویداد برای این روز", "", InputType.TYPE_CLASS_TEXT);
        dayCard.addView(eventTitle, margin(0, 12, 0, 8));
        LinearLayout colorPicker = new LinearLayout(this);
        colorPicker.setGravity(Gravity.CENTER);
        colorPicker.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        final int[] selectedColor = {EVENT_COLORS[0]};
        ArrayList<View> colorDots = new ArrayList<>();
        for (int color : EVENT_COLORS) {
            View colorDot = new View(this);
            colorDot.setBackground(round(color, 50, color == selectedColor[0] ? TEXT : color));
            LinearLayout.LayoutParams colorParams = new LinearLayout.LayoutParams(dp(30), dp(30));
            colorParams.setMargins(dp(6), dp(3), dp(6), dp(3));
            colorPicker.addView(colorDot, colorParams);
            colorDots.add(colorDot);
            colorDot.setOnClickListener(v -> {
                selectedColor[0] = color;
                for (int i = 0; i < colorDots.size(); i++) {
                    int dotColor = EVENT_COLORS[i];
                    colorDots.get(i).setBackground(round(dotColor, 50, dotColor == selectedColor[0] ? TEXT : dotColor));
                }
            });
        }
        dayCard.addView(colorPicker, margin(0, 2, 0, 0));
        Button addEvent = button("افزودن رویداد", GREEN);
        dayCard.addView(addEvent, margin(0, 8, 0, 0));
        addEvent.setOnClickListener(v -> {
            String value = eventTitle.getText().toString().trim();
            if (value.isEmpty()) {
                eventTitle.setError("عنوان رویداد را وارد کن");
                return;
            }
            database.addCalendarEvent(dateKey, value, selectedColor[0]);
            renderPage();
        });
        content.addView(dayCard, margin(0, 0, 0, 14));
    }

    private String calendarDateKey(Calendar date) {
        return Instant.ofEpochMilli(date.getTimeInMillis()).atZone(ZoneId.systemDefault()).toLocalDate().toString();
    }

    private void renderProfile() {
        String[] profile = database.profile();
        TextView heading = text("تنظیمات", 20, TEXT);
        heading.setTypeface(null, Typeface.BOLD);
        content.addView(heading, margin(0, 0, 0, 12));
        LinearLayout card = card();
        FrameLayout avatar = new FrameLayout(this);
        avatar.setBackground(round(0xff0b0f17, 100, GREEN));
        avatar.setClipToOutline(true);
        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(dp(128), dp(128));
        avatarParams.gravity = Gravity.CENTER_HORIZONTAL;
        String photoUri = pendingPhoto == null ? profile[4] : pendingPhoto.toString();
        boolean imageLoaded = false;
        if (!photoUri.isEmpty()) {
            try {
                ImageView image = new ImageView(this);
                image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                image.setImageURI(Uri.parse(photoUri));
                avatar.addView(image, new FrameLayout.LayoutParams(-1, -1));
                imageLoaded = image.getDrawable() != null;
            } catch (RuntimeException ignored) { }
        }
        if (!imageLoaded) {
            avatar.removeAllViews();
            TextView placeholder = text("🤖", 58, TEXT);
            placeholder.setGravity(Gravity.CENTER);
            avatar.addView(placeholder, new FrameLayout.LayoutParams(-1, -1));
        }
        card.addView(avatar, avatarParams);
        Button choosePhoto = button(profile[4].isEmpty() ? "انتخاب تصویر" : "تغییر تصویر", 0xff1f2937);
        card.addView(choosePhoto, margin(0, 10, 0, 10));
        choosePhoto.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("image/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(intent, 11);
        });
        if (!photoUri.isEmpty()) {
            Button removePhoto = button("حذف تصویر", 0xff7f1d1d);
            card.addView(removePhoto, margin(0, 8, 0, 10));
            removePhoto.setOnClickListener(v -> {
                pendingPhoto = Uri.EMPTY;
                renderPage();
            });
        }
        EditText name = field("نام", profile[0], InputType.TYPE_CLASS_TEXT);
        card.addView(name);
        TextView birthDateLabel = text("تاریخ تولد شمسی", 13, MUTED);
        card.addView(birthDateLabel, margin(0, 12, 0, 6));
        Button birthDate = button("انتخاب سال، ماه و روز", 0xff1f2937);
        String[] selectedBirthDate = {profile[1]};
        int[] birthParts = parseBirthDate(profile[1]);
        TextView calculatedAge = text(birthParts == null ? getString(R.string.profile_age_missing)
                : getString(R.string.profile_age, persianDigits(calculateAge(birthParts))), 13, GREEN);
        card.addView(birthDate, margin(0, 0, 0, 4));
        card.addView(calculatedAge, margin(0, 0, 0, 8));
        birthDate.setText(birthParts == null ? "انتخاب سال، ماه و روز" : formatBirthDate(birthParts));
        birthDate.setOnClickListener(v -> showBirthDatePicker(selectedBirthDate[0], (value, parts) -> {
            selectedBirthDate[0] = value;
            birthDate.setText(formatBirthDate(parts));
            calculatedAge.setText(getString(R.string.profile_age, persianDigits(calculateAge(parts))));
        }));
        EditText email = field("ایمیل", profile[2], InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText phone = field("شماره تلفن", profile[3], InputType.TYPE_CLASS_PHONE);
        card.addView(email, margin(0, 8, 0, 0));
        card.addView(phone, margin(0, 8, 0, 0));
        Button save = button("ذخیره تنظیمات", GREEN);
        card.addView(save, margin(0, 12, 0, 0));
        save.setOnClickListener(v -> {
            String emailValue = email.getText().toString().trim();
            if (!emailValue.isEmpty() && !android.util.Patterns.EMAIL_ADDRESS.matcher(emailValue).matches()) {
                email.setError("ایمیل معتبر وارد کن");
                return;
            }
            String phoneValue = toAsciiDigits(phone.getText().toString().trim()).replaceAll("[\\s()-]", "");
            if (!phoneValue.isEmpty() && !Pattern.matches("^(?:\\+98|0098|98|0)?9\\d{9}$", phoneValue)) {
                phone.setError("شماره موبایل معتبر وارد کن");
                return;
            }
            String photo = pendingPhoto == null ? profile[4] : pendingPhoto.equals(Uri.EMPTY) ? "" : pendingPhoto.toString();
            database.saveProfile(name.getText().toString(), selectedBirthDate[0], emailValue, phoneValue, photo);
            if (parseBirthDate(selectedBirthDate[0]) == null) {
                BirthdayReminderScheduler.cancel(this);
            } else {
                BirthdayReminderScheduler.schedule(this, selectedBirthDate[0]);
                requestBirthdayNotificationPermission();
            }
            pendingPhoto = null;
            renderPage();
            showMessage("اطلاعات روی همین گوشی ذخیره شد.");
        });
        content.addView(card);
        content.addView(text("نسخهٔ مستقل آفلاین؛ اطلاعات این اپ با نسخهٔ وب همگام نمی‌شود.", 12, MUTED), margin(0, 12, 0, 0));
    }

    private interface BirthDateSelection {
        void onSelected(String value, int[] parts);
    }

    private void showBirthDatePicker(String currentValue, BirthDateSelection onSelected) {
        Calendar today = Calendar.getInstance(new ULocale("fa_IR@calendar=persian"));
        int[] currentParts = parseBirthDate(currentValue);
        if (currentParts == null) {
            currentParts = new int[]{today.get(Calendar.YEAR) - 20, today.get(Calendar.MONTH), today.get(Calendar.DAY_OF_MONTH)};
        }
        int currentYear = today.get(Calendar.YEAR);
        int firstYear = Math.max(1300, currentYear - 120);
        String[] years = new String[currentYear - firstYear + 1];
        for (int i = 0; i < years.length; i++) years[i] = persianDigits(firstYear + i);
        String[] months = {"فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
                "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"};
        LinearLayout form = form();
        form.addView(text("سال تولد", 12, MUTED));
        Spinner yearSpinner = spinner(years);
        form.addView(yearSpinner);
        form.addView(text("ماه تولد", 12, MUTED), margin(0, 8, 0, 0));
        Spinner monthSpinner = spinner(months);
        form.addView(monthSpinner);
        form.addView(text("روز تولد", 12, MUTED), margin(0, 8, 0, 0));
        Spinner daySpinner = spinner(new String[]{"۱"});
        form.addView(daySpinner);

        final String[][] days = {new String[0]};
        Runnable updateDays = () -> {
            int year = firstYear + yearSpinner.getSelectedItemPosition();
            int month = monthSpinner.getSelectedItemPosition();
            Calendar selectedMonth = Calendar.getInstance(new ULocale("fa_IR@calendar=persian"));
            selectedMonth.clear();
            selectedMonth.set(Calendar.YEAR, year);
            selectedMonth.set(Calendar.MONTH, month);
            selectedMonth.set(Calendar.DAY_OF_MONTH, 1);
            int count = selectedMonth.getActualMaximum(Calendar.DAY_OF_MONTH);
            int previousDay = daySpinner.getSelectedItemPosition() + 1;
            days[0] = new String[count];
            for (int i = 0; i < count; i++) days[0][i] = persianDigits(i + 1);
            ArrayAdapter<String> dayAdapter = spinnerAdapter(days[0]);
            daySpinner.setAdapter(dayAdapter);
            daySpinner.setSelection(Math.min(previousDay, count) - 1);
        };
        yearSpinner.setSelection(Math.max(0, Math.min(years.length - 1, currentParts[0] - firstYear)));
        monthSpinner.setSelection(Math.max(0, Math.min(11, currentParts[1])));
        yearSpinner.setOnItemSelectedListener(selectionListener(updateDays));
        monthSpinner.setOnItemSelectedListener(selectionListener(updateDays));
        updateDays.run();
        daySpinner.setSelection(Math.max(0, Math.min(daySpinner.getCount() - 1, currentParts[2] - 1)));

        new AlertDialog.Builder(this).setTitle("تاریخ تولد شمسی")
                .setView(form)
                .setNegativeButton("انصراف", null)
                .setPositiveButton("ثبت تاریخ", (dialog, which) -> {
                    int[] parts = {
                            firstYear + yearSpinner.getSelectedItemPosition(),
                            monthSpinner.getSelectedItemPosition(),
                            daySpinner.getSelectedItemPosition() + 1
                    };
                    if (isBirthDateInFuture(parts)) {
                        showMessage("تاریخ تولد نمی‌تواند در آینده باشد.");
                        return;
                    }
                    onSelected.onSelected(serializeBirthDate(parts), parts);
                }).show();
    }

    private android.widget.AdapterView.OnItemSelectedListener selectionListener(Runnable action) {
        return new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                action.run();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        };
    }

    private int[] parseBirthDate(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        try {
            String[] parts = value.split("-");
            if (parts.length != 3) return null;
            int[] date = {Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1, Integer.parseInt(parts[2])};
            if (date[0] < 1300 || date[1] < 0 || date[1] > 11 || date[2] < 1 || date[2] > 31) return null;
            return date;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String formatBirthDate(int[] parts) {
        return persianDigits(parts[0]) + " / " + persianDigits(parts[1] + 1) + " / " + persianDigits(parts[2]);
    }

    private String serializeBirthDate(int[] parts) {
        return parts[0] + "-" + (parts[1] + 1) + "-" + parts[2];
    }

    private int calculateAge(int[] birthDate) {
        Calendar today = Calendar.getInstance(new ULocale("fa_IR@calendar=persian"));
        int age = today.get(Calendar.YEAR) - birthDate[0];
        if (today.get(Calendar.MONTH) < birthDate[1]
                || (today.get(Calendar.MONTH) == birthDate[1] && today.get(Calendar.DAY_OF_MONTH) < birthDate[2])) {
            age--;
        }
        return Math.max(0, age);
    }

    private boolean isBirthDateInFuture(int[] birthDate) {
        Calendar selected = Calendar.getInstance(new ULocale("fa_IR@calendar=persian"));
        selected.set(Calendar.YEAR, birthDate[0]);
        selected.set(Calendar.MONTH, birthDate[1]);
        selected.set(Calendar.DAY_OF_MONTH, birthDate[2]);
        Calendar today = Calendar.getInstance(new ULocale("fa_IR@calendar=persian"));
        selected.set(Calendar.HOUR_OF_DAY, 0);
        selected.set(Calendar.MINUTE, 0);
        selected.set(Calendar.SECOND, 0);
        selected.set(Calendar.MILLISECOND, 0);
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        return selected.after(today);
    }

    private String toAsciiDigits(String value) {
        return value.replace('۰', '0').replace('۱', '1').replace('۲', '2').replace('۳', '3')
                .replace('۴', '4').replace('۵', '5').replace('۶', '6').replace('۷', '7')
                .replace('۸', '8').replace('۹', '9').replace('٠', '0').replace('١', '1')
                .replace('٢', '2').replace('٣', '3').replace('٤', '4').replace('٥', '5')
                .replace('٦', '6').replace('٧', '7').replace('٨', '8').replace('٩', '9');
    }

    private void addHabitDialog() {
        LinearLayout form = form();
        EditText title = field("نام عادت", "", InputType.TYPE_CLASS_TEXT);
        EditText emoji = field("ایموجی", "📌", InputType.TYPE_CLASS_TEXT);
        Spinner type = spinner(new String[]{"تیک‌زدنی", "عددی"});
        EditText min = field("حداقل مقدار (برای عددی)", "1", InputType.TYPE_CLASS_NUMBER);
        EditText max = field("حداکثر مقدار (برای عددی)", "100", InputType.TYPE_CLASS_NUMBER);
        EditText unit = field("واحد", "", InputType.TYPE_CLASS_TEXT);
        EditText note = field("توضیحات کوتاه (اختیاری)", "", InputType.TYPE_CLASS_TEXT);
        form.addView(title); form.addView(emoji); form.addView(type); form.addView(min); form.addView(max); form.addView(unit); form.addView(note);
        new AlertDialog.Builder(this).setTitle("عادت جدید").setView(form)
                .setNegativeButton("انصراف", null)
                .setPositiveButton("افزودن", (dialog, which) -> {
                    String name = title.getText().toString().trim();
                    if (name.isEmpty()) { showMessage("نام عادت را وارد کن."); return; }
                    boolean numeric = type.getSelectedItemPosition() == 1;
                    int low = parse(min.getText().toString(), 1);
                    int high = Math.max(low, parse(max.getText().toString(), 100));
                    database.addHabit(name, emoji.getText().toString().trim().isEmpty() ? "📌" : emoji.getText().toString(), numeric ? "quantitative" : "boolean", low, high, unit.getText().toString(), note.getText().toString());
                    renderPage();
                }).show();
    }

    private void editHabitDialog(DbHelper.Habit habit) {
        LinearLayout form = form();
        EditText title = field("نام عادت", habit.title, InputType.TYPE_CLASS_TEXT);
        EditText emoji = field("ایموجی", habit.emoji, InputType.TYPE_CLASS_TEXT);
        Spinner type = spinner(new String[]{"تیک‌زدنی", "عددی"});
        type.setSelection(habit.type.equals("quantitative") ? 1 : 0);
        EditText min = field("حداقل مقدار", String.valueOf(habit.min), InputType.TYPE_CLASS_NUMBER);
        EditText max = field("حداکثر مقدار", String.valueOf(habit.max), InputType.TYPE_CLASS_NUMBER);
        EditText unit = field("واحد", habit.unit, InputType.TYPE_CLASS_TEXT);
        EditText note = field("توضیحات", habit.description == null ? "" : habit.description, InputType.TYPE_CLASS_TEXT);
        form.addView(title); form.addView(emoji); form.addView(type); form.addView(min); form.addView(max); form.addView(unit); form.addView(note);
        new AlertDialog.Builder(this).setTitle("ویرایش عادت").setView(form)
                .setNegativeButton("انصراف", null)
                .setNeutralButton("حذف عادت", (dialog, which) -> new AlertDialog.Builder(this)
                        .setMessage("عادت و سوابق آن حذف شود؟")
                        .setNegativeButton("انصراف", null)
                        .setPositiveButton("حذف", (confirm, action) -> {
                            database.deleteHabit(habit.id);
                            renderPage();
                        }).show())
                .setPositiveButton("ذخیره", (dialog, which) -> {
                    String name = title.getText().toString().trim();
                    if (name.isEmpty()) { showMessage("نام عادت را وارد کن."); return; }
                    boolean numeric = type.getSelectedItemPosition() == 1;
                    int low = parse(min.getText().toString(), 1);
                    int high = Math.max(low, parse(max.getText().toString(), 100));
                    database.updateHabit(habit.id, name, emoji.getText().toString().trim().isEmpty() ? "📌" : emoji.getText().toString(),
                            numeric ? "quantitative" : "boolean", low, high, unit.getText().toString(), note.getText().toString());
                    renderPage();
                }).show();
    }

    private Spinner spinner(String[] options) {
        Spinner spinner = new Spinner(this);
        spinner.setAdapter(spinnerAdapter(options));
        spinner.setBackground(round(0xff0b0f17, 9, 0xff334155));
        spinner.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        spinner.setPopupBackgroundDrawable(round(CARD, 10, 0xff334155));
        spinner.setDropDownWidth(-1);
        return spinner;
    }

    private ArrayAdapter<String> spinnerAdapter(String[] options) {
        return new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, options) {
            private TextView optionView(String value, boolean dropdown) {
                TextView view = new TextView(MainActivity.this);
                view.setText(value);
                view.setTextColor(TEXT);
                view.setTextSize(14);
                view.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
                view.setSingleLine(true);
                view.setEllipsize(TextUtils.TruncateAt.END);
                view.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
                view.setPadding(dp(12), dp(dropdown ? 12 : 8), dp(12), dp(dropdown ? 12 : 8));
                if (dropdown) view.setBackgroundColor(CARD);
                return view;
            }

            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                return optionView(getItem(position), false);
            }

            @Override
            public View getDropDownView(int position, View convertView, android.view.ViewGroup parent) {
                return optionView(getItem(position), true);
            }
        };
    }

    private void addReminderDialog() {
        reminderDialog(null);
    }

    private void reminderDialog(DbHelper.Reminder reminder) {
        Dialog dialog = new Dialog(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(20), dp(20), dp(20));
        page.setBackgroundColor(BG);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        TextView heading = text(reminder == null ? "یادآوری جدید" : "ویرایش یادآوری", 20, TEXT);
        heading.setTypeface(null, Typeface.BOLD);
        titleRow.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        Button close = button("×", 0xff263244);
        titleRow.addView(close, new LinearLayout.LayoutParams(dp(42), dp(42)));
        page.addView(titleRow);
        close.setOnClickListener(v -> dialog.dismiss());

        LinearLayout form = form();
        EditText title = field("عنوان یادآوری", reminder == null ? "" : reminder.title, InputType.TYPE_CLASS_TEXT);
        Spinner frequency = spinner(new String[]{"روزانه", "هفتگی", "ماهانه", "سالانه"});
        String[] frequencies = {"روزانه", "هفتگی", "ماهانه", "سالانه"};
        if (reminder != null) {
            for (int i = 0; i < frequencies.length; i++) {
                if (matchesFrequency(reminder.frequency, i + 1)) frequency.setSelection(i);
            }
        }
        EditText time = field("زمان (اختیاری)", reminder == null ? "" : reminder.time, InputType.TYPE_CLASS_TEXT);
        form.addView(text("عنوان", 12, MUTED));
        form.addView(title, margin(0, 4, 0, 12));
        form.addView(text("بازهٔ تکرار", 12, MUTED));
        form.addView(frequency, margin(0, 4, 0, 12));
        form.addView(text("زمان", 12, MUTED));
        form.addView(time, margin(0, 4, 0, 0));
        page.addView(form, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        Button save = button("ذخیره", GREEN);
        actions.addView(save, new LinearLayout.LayoutParams(0, dp(50), 1));
        save.setOnClickListener(v -> {
            String value = title.getText().toString().trim();
            if (value.isEmpty()) {
                title.setError("عنوان یادآوری را وارد کن");
                return;
            }
            if (reminder == null) database.addReminder(value, frequency.getSelectedItem().toString(), time.getText().toString());
            else database.updateReminder(reminder.id, value, frequency.getSelectedItem().toString(), time.getText().toString());
            dialog.dismiss();
            renderPage();
        });
        if (reminder != null) {
            Button delete = button("حذف", 0xff7f1d1d);
            LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(0, dp(50), 1);
            deleteParams.setMargins(dp(10), 0, 0, 0);
            actions.addView(delete, deleteParams);
            delete.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setMessage("این یادآوری حذف شود؟")
                    .setNegativeButton("انصراف", null)
                    .setPositiveButton("حذف", (confirm, action) -> {
                        database.deleteReminder(reminder.id);
                        dialog.dismiss();
                        renderPage();
                    }).show());
        }
        page.addView(actions);
        dialog.setContentView(page);
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout(-1, -1);
        }
    }

    private LinearLayout form() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(8), dp(20), dp(4));
        return layout;
    }

    private EditText field(String hint, String value, int type) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint(hint);
        input.setText(value);
        input.setTextColor(TEXT);
        input.setHintTextColor(MUTED);
        input.setInputType(type);
        input.setPadding(dp(12), dp(8), dp(12), dp(8));
        input.setBackground(round(0xff0b0f17, 9, 0xff334155));
        return input;
    }

    private LinearLayout card() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(14), dp(13), dp(14), dp(13));
        layout.setBackground(round(CARD, 16, 0xff263244));
        return layout;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(size);
        return view;
    }

    private Button button(String label, int color) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(TEXT);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setMinHeight(dp(44));
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setBackground(round(color, 11, color));
        return button;
    }

    private GradientDrawable round(int color, int radius, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private LinearLayout.LayoutParams margin(int left, int top, int right, int bottom) {
        return new LinearLayout.LayoutParams(-1, -2) {{
            setMargins(dp(left), dp(top), dp(right), dp(bottom));
        }};
    }

    private LinearLayout.LayoutParams wrapMargin(int left, int top, int right, int bottom) {
        return new LinearLayout.LayoutParams(-2, -2) {{
            setMargins(dp(left), dp(top), dp(right), dp(bottom));
        }};
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private int parse(String value, int fallback) {
        try { return Integer.parseInt(value); } catch (NumberFormatException exception) { return fallback; }
    }

    private void showMessage(String message) {
        new AlertDialog.Builder(this).setMessage(message).setPositiveButton("باشه", null).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 11 && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) {
            }
            pendingPhoto = uri;
            renderPage();
        }
    }

    private static final class ChartView extends View {
        private ArrayList<Integer> values;
        private int minValue;
        private int maxValue;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        ChartView(MainActivity context, ArrayList<Integer> values, int minValue, int maxValue, boolean quantitative) {
            super(context);
            this.values = values;
            this.minValue = quantitative ? minValue : 0;
            this.maxValue = quantitative ? maxValue : 1;
        }

        void setValues(ArrayList<Integer> nextValues, int min, int max, boolean isQuantitative) {
            values = nextValues;
            minValue = isQuantitative ? min : 0;
            maxValue = isQuantitative ? max : 1;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float left = dp(42), right = getWidth() - dp(8), top = dp(14), bottom = getHeight() - dp(22);
            float valueRange = maxValue - minValue;
            float range = valueRange > 0 ? valueRange : 1f;
            paint.setColor(0xff334155);
            paint.setStrokeWidth(dp(1));
            paint.setTextSize(dp(10));
            paint.setTextAlign(Paint.Align.RIGHT);
            for (int i = 0; i < 4; i++) {
                float y = top + (bottom - top) * i / 3f;
                canvas.drawLine(left, y, right, y, paint);
                float tick = valueRange > 0 ? maxValue - range * i / 3f : minValue;
                canvas.drawText(String.valueOf(Math.round(tick)), left - dp(6), y + dp(4), paint);
            }
            paint.setColor(GREEN);
            paint.setStrokeWidth(dp(2));
            paint.setStyle(Paint.Style.STROKE);
            float previousX = 0, previousY = 0;
            int previousIndex = -2;
            for (int i = 0; i < values.size(); i++) {
                Integer value = values.get(i);
                if (value == null) {
                    previousIndex = -2;
                    continue;
                }
                float x = left + (right - left) * i / Math.max(1f, values.size() - 1f);
                float normalized = valueRange > 0
                        ? Math.max(0f, Math.min(1f, (value - minValue) / range))
                        : 0.5f;
                float y = bottom - (bottom - top) * normalized;
                if (previousIndex == i - 1) canvas.drawLine(previousX, previousY, x, y, paint);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(x, y, dp(3), paint);
                paint.setStyle(Paint.Style.STROKE);
                previousX = x;
                previousY = y;
                previousIndex = i;
            }
            paint.setStyle(Paint.Style.FILL);
        }

        private float dp(int value) {
            return value * getResources().getDisplayMetrics().density;
        }
    }

}
