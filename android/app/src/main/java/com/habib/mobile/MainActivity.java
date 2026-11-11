package com.habib.mobile;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteException;
import android.graphics.Canvas;
import android.graphics.Color;
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
import android.text.TextWatcher;
import android.text.Editable;
import android.view.Gravity;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
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
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final int REQUEST_NOTIFICATIONS = 51;
    private static final int REQUEST_BIRTHDAY_NOTIFICATIONS = 52;
    private static final String NOTIFICATION_CHANNEL = "habib_reminders";
    private static final String PREFS_NAME = "habib_preferences";
    private static final String SCHEDULED_NOTIFICATION_CODES = "scheduled_notification_codes";
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
            0xffef4444, 0xfff97316, 0xfffb923c, 0xfffacc15
    };
    private static final String[] WEEKDAY_NAMES = {"شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنج‌شنبه", "جمعه"};
    private static final int WEEK_COLOR_WORK = 0xff818cf8;
    private static final int WEEK_COLOR_GYM = 0xff059669;
    private static final int WEEK_COLOR_FREE_SHORT = 0xff3b82f6;
    private static final int WEEK_COLOR_FREE_LONG = 0xff10b981;
    private DbHelper database;
    private LinearLayout root;
    private LinearLayout content;
    private String currentPage = "habits";
    private Uri pendingPhoto;
    private long selectedChartHabitId = -1;
    private int calendarMonthOffset;
    private int reminderFilter = 1;
    private int quoteIndex;
    private String selectedCalendarDateKey = "";
    private LocalDate selectedHabitDate = LocalDate.now();
    private int selectedTrophyIndex = -1;
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
        scheduleReminders();
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
            int amount = Integer.parseInt(toAsciiDigits(value.trim()));
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
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        Set<String> scheduledCodes = new HashSet<>(preferences.getStringSet(
                SCHEDULED_NOTIFICATION_CODES, new HashSet<>()));
        scheduledCodes.add(String.valueOf(requestCode));
        preferences.edit().putStringSet(SCHEDULED_NOTIFICATION_CODES, scheduledCodes).apply();
        showMessage("اعلان برای " + persianDigits((int) (delayMillis / 60_000L)) + " دقیقهٔ دیگر زمان‌بندی شد.");
    }

    private void cancelScheduledNotifications() {
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        Set<String> scheduledCodes = preferences.getStringSet(SCHEDULED_NOTIFICATION_CODES, new HashSet<>());
        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        for (String code : scheduledCodes) {
            try {
                Intent intent = new Intent(this, NotificationReceiver.class);
                PendingIntent pending = PendingIntent.getBroadcast(this, Integer.parseInt(code), intent,
                        PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
                if (pending != null) {
                    alarmManager.cancel(pending);
                    pending.cancel();
                }
            } catch (NumberFormatException exception) {
                android.util.Log.w("Habib", "Ignoring invalid scheduled notification id", exception);
            }
        }
        preferences.edit().remove(SCHEDULED_NOTIFICATION_CODES).apply();
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notificationManager.cancelAll();
    }

    private void scheduleReminders() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return;
        }
        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        Set<String> scheduledCodes = new HashSet<>(preferences.getStringSet(
                SCHEDULED_NOTIFICATION_CODES, new HashSet<>()));
        for (String code : scheduledCodes) {
            try {
                Intent intent = new Intent(this, NotificationReceiver.class);
                PendingIntent pending = PendingIntent.getBroadcast(this, Integer.parseInt(code),
                        intent, PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
                if (pending != null) {
                    alarmManager.cancel(pending);
                    pending.cancel();
                }
            } catch (NumberFormatException ignored) { }
        }
        scheduledCodes.clear();

        Calendar now = Calendar.getInstance();
        ArrayList<DbHelper.Reminder> reminders = database.reminders();
        for (DbHelper.Reminder r : reminders) {
            long delay = nextReminderDelay(r, now);
            if (delay < 0) continue;
            Intent intent = new Intent(this, NotificationReceiver.class);
            intent.putExtra("title", r.title);
            intent.putExtra("text", r.title);
            int requestCode = (int) (System.currentTimeMillis() + r.id);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(this, requestCode, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + delay, pendingIntent);
            scheduledCodes.add(String.valueOf(requestCode));
        }
        preferences.edit().putStringSet(SCHEDULED_NOTIFICATION_CODES, scheduledCodes).apply();
    }

    private long nextReminderDelay(DbHelper.Reminder r, Calendar now) {
        String time = r.time;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{1,2}):(\\d{2})").matcher(time);
        if (!m.find()) return -1;
        int hour = Integer.parseInt(m.group(1));
        int minute = Integer.parseInt(m.group(2));

        Calendar target = (Calendar) now.clone();
        target.set(Calendar.HOUR_OF_DAY, hour);
        target.set(Calendar.MINUTE, minute);
        target.set(Calendar.SECOND, 0);
        target.set(Calendar.MILLISECOND, 0);

        String lower = r.frequency.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("روز") || lower.contains("daily")) {
            if (!target.after(now)) target.add(Calendar.DAY_OF_MONTH, 1);
        } else if (lower.contains("هفت") || lower.contains("weekly")) {
            if (!target.after(now)) target.add(Calendar.DAY_OF_MONTH, 7);
        } else {
            if (!target.after(now)) target.add(Calendar.DAY_OF_MONTH, 1);
        }
        return target.getTimeInMillis() - now.getTimeInMillis();
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
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(20), dp(14), dp(20), dp(14));

        // Version on the left
        TextView versionText = text("نسخه ۰.۲", 10, MUTED);
        header.addView(versionText, wrapMargin(0, 0, 0, 0));

        // Logo/title in the center
        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        titleCol.setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL);
        titleCol.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        TextView title = text("💎  حبیب  |  Habib", 21, TEXT);
        title.setTypeface(null, Typeface.BOLD);
        titleCol.addView(title);
        titleCol.addView(text("محل توسعهٔ عادت‌های من :)", 12, MUTED), margin(0, 3, 0, 0));
        header.addView(titleCol, new LinearLayout.LayoutParams(0, -2, 1));

        // Settings button on the right
        Button settingsBtn = button("⋮", 0xff263244);
        settingsBtn.setTextSize(20);
        settingsBtn.setPadding(dp(8), 0, dp(8), 0);
        settingsBtn.setOnClickListener(v -> {
            currentPage = "profile";
            showApp();
        });
        header.addView(settingsBtn, wrapMargin(0, 0, 0, 0));
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
        root.addView(pageFrame, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout navigation = new LinearLayout(this);
        navigation.setGravity(Gravity.CENTER);
        navigation.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        navigation.setBackgroundColor(CARD);
        navigation.setPadding(dp(8), dp(5), dp(8), dp(5));
        addNav(navigation, "habits", "عادت‌ها", "⌂");
        addNav(navigation, "weekly_schedule", "برنامه هفتگی", "📅");
        addNav(navigation, "reminders", "یادآوری‌ها", "🔔");
        addNav(navigation, "achievements", "دستاوردها", "🏆");
        root.addView(navigation, new LinearLayout.LayoutParams(-1, dp(76)));
        renderPage();
    }

    private void addNav(LinearLayout bar, String page, String label, String icon) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        int iconColor = page.equals(currentPage) ? GREEN : MUTED;
        View glyph;
        if ("habits".equals(page)) {
            ImageView home = new ImageView(this);
            home.setImageResource(R.drawable.ic_home);
            home.setColorFilter(iconColor);
            home.setPadding(dp(9), dp(8), dp(9), dp(8));
            glyph = home;
        } else {
            TextView textGlyph = text(icon, "reminders".equals(page) ? 24 : 22, iconColor);
            textGlyph.setGravity(Gravity.CENTER);
            glyph = textGlyph;
        }
        glyph.setBackground(round(page.equals(currentPage) ? 0xff17372f : 0xff1e293b, 12, 0xff263244));
        item.addView(glyph, new LinearLayout.LayoutParams(dp(42), dp(40)));
        TextView caption = text(label, 10, iconColor);
        caption.setGravity(Gravity.CENTER);
        item.addView(caption);
        item.setOnClickListener(v -> {
            if ("achievements".equals(page)) selectedTrophyIndex = -1;
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
        else if ("achievements".equals(currentPage)) renderAchievements();
        else if ("recovery".equals(currentPage)) renderRecovery();
        else if ("profileEdit".equals(currentPage)) renderProfile(true);
        else if ("weekly_schedule".equals(currentPage)) renderWeeklySchedule();
        else renderProfile(false);
    }

    private void renderHabits() {
        ArrayList<DbHelper.Habit> habits = database.habits(selectedHabitDate);
        int completed = 0;
        for (DbHelper.Habit habit : habits) if (habit.completed) completed++;
        renderMotivationalQuote();
        TextView heading = text(selectedHabitDate.equals(LocalDate.now())
                ? "عادت‌های امروز" : "عادت‌های " + jalaliDateLabel(selectedHabitDate), 20, TEXT);
        heading.setTypeface(null, Typeface.BOLD);
        heading.setOnClickListener(v -> {
            selectedHabitDate = selectedHabitDate.minusDays(1);
            renderPage();
        });
        LinearLayout dateHeading = new LinearLayout(this);
        dateHeading.setGravity(Gravity.CENTER_VERTICAL);
        dateHeading.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        dateHeading.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        Button dateHelp = button("؟", 0xff2563eb);
        dateHelp.setTextSize(18);
        dateHelp.setContentDescription("نمایش جابه‌جایی بین روزها");
        dateHeading.addView(dateHelp, new LinearLayout.LayoutParams(dp(38), dp(38)));
        content.addView(dateHeading);
        content.addView(text(persianDigits(completed) + " از " + persianDigits(habits.size())
                + " عادت انجام شده", 14, GREEN), margin(0, 4, 0, 8));
        LinearLayout dateNavigation = new LinearLayout(this);
        dateNavigation.setGravity(Gravity.CENTER);
        dateNavigation.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        Button previousDate = button("روز قبل", 0xff263244);
        Button nextDate = button("روز بعد", 0xff263244);
        Button todayDate = button("برو به امروز", GREEN);
        dateNavigation.addView(previousDate, new LinearLayout.LayoutParams(0, dp(40), 1));
        dateNavigation.addView(nextDate, new LinearLayout.LayoutParams(0, dp(40), 1));
        dateNavigation.addView(todayDate, new LinearLayout.LayoutParams(0, dp(40), 1));
        dateNavigation.setVisibility(View.GONE);
        content.addView(dateNavigation, margin(0, 0, 0, 12));
        dateHelp.setOnClickListener(v -> dateNavigation.setVisibility(
                dateNavigation.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        nextDate.setEnabled(selectedHabitDate.isBefore(LocalDate.now()));
        previousDate.setOnClickListener(v -> {
            selectedHabitDate = selectedHabitDate.minusDays(1);
            renderPage();
        });
        nextDate.setOnClickListener(v -> {
            if (selectedHabitDate.isBefore(LocalDate.now())) {
                selectedHabitDate = selectedHabitDate.plusDays(1);
                renderPage();
            }
        });
        todayDate.setVisibility(selectedHabitDate.equals(LocalDate.now()) ? View.GONE : View.VISIBLE);
        todayDate.setOnClickListener(v -> {
            selectedHabitDate = LocalDate.now();
            renderPage();
        });

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
                amount.setText(habit.value);
                amount.setSelectAllOnFocus(true);
                amount.setGravity(Gravity.CENTER);
                amount.setPadding(dp(4), 0, dp(4), 0);
                amount.setBackground(round(0xff0b0f17, 8, 0xff334155));
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
            Button check = button("✓", habit.completed ? GREEN : 0xff334155);
            check.setTextSize(23);
            check.setTextColor(habit.completed ? TEXT : MUTED);
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
                    if (amountField != null && !value.trim().isEmpty()
                            && !validAmount(value, habit.min, habit.max)) {
                        amountField.setError("مقدار باید بین " + habit.min + " و " + habit.max + " باشد");
                        return;
                    }
                    if (amountField != null && next && value.trim().isEmpty()) {
                        value = habit.defaultValue == null
                                ? String.valueOf(habit.min) : String.valueOf(habit.defaultValue);
                    }
                    database.saveLog(habit.id, selectedHabitDate, next, false, value);
                    AchievementTracker.refresh(database);
                    renderPage();
                } catch (IllegalArgumentException exception) {
                    if (amountField != null) amountField.setError("مقدار باید عدد باشد");
                }
            });
            Button skip = button(habit.skipped ? "×" : "×", habit.skipped ? 0xffef4444 : 0xff334155);
            skip.setTextSize(20);
            LinearLayout.LayoutParams skipParams = new LinearLayout.LayoutParams(dp(40), dp(40));
            skipParams.setMargins(dp(4), 0, 0, 0);
            actions.addView(skip, skipParams);
            skip.setOnClickListener(v -> {
                database.saveLog(habit.id, selectedHabitDate, false, true, null);
                renderPage();
            });
            Button edit = button("✎", 0xff263244);
            edit.setTextSize(18);
            LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(dp(40), dp(40));
            actions.addView(edit, editParams);
            edit.setOnClickListener(v -> editHabitDialog(habit));
            row.addView(description, new LinearLayout.LayoutParams(0, -2, 1));
            boolean latinTitle = Pattern.compile("\\p{IsLatin}").matcher(habit.title).find();
            row.addView(actions, wrapMargin(0, 0, latinTitle ? 12 : 6, 0));
            card.addView(row);
            if (amountControl != null) {
                LinearLayout amountRow = new LinearLayout(this);
                amountRow.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
                amountRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
                amountRow.addView(amountControl);
                card.addView(amountRow, margin(0, 8, 0, 0));
            }
            if (habit.type.equals("quantitative")) {
                amount.setOnFocusChangeListener((v, focused) -> {
                    if (focused) {
                        check.setBackground(round(0xff334155, 10, 0xff334155));
                        check.setText("✓");
                        check.setTextColor(MUTED);
                    } else {
                        boolean unchanged = amountField.getText().toString().equals(habit.value);
                        check.setBackground(round(unchanged && habit.completed ? GREEN : 0xff334155, 10, 0xff334155));
                        check.setText("✓");
                        check.setTextColor(unchanged && habit.completed ? TEXT : MUTED);
                    }
                });
                amount.addTextChangedListener(new TextWatcher() {
                    @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                    @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                        amountWasEdited[0] = !s.toString().equals(habit.value);
                    }
                    @Override public void afterTextChanged(Editable s) { }
                });
            }
            content.addView(card, margin(0, 0, 0, 10));
        }

        Button add = button("+  افزودن عادت", GREEN);
        content.addView(add, margin(0, 2, 0, 14));
        add.setOnClickListener(v -> addHabitDialog());
        add.setEnabled(habits.size() < 10);
        ArrayList<DbHelper.Habit> deletedHabits = database.deletedHabits();
        if (!deletedHabits.isEmpty()) {
            Button recovery = button("بازیابی عادت‌های حذف‌شده", 0xff263244);
            content.addView(recovery, margin(0, 0, 0, 14));
            recovery.setOnClickListener(v -> {
                currentPage = "recovery";
                renderPage();
            });
        }
        if (!habits.isEmpty()) {
            renderActivityGrid();
            renderHabitChart(habits);
        }
    }

    private String jalaliDateLabel(LocalDate date) {
        Calendar calendar = Calendar.getInstance(new ULocale("fa_IR@calendar=persian"));
        calendar.setTimeInMillis(date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli());
        return persianDigits(calendar.get(Calendar.DAY_OF_MONTH)) + " "
                + persianMonthName(calendar) + " " + persianDigits(calendar.get(Calendar.YEAR));
    }

    private void renderRecovery() {
        TextView heading = text("بازیابی عادت‌ها", 20, TEXT);
        heading.setTypeface(null, Typeface.BOLD);
        content.addView(heading, margin(0, 0, 0, 12));
        ArrayList<DbHelper.Habit> deleted = database.deletedHabits();
        if (deleted.isEmpty()) {
            content.addView(text("عادت حذف‌شده‌ای برای بازیابی وجود ندارد.", 14, MUTED));
            return;
        }
        for (DbHelper.Habit habit : deleted) {
            LinearLayout card = card();
            TextView title = text(habit.emoji + "  " + habit.title, 16, TEXT);
            card.addView(title);
            Button restore = button("بازیابی", GREEN);
            card.addView(restore, margin(0, 8, 0, 0));
            restore.setOnClickListener(v -> {
                database.restoreHabit(habit.id);
                renderPage();
            });
            content.addView(card, margin(0, 0, 0, 10));
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
        TextView quote = new SwipeableQuoteTextView(this);
        quote.setText(MOTIVATIONAL_QUOTES[quoteIndex]);
        quote.setTextColor(TEXT);
        quote.setTextSize(14);
        quote.setGravity(Gravity.CENTER);
        quote.setPadding(dp(2), dp(10), dp(2), dp(8));
        quote.setMaxLines(4);
        quote.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        quoteRow.addView(quote);
        quoteRow.addView(next, new LinearLayout.LayoutParams(dp(40), dp(40)));
        quoteCard.addView(quoteRow);
        previous.setOnClickListener(v -> moveQuote(-1));
        next.setOnClickListener(v -> moveQuote(1));
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
        FrameLayout chartFrame = new FrameLayout(this);
        chartCard.addView(chartFrame, new LinearLayout.LayoutParams(-1, dp(210)));
        content.addView(chartCard, margin(0, 0, 0, 12));
        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        stats.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(stats, margin(0, 0, 0, 14));
        showHabitChartAndStats(habits.get(selectedPosition), chartFrame, stats);
        selector.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                DbHelper.Habit selected = habits.get(position);
                selectedChartHabitId = selected.id;
                showHabitChartAndStats(selected, chartFrame, stats);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });
    }

    private void showHabitChartAndStats(DbHelper.Habit habit, FrameLayout chartFrame, LinearLayout stats) {
        chartFrame.removeAllViews();
        stats.removeAllViews();
        boolean quantitative = "quantitative".equals(habit.type);
        if (quantitative) {
            ArrayList<Integer> values = database.chartValues(habit.id);
            String[] dateLabels = new String[values.size()];
            LocalDate firstDate = LocalDate.now().minusDays(values.size() - 1L);
            for (int i = 0; i < dateLabels.length; i++) {
                Calendar date = Calendar.getInstance(new ULocale("fa_IR@calendar=persian"));
                date.setTimeInMillis(firstDate.plusDays(i).atStartOfDay(ZoneId.systemDefault())
                        .toInstant().toEpochMilli());
                dateLabels[i] = persianDigits(date.get(Calendar.DAY_OF_MONTH)) + "/"
                        + persianDigits(date.get(Calendar.MONTH) + 1);
            }
            ChartView chart = new ChartView(this, values, dateLabels, habit.min, habit.max);
            chart.setBackgroundColor(CARD);
            chartFrame.addView(chart, new FrameLayout.LayoutParams(-1, -1));
        }

        long[] completionStats = database.habitCompletionStats(habit.id);
        addStatCard(stats, "تعداد ثبت موفق", persianDigits((int) completionStats[0]));
        addStatCard(stats, quantitative ? "رکورد تداوم" : "رکورد روزهای پشت سر هم",
                persianDigits((int) completionStats[1]) + " روز");
        if (quantitative) {
            addStatCard(stats, "مجموع عادت",
                    persianDigits((int) database.totalHabitValue(habit.id)) + " " + habit.unit);
        }
    }

    private void addStatCard(LinearLayout container, String label, String value) {
        LinearLayout stat = card();
        TextView amount = text(value, 18, TEXT);
        amount.setTypeface(null, Typeface.BOLD);
        stat.addView(amount);
        stat.addView(text(label, 12, MUTED), margin(0, 3, 0, 0));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        params.setMargins(dp(3), 0, dp(3), 0);
        container.addView(stat, params);
    }

    private void renderReminders() {
        renderCalendar();
        TextView heading = text("یادآوری‌ها", 20, TEXT);
        heading.setTypeface(null, Typeface.BOLD);
        content.addView(heading, margin(0, 0, 0, 12));
        String[] filters = {"روزانه", "هفتگی", "ماهانه", "سالانه", "همه"};
        LinearLayout filterOptions = new LinearLayout(this);
        filterOptions.setGravity(Gravity.CENTER_VERTICAL);
        filterOptions.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        for (int i = 0; i < filters.length; i++) {
            final int selected = i == filters.length - 1 ? 0 : i + 1;
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
        ArrayList<DbHelper.Reminder> reminders = database.reminders();
        int displayedReminders = 0;
        for (DbHelper.Reminder reminder : reminders) {
            if (reminderFilter != 0 && !matchesFrequency(reminder.frequency, reminderFilter)) continue;
            displayedReminders++;
            LinearLayout reminderCard = card();
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView icon = text(reminderIcon(reminder.frequency), 34, GREEN);
            row.addView(icon, wrapMargin(0, 0, 10, 0));
            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            info.addView(text(reminder.title, 16, TEXT));
            String noticeText = reminder.advanceNoticeValue == 0 ? "" : " • "
                    + persianDigits(reminder.advanceNoticeValue)
                    + (matchesFrequency(reminder.frequency, 1) ? " ساعت پیش‌آگاهی" : " روز پیش‌آگاهی");
            info.addView(text(reminder.frequency + (reminder.time.isEmpty() ? "" : " • " + reminder.time)
                    + noticeText, 12, MUTED), margin(0, 4, 0, 0));
            if (reminder.description != null && !reminder.description.isEmpty()) {
                info.addView(text(reminder.description, 12, MUTED), margin(0, 4, 0, 0));
            }
            row.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
            reminderCard.addView(row);
            reminderCard.setOnClickListener(v -> reminderDialog(reminder));
            content.addView(reminderCard, margin(0, 0, 0, 10));
        }
        if (displayedReminders == 0) content.addView(text("یادآوری‌ای در این بازه ثبت نشده است.", 14, MUTED));
        Button addReminder = button("+  افزودن یادآوری جدید", GREEN);
        content.addView(addReminder, margin(0, 8, 0, 16));
        addReminder.setOnClickListener(v -> addReminderDialog());
    }

    private boolean matchesFrequency(String frequency, int filter) {
        String lower = frequency.toLowerCase(Locale.ROOT);
        if (filter == 1) return frequency.contains("روز") || lower.contains("daily");
        if (filter == 2) return frequency.contains("هفت") || lower.contains("weekly");
        if (filter == 3) return frequency.contains("ماه") || lower.contains("monthly");
        return frequency.contains("سال") || lower.contains("yearly");
    }

    private String reminderIcon(String frequency) {
        return "🔔";
    }

    private String buildReminderSchedule(
            int frequency, String time, boolean[] selectedDays, String[] weekdays,
            String monthDayText, String yearlyDate
    ) {
        String normalizedTime = toAsciiDigits(time);
        if (!Pattern.matches("(?:[01]?\\d|2[0-3]):[0-5]\\d", normalizedTime)) {
            showMessage("زمان را به شکل ساعت و دقیقه وارد کن.");
            return null;
        }
        String timeSuffix = " - ساعت " + normalizedTime;
        if (frequency == 0) return "ساعت " + normalizedTime;
        if (frequency == 1) {
            ArrayList<String> days = new ArrayList<>();
            for (int i = 0; i < weekdays.length; i++) {
                if (selectedDays[i]) days.add(weekdays[i]);
            }
            if (days.isEmpty() || days.size() == weekdays.length) {
                showMessage("حداقل یک و حداکثر شش روز هفته را انتخاب کن.");
                return null;
            }
            return "روزهای " + TextUtils.join("، ", days) + timeSuffix;
        }
        if (frequency == 2) {
            int day = parse(toAsciiDigits(monthDayText), -1);
            if (day < 1 || day > 31) {
                showMessage("روز ماه باید بین ۱ تا ۳۱ باشد.");
                return null;
            }
            return "روز " + persianDigits(day) + " ماه" + timeSuffix;
        }
        int[] parts = parseBirthDate(yearlyDate);
        if (parts == null) {
            showMessage("تاریخ شمسی واردشده معتبر نیست.");
            return null;
        }
        Calendar date = Calendar.getInstance(new ULocale("fa_IR@calendar=persian"));
        date.clear();
        date.set(Calendar.YEAR, parts[0]);
        date.set(Calendar.MONTH, parts[1] - 1);
        date.set(Calendar.DAY_OF_MONTH, parts[2]);
        if (date.get(Calendar.DAY_OF_MONTH) != parts[2]) {
            showMessage("تاریخ شمسی واردشده معتبر نیست.");
            return null;
        }
        return persianDigits(parts[2]) + " " + persianMonthName(date) + " "
                + persianDigits(parts[0]) + timeSuffix;
    }

    private String parseReminderTime(String value) {
        if (value == null) return "09:00";
        java.util.regex.Matcher matcher = Pattern.compile("(?<!\\d)([01]?\\d|2[0-3]):([0-5]\\d)(?!\\d)")
                .matcher(toAsciiDigits(value));
        return matcher.find() ? matcher.group() : "09:00";
    }

    private String parseReminderMonthDay(String value) {
        if (value == null) return null;
        java.util.regex.Matcher matcher = Pattern.compile("روز\\s*(\\d{1,2})").matcher(toAsciiDigits(value));
        return matcher.find() ? matcher.group(1) : null;
    }

    private String[] parseReminderDate(String value) {
        if (value == null) return null;
        String normalized = toAsciiDigits(value);
        String[] months = {"فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
                "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"};
        int month = -1;
        for (int i = 0; i < months.length; i++) {
            if (normalized.contains(months[i])) {
                month = i + 1;
                break;
            }
        }
        java.util.regex.Matcher dayMatcher = Pattern.compile("(?<!\\d)(\\d{1,2})\\s+").matcher(normalized);
        if (month < 0 || !dayMatcher.find()) return null;
        java.util.regex.Matcher yearMatcher = Pattern.compile("(?<!\\d)(13\\d{2}|14\\d{2}|15\\d{2})(?!\\d)")
                .matcher(normalized);
        Calendar now = Calendar.getInstance(new ULocale("fa_IR@calendar=persian"));
        int year = yearMatcher.find() ? parse(yearMatcher.group(), now.get(Calendar.YEAR)) : now.get(Calendar.YEAR);
        int day = parse(dayMatcher.group(1), -1);
        if (day < 1 || day > 31) return null;
        return new String[]{String.format(Locale.ROOT, "%04d-%02d-%02d", year, month, day),
                String.valueOf(year), String.valueOf(month), String.valueOf(day)};
    }

    private int reminderColor(String frequency) {
        if (matchesFrequency(frequency, 1)) return 0xff38bdf8;
        if (matchesFrequency(frequency, 2)) return 0xff93c5fd;
        if (matchesFrequency(frequency, 3)) return 0xff2563eb;
        return 0xffa855f7;
    }

    private ArrayList<DbHelper.Reminder> remindersForDate(Calendar date) {
        ArrayList<DbHelper.Reminder> matches = new ArrayList<>();
        int jalaliDay = date.get(Calendar.DAY_OF_MONTH);
        int jalaliMonth = date.get(Calendar.MONTH) + 1;
        int weekdayIndex = date.get(Calendar.DAY_OF_WEEK) % 7;
        String[] weekdays = {"شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنج‌شنبه", "جمعه"};
        String weekday = weekdays[weekdayIndex];
        for (DbHelper.Reminder reminder : database.reminders()) {
            if (matchesFrequency(reminder.frequency, 1)) {
                continue;
            } else if (matchesFrequency(reminder.frequency, 2)) {
                if (reminder.time.contains(weekday)) matches.add(reminder);
            } else if (matchesFrequency(reminder.frequency, 3)) {
                if (parseReminderMonthDay(reminder.time) != null
                        && parse(parseReminderMonthDay(reminder.time), -1) == jalaliDay) matches.add(reminder);
            } else {
                String[] annualDate = parseReminderDate(reminder.time);
                if (annualDate != null && parse(annualDate[2], -1) == jalaliMonth
                        && parse(annualDate[3], -1) == jalaliDay) matches.add(reminder);
            }
        }
        return matches;
    }

    private void editCalendarEventDialog(DbHelper.CalendarEvent event) {
        LinearLayout form = form();
        EditText title = field("عنوان رویداد", event.title, InputType.TYPE_CLASS_TEXT);
        form.addView(title);
        form.addView(text("رنگ رویداد", 12, MUTED), margin(0, 10, 0, 4));
        LinearLayout colors = new LinearLayout(this);
        colors.setGravity(Gravity.CENTER);
        final int[] selectedColor = {event.color};
        ArrayList<View> dots = new ArrayList<>();
        for (int color : EVENT_COLORS) {
            View dot = new View(this);
            dot.setBackground(round(color, 50, color == selectedColor[0] ? TEXT : color));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(34), dp(34));
            params.setMargins(dp(6), dp(4), dp(6), dp(4));
            colors.addView(dot, params);
            dots.add(dot);
            dot.setOnClickListener(v -> {
                selectedColor[0] = color;
                for (int i = 0; i < dots.size(); i++) {
                    int dotColor = EVENT_COLORS[i];
                    dots.get(i).setBackground(round(dotColor, 50,
                            dotColor == selectedColor[0] ? TEXT : dotColor));
                }
            });
        }
        form.addView(colors);
        new AlertDialog.Builder(this).setTitle("ویرایش رویداد")
                .setView(form)
                .setNegativeButton("انصراف", null)
                .setPositiveButton("ذخیره", (dialog, which) -> {
                    String value = title.getText().toString().trim();
                    if (value.isEmpty() || value.length() > 255) {
                        showMessage("عنوان رویداد باید بین ۱ تا ۲۵۵ نویسه باشد.");
                        return;
                    }
                    database.updateCalendarEvent(event.id, value, selectedColor[0]);
                    renderPage();
                })
                .show();
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
                ArrayList<DbHelper.Reminder> dayReminders = remindersForDate(date);
                int dotCount = 0;
                for (int i = 0; i < Math.min(events.size(), 4); i++) {
                    if (dotCount >= 4) break;
                    View dot = new View(this);
                    dot.setBackground(round(events.get(i).color, 50, events.get(i).color));
                    LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(5), dp(5));
                    dotParams.setMargins(dp(1), 0, dp(1), dp(2));
                    eventDots.addView(dot, dotParams);
                    dotCount++;
                }
                for (DbHelper.Reminder reminder : dayReminders) {
                    if (dotCount >= 4) break;
                    int color = reminderColor(reminder.frequency);
                    View dot = new View(this);
                    dot.setBackground(round(color, 50, color));
                    LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(5), dp(5));
                    dotParams.setMargins(dp(1), 0, dp(1), dp(2));
                    eventDots.addView(dot, dotParams);
                    dotCount++;
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
                Button edit = button("ویرایش", 0xff263244);
                edit.setTextSize(10);
                row.addView(edit, new LinearLayout.LayoutParams(dp(64), dp(36)));
                row.addView(delete, new LinearLayout.LayoutParams(dp(54), dp(36)));
                eventTitle.setOnClickListener(v -> editCalendarEventDialog(event));
                edit.setOnClickListener(v -> editCalendarEventDialog(event));
                delete.setOnClickListener(v -> {
                    database.deleteCalendarEvent(event.id);
                    renderPage();
                });
                dayCard.addView(row, margin(0, 8, 0, 0));
            }
        }
        TextView remindersTitle = text("یادآوری‌های این روز", 14, TEXT);
        remindersTitle.setTypeface(null, Typeface.BOLD);
        dayCard.addView(remindersTitle, margin(0, 12, 0, 4));
        ArrayList<DbHelper.Reminder> dayReminders = remindersForDate(date);
        if (dayReminders.isEmpty()) {
            dayCard.addView(text("یادآوری‌ای برای این روز نیست.", 12, MUTED));
        } else {
            for (DbHelper.Reminder reminder : dayReminders) {
                String advanceText = reminder.advanceNoticeValue == 0 ? "" : " • "
                        + persianDigits(reminder.advanceNoticeValue)
                        + (matchesFrequency(reminder.frequency, 1) ? " ساعت پیش‌آگاهی" : " روز پیش‌آگاهی");
                LinearLayout reminderRow = new LinearLayout(this);
                reminderRow.setGravity(Gravity.CENTER_VERTICAL);
                reminderRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
                View reminderDot = new View(this);
                int reminderColor = reminderColor(reminder.frequency);
                reminderDot.setBackground(round(reminderColor, 50, reminderColor));
                reminderRow.addView(reminderDot, new LinearLayout.LayoutParams(dp(10), dp(10)));
                TextView reminderText = text(reminderIcon(reminder.frequency) + "  " + reminder.title
                        + " • " + reminder.time + advanceText, 13, reminderColor);
                reminderRow.addView(reminderText, wrapMargin(0, 0, 8, 0));
                dayCard.addView(reminderRow, margin(0, 8, 0, 0));
                if (reminder.description != null && !reminder.description.isEmpty()) {
                    dayCard.addView(text(reminder.description, 12, MUTED), margin(0, 2, 0, 0));
                }
            }
        }
        Button addEvent = button("افزودن رویداد", GREEN);
        dayCard.addView(addEvent, margin(0, 12, 0, 0));
        addEvent.setOnClickListener(v -> addCalendarEventDialog(dateKey));
        content.addView(dayCard, margin(0, 0, 0, 14));
    }

    private void addCalendarEventDialog(String dateKey) {
        LinearLayout eventForm = form();
        EditText eventTitle = field("عنوان رویداد", "", InputType.TYPE_CLASS_TEXT);
        eventForm.addView(eventTitle);
        eventForm.addView(text("رنگ رویداد", 12, MUTED), margin(0, 10, 0, 4));
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
                    colorDots.get(i).setBackground(round(dotColor, 50,
                            dotColor == selectedColor[0] ? TEXT : dotColor));
                }
            });
        }
        eventForm.addView(colorPicker);
        new AlertDialog.Builder(this).setTitle("افزودن رویداد")
                .setView(eventForm)
                .setNegativeButton("انصراف", null)
                .setPositiveButton("ثبت", (dialog, which) -> {
                    String value = eventTitle.getText().toString().trim();
                    if (value.isEmpty() || value.length() > 255) {
                        showMessage("عنوان رویداد را وارد کن.");
                        return;
                    }
                    database.addCalendarEvent(dateKey, value, selectedColor[0]);
                    renderPage();
                })
                .show();
    }

    private String calendarDateKey(Calendar date) {
        return Instant.ofEpochMilli(date.getTimeInMillis()).atZone(ZoneId.systemDefault()).toLocalDate().toString();
    }

    private void renderAchievements() {
        AchievementTracker.Summary summary = AchievementTracker.refresh(database);
        TextView heading = text("دستاوردها", 21, TEXT);
        heading.setTypeface(null, Typeface.BOLD);
        content.addView(heading, margin(0, 0, 0, 12));
        if (selectedTrophyIndex < 0 || selectedTrophyIndex >= summary.trophies.size()) {
            selectedTrophyIndex = summary.currentTrophyIndex;
        }

        LinearLayout trophyCard = card();
        TextView trophyHeading = text("کاپ حبیب", 17, TEXT);
        trophyHeading.setTypeface(null, Typeface.BOLD);
        trophyHeading.setGravity(Gravity.CENTER);
        trophyCard.addView(trophyHeading);
        LinearLayout trophyRow = new LinearLayout(this);
        trophyRow.setGravity(Gravity.CENTER);
        trophyRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        Button previous = button("‹", 0xff263244);
        Button next = button("›", 0xff263244);
        TrophyView trophyImage = new TrophyView(this);
        trophyRow.addView(previous, new LinearLayout.LayoutParams(dp(42), dp(42)));
        trophyRow.addView(trophyImage, new LinearLayout.LayoutParams(dp(138), dp(150)));
        trophyRow.addView(next, new LinearLayout.LayoutParams(dp(42), dp(42)));
        trophyCard.addView(trophyRow, margin(0, 8, 0, 0));
        TextView selectedTitle = text("", 17, TEXT);
        selectedTitle.setGravity(Gravity.CENTER);
        trophyCard.addView(selectedTitle);
        TextView trophyStatus = text("", 13, MUTED);
        trophyStatus.setGravity(Gravity.CENTER);
        trophyCard.addView(trophyStatus, margin(0, 4, 0, 0));
        LinearLayout progressTrack = progressBar(0, 0xff3b82f6, dp(7));
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(-1, dp(7));
        progressParams.setMargins(0, dp(10), 0, dp(4));
        trophyCard.addView(progressTrack, progressParams);
        previous.setOnClickListener(v -> {
            selectedTrophyIndex = Math.max(0, selectedTrophyIndex - 1);
            renderPage();
        });
        next.setOnClickListener(v -> {
            selectedTrophyIndex = Math.min(summary.trophies.size() - 1, selectedTrophyIndex + 1);
            renderPage();
        });
        previous.setEnabled(selectedTrophyIndex > 0);
        next.setEnabled(selectedTrophyIndex < summary.trophies.size() - 1);
        AchievementTracker.Trophy selected = summary.trophies.get(selectedTrophyIndex);
        trophyImage.setLocked(!selected.earned);
        selectedTitle.setText(selected.title);
        trophyStatus.setText(selected.earned ? "این کاپ را گرفته‌ای" :
                "برای رسیدن به این کاپ " + persianDigits(selected.xpNeeded) + " XP دیگر نیاز داری");
        trophyStatus.setTextColor(selected.earned ? GREEN : 0xff60a5fa);
        setProgress(progressTrack, selected.progressPercent, 0xff3b82f6);
        trophyCard.addView(text("XP کل: " + persianDigits(summary.totalXp)
                + "  •  کاپ فعلی: " + summary.trophies.get(summary.currentTrophyIndex).title,
                13, MUTED), margin(0, 6, 0, 0));
        content.addView(trophyCard, margin(0, 0, 0, 12));

        LinearLayout summaryCard = card();
        summaryCard.setOrientation(LinearLayout.HORIZONTAL);
        addAchievementStat(summaryCard, summary.completedLogCount, "ثبت موفق");
        addAchievementStat(summaryCard, summary.activeDayCount, "روز فعال");
        addAchievementStat(summaryCard, summary.items.size(), "دستاورد");
        content.addView(summaryCard, margin(0, 0, 0, 14));

        TextView listHeading = text("مسیر دستاوردها", 17, TEXT);
        listHeading.setTypeface(null, Typeface.BOLD);
        content.addView(listHeading, margin(0, 0, 0, 8));
        for (AchievementTracker.Item item : summary.items) {
            LinearLayout achievementCard = card();
            TextView title = text((item.unlocked ? "🏅 " : "🔒 ") + item.title, 14,
                    item.unlocked ? TEXT : MUTED);
            title.setTypeface(null, Typeface.BOLD);
            achievementCard.addView(title);
            achievementCard.addView(text(item.description, 12, MUTED), margin(0, 4, 0, 0));
            LinearLayout progress = progressBar(item.progressPercent,
                    item.unlocked ? 0xfff59e0b : 0xff3b82f6, dp(5));
            achievementCard.addView(progress, margin(0, 8, 0, 0));
            achievementCard.addView(text(persianDigits(item.progress) + " از "
                            + persianDigits(item.target) + "  •  +" + persianDigits(item.xpReward) + " XP",
                    12, item.unlocked ? 0xfffbbf24 : MUTED), margin(0, 5, 0, 0));
            content.addView(achievementCard, margin(0, 0, 0, 8));
        }
    }

    private void addAchievementStat(LinearLayout container, int amount, String label) {
        LinearLayout stat = new LinearLayout(this);
        stat.setOrientation(LinearLayout.VERTICAL);
        stat.setGravity(Gravity.CENTER);
        stat.addView(text(persianDigits(amount), 18, TEXT));
        stat.addView(text(label, 10, MUTED));
        container.addView(stat, new LinearLayout.LayoutParams(0, -2, 1));
    }

    private LinearLayout progressBar(int percent, int color, int height) {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        bar.setBackground(round(0xff263244, 20, 0xff263244));
        setProgress(bar, percent, color);
        bar.setMinimumHeight(height);
        return bar;
    }

    private void setProgress(LinearLayout bar, int percent, int color) {
        bar.removeAllViews();
        int bounded = Math.max(0, Math.min(100, percent));
        if (bounded > 0) {
            View fill = new View(this);
            fill.setBackground(round(color, 20, color));
            bar.addView(fill, new LinearLayout.LayoutParams(0, -1, bounded));
        }
        if (bounded < 100) {
            View remainder = new View(this);
            bar.addView(remainder, new LinearLayout.LayoutParams(0, -1, 100 - bounded));
        }
    }

    private void renderHabitReorder() {
        content.removeAllViews();
        TextView heading = text("ترتيب عادتها", 20, TEXT);
        heading.setTypeface(null, Typeface.BOLD);
        content.addView(heading, margin(0, 0, 0, 12));

        ArrayList<DbHelper.Habit> habits = database.habits(LocalDate.now());
        if (habits.isEmpty()) {
            content.addView(text("عادت‌ای برای مرتب‌سازی وجود ندارد.", 14, MUTED), margin(0, 8, 0, 8));
            Button back = button("بازگشت به تنظیمات", 0xff263244);
            back.setOnClickListener(v -> { currentPage = "profile"; showApp(); });
            content.addView(back, margin(0, 8, 0, 0));
            return;
        }

        for (int i = 0; i < habits.size(); i++) {
            final int ii = i;
            DbHelper.Habit habit = habits.get(i);
            LinearLayout row = card();
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

            TextView num = text(String.valueOf(i + 1), 16, MUTED);
            num.setTypeface(null, Typeface.BOLD);
            row.addView(num, new LinearLayout.LayoutParams(dp(32), -2));

            TextView title = text(habit.emoji + "  " + habit.title, 15, TEXT);
            row.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

            Button moveUp = button("↑", 0xff263244);
            moveUp.setTextSize(14);
            moveUp.setEnabled(i > 0);
            moveUp.setOnClickListener(v -> {
                database.swapHabitOrder(habit.id, habits.get(ii - 1).id);
                renderHabitReorder();
            });
            row.addView(moveUp, new LinearLayout.LayoutParams(dp(40), dp(36)));

            Button moveDown = button("↓", 0xff263244);
            moveDown.setTextSize(14);
            moveDown.setEnabled(i < habits.size() - 1);
            moveDown.setOnClickListener(v -> {
                database.swapHabitOrder(habit.id, habits.get(ii + 1).id);
                renderHabitReorder();
            });
            row.addView(moveDown, new LinearLayout.LayoutParams(dp(40), dp(36)));

            content.addView(row, margin(0, 0, 0, 8));
        }

        Button back = button("بازگشت به تنظیمات", GREEN);
        back.setOnClickListener(v -> { currentPage = "profile"; showApp(); });
        content.addView(back, margin(0, 12, 0, 0));
    }

    private void renderProfile(boolean editing) {
        String[] profile = database.profile();
        TextView heading = text("تنظیمات", 20, TEXT);
        heading.setTypeface(null, Typeface.BOLD);
        content.addView(heading, margin(0, 0, 0, 12));
        if (!editing) {
            LinearLayout summary = card();
            LinearLayout accountRow = new LinearLayout(this);
            accountRow.setGravity(Gravity.CENTER_VERTICAL);
            accountRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            FrameLayout accountAvatar = new FrameLayout(this);
            accountAvatar.setBackground(round(0xff0b0f17, 100, GREEN));
            accountAvatar.setClipToOutline(true);
            LinearLayout.LayoutParams accountAvatarParams = new LinearLayout.LayoutParams(dp(58), dp(58));
            accountAvatarParams.setMargins(0, 0, 0, 0);
            boolean accountImageLoaded = false;
            if (!profile[4].isEmpty()) {
                try {
                    ImageView accountImage = new ImageView(this);
                    accountImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    accountImage.setImageURI(Uri.parse(profile[4]));
                    accountAvatar.addView(accountImage, new FrameLayout.LayoutParams(-1, -1));
                    accountImageLoaded = accountImage.getDrawable() != null;
                } catch (RuntimeException exception) {
                    accountImageLoaded = false;
                }
            }
            if (!accountImageLoaded) {
                TextView placeholder = text("👤", 30, TEXT);
                placeholder.setGravity(Gravity.CENTER);
                accountAvatar.addView(placeholder, new FrameLayout.LayoutParams(-1, -1));
            }
            accountRow.addView(accountAvatar, accountAvatarParams);
            LinearLayout accountDetails = new LinearLayout(this);
            accountDetails.setOrientation(LinearLayout.VERTICAL);
            TextView accountName = text("نام کاربری: " + profile[0], 17, TEXT);
            accountName.setTypeface(null, Typeface.BOLD);
            accountDetails.addView(accountName);
            accountDetails.addView(text(profile[2], 13, MUTED), margin(0, 5, 0, 0));
            LinearLayout.LayoutParams accountDetailsParams = new LinearLayout.LayoutParams(0, -2, 1);
            accountDetailsParams.setMargins(0, 0, dp(12), 0);
            accountRow.addView(accountDetails, accountDetailsParams);
            summary.addView(accountRow);
            int[] birthParts = parseBirthDate(profile[1]);
            summary.addView(text(birthParts == null ? "تاریخ تولد ثبت نشده"
                    : "سن: " + persianDigits(calculateAge(birthParts)) + " سال", 13, MUTED),
                    margin(0, 5, 0, 10));
            DbHelper.WeeklySchedule schedule = database.getWeeklySchedule();
            if (schedule != null) {
                summary.addView(text("خواب: " + schedule.sleepTime + "  •  بیداری: " + schedule.wakeTime, 13, MUTED),
                        margin(0, 5, 0, 10));
            }
            Button edit = button("✏️  تغییر اطلاعات کاربری", 0xff263244);
            summary.addView(edit);
            edit.setOnClickListener(v -> {
                currentPage = "profileEdit";
                renderPage();
            });
            content.addView(summary, margin(0, 0, 0, 12));
            String[][] options = {
                    {"ⓘ", "درباره ما"},
                    {"↓", "تهیه فایل پشتیبان"},
                    {"↑", "بازگردانی اطلاعات"},
                    {"🔄", "ترتيب عادتها"},
                    {"✉", "انتقادات و پیشنهادات"}
            };
            for (String[] option : options) {
                LinearLayout item = card();
                item.setOrientation(LinearLayout.HORIZONTAL);
                item.setGravity(Gravity.CENTER_VERTICAL);
                item.addView(text(option[0], 18, GREEN), wrapMargin(0, 0, 12, 0));
                item.addView(text(option[1], 15, TEXT));
                content.addView(item, margin(0, 0, 0, 8));
                final String optLabel = option[1];
                item.setOnClickListener(v -> {
                    if ("ترتيب عادتها".equals(optLabel)) {
                        renderHabitReorder();
                    }
                });
            }
            Button deleteData = button("حذف اطلاعات", 0xff7f1d1d);
            deleteData.setTextSize(15);
            content.addView(deleteData, margin(0, 4, 0, 0));
            deleteData.setOnClickListener(v -> confirmDeleteAllData());
            return;
        }
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
            String nameValue = name.getText().toString().trim();
            if (nameValue.isEmpty() || nameValue.length() > 100) {
                name.setError("نام کاربری را وارد کن.");
                return;
            }
            String emailValue = email.getText().toString().trim();
            if (emailValue.isEmpty() || emailValue.length() > 254
                    || !android.util.Patterns.EMAIL_ADDRESS.matcher(emailValue).matches()) {
                email.setError("ایمیل معتبر وارد کن");
                return;
            }
            String phoneValue = toAsciiDigits(phone.getText().toString().trim()).replaceAll("[\\s()-]", "");
            if (!phoneValue.isEmpty() && !Pattern.matches("^\\+?[0-9]{7,15}$", phoneValue)) {
                phone.setError("شماره تلفن باید ۷ تا ۱۵ رقم باشد.");
                return;
            }
            String photo = pendingPhoto == null ? profile[4] : pendingPhoto.equals(Uri.EMPTY) ? "" : pendingPhoto.toString();
            database.saveProfile(nameValue, selectedBirthDate[0], emailValue, phoneValue, photo);
            if (parseBirthDate(selectedBirthDate[0]) == null) {
                BirthdayReminderScheduler.cancel(this);
            } else {
                BirthdayReminderScheduler.schedule(this, selectedBirthDate[0]);
                requestBirthdayNotificationPermission();
            }
            pendingPhoto = null;
            currentPage = "profile";
            renderPage();
            showMessage("اطلاعات روی همین گوشی ذخیره شد.");
        });
        content.addView(card);
        content.addView(text("نسخهٔ مستقل آفلاین؛ اطلاعات این اپ با نسخهٔ وب همگام نمی‌شود.", 12, MUTED), margin(0, 12, 0, 0));
    }

    private void confirmDeleteAllData() {
        new AlertDialog.Builder(this)
                .setTitle("حذف همهٔ اطلاعات؟")
                .setMessage("این کار همهٔ عادت‌ها و ثبت‌هایشان، یادآوری‌ها، رویدادهای تقویم، دستاوردها و اطلاعات پروفایل این گوشی را پاک می‌کند و قابل بازگشت نیست. پس از حذف، عادت‌ها و یادآوری‌های اولیهٔ حبیب دوباره ساخته می‌شوند.")
                .setNegativeButton("انصراف", null)
                .setPositiveButton("حذف همهٔ اطلاعات", (dialog, which) -> {
                    try {
                        database.resetAllData();
                        BirthdayReminderScheduler.cancel(this);
                        cancelScheduledNotifications();
                        pendingPhoto = null;
                        selectedHabitDate = LocalDate.now();
                        selectedCalendarDateKey = "";
                        calendarMonthOffset = 0;
                        selectedChartHabitId = -1;
                        selectedTrophyIndex = -1;
                        reminderFilter = 1;
                        currentPage = "habits";
                        showApp();
                        showMessage("اطلاعات حذف شد و حبیب به نسخهٔ اولیه برگشت.");
                    } catch (SQLiteException exception) {
                        android.util.Log.e("Habib", "Failed to reset local app data", exception);
                        showMessage("حذف اطلاعات انجام نشد. دوباره تلاش کن.");
                    }
                })
                .show()
                .getButton(AlertDialog.BUTTON_POSITIVE)
                .setTextColor(0xffef4444);
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
        if (database.activeHabitCount() >= 10) {
            showMessage("حداکثر ۱۰ عادت فعال می‌توانی داشته باشی.");
            return;
        }
        LinearLayout form = form();
        EditText title = field("نام عادت", "", InputType.TYPE_CLASS_TEXT);
        EditText emoji = field("ایموجی", "📌", InputType.TYPE_CLASS_TEXT);
        Spinner type = spinner(new String[]{"تیک‌زدنی", "عددی"});
        EditText min = field("حداقل مقدار (برای عددی)", "1", InputType.TYPE_CLASS_NUMBER);
        EditText max = field("حداکثر مقدار (برای عددی)", "100", InputType.TYPE_CLASS_NUMBER);
        EditText unit = field("واحد", "", InputType.TYPE_CLASS_TEXT);
        EditText defaultValue = field("مقدار پیش‌فرض (اختیاری)", "", InputType.TYPE_CLASS_NUMBER);
        EditText note = field("توضیحات کوتاه (اختیاری)", "", InputType.TYPE_CLASS_TEXT);
        form.addView(title); form.addView(emoji); form.addView(type); form.addView(min); form.addView(max); form.addView(unit); form.addView(defaultValue); form.addView(note);
        new AlertDialog.Builder(this).setTitle("عادت جدید").setView(form)
                .setNegativeButton("انصراف", null)
                .setPositiveButton("افزودن", (dialog, which) -> {
                    String name = title.getText().toString().trim();
                    if (name.isEmpty()) { showMessage("نام عادت را وارد کن."); return; }
                    boolean numeric = type.getSelectedItemPosition() == 1;
                    int low = parse(min.getText().toString(), 1);
                    int high = parse(max.getText().toString(), 100);
                    Integer defaultAmount = defaultValue.getText().toString().trim().isEmpty()
                            ? null : parseNullable(defaultValue.getText().toString());
                    if ((defaultAmount == null && !defaultValue.getText().toString().trim().isEmpty())
                            || low > high || (defaultAmount != null && (defaultAmount < low || defaultAmount > high))) {
                        showMessage("حداقل، حداکثر و مقدار پیش‌فرض را بررسی کن.");
                        return;
                    }
                    database.addHabit(name, emoji.getText().toString().trim().isEmpty() ? "📌" : emoji.getText().toString(), numeric ? "quantitative" : "boolean", low, high, unit.getText().toString(), note.getText().toString(), defaultAmount);
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
        EditText defaultValue = field("مقدار پیش‌فرض (اختیاری)", habit.defaultValue == null ? "" : String.valueOf(habit.defaultValue), InputType.TYPE_CLASS_NUMBER);
        EditText note = field("توضیحات", habit.description == null ? "" : habit.description, InputType.TYPE_CLASS_TEXT);
        form.addView(title); form.addView(emoji); form.addView(type); form.addView(min); form.addView(max); form.addView(unit); form.addView(defaultValue); form.addView(note);
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
                    int high = parse(max.getText().toString(), 100);
                    Integer defaultAmount = defaultValue.getText().toString().trim().isEmpty()
                            ? null : parseNullable(defaultValue.getText().toString());
                    if ((defaultAmount == null && !defaultValue.getText().toString().trim().isEmpty())
                            || low > high || (defaultAmount != null && (defaultAmount < low || defaultAmount > high))) {
                        showMessage("حداقل، حداکثر و مقدار پیش‌فرض را بررسی کن.");
                        return;
                    }
                    database.updateHabit(habit.id, name, emoji.getText().toString().trim().isEmpty() ? "📌" : emoji.getText().toString(),
                            numeric ? "quantitative" : "boolean", low, high, unit.getText().toString(), note.getText().toString(), defaultAmount);
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
        EditText time = field("زمان", reminder == null ? "09:00" : parseReminderTime(reminder.time), InputType.TYPE_CLASS_TEXT);
        time.setHint("مثلاً 18:00");
        EditText description = field("توضیحات (اختیاری)", reminder == null ? "" : reminder.description,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        EditText advanceNotice = field("پیش‌آگاهی (ساعت روزانه، روز برای بقیه)", reminder == null
                || reminder.advanceNoticeValue == 0 ? "" : String.valueOf(reminder.advanceNoticeValue),
                InputType.TYPE_CLASS_NUMBER);
        CheckBox advanceNoticeEnabled = new CheckBox(this);
        advanceNoticeEnabled.setText("فعال‌کردن پیش‌آگاهی");
        advanceNoticeEnabled.setTextColor(TEXT);
        advanceNoticeEnabled.setTextSize(13);
        advanceNoticeEnabled.setButtonTintList(android.content.res.ColorStateList.valueOf(GREEN));
        advanceNoticeEnabled.setChecked(reminder != null && reminder.advanceNoticeValue > 0);
        advanceNotice.setHint("تعداد ساعت یا روز");
        advanceNotice.setVisibility(advanceNoticeEnabled.isChecked() ? View.VISIBLE : View.GONE);
        advanceNoticeEnabled.setOnCheckedChangeListener((buttonView, isChecked) ->
                advanceNotice.setVisibility(isChecked ? View.VISIBLE : View.GONE));
        LinearLayout scheduleFields = new LinearLayout(this);
        scheduleFields.setOrientation(LinearLayout.VERTICAL);
        Button weekdayPicker = button("انتخاب روزهای هفته", 0xff1f2937);
        EditText monthDay = field("روز ماه (۱ تا ۳۱)", "1", InputType.TYPE_CLASS_NUMBER);
        Button yearlyDateButton = button("انتخاب تاریخ شمسی", 0xff1f2937);
        Calendar jalaliToday = Calendar.getInstance(new ULocale("fa_IR@calendar=persian"));
        String[] yearlyDate = {String.format(Locale.ROOT, "%04d-%02d-%02d",
                jalaliToday.get(Calendar.YEAR), jalaliToday.get(Calendar.MONTH) + 1,
                jalaliToday.get(Calendar.DAY_OF_MONTH))};
        if (reminder != null) {
            String[] parsed = parseReminderDate(reminder.time);
            if (parsed != null) yearlyDate[0] = parsed[0];
            String parsedDay = parseReminderMonthDay(reminder.time);
            if (parsedDay != null) monthDay.setText(parsedDay);
        }
        yearlyDateButton.setText(formatBirthDate(parseBirthDate(yearlyDate[0])));
        String[] weekdays = {"شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنج‌شنبه", "جمعه"};
        boolean[] selectedWeekdays = new boolean[weekdays.length];
        if (reminder != null && matchesFrequency(reminder.frequency, 2)) {
            for (int i = 0; i < weekdays.length; i++) {
                selectedWeekdays[i] = reminder.time.contains(weekdays[i]);
            }
        } else {
            selectedWeekdays[6] = true;
        }
        ArrayList<String> initialWeekdayNames = new ArrayList<>();
        for (int i = 0; i < weekdays.length; i++) {
            if (selectedWeekdays[i]) initialWeekdayNames.add(weekdays[i]);
        }
        if (!initialWeekdayNames.isEmpty()) weekdayPicker.setText(TextUtils.join("، ", initialWeekdayNames));
        weekdayPicker.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("روزهای یادآوری هفتگی")
                .setMultiChoiceItems(weekdays, selectedWeekdays, (weekdayDialog, which, checked) ->
                        selectedWeekdays[which] = checked)
                .setPositiveButton("تأیید", (weekdayDialog, which) -> {
                    ArrayList<String> selectedNames = new ArrayList<>();
                    for (int i = 0; i < weekdays.length; i++) {
                        if (selectedWeekdays[i]) selectedNames.add(weekdays[i]);
                    }
                    weekdayPicker.setText(selectedNames.isEmpty()
                            ? "انتخاب روزهای هفته" : TextUtils.join("، ", selectedNames));
                })
                .setNegativeButton("انصراف", null)
                .show());
        yearlyDateButton.setOnClickListener(v -> showBirthDatePicker(yearlyDate[0], (value, parts) -> {
            yearlyDate[0] = value;
            yearlyDateButton.setText(formatBirthDate(parts));
        }));
        form.addView(text("عنوان", 12, MUTED));
        form.addView(title, margin(0, 4, 0, 12));
        form.addView(text("بازهٔ تکرار", 12, MUTED));
        form.addView(frequency, margin(0, 4, 0, 12));
        form.addView(text("زمان", 12, MUTED));
        form.addView(time, margin(0, 4, 0, 8));
        scheduleFields.addView(weekdayPicker, margin(0, 0, 0, 8));
        scheduleFields.addView(monthDay, margin(0, 0, 0, 8));
        scheduleFields.addView(yearlyDateButton, margin(0, 0, 0, 8));
        form.addView(scheduleFields);
        Runnable updateScheduleFields = () -> {
            int selectedFrequency = frequency.getSelectedItemPosition();
            weekdayPicker.setVisibility(selectedFrequency == 1 ? View.VISIBLE : View.GONE);
            monthDay.setVisibility(selectedFrequency == 2 ? View.VISIBLE : View.GONE);
            yearlyDateButton.setVisibility(selectedFrequency == 3 ? View.VISIBLE : View.GONE);
            advanceNotice.setHint(selectedFrequency == 0 ? "تعداد ساعت" : "تعداد روز");
        };
        frequency.setOnItemSelectedListener(selectionListener(updateScheduleFields));
        updateScheduleFields.run();
        form.addView(advanceNoticeEnabled, margin(0, 8, 0, 0));
        form.addView(advanceNotice, margin(0, 4, 0, 8));
        form.addView(text("توضیحات", 12, MUTED));
        form.addView(description, margin(0, 4, 0, 0));
        page.addView(form, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        Button save = button("ذخیره", GREEN);
        actions.addView(save, new LinearLayout.LayoutParams(0, dp(50), 1));
        save.setOnClickListener(v -> {
            String value = title.getText().toString().trim();
            if (value.isEmpty() || value.length() > 255) {
                title.setError("عنوان یادآوری را وارد کن");
                return;
            }
            String descriptionValue = description.getText().toString().trim();
            if (descriptionValue.length() > 1000) {
                description.setError("توضیحات نمی‌تواند بیشتر از ۱۰۰۰ نویسه باشد.");
                return;
            }
            String schedule = buildReminderSchedule(frequency.getSelectedItemPosition(),
                    time.getText().toString().trim(), selectedWeekdays, weekdays,
                    monthDay.getText().toString(), yearlyDate[0]);
            if (schedule == null) return;
            String noticeText = toAsciiDigits(advanceNotice.getText().toString().trim());
            int notice = !advanceNoticeEnabled.isChecked() ? 0
                    : noticeText.isEmpty() ? -1 : parse(noticeText, -1);
            int maxNotice = frequency.getSelectedItemPosition() == 0 ? 23 : 365;
            if (notice < 0 || notice > maxNotice) {
                advanceNotice.setError("مقدار پیش‌آگاهی خارج از محدوده است.");
                return;
            }
            if (reminder == null) {
                database.addReminder(value, frequency.getSelectedItem().toString(), schedule,
                        descriptionValue, notice);
            } else {
                database.updateReminder(reminder.id, value, frequency.getSelectedItem().toString(),
                        schedule, descriptionValue, notice);
            }
            scheduleReminders();
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
        try { return Integer.parseInt(toAsciiDigits(value.trim())); } catch (NumberFormatException exception) { return fallback; }
    }

    private Integer parseNullable(String value) {
        try {
            return Integer.valueOf(toAsciiDigits(value.trim()));
        } catch (NumberFormatException exception) {
            return null;
        }
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

    private final class SwipeableQuoteTextView extends TextView {
        private final GestureDetector gestureDetector;
        private boolean swiped;

        SwipeableQuoteTextView(MainActivity context) {
            super(context);
            setClickable(true);
            gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onDown(MotionEvent event) {
                    return true;
                }

                @Override
                public boolean onFling(MotionEvent start, MotionEvent end,
                                       float velocityX, float velocityY) {
                    float distance = end.getX() - start.getX();
                    if (Math.abs(distance) < dp(48) || Math.abs(velocityX) < dp(120)) return false;
                    moveQuote(distance < 0 ? 1 : -1);
                    swiped = true;
                    return true;
                }
            });
        }

        @Override
        public boolean performClick() {
            super.performClick();
            moveQuote(1);
            return true;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            boolean handled = gestureDetector.onTouchEvent(event);
            if (event.getAction() == MotionEvent.ACTION_UP) {
                if (!swiped) performClick();
                swiped = false;
            } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                swiped = false;
            }
            return handled || super.onTouchEvent(event);
        }
    }

    private static final class TrophyView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Path cup = new android.graphics.Path();
        private boolean locked;

        TrophyView(MainActivity context) {
            super(context);
            setContentDescription("نمایش کاپ");
        }

        void setLocked(boolean value) {
            locked = value;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float scale = getResources().getDisplayMetrics().density;
            canvas.save();
            canvas.translate((getWidth() - 112 * scale) / 2f, (getHeight() - 132 * scale) / 2f);
            canvas.scale(scale, scale);
            int gold = locked ? 0xff64748b : 0xfff59e0b;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(gold);
            canvas.drawRoundRect(29, 15, 83, 67, 8, 8, paint);
            paint.setColor(locked ? 0xff475569 : 0xfffbbf24);
            cup.reset();
            cup.moveTo(32, 24);
            cup.lineTo(80, 24);
            cup.lineTo(73, 55);
            cup.quadTo(56, 76, 39, 55);
            cup.close();
            canvas.drawPath(cup, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(7);
            paint.setColor(gold);
            canvas.drawArc(12, 24, 42, 58, 65, 230, false, paint);
            canvas.drawArc(70, 24, 100, 58, -115, 230, false, paint);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(51, 70, 61, 93, 3, 3, paint);
            canvas.drawRoundRect(37, 91, 75, 100, 4, 4, paint);
            canvas.drawRoundRect(28, 102, 84, 112, 5, 5, paint);
            if (locked) {
                paint.setColor(0xff0f172a);
                canvas.drawCircle(56, 49, 18, paint);
                paint.setColor(Color.WHITE);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(3);
                canvas.drawArc(49, 38, 63, 54, 180, 180, false, paint);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawRoundRect(46, 47, 66, 64, 3, 3, paint);
                canvas.drawCircle(56, 54, 2, paint);
                canvas.drawRect(55, 54, 57, 59, paint);
            }
            canvas.restore();
        }
    }

    private final class ChartView extends View {
        private final ArrayList<Integer> values;
        private final String[] dateLabels;
        private final int minValue;
        private final int maxValue;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        ChartView(MainActivity context, ArrayList<Integer> values, String[] dateLabels,
                  int minValue, int maxValue) {
            super(context);
            this.values = values;
            this.dateLabels = dateLabels;
            this.minValue = minValue;
            this.maxValue = maxValue;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float left = dp(42), right = getWidth() - dp(22), top = dp(14), bottom = getHeight() - dp(38);
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
                canvas.drawText(persianDigits((int) Math.round(tick)), left - dp(6), y + dp(4), paint);
            }
            paint.setColor(MUTED);
            paint.setTextSize(dp(8));
            paint.setTextAlign(Paint.Align.CENTER);
            for (int i = 0; i < dateLabels.length; i++) {
                float x = left + (right - left) * i / Math.max(1f, dateLabels.length - 1f);
                canvas.drawText(dateLabels[i], x, getHeight() - dp(10), paint);
            }
            boolean hasValues = false;
            for (Integer value : values) {
                if (value != null) {
                    hasValues = true;
                    break;
                }
            }
            if (!hasValues) {
                paint.setColor(MUTED);
                paint.setTextSize(dp(12));
                paint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("هنوز مقداری برای نمایش ثبت نشده",
                        (left + right) / 2f, (top + bottom) / 2f, paint);
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

    /* ================= برنامه هفتگی (همخوانی با نسخه وب) ================= */

    private String weekSetupSleepTime = "22:00";
    private String weekSetupWakeTime = "06:00";
    private final boolean[] weekSetupWorkEnabled = new boolean[7];
    private final boolean[] weekSetupGymEnabled = new boolean[7];
    private final String[] weekSetupWorkStart = new String[7];
    private final String[] weekSetupWorkEnd = new String[7];
    private final String[] weekSetupGymStart = new String[7];
    private final String[] weekSetupGymEnd = new String[7];

    private String weekActivityName = "";
    private final boolean[] weekActivityDays = new boolean[7];
    private final String[] weekActivityStart = new String[7];
    private final String[] weekActivityEnd = new String[7];
    private long weekEditActivityId = -1;

    private static final class WeekBlock {
        String name;
        int startMin;
        int endMin;
        int color;
        boolean isFree;

        WeekBlock(String name, int startMin, int endMin, int color, boolean isFree) {
            this.name = name;
            this.startMin = startMin;
            this.endMin = endMin;
            this.color = color;
            this.isFree = isFree;
        }
    }

    private void renderWeeklySchedule() {
        content.removeAllViews();
        DbHelper.WeeklySchedule schedule = database.getWeeklySchedule();

        TextView heading = text("برنامه هفتگی", 20, TEXT);
        heading.setTypeface(null, Typeface.BOLD);
        content.addView(heading, margin(0, 0, 0, 12));

        if (schedule == null) {
            database.saveWeeklySchedule("22:00", "06:00");
            for (int d = 1; d <= 5; d++) {
                database.saveScheduleDay(d, null, null, "21:00", "23:00");
            }
            schedule = database.getWeeklySchedule();
        }
        final DbHelper.WeeklySchedule finalSchedule = schedule;

        renderWeeklyTimeline(schedule);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);
        Button setupBtn = button("ویرایش خواب/بیداری", 0xff263244);
        Button manageBtn = button("مدیریت فعالیت‌ها", 0xff263244);
        Button addBtn = button("+ فعالیت", GREEN);
        actions.addView(setupBtn, new LinearLayout.LayoutParams(0, dp(44), 1));
        actions.addView(manageBtn, new LinearLayout.LayoutParams(0, dp(44), 1));
        actions.addView(addBtn, new LinearLayout.LayoutParams(0, dp(44), 1));
        content.addView(actions, margin(0, 8, 0, 14));

        setupBtn.setOnClickListener(v -> {
            loadWeekSetupState(finalSchedule);
            renderWeeklySetup(finalSchedule);
        });
        manageBtn.setOnClickListener(v -> renderWeeklyManageActivities());
        addBtn.setOnClickListener(v -> {
            resetWeekActivityState();
            renderWeeklyAddActivityDialog();
        });
    }

    private void renderWeeklyTimeline(DbHelper.WeeklySchedule schedule) {
        int wakeMin = timeToMinutes(schedule.wakeTime);
        int sleepMin = timeToMinutes(schedule.sleepTime);
        int awakeMin = (sleepMin - wakeMin + 1440) % 1440;
        if (awakeMin == 0) awakeMin = 1440;
        int slotCount = awakeMin / 15;

        ArrayList<DbHelper.ScheduleDay> days = database.getScheduleDays();
        ArrayList<DbHelper.WeeklyActivity> activities = database.getWeeklyActivities();

        HashMap<Long, ArrayList<DbHelper.ActivitySlot>> slotsByActivity = new HashMap<>();
        for (DbHelper.WeeklyActivity a : activities) {
            slotsByActivity.put(a.id, database.getActivitySlots(a.id));
        }

        LinearLayout timelineCard = card();
        timelineCard.setOrientation(LinearLayout.VERTICAL);

        LinearLayout descSection = new LinearLayout(this);
        descSection.setOrientation(LinearLayout.VERTICAL);
        descSection.setPadding(dp(12), dp(12), dp(12), dp(12));
        descSection.setBackground(round(0xff0b0f17, 8, 0xff334155));

        TextView descTitle = text("تنظیمات برنامه هفتگی", 15, TEXT);
        descTitle.setTypeface(null, Typeface.BOLD);
        descSection.addView(descTitle, margin(0, 0, 0, 6));
        TextView descBody = text("بیداری " + schedule.wakeTime + " تا خواب " + schedule.sleepTime + ".\nهر روز از زمان بیداری آغاز می‌شود؛ زمان‌های پس از نیمه‌شب در همان ردیف روز نمایش داده می‌شوند.", 12, MUTED);
        descSection.addView(descBody);
        timelineCard.addView(descSection, margin(0, 0, 0, 10));

        TextView info = text("بیداری " + schedule.wakeTime + "  تا  خواب " + schedule.sleepTime, 13, MUTED);
        timelineCard.addView(info, margin(0, 0, 0, 10));

        for (DbHelper.ScheduleDay day : days) {
            ArrayList<WeekBlock> blocks = new ArrayList<>();
            if (day.workStart != null && day.workEnd != null) {
                blocks.add(new WeekBlock("کار", timeToMinutes(day.workStart), timeToMinutes(day.workEnd), WEEK_COLOR_WORK, false));
            }
            if (day.gymStart != null && day.gymEnd != null) {
                blocks.add(new WeekBlock("باشگاه", timeToMinutes(day.gymStart), timeToMinutes(day.gymEnd), WEEK_COLOR_GYM, false));
            }
            for (DbHelper.WeeklyActivity a : activities) {
                ArrayList<DbHelper.ActivitySlot> slots = slotsByActivity.get(a.id);
                if (slots == null) continue;
                for (DbHelper.ActivitySlot s : slots) {
                    if (s.weekday == day.weekday) {
                        blocks.add(new WeekBlock(a.name, timeToMinutes(s.startTime), timeToMinutes(s.endTime), activityColor(a.id), false));
                    }
                }
            }
            blocks.sort((a, b) -> Integer.compare(a.startMin, b.startMin));

            LinearLayout dayRow = new LinearLayout(this);
            dayRow.setOrientation(LinearLayout.HORIZONTAL);
            dayRow.setGravity(Gravity.CENTER_VERTICAL);
            dayRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

            TextView dayLabel = text(WEEKDAY_NAMES[day.weekday], 14, TEXT);
            dayLabel.setTypeface(null, Typeface.BOLD);
            dayLabel.setMinWidth(dp(56));
            dayRow.addView(dayLabel, new LinearLayout.LayoutParams(dp(56), -2));

            LinearLayout track = new LinearLayout(this);
            track.setOrientation(LinearLayout.HORIZONTAL);
            track.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            track.setBackground(round(0xff0b0f17, 8, 0xff334155));
            track.setMinimumHeight(dp(44));

            int cursorSlot = 0;
            for (WeekBlock b : blocks) {
                int startSlot = (b.startMin - wakeMin) / 15;
                int durationSlots = (b.endMin - b.startMin) / 15;
                int gapSlots = startSlot - cursorSlot;
                if (gapSlots > 0) {
                    View gap = new View(this);
                    gap.setBackgroundColor(0xff0b0f17);
                    gap.setLayoutParams(new LinearLayout.LayoutParams(0, -1, gapSlots));
                    track.addView(gap);
                }
                View block = new View(this);
                block.setBackground(round(b.color, 4, b.color));
                block.setContentDescription(b.name);
                block.setLayoutParams(new LinearLayout.LayoutParams(0, -1, durationSlots));
                track.addView(block);
                cursorSlot = startSlot + durationSlots;
            }
            int remainingSlots = slotCount - cursorSlot;
            if (remainingSlots > 0) {
                View gap = new View(this);
                gap.setBackgroundColor(0xff0b0f17);
                gap.setLayoutParams(new LinearLayout.LayoutParams(0, -1, remainingSlots));
                track.addView(gap);
            }

            dayRow.addView(track, new LinearLayout.LayoutParams(0, -2, 1));
            timelineCard.addView(dayRow, margin(0, 0, 0, 8));
        }

        LinearLayout legend = new LinearLayout(this);
        legend.setOrientation(LinearLayout.HORIZONTAL);
        legend.setGravity(Gravity.CENTER);
        legend.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        legend.addView(legendItem(WEEK_COLOR_WORK, "کار"));
        legend.addView(legendItem(WEEK_COLOR_GYM, "باشگاه"));
        for (DbHelper.WeeklyActivity a : activities) {
            legend.addView(legendItem(activityColor(a.id), a.name));
        }
        timelineCard.addView(legend, margin(0, 10, 0, 0));

        content.addView(timelineCard, margin(0, 0, 0, 14));
    }

    private LinearLayout legendItem(int color, String label) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        View dot = new View(this);
        dot.setBackground(round(color, 3, color));
        item.addView(dot, new LinearLayout.LayoutParams(dp(14), dp(14)));
        item.addView(text(label, 11, TEXT), wrapMargin(4, 0, 6, 0));
        return item;
    }

    private void renderWeeklySetup(DbHelper.WeeklySchedule schedule) {
        content.removeAllViews();
        TextView heading = text(schedule == null ? "تنظیم برنامه هفتگی" : "ویرایش خواب و برنامه روزها", 20, TEXT);
        heading.setTypeface(null, Typeface.BOLD);
        content.addView(heading, margin(0, 0, 0, 12));

        ScrollView scroll = new ScrollView(this);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(16), 0, dp(16), 0);
        scroll.addView(form);

        LinearLayout timePair = new LinearLayout(this);
        timePair.setOrientation(LinearLayout.HORIZONTAL);
        timePair.setGravity(Gravity.CENTER_VERTICAL);
        timePair.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        Button sleepBtn = button(weekSetupSleepTime, 0xff263244);
        sleepBtn.setOnClickListener(v -> pickTime(weekSetupSleepTime, t -> {
            weekSetupSleepTime = t;
            sleepBtn.setText(t);
        }));
        Button wakeBtn = button(weekSetupWakeTime, 0xff263244);
        wakeBtn.setOnClickListener(v -> pickTime(weekSetupWakeTime, t -> {
            weekSetupWakeTime = t;
            wakeBtn.setText(t);
        }));

        timePair.addView(text("خواب:", 14, MUTED), wrapMargin(0, 0, 8, 0));
        timePair.addView(sleepBtn, new LinearLayout.LayoutParams(0, dp(44), 1));
        timePair.addView(text("بیداری:", 14, MUTED), wrapMargin(8, 0, 8, 0));
        timePair.addView(wakeBtn, new LinearLayout.LayoutParams(0, dp(44), 1));
        form.addView(timePair, margin(0, 0, 0, 16));

        form.addView(text("ساعت کار و باشگاه (اختیاری)", 15, TEXT), margin(0, 0, 0, 8));
        form.addView(text("زمان‌ها را در ساعات بیداری انتخاب کنید؛ تقاطع مجاز نیست.", 12, MUTED), margin(0, 0, 0, 12));

        for (int w = 0; w < 7; w++) {
            LinearLayout dayCard = card();
            dayCard.setOrientation(LinearLayout.VERTICAL);
            TextView dayLabel = text(WEEKDAY_NAMES[w], 15, TEXT);
            dayLabel.setTypeface(null, Typeface.BOLD);
            dayCard.addView(dayLabel, margin(0, 0, 0, 8));

            for (int act = 0; act < 2; act++) {
                boolean work = act == 0;
                String label = work ? "کار" : "باشگاه";
                boolean[] enabled = work ? weekSetupWorkEnabled : weekSetupGymEnabled;
                String[] starts = work ? weekSetupWorkStart : weekSetupGymStart;
                String[] ends = work ? weekSetupWorkEnd : weekSetupGymEnd;
                final int wi = w;

                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

                CheckBox check = new CheckBox(this);
                check.setText("سر کار می‌رم - " + label);
                check.setTextColor(TEXT);
                check.setChecked(enabled[wi]);
                check.setOnCheckedChangeListener((b, checked) -> enabled[wi] = checked);

                Button startBtn = button(starts[wi] != null ? starts[wi] : "07:00", 0xff263244);
                startBtn.setEnabled(enabled[wi]);
                startBtn.setAlpha(enabled[wi] ? 1f : 0.5f);
                startBtn.setOnClickListener(v -> pickTime(starts[wi] != null ? starts[wi] : "07:00", t -> {
                    starts[wi] = t;
                    startBtn.setText(t);
                }));

                Button endBtn = button(ends[wi] != null ? ends[wi] : "08:00", 0xff263244);
                endBtn.setEnabled(enabled[wi]);
                endBtn.setAlpha(enabled[wi] ? 1f : 0.5f);
                endBtn.setOnClickListener(v -> pickTime(ends[wi] != null ? ends[wi] : "08:00", t -> {
                    ends[wi] = t;
                    endBtn.setText(t);
                }));

                row.addView(check);
                row.addView(text("شروع", 12, MUTED), wrapMargin(8, 0, 4, 0));
                row.addView(startBtn, new LinearLayout.LayoutParams(0, dp(40), 1));
                row.addView(text("پایان", 12, MUTED), wrapMargin(8, 0, 4, 0));
                row.addView(endBtn, new LinearLayout.LayoutParams(0, dp(40), 1));
                dayCard.addView(row, margin(0, 0, 0, 6));
            }

            form.addView(dayCard, margin(0, 0, 0, 10));
        }

        Button saveBtn = button("ذخیره و نمایش برنامه", GREEN);
        saveBtn.setOnClickListener(v -> saveWeeklySetup());
        form.addView(saveBtn, margin(0, 12, 0, 0));

        Button backBtn = button("بازگشت به برنامه", 0xff263244);
        backBtn.setOnClickListener(v -> renderWeeklySchedule());
        form.addView(backBtn, margin(0, 4, 0, 0));

        content.addView(scroll);
    }

    private void resetWeekSetupState() {
        weekSetupSleepTime = "22:00";
        weekSetupWakeTime = "06:00";
        for (int i = 0; i < 7; i++) {
            weekSetupWorkEnabled[i] = false;
            weekSetupGymEnabled[i] = false;
            weekSetupWorkStart[i] = null;
            weekSetupWorkEnd[i] = null;
            weekSetupGymStart[i] = null;
            weekSetupGymEnd[i] = null;
        }
    }

    private void loadWeekSetupState(DbHelper.WeeklySchedule schedule) {
        weekSetupSleepTime = schedule.sleepTime;
        weekSetupWakeTime = schedule.wakeTime;
        ArrayList<DbHelper.ScheduleDay> days = database.getScheduleDays();
        for (DbHelper.ScheduleDay day : days) {
            int w = day.weekday;
            weekSetupWorkEnabled[w] = day.workStart != null || day.workEnd != null;
            weekSetupGymEnabled[w] = day.gymStart != null || day.gymEnd != null;
            weekSetupWorkStart[w] = day.workStart;
            weekSetupWorkEnd[w] = day.workEnd;
            weekSetupGymStart[w] = day.gymStart;
            weekSetupGymEnd[w] = day.gymEnd;
        }
    }

    private void saveWeeklySetup() {
        int wakeMin = timeToMinutes(weekSetupWakeTime);
        int sleepMin = timeToMinutes(weekSetupSleepTime);
        int awakeMin = (sleepMin - wakeMin + 1440) % 1440;
        if (awakeMin == 0) {
            showMessage("زمان خواب و بیداری نباید یکسان باشد.");
            return;
        }
        if (wakeMin % 15 != 0 || sleepMin % 15 != 0) {
            showMessage("زمان‌ها باید مضربی از ۱۵ دقیقه باشند.");
            return;
        }
        int sleepHours = (1440 - awakeMin) / 60;
        if (sleepHours > 12) {
            showMessage("مدت خواب نمی‌تواند بیشتر از ۱۲ ساعت باشد.");
            return;
        }

        for (int w = 0; w < 7; w++) {
            ArrayList<int[]> intervals = new ArrayList<>();
            if (weekSetupWorkEnabled[w] && weekSetupWorkStart[w] != null && weekSetupWorkEnd[w] != null) {
                int s = timeToMinutes(weekSetupWorkStart[w]);
                int e = timeToMinutes(weekSetupWorkEnd[w]);
                if (!inAwakeWindow(s, e, wakeMin, sleepMin)) {
                    showMessage("ساعت کار روز " + WEEKDAY_NAMES[w] + " باید در ساعات بیداری باشد.");
                    return;
                }
                intervals.add(new int[]{s, e});
            }
            if (weekSetupGymEnabled[w] && weekSetupGymStart[w] != null && weekSetupGymEnd[w] != null) {
                int s = timeToMinutes(weekSetupGymStart[w]);
                int e = timeToMinutes(weekSetupGymEnd[w]);
                if (!inAwakeWindow(s, e, wakeMin, sleepMin)) {
                    showMessage("ساعت باشگاه روز " + WEEKDAY_NAMES[w] + " باید در ساعات بیداری باشد.");
                    return;
                }
                intervals.add(new int[]{s, e});
            }
            for (int i = 0; i < intervals.size(); i++) {
                for (int j = i + 1; j < intervals.size(); j++) {
                    if (overlap(intervals.get(i), intervals.get(j))) {
                        showMessage("کار و باشگاه در روز " + WEEKDAY_NAMES[w] + " تداخل دارند.");
                        return;
                    }
                }
            }
        }

        database.saveWeeklySchedule(weekSetupSleepTime, weekSetupWakeTime);
        for (int w = 0; w < 7; w++) {
            database.saveScheduleDay(
                    w,
                    weekSetupWorkEnabled[w] ? weekSetupWorkStart[w] : null,
                    weekSetupWorkEnabled[w] ? weekSetupWorkEnd[w] : null,
                    weekSetupGymEnabled[w] ? weekSetupGymStart[w] : null,
                    weekSetupGymEnabled[w] ? weekSetupGymEnd[w] : null
            );
        }
        showMessage("برنامه هفتگی ذخیره شد.");
        renderWeeklySchedule();
    }

    private boolean inAwakeWindow(int startMin, int endMin, int wakeMin, int sleepMin) {
        int duration = (endMin - startMin + 1440) % 1440;
        if (duration == 0) return false;
        int startOffset = (startMin - wakeMin + 1440) % 1440;
        int awakeMin = (sleepMin - wakeMin + 1440) % 1440;
        if (awakeMin == 0) awakeMin = 1440;
        return startMin % 15 == 0 && endMin % 15 == 0 && startOffset + duration <= awakeMin;
    }

    private boolean overlap(int[] a, int[] b) {
        return a[0] < b[1] && b[0] < a[1];
    }

    private void renderWeeklyManageActivities() {
        content.removeAllViews();
        TextView heading = text("مدیریت فعالیت‌های هفتگی", 20, TEXT);
        heading.setTypeface(null, Typeface.BOLD);
        content.addView(heading, margin(0, 0, 0, 12));

        ArrayList<DbHelper.WeeklyActivity> activities = database.getWeeklyActivities();
        if (activities.isEmpty()) {
            LinearLayout emptyCard = card();
            emptyCard.addView(text("هنوز فعالیتی ثبت نشده است.", 14, MUTED), margin(0, 8, 0, 8));
            Button addBtn = button("افزودن فعالیت", GREEN);
            addBtn.setOnClickListener(v -> {
                resetWeekActivityState();
                renderWeeklyAddActivityDialog();
            });
            emptyCard.addView(addBtn, margin(0, 8, 0, 0));
            content.addView(emptyCard, margin(0, 0, 0, 14));
            Button backBtn = button("بازگشت به برنامه", 0xff263244);
            backBtn.setOnClickListener(v -> renderWeeklySchedule());
            content.addView(backBtn, margin(0, 4, 0, 0));
            return;
        }

        for (DbHelper.WeeklyActivity a : activities) {
            ArrayList<DbHelper.ActivitySlot> slots = database.getActivitySlots(a.id);
            LinearLayout row = card();
            row.setOrientation(LinearLayout.VERTICAL);
            TextView title = text(a.name, 15, TEXT);
            title.setTypeface(null, Typeface.BOLD);
            row.addView(title, margin(0, 0, 0, 6));
            StringBuilder slotText = new StringBuilder();
            for (DbHelper.ActivitySlot s : slots) {
                if (slotText.length() > 0) slotText.append("، ");
                slotText.append(WEEKDAY_NAMES[s.weekday]).append(" ").append(s.startTime).append("–").append(s.endTime);
            }
            row.addView(text(slotText.length() > 0 ? slotText.toString() : "بدون زمان*", 12, MUTED), margin(0, 0, 0, 8));
            LinearLayout btns = new LinearLayout(this);
            btns.setOrientation(LinearLayout.HORIZONTAL);
            btns.setGravity(Gravity.CENTER);
            Button editBtn = button("ویرایش", 0xff263244);
            editBtn.setTextSize(11);
            Button delBtn = button("حذف", 0xff7f1d1d);
            delBtn.setTextSize(11);
            btns.addView(editBtn, new LinearLayout.LayoutParams(dp(64), dp(36)));
            btns.addView(delBtn, new LinearLayout.LayoutParams(dp(60), dp(36)));
            row.addView(btns);
            content.addView(row, margin(0, 0, 0, 8));

            editBtn.setOnClickListener(v -> {
                weekEditActivityId = a.id;
                weekActivityName = a.name;
                for (int i = 0; i < 7; i++) {
                    weekActivityDays[i] = false;
                    weekActivityStart[i] = null;
                    weekActivityEnd[i] = null;
                }
                for (DbHelper.ActivitySlot s : slots) {
                    weekActivityDays[s.weekday] = true;
                    weekActivityStart[s.weekday] = s.startTime;
                    weekActivityEnd[s.weekday] = s.endTime;
                }
                renderWeeklyEditActivityDialog(a.id);
            });
            delBtn.setOnClickListener(v -> deleteWeeklyActivity(a.id));
        }

        Button addBtn = button("+ فعالیت جدید", GREEN);
        addBtn.setOnClickListener(v -> {
            resetWeekActivityState();
            renderWeeklyAddActivityDialog();
        });
        content.addView(addBtn, margin(0, 8, 0, 8));

        Button backBtn = button("بازگشت به برنامه", 0xff263244);
        backBtn.setOnClickListener(v -> renderWeeklySchedule());
        content.addView(backBtn, margin(0, 4, 0, 0));
    }

    private void renderWeeklyAddActivityDialog() {
        showWeeklyActivityDialog(null, "افزودن فعالیت");
    }

    private void renderWeeklyEditActivityDialog(long activityId) {
        DbHelper.WeeklyActivity activity = null;
        for (DbHelper.WeeklyActivity a : database.getWeeklyActivities()) {
            if (a.id == activityId) { activity = a; break; }
        }
        if (activity == null) return;
        weekEditActivityId = activityId;
        weekActivityName = activity.name;
        showWeeklyActivityDialog(activityId, "ویرایش فعالیت");
    }

    private void showWeeklyActivityDialog(Long activityId, String title) {
        Dialog dialog = new Dialog(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(20), dp(20), dp(20));
        page.setBackgroundColor(BG);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        TextView heading = text(title, 20, TEXT);
        heading.setTypeface(null, Typeface.BOLD);
        titleRow.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        Button close = button("×", 0xff263244);
        titleRow.addView(close, new LinearLayout.LayoutParams(dp(42), dp(42)));
        page.addView(titleRow);
        close.setOnClickListener(v -> dialog.dismiss());

        ScrollView scroll = new ScrollView(this);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(0, dp(8), 0, 0);
        scroll.addView(form);

        EditText name = field("نام فعالیت", weekActivityName, android.text.InputType.TYPE_CLASS_TEXT);
        form.addView(text("نام", 12, MUTED));
        form.addView(name, margin(0, 4, 0, 12));

        form.addView(text("روزها", 12, MUTED));
        LinearLayout dayChecks = new LinearLayout(this);
        dayChecks.setOrientation(LinearLayout.VERTICAL);
        dayChecks.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        for (int w = 0; w < 7; w++) {
            final int day = w;
            CheckBox cb = new CheckBox(this);
            cb.setText(WEEKDAY_NAMES[w]);
            cb.setTextColor(TEXT);
            cb.setChecked(weekActivityDays[w]);
            cb.setOnCheckedChangeListener((b, checked) -> weekActivityDays[day] = checked);
            dayChecks.addView(cb, margin(0, 2, 0, 2));
        }
        form.addView(dayChecks, margin(0, 0, 0, 12));

        for (int w = 0; w < 7; w++) {
            final int day = w;
            LinearLayout timeRow = new LinearLayout(this);
            timeRow.setOrientation(LinearLayout.HORIZONTAL);
            timeRow.setGravity(Gravity.CENTER_VERTICAL);
            timeRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            timeRow.setVisibility(weekActivityDays[w] ? View.VISIBLE : View.GONE);

            Button startBtn = button(weekActivityStart[day] != null ? weekActivityStart[day] : "09:00", 0xff263244);
            startBtn.setOnClickListener(v -> pickTime(weekActivityStart[day] != null ? weekActivityStart[day] : "09:00", t -> weekActivityStart[day] = t));
            Button endBtn = button(weekActivityEnd[day] != null ? weekActivityEnd[day] : "10:00", 0xff263244);
            endBtn.setOnClickListener(v -> pickTime(weekActivityEnd[day] != null ? weekActivityEnd[day] : "10:00", t -> weekActivityEnd[day] = t));

            timeRow.addView(text(WEEKDAY_NAMES[w], 13, TEXT), wrapMargin(0, 0, 8, 0));
            timeRow.addView(startBtn, new LinearLayout.LayoutParams(0, dp(40), 1));
            timeRow.addView(text("تا", 12, MUTED), wrapMargin(4, 0, 4, 0));
            timeRow.addView(endBtn, new LinearLayout.LayoutParams(0, dp(40), 1));
            form.addView(timeRow, margin(0, 0, 0, 8));
        }

        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        Button save = button("ذخیره", GREEN);
        actions.addView(save, new LinearLayout.LayoutParams(0, dp(50), 1));
        save.setOnClickListener(v -> saveWeeklyActivity(dialog));
        if (activityId != null) {
            Button del = button("حذف", 0xff7f1d1d);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(50), 1);
            p.setMargins(dp(10), 0, 0, 0);
            actions.addView(del, p);
            del.setOnClickListener(v -> {
                deleteWeeklyActivity(activityId);
                dialog.dismiss();
            });
        }
        page.addView(actions);
        dialog.setContentView(page);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout(-1, -1);
        }
        dialog.show();
    }

    private void saveWeeklyActivity(Dialog dialog) {
        String name = weekActivityName.trim();
        if (name.isEmpty() || name.length() > 100) {
            showMessage("نام فعالیت را وارد کنید (بیشینه ۱۰۰ نویسه).");
            return;
        }
        ArrayList<Integer> selectedDays = new ArrayList<>();
        for (int w = 0; w < 7; w++) {
            if (weekActivityDays[w]) selectedDays.add(w);
        }
        if (selectedDays.isEmpty()) {
            showMessage("حداقل یک روز را انتخاب کنید.");
            return;
        }

        int wakeMin = timeToMinutes(weekSetupWakeTime);
        int sleepMin = timeToMinutes(weekSetupSleepTime);
        DbHelper.WeeklySchedule schedule = database.getWeeklySchedule();
        if (schedule != null) {
            wakeMin = timeToMinutes(schedule.wakeTime);
            sleepMin = timeToMinutes(schedule.sleepTime);
        }

        for (int w : selectedDays) {
            if (weekActivityStart[w] == null || weekActivityEnd[w] == null) {
                showMessage("زمان شروع و پایان برای روز " + WEEKDAY_NAMES[w] + " را وارد کنید.");
                return;
            }
            int s = timeToMinutes(weekActivityStart[w]);
            int e = timeToMinutes(weekActivityEnd[w]);
            if (!inAwakeWindow(s, e, wakeMin, sleepMin)) {
                showMessage("زمان روز " + WEEKDAY_NAMES[w] + " باید در ساعات بیداری و روی تقسیمات ۱۵ دقیقه‌ای باشد.");
                return;
            }
            for (int w2 : selectedDays) {
                if (w2 == w) continue;
                // overlap check only within same day, skip cross-day
            }
            // Same-day overlap with existing slots (excluding this activity if editing)
            ArrayList<DbHelper.WeeklyActivity> activities = database.getWeeklyActivities();
            for (DbHelper.WeeklyActivity a : activities) {
                if (weekEditActivityId != -1 && a.id == weekEditActivityId) continue;
                for (DbHelper.ActivitySlot slot : database.getActivitySlots(a.id)) {
                    if (slot.weekday != w) continue;
                    int ss = timeToMinutes(slot.startTime);
                    int ee = timeToMinutes(slot.endTime);
                    if (overlap(new int[]{s, e}, new int[]{ss, ee})) {
                        showMessage("این زمان با فعالیت \"" + a.name + "\" در روز " + WEEKDAY_NAMES[w] + " تداخل دارد.");
                        return;
                    }
                }
            }
            // Same-day overlap with work/gym
            ArrayList<DbHelper.ScheduleDay> days = database.getScheduleDays();
            for (DbHelper.ScheduleDay day : days) {
                if (day.weekday != w) continue;
                if (day.workStart != null && day.workEnd != null) {
                    int ws = timeToMinutes(day.workStart);
                    int we = timeToMinutes(day.workEnd);
                    if (overlap(new int[]{s, e}, new int[]{ws, we})) {
                        showMessage("زمان با ساعت کار در روز " + WEEKDAY_NAMES[w] + " تداخل دارد.");
                        return;
                    }
                }
                if (day.gymStart != null && day.gymEnd != null) {
                    int gs = timeToMinutes(day.gymStart);
                    int ge = timeToMinutes(day.gymEnd);
                    if (overlap(new int[]{s, e}, new int[]{gs, ge})) {
                        showMessage("زمان با ساعت باشگاه در روز " + WEEKDAY_NAMES[w] + " تداخل دارد.");
                        return;
                    }
                }
            }
        }

        long activityId;
        if (weekEditActivityId != -1) {
            // Delete old slots and recreate
            database.deleteWeeklyActivity(weekEditActivityId);
            activityId = weekEditActivityId;
            // Re-insert activity with same id is not supported by insert; delete+add creates new id.
            // Instead, update name and re-add slots.
            // DbHelper doesn't have update activity name; we delete and re-add.
            activityId = database.addWeeklyActivity(name);
        } else {
            activityId = database.addWeeklyActivity(name);
        }
        for (int w : selectedDays) {
            database.addActivitySlot(activityId, w, weekActivityStart[w], weekActivityEnd[w]);
        }
        dialog.dismiss();
        resetWeekActivityState();
        weekEditActivityId = -1;
        renderWeeklyManageActivities();
        showMessage("فعالیت ذخیره شد.");
    }

    private void deleteWeeklyActivity(long activityId) {
        new android.app.AlertDialog.Builder(this)
                .setTitle("حذف فعالیت؟")
                .setMessage("این فعالیت و تمامbraceزمان‌های آن از برنامه هفتگی حذف می‌شود.")
                .setNegativeButton("انصراف", null)
                .setPositiveButton("حذف", (d, w) -> {
                    database.deleteWeeklyActivity(activityId);
                    renderWeeklyManageActivities();
                    showMessage("فعالیت حذف شد.");
                })
                .show()
                .getButton(android.app.AlertDialog.BUTTON_POSITIVE)
                .setTextColor(0xffef4444);
    }

    private void resetWeekActivityState() {
        weekActivityName = "";
        for (int i = 0; i < 7; i++) {
            weekActivityDays[i] = false;
            weekActivityStart[i] = null;
            weekActivityEnd[i] = null;
        }
        weekEditActivityId = -1;
    }

    private void pickTime(String current, TimePickerCallback callback) {
        String[] parts = (current == null || current.isEmpty()) ? new String[]{"07", "00"} : current.split(":");
        int hour;
        int minute;
        try {
            hour = Integer.parseInt(parts[0]);
            minute = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            hour = 7;
            minute = 0;
        }
        TimePickerDialog dialog = new TimePickerDialog(this, (view, selectedHour, selectedMinute) -> {
            callback.onTimeSelected(String.format(java.util.Locale.ROOT, "%02d:%02d", selectedHour, selectedMinute));
        }, hour, minute, true);
        dialog.show();
    }

    private int timeToMinutes(String time) {
        if (time == null || time.isEmpty()) return -1;
        String[] parts = time.split(":");
        return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
    }

    private int activityColor(long id) {
        int[] colors = {0xffef4444, 0xfff97316, 0xfffb923c, 0xfffacc15, 0xff84cc16, 0xff22c55e, 0xff06b6d4, 0xff3b82f6, 0xff8b5cf6, 0xffd946ef};
        return colors[(int) (id % colors.length)];
    }

    private interface TimePickerCallback {
        void onTimeSelected(String time);
    }

}
