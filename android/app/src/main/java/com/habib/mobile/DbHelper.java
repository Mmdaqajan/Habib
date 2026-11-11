package com.habib.mobile;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Set;

final class DbHelper extends SQLiteOpenHelper {
    static final class Habit {
        long id;
        String title;
        String emoji;
        String type;
        String unit;
        String description;
        int min;
        int max;
        boolean completed;
        boolean skipped;
        String value;
        Integer defaultValue;
        int sortOrder;
    }

    static final class Reminder {
        long id;
        String title;
        String frequency;
        String time;
        String description;
        int advanceNoticeValue;
    }

    static final class CalendarEvent {
        long id;
        String title;
        int color;
        String date;
    }

    static final class Achievement {
        String code;
        String title;
        String description;
        int xpReward;
        boolean unlocked;
    }

    static final class WeeklySchedule {
        long id;
        String sleepTime;
        String wakeTime;
    }

    static final class ScheduleDay {
        long id;
        long scheduleId;
        int weekday;
        String workStart;
        String workEnd;
        String gymStart;
        String gymEnd;
    }

    static final class WeeklyActivity {
        long id;
        String name;
    }

    static final class ActivitySlot {
        long id;
        long activityId;
        int weekday;
        String startTime;
        String endTime;
    }

    DbHelper(Context context) {
        super(context, "habib-local.db", null, 9);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE habits (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, emoji TEXT NOT NULL, type TEXT NOT NULL, min_value INTEGER NOT NULL DEFAULT 0, max_value INTEGER NOT NULL DEFAULT 100, unit TEXT NOT NULL DEFAULT '', description TEXT NOT NULL DEFAULT '', default_value INTEGER, active INTEGER NOT NULL DEFAULT 1, sort_order INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE habit_logs (id INTEGER PRIMARY KEY AUTOINCREMENT, habit_id INTEGER NOT NULL, date TEXT NOT NULL, completed INTEGER NOT NULL DEFAULT 0, skipped INTEGER NOT NULL DEFAULT 0, value INTEGER, UNIQUE(habit_id, date), FOREIGN KEY(habit_id) REFERENCES habits(id) ON DELETE CASCADE)");
        db.execSQL("CREATE TABLE reminders (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, frequency TEXT NOT NULL, time TEXT NOT NULL DEFAULT '', description TEXT NOT NULL DEFAULT '', advance_notice_value INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE calendar_events (id INTEGER PRIMARY KEY AUTOINCREMENT, date TEXT NOT NULL, title TEXT NOT NULL, color INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE profile (id INTEGER PRIMARY KEY CHECK(id=1), name TEXT NOT NULL, age TEXT NOT NULL DEFAULT '', birth_date TEXT NOT NULL DEFAULT '', email TEXT NOT NULL, phone TEXT NOT NULL, photo_uri TEXT NOT NULL DEFAULT '', photo_x INTEGER NOT NULL DEFAULT 50, photo_y INTEGER NOT NULL DEFAULT 50)");
        
        // Weekly Schedule Tables
        db.execSQL("CREATE TABLE weekly_schedule (id INTEGER PRIMARY KEY CHECK(id=1), sleep_time TEXT DEFAULT '22:00', wake_time TEXT DEFAULT '06:00')");
        db.execSQL("CREATE TABLE schedule_days (id INTEGER PRIMARY KEY AUTOINCREMENT, schedule_id INTEGER NOT NULL, weekday INTEGER NOT NULL, work_start TEXT, work_end TEXT, gym_start TEXT, gym_end TEXT, FOREIGN KEY(schedule_id) REFERENCES weekly_schedule(id) ON DELETE CASCADE, UNIQUE(schedule_id, weekday))");
        db.execSQL("CREATE TABLE weekly_activities (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL)");
        db.execSQL("CREATE TABLE activity_slots (id INTEGER PRIMARY KEY AUTOINCREMENT, activity_id INTEGER NOT NULL, weekday INTEGER NOT NULL, start_time TEXT NOT NULL, end_time TEXT NOT NULL, FOREIGN KEY(activity_id) REFERENCES weekly_activities(id) ON DELETE CASCADE, UNIQUE(activity_id, weekday))");
        
        createAchievementsTable(db);
        insertInitialData(db);
    }

    private void insertInitialData(SQLiteDatabase db) {
        insertHabit(db, "مطالعه", "📖", "quantitative", 10, 100, "صفحه", "مطالعه روزانه", 1);
        insertHabit(db, "ورزش", "🏋️", "boolean", 0, 100, "", "باشگاه یا پیاده‌روی", 2);
        insertHabit(db, "سحرخیزی (07:00)", "☀️", "boolean", 0, 100, "", "", 3);
        insertHabit(db, "Study English", "🗽", "quantitative", 10, 60, "دقیقه", "لغات و ریدینگ", 4);
        ContentValues profile = new ContentValues();
        profile.put("id", 1);
        profile.put("name", "user58192");
        profile.put("age", "");
        profile.put("email", "");
        profile.put("phone", "");
        profile.put("photo_uri", "");
        db.insert("profile", null, profile);
        insertDefaultReminders(db);

        // Weekly schedule defaults: sleep 22:00, wake 06:00; gym 21:00-23:00 Sun-Thu (days 1-5)
        saveWeeklySchedule(db, "22:00", "06:00");
        for (int d = 1; d <= 5; d++) {
            saveScheduleDay(db, d, null, null, "21:00", "23:00");
        }
    }

    private void saveWeeklySchedule(SQLiteDatabase db, String sleepTime, String wakeTime) {
        ContentValues values = new ContentValues();
        values.put("id", 1);
        values.put("sleep_time", sleepTime);
        values.put("wake_time", wakeTime);
        db.insertWithOnConflict("weekly_schedule", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    private void saveScheduleDay(SQLiteDatabase db, int weekday, String workStart, String workEnd, String gymStart, String gymEnd) {
        ContentValues values = new ContentValues();
        values.put("schedule_id", 1);
        values.put("weekday", weekday);
        values.put("work_start", workStart);
        values.put("work_end", workEnd);
        values.put("gym_start", gymStart);
        values.put("gym_end", gymEnd);
        db.insertWithOnConflict("schedule_days", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    void resetAllData() {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("habit_logs", null, null);
            db.delete("calendar_events", null, null);
            db.delete("reminders", null, null);
            db.delete("achievement_unlocks", null, null);
            db.delete("habits", null, null);
            db.delete("profile", null, null);
            db.delete("sqlite_sequence", null, null);
            insertInitialData(db);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private void insertHabit(SQLiteDatabase db, String title, String emoji, String type, int min, int max, String unit, String description, int sortOrder) {
        ContentValues values = new ContentValues();
        values.put("title", title);
        values.put("emoji", emoji);
        values.put("type", type);
        values.put("min_value", min);
        values.put("max_value", max);
        values.put("unit", unit);
        values.put("description", description);
        values.put("sort_order", sortOrder);
        db.insert("habits", null, values);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            insertDefaultReminders(db);
        }
        if (oldVersion < 3) {
            addColumnIfMissing(db, "profile", "photo_x", "INTEGER NOT NULL DEFAULT 50");
            addColumnIfMissing(db, "profile", "photo_y", "INTEGER NOT NULL DEFAULT 50");
        }
        if (oldVersion < 4) {
            addColumnIfMissing(db, "profile", "age", "TEXT NOT NULL DEFAULT ''");
        }
        if (oldVersion < 5) {
            db.execSQL("CREATE TABLE IF NOT EXISTS calendar_events (id INTEGER PRIMARY KEY AUTOINCREMENT, date TEXT NOT NULL, title TEXT NOT NULL, color INTEGER NOT NULL)");
        }
        if (oldVersion < 6) {
            addColumnIfMissing(db, "profile", "birth_date", "TEXT NOT NULL DEFAULT ''");
            migrateExistingBirthDate(db);
        }
        if (oldVersion < 7) {
            addColumnIfMissing(db, "habits", "default_value", "INTEGER");
            addColumnIfMissing(db, "habit_logs", "skipped", "INTEGER NOT NULL DEFAULT 0");
            addColumnIfMissing(db, "reminders", "description", "TEXT NOT NULL DEFAULT ''");
            addColumnIfMissing(db, "reminders", "advance_notice_value", "INTEGER NOT NULL DEFAULT 0");
            createAchievementsTable(db);
            db.execSQL("UPDATE habits SET title='سحرخیزی (07:00)' WHERE title='سحرخیزی' AND type='boolean'");
            db.execSQL("UPDATE reminders SET description=? WHERE title=? AND description=''",
                    new Object[]{"بهترین کاری که میتونی جمعه ها انجام بدی نوشتن برنامه ی هفته ی آینده ست", "برنامه‌ریزی هفتگی"});
            db.execSQL("UPDATE reminders SET description=? WHERE title=? AND description=''",
                    new Object[]{"ی تصمیم خوب و ماندگار :)", "کاشت درخت"});
        }
        if (oldVersion < 8) {
            db.execSQL("CREATE TABLE IF NOT EXISTS weekly_schedule (id INTEGER PRIMARY KEY CHECK(id=1), sleep_time TEXT DEFAULT '22:00', wake_time TEXT DEFAULT '06:00')");
            db.execSQL("CREATE TABLE IF NOT EXISTS schedule_days (id INTEGER PRIMARY KEY AUTOINCREMENT, schedule_id INTEGER NOT NULL, weekday INTEGER NOT NULL, work_start TEXT, work_end TEXT, gym_start TEXT, gym_end TEXT, FOREIGN KEY(schedule_id) REFERENCES weekly_schedule(id) ON DELETE CASCADE, UNIQUE(schedule_id, weekday))");
            db.execSQL("CREATE TABLE IF NOT EXISTS weekly_activities (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL)");
            db.execSQL("CREATE TABLE IF NOT EXISTS activity_slots (id INTEGER PRIMARY KEY AUTOINCREMENT, activity_id INTEGER NOT NULL, weekday INTEGER NOT NULL, start_time TEXT NOT NULL, end_time TEXT NOT NULL, FOREIGN KEY(activity_id) REFERENCES weekly_activities(id) ON DELETE CASCADE, UNIQUE(activity_id, weekday))");
        }
        if (oldVersion < 9) {
            addColumnIfMissing(db, "habits", "sort_order", "INTEGER NOT NULL DEFAULT 0");
            db.execSQL("UPDATE habits SET sort_order = id WHERE sort_order = 0");
        }
    }

    private void createAchievementsTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS achievement_unlocks (code TEXT PRIMARY KEY, title TEXT NOT NULL, description TEXT NOT NULL, xp_reward INTEGER NOT NULL, unlocked_at INTEGER NOT NULL)");
    }

    private void migrateExistingBirthDate(SQLiteDatabase db) {
        try (Cursor cursor = db.rawQuery("SELECT age FROM profile WHERE id=1", null)) {
            if (!cursor.moveToFirst()) return;
            String previousValue = cursor.getString(0);
            if (previousValue == null) return;
            String[] parts = previousValue.split("-");
            if (parts.length != 3) return;
            try {
                int year = Integer.parseInt(parts[0]);
                int month = Integer.parseInt(parts[1]);
                int day = Integer.parseInt(parts[2]);
                if (year < 1300 || month < 1 || month > 12 || day < 1 || day > 31) return;
                ContentValues values = new ContentValues();
                values.put("birth_date", String.format(Locale.ROOT, "%04d-%02d-%02d", year, month, day));
                db.update("profile", values, "id=1", null);
            } catch (NumberFormatException ignored) {
                // Existing numeric ages cannot be converted into a precise birth date.
            }
        }
    }

    private void addColumnIfMissing(SQLiteDatabase db, String table, String column, String definition) {
        try {
            db.execSQL("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        } catch (SQLiteException exception) {
            if (!exception.getMessage().contains("duplicate column name")) throw exception;
        }
    }

    private void insertDefaultReminders(SQLiteDatabase db) {
        if (dbDatabaseCount(db, "reminders") > 0) return;
        insertReminder(db, "ثبت عادت‌های امروز", "روزانه", "ساعت 21:00", "");
        insertReminder(db, "برنامه‌ریزی هفتگی", "هفتگی", "جمعه‌ها - ساعت 18:00",
                "بهترین کاری که میتونی جمعه ها انجام بدی نوشتن برنامه ی هفته ی آینده ست");
        insertReminder(db, "پرداخت شهریه باشگاه", "ماهانه", "روز 1 ماه - ساعت 10:00", "");
        insertReminder(db, "کاشت درخت", "سالانه", "15 آذر - ساعت 09:00", "ی تصمیم خوب و ماندگار :)");
        insertReminder(db, "رویداد ۱۰ آبان", "سالانه", "۱۰ آبان - ساعت 07:00", "به چالش NNN بپیوندید!");
        insertReminder(db, "ژورنال امروز رو بنویس", "روزانه", "ساعت 14:00", "");
    }

    private long dbDatabaseCount(SQLiteDatabase db, String table) {
        try (Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + table, null)) {
            return cursor.moveToFirst() ? cursor.getLong(0) : 0;
        }
    }

    private void insertReminder(SQLiteDatabase db, String title, String frequency, String time, String description) {
        ContentValues values = new ContentValues();
        values.put("title", title);
        values.put("frequency", frequency);
        values.put("time", time);
        values.put("description", description);
        db.insert("reminders", null, values);
    }

    ArrayList<Habit> habits() {
        return habits(LocalDate.now());
    }

    ArrayList<Habit> habits(LocalDate date) {
        ArrayList<Habit> result = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.rawQuery(
                "SELECT h.id,h.title,h.emoji,h.type,h.min_value,h.max_value,h.unit,h.description,l.completed,l.skipped,l.value,h.default_value,h.sort_order " +
                        "FROM habits h LEFT JOIN habit_logs l ON l.habit_id=h.id AND l.date=? " +
                        "WHERE h.active=1 ORDER BY h.sort_order, h.id", new String[]{date.toString()})) {
            while (cursor.moveToNext()) {
                Habit habit = new Habit();
                habit.id = cursor.getLong(0);
                habit.title = cursor.getString(1);
                habit.emoji = cursor.getString(2);
                habit.type = cursor.getString(3);
                habit.min = cursor.getInt(4);
                habit.max = cursor.getInt(5);
                habit.unit = cursor.getString(6);
                habit.description = cursor.getString(7);
                habit.completed = cursor.getInt(8) == 1;
                habit.skipped = cursor.getInt(9) == 1;
                habit.value = cursor.isNull(10) ? "" : cursor.getString(10);
                habit.defaultValue = cursor.isNull(11) ? null : cursor.getInt(11);
                habit.sortOrder = cursor.isNull(12) ? 0 : cursor.getInt(12);
                if (!habit.completed && !habit.skipped && habit.value.isEmpty()
                        && habit.defaultValue != null) {
                    habit.value = String.valueOf(habit.defaultValue);
                }
                result.add(habit);
            }
        }
        return result;
    }

    void saveLog(long habitId, LocalDate date, boolean completed, boolean skipped, String value) {
        if (completed && skipped) throw new IllegalArgumentException("Habit cannot be completed and skipped");
        if (value != null) value = toAsciiDigits(value.trim());
        Integer defaultValue = null;
        int min = 0;
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT min_value,default_value FROM habits WHERE id=?",
                new String[]{String.valueOf(habitId)})) {
            if (!cursor.moveToFirst()) throw new IllegalArgumentException("Unknown habit");
            min = cursor.getInt(0);
            if (!cursor.isNull(1)) defaultValue = cursor.getInt(1);
        }
        if (skipped) {
            completed = false;
            value = null;
        } else if (completed && (value == null || value.trim().isEmpty())) {
            value = String.valueOf(defaultValue == null ? min : defaultValue);
        }
        if (value != null && !value.trim().isEmpty()) {
            int amount = Integer.parseInt(value.trim());
            try (Cursor cursor = getReadableDatabase().rawQuery(
                    "SELECT min_value,max_value FROM habits WHERE id=?", new String[]{String.valueOf(habitId)})) {
                if (!cursor.moveToFirst()) throw new IllegalArgumentException("Unknown habit");
                if (amount < cursor.getInt(0) || amount > cursor.getInt(1)) {
                    throw new IllegalArgumentException("Habit amount is outside its configured range");
                }
            }
        }
        ContentValues values = new ContentValues();
        values.put("habit_id", habitId);
        values.put("date", date.toString());
        values.put("completed", completed ? 1 : 0);
        values.put("skipped", skipped ? 1 : 0);
        if (value == null || value.trim().isEmpty()) values.putNull("value");
        else values.put("value", Integer.parseInt(value));
        getWritableDatabase().insertWithOnConflict("habit_logs", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    private String toAsciiDigits(String value) {
        return value.replace('۰', '0').replace('۱', '1').replace('۲', '2')
                .replace('۳', '3').replace('۴', '4').replace('۵', '5')
                .replace('۶', '6').replace('۷', '7').replace('۸', '8')
                .replace('۹', '9').replace('٠', '0').replace('١', '1')
                .replace('٢', '2').replace('٣', '3').replace('٤', '4')
                .replace('٥', '5').replace('٦', '6').replace('٧', '7')
                .replace('٨', '8').replace('٩', '9');
    }

    long addHabit(String title, String emoji, String type, int min, int max, String unit, String description, Integer defaultValue) {
        ContentValues values = new ContentValues();
        values.put("title", title);
        values.put("emoji", emoji);
        values.put("type", type);
        values.put("min_value", min);
        values.put("max_value", max);
        values.put("unit", unit);
        values.put("description", description);
        if (defaultValue == null || !"quantitative".equals(type)) values.putNull("default_value");
        else values.put("default_value", defaultValue);
        int nextOrder = 1;
        try (Cursor c = getReadableDatabase().rawQuery("SELECT MAX(sort_order) FROM habits WHERE active=1", null)) {
            if (c.moveToFirst()) nextOrder = c.getInt(0) + 1;
        }
        values.put("sort_order", nextOrder);
        return getWritableDatabase().insert("habits", null, values);
    }

    void updateHabit(long id, String title, String emoji, String type, int min, int max, String unit, String description, Integer defaultValue) {
        ContentValues values = new ContentValues();
        values.put("title", title);
        values.put("emoji", emoji);
        values.put("type", type);
        values.put("min_value", min);
        values.put("max_value", max);
        values.put("unit", unit);
        values.put("description", description);
        if (defaultValue == null || !"quantitative".equals(type)) values.putNull("default_value");
        else values.put("default_value", defaultValue);
        getWritableDatabase().update("habits", values, "id=?", new String[]{String.valueOf(id)});
    }

    void updateHabitSortOrder(long id, int sortOrder) {
        ContentValues values = new ContentValues();
        values.put("sort_order", sortOrder);
        getWritableDatabase().update("habits", values, "id=?", new String[]{String.valueOf(id)});
    }

    void swapHabitOrder(long id1, long id2) {
        SQLiteDatabase db = getWritableDatabase();
        int order1 = 0, order2 = 0;
        try (Cursor c1 = db.rawQuery("SELECT sort_order FROM habits WHERE id=?", new String[]{String.valueOf(id1)})) {
            if (c1.moveToFirst()) order1 = c1.getInt(0);
        }
        try (Cursor c2 = db.rawQuery("SELECT sort_order FROM habits WHERE id=?", new String[]{String.valueOf(id2)})) {
            if (c2.moveToFirst()) order2 = c2.getInt(0);
        }
        ContentValues v1 = new ContentValues(); v1.put("sort_order", order2);
        db.update("habits", v1, "id=?", new String[]{String.valueOf(id1)});
        ContentValues v2 = new ContentValues(); v2.put("sort_order", order1);
        db.update("habits", v2, "id=?", new String[]{String.valueOf(id2)});
    }

    void deleteHabit(long id) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("active", 0);
        db.update("habits", values, "id=?", new String[]{String.valueOf(id)});
        db.delete("achievement_unlocks", "code LIKE ?", new String[]{"habit:" + id + ":%"});
    }

    ArrayList<Habit> deletedHabits() {
        ArrayList<Habit> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT id,title,emoji,type,min_value,max_value,unit,description FROM habits WHERE active=0 ORDER BY id",
                null)) {
            while (cursor.moveToNext()) {
                Habit habit = new Habit();
                habit.id = cursor.getLong(0);
                habit.title = cursor.getString(1);
                habit.emoji = cursor.getString(2);
                habit.type = cursor.getString(3);
                habit.min = cursor.getInt(4);
                habit.max = cursor.getInt(5);
                habit.unit = cursor.getString(6);
                habit.description = cursor.getString(7);
                result.add(habit);
            }
        }
        return result;
    }

    void restoreHabit(long id) {
        ContentValues values = new ContentValues();
        values.put("active", 1);
        getWritableDatabase().update("habits", values, "id=? AND active=0", new String[]{String.valueOf(id)});
    }

    ArrayList<Reminder> reminders() {
        ArrayList<Reminder> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT id,title,frequency,time,description,advance_notice_value FROM reminders ORDER BY id DESC", null)) {
            while (cursor.moveToNext()) {
                Reminder reminder = new Reminder();
                reminder.id = cursor.getLong(0);
                reminder.title = cursor.getString(1);
                reminder.frequency = cursor.getString(2);
                reminder.time = cursor.getString(3);
                reminder.description = cursor.getString(4);
                reminder.advanceNoticeValue = cursor.getInt(5);
                result.add(reminder);
            }
        }
        return result;
    }

    void addReminder(String title, String frequency, String time, String description, int advanceNoticeValue) {
        ContentValues values = new ContentValues();
        values.put("title", title);
        values.put("frequency", frequency);
        values.put("time", time);
        values.put("description", description);
        values.put("advance_notice_value", advanceNoticeValue);
        getWritableDatabase().insert("reminders", null, values);
    }

    void updateReminder(long id, String title, String frequency, String time, String description, int advanceNoticeValue) {
        ContentValues values = new ContentValues();
        values.put("title", title);
        values.put("frequency", frequency);
        values.put("time", time);
        values.put("description", description);
        values.put("advance_notice_value", advanceNoticeValue);
        getWritableDatabase().update("reminders", values, "id=?", new String[]{String.valueOf(id)});
    }

    void deleteReminder(long id) {
        getWritableDatabase().delete("reminders", "id=?", new String[]{String.valueOf(id)});
    }

    void addCalendarEvent(String date, String title, int color) {
        ContentValues values = new ContentValues();
        values.put("date", date);
        values.put("title", title);
        values.put("color", color);
        getWritableDatabase().insert("calendar_events", null, values);
    }

    ArrayList<CalendarEvent> calendarEvents(String date) {
        ArrayList<CalendarEvent> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT id,title,color,date FROM calendar_events WHERE date=? ORDER BY id", new String[]{date})) {
            while (cursor.moveToNext()) {
                CalendarEvent event = new CalendarEvent();
                event.id = cursor.getLong(0);
                event.title = cursor.getString(1);
                event.color = cursor.getInt(2);
                event.date = cursor.getString(3);
                result.add(event);
            }
        }
        return result;
    }

    void deleteCalendarEvent(long id) {
        getWritableDatabase().delete("calendar_events", "id=?", new String[]{String.valueOf(id)});
    }

    void updateCalendarEvent(long id, String title, int color) {
        ContentValues values = new ContentValues();
        values.put("title", title);
        values.put("color", color);
        getWritableDatabase().update("calendar_events", values, "id=?", new String[]{String.valueOf(id)});
    }

    String[] profile() {
        try (Cursor cursor = getReadableDatabase().rawQuery("SELECT name,birth_date,email,phone,photo_uri FROM profile WHERE id=1", null)) {
            if (cursor.moveToFirst()) return new String[]{cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getString(3), cursor.getString(4)};
        }
        return new String[]{"", "", "", "", ""};
    }

    void saveProfile(String name, String birthDate, String email, String phone, String photoUri) {
        ContentValues values = new ContentValues();
        values.put("id", 1);
        values.put("name", name);
        values.put("birth_date", birthDate);
        values.put("email", email);
        values.put("phone", phone);
        values.put("photo_uri", photoUri);
        SQLiteDatabase db = getWritableDatabase();
        if (db.update("profile", values, "id=1", null) == 0) db.insert("profile", null, values);
    }

    ArrayList<Integer> chartValues(long habitId) {
        Integer[] values = new Integer[14];
        LocalDate start = LocalDate.now().minusDays(13);
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT date,value FROM habit_logs WHERE habit_id=? AND date>=? AND completed=1 ORDER BY date",
                new String[]{String.valueOf(habitId), start.toString()})) {
            while (cursor.moveToNext()) {
                LocalDate date = LocalDate.parse(cursor.getString(0));
                int index = (int) (date.toEpochDay() - start.toEpochDay());
                if (index >= 0 && index < values.length && !cursor.isNull(1)) values[index] = cursor.getInt(1);
            }
        }
        ArrayList<Integer> result = new ArrayList<>();
        for (Integer value : values) result.add(value);
        return result;
    }

    ArrayList<LocalDate> completedDates(long habitId) {
        ArrayList<LocalDate> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT date FROM habit_logs WHERE habit_id=? AND completed=1 ORDER BY date",
                new String[]{String.valueOf(habitId)})) {
            while (cursor.moveToNext()) result.add(LocalDate.parse(cursor.getString(0)));
        }
        return result;
    }

    ArrayList<LocalDate> completedDates() {
        ArrayList<LocalDate> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT DISTINCT l.date FROM habit_logs l JOIN habits h ON h.id=l.habit_id " +
                        "WHERE l.completed=1 AND h.active=1 ORDER BY l.date",
                null)) {
            while (cursor.moveToNext()) result.add(LocalDate.parse(cursor.getString(0)));
        }
        return result;
    }

    ArrayList<LocalDate> allHabitsCompletedDates(int activeHabitCount) {
        ArrayList<LocalDate> result = new ArrayList<>();
        if (activeHabitCount == 0) return result;
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT l.date FROM habit_logs l JOIN habits h ON h.id=l.habit_id " +
                        "WHERE l.completed=1 AND h.active=1 GROUP BY l.date " +
                        "HAVING COUNT(DISTINCT h.id)>=? ORDER BY l.date",
                new String[]{String.valueOf(activeHabitCount)})) {
            while (cursor.moveToNext()) result.add(LocalDate.parse(cursor.getString(0)));
        }
        return result;
    }

    HashMap<String, Integer> activityCounts(int days) {
        HashMap<String, Integer> result = new HashMap<>();
        LocalDate start = LocalDate.now().minusDays(days - 1L);
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT date,COUNT(*) FROM habit_logs l JOIN habits h ON h.id=l.habit_id WHERE l.completed=1 AND h.active=1 AND date>=? GROUP BY date",
                new String[]{start.toString()})) {
            while (cursor.moveToNext()) result.put(cursor.getString(0), cursor.getInt(1));
        }
        return result;
    }

    long[] habitCompletionStats(long habitId) {
        long total = 0;
        long longest = 0;
        long streak = 0;
        LocalDate previous = null;
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT date FROM habit_logs WHERE habit_id=? AND completed=1 ORDER BY date",
                new String[]{String.valueOf(habitId)})) {
            while (cursor.moveToNext()) {
                LocalDate date = LocalDate.parse(cursor.getString(0));
                total++;
                streak = previous != null && previous.plusDays(1).equals(date) ? streak + 1 : 1;
                longest = Math.max(longest, streak);
                previous = date;
            }
        }
        return new long[]{total, longest};
    }

    long totalHabitValue(long habitId) {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(value),0) FROM habit_logs WHERE habit_id=? AND completed=1",
                new String[]{String.valueOf(habitId)})) {
            return cursor.moveToFirst() ? cursor.getLong(0) : 0;
        }
    }

    int totalCompletedLogs() {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM habit_logs l JOIN habits h ON h.id=l.habit_id WHERE l.completed=1 AND h.active=1",
                null)) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    int activeDayCount() {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT COUNT(DISTINCT l.date) FROM habit_logs l JOIN habits h ON h.id=l.habit_id WHERE l.completed=1 AND h.active=1",
                null)) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    int activeHabitCount() {
        try (Cursor cursor = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM habits WHERE active=1", null)) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    int completedActiveHabitCount(String date) {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM habit_logs l JOIN habits h ON h.id=l.habit_id WHERE l.completed=1 AND h.active=1 AND l.date=?",
                new String[]{date})) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    int unlockAchievement(String code, String title, String description, int xpReward) {
        SQLiteDatabase db = getWritableDatabase();
        try (Cursor cursor = db.rawQuery(
                "SELECT xp_reward FROM achievement_unlocks WHERE code=?", new String[]{code})) {
            if (cursor.moveToFirst()) {
                ContentValues update = new ContentValues();
                update.put("title", title);
                update.put("description", description);
                update.put("xp_reward", xpReward);
                db.update("achievement_unlocks", update, "code=?", new String[]{code});
                return 0;
            }
        }
        ContentValues values = new ContentValues();
        values.put("code", code);
        values.put("title", title);
        values.put("description", description);
        values.put("xp_reward", xpReward);
        values.put("unlocked_at", System.currentTimeMillis());
        db.insertOrThrow("achievement_unlocks", null, values);
        return xpReward;
    }

    void removeStaleAchievements(Set<String> validCodes) {
        ArrayList<String> storedCodes = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().rawQuery("SELECT code FROM achievement_unlocks", null)) {
            while (cursor.moveToNext()) storedCodes.add(cursor.getString(0));
        }
        SQLiteDatabase db = getWritableDatabase();
        for (String code : storedCodes) {
            if (!validCodes.contains(code)) db.delete("achievement_unlocks", "code=?", new String[]{code});
        }
    }

    ArrayList<Achievement> achievements() {
        ArrayList<Achievement> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT code,title,description,xp_reward FROM achievement_unlocks ORDER BY xp_reward,code",
                null)) {
            while (cursor.moveToNext()) {
                Achievement achievement = new Achievement();
                achievement.code = cursor.getString(0);
                achievement.title = cursor.getString(1);
                achievement.description = cursor.getString(2);
                achievement.xpReward = cursor.getInt(3);
                achievement.unlocked = true;
                result.add(achievement);
            }
        }
        return result;
    }

    int totalXp() {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(xp_reward),0) FROM achievement_unlocks", null)) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    // Weekly Schedule Methods
    WeeklySchedule getWeeklySchedule() {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT id, sleep_time, wake_time FROM weekly_schedule WHERE id=1", null)) {
            if (cursor.moveToFirst()) {
                WeeklySchedule schedule = new WeeklySchedule();
                schedule.id = cursor.getLong(0);
                schedule.sleepTime = cursor.getString(1);
                schedule.wakeTime = cursor.getString(2);
                return schedule;
            }
        }
        return null;
    }

    void saveWeeklySchedule(String sleepTime, String wakeTime) {
        ContentValues values = new ContentValues();
        values.put("id", 1);
        values.put("sleep_time", sleepTime);
        values.put("wake_time", wakeTime);
        getWritableDatabase().insertWithOnConflict("weekly_schedule", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    ArrayList<ScheduleDay> getScheduleDays() {
        ArrayList<ScheduleDay> days = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT id, schedule_id, weekday, work_start, work_end, gym_start, gym_end FROM schedule_days ORDER BY weekday", null)) {
            while (cursor.moveToNext()) {
                ScheduleDay day = new ScheduleDay();
                day.id = cursor.getLong(0);
                day.scheduleId = cursor.getLong(1);
                day.weekday = cursor.getInt(2);
                day.workStart = cursor.getString(3);
                day.workEnd = cursor.getString(4);
                day.gymStart = cursor.getString(5);
                day.gymEnd = cursor.getString(6);
                days.add(day);
            }
        }
        return days;
    }

    void saveScheduleDay(int weekday, String workStart, String workEnd, String gymStart, String gymEnd) {
        ContentValues values = new ContentValues();
        values.put("schedule_id", 1);
        values.put("weekday", weekday);
        values.put("work_start", workStart);
        values.put("work_end", workEnd);
        values.put("gym_start", gymStart);
        values.put("gym_end", gymEnd);
        getWritableDatabase().insertWithOnConflict("schedule_days", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    ArrayList<WeeklyActivity> getWeeklyActivities() {
        ArrayList<WeeklyActivity> activities = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT id, name FROM weekly_activities ORDER BY name", null)) {
            while (cursor.moveToNext()) {
                WeeklyActivity activity = new WeeklyActivity();
                activity.id = cursor.getLong(0);
                activity.name = cursor.getString(1);
                activities.add(activity);
            }
        }
        return activities;
    }

    ArrayList<ActivitySlot> getActivitySlots(long activityId) {
        ArrayList<ActivitySlot> slots = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT id, activity_id, weekday, start_time, end_time FROM activity_slots WHERE activity_id=? ORDER BY weekday, start_time",
                new String[]{String.valueOf(activityId)})) {
            while (cursor.moveToNext()) {
                ActivitySlot slot = new ActivitySlot();
                slot.id = cursor.getLong(0);
                slot.activityId = cursor.getLong(1);
                slot.weekday = cursor.getInt(2);
                slot.startTime = cursor.getString(3);
                slot.endTime = cursor.getString(4);
                slots.add(slot);
            }
        }
        return slots;
    }

    long addWeeklyActivity(String name) {
        ContentValues values = new ContentValues();
        values.put("name", name);
        return getWritableDatabase().insert("weekly_activities", null, values);
    }

    void addActivitySlot(long activityId, int weekday, String startTime, String endTime) {
        ContentValues values = new ContentValues();
        values.put("activity_id", activityId);
        values.put("weekday", weekday);
        values.put("start_time", startTime);
        values.put("end_time", endTime);
        getWritableDatabase().insertWithOnConflict("activity_slots", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    void deleteWeeklyActivity(long activityId) {
        getWritableDatabase().delete("weekly_activities", "id=?", new String[]{String.valueOf(activityId)});
        getWritableDatabase().delete("activity_slots", "activity_id=?", new String[]{String.valueOf(activityId)});
    }
}
