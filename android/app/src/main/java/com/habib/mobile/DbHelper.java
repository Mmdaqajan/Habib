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
        String value;
    }

    static final class Reminder {
        long id;
        String title;
        String frequency;
        String time;
    }

    static final class CalendarEvent {
        long id;
        String title;
        int color;
    }

    DbHelper(Context context) {
        super(context, "habib-local.db", null, 6);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE habits (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, emoji TEXT NOT NULL, type TEXT NOT NULL, min_value INTEGER NOT NULL DEFAULT 0, max_value INTEGER NOT NULL DEFAULT 100, unit TEXT NOT NULL DEFAULT '', description TEXT NOT NULL DEFAULT '', active INTEGER NOT NULL DEFAULT 1)");
        db.execSQL("CREATE TABLE habit_logs (id INTEGER PRIMARY KEY AUTOINCREMENT, habit_id INTEGER NOT NULL, date TEXT NOT NULL, completed INTEGER NOT NULL DEFAULT 0, value INTEGER, UNIQUE(habit_id, date), FOREIGN KEY(habit_id) REFERENCES habits(id) ON DELETE CASCADE)");
        db.execSQL("CREATE TABLE reminders (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, frequency TEXT NOT NULL, time TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE TABLE calendar_events (id INTEGER PRIMARY KEY AUTOINCREMENT, date TEXT NOT NULL, title TEXT NOT NULL, color INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE profile (id INTEGER PRIMARY KEY CHECK(id=1), name TEXT NOT NULL, age TEXT NOT NULL DEFAULT '', birth_date TEXT NOT NULL DEFAULT '', email TEXT NOT NULL, phone TEXT NOT NULL, photo_uri TEXT NOT NULL DEFAULT '', photo_x INTEGER NOT NULL DEFAULT 50, photo_y INTEGER NOT NULL DEFAULT 50)");
        insertHabit(db, "مطالعه", "📖", "quantitative", 10, 100, "صفحه", "مطالعه روزانه");
        insertHabit(db, "ورزش", "🏋️", "boolean", 0, 100, "", "باشگاه یا پیاده‌روی");
        insertHabit(db, "سحرخیزی", "☀️", "boolean", 0, 100, "", "بیداری ساعت ۷");
        insertHabit(db, "Study English", "🗽", "quantitative", 10, 60, "دقیقه", "لغات و ریدینگ");
        ContentValues profile = new ContentValues();
        profile.put("id", 1);
        profile.put("name", "محمد");
        profile.put("age", "");
        profile.put("email", "");
        profile.put("phone", "");
        profile.put("photo_uri", "");
        db.insert("profile", null, profile);
        insertDefaultReminders(db);
    }

    private void insertHabit(SQLiteDatabase db, String title, String emoji, String type, int min, int max, String unit, String description) {
        ContentValues values = new ContentValues();
        values.put("title", title);
        values.put("emoji", emoji);
        values.put("type", type);
        values.put("min_value", min);
        values.put("max_value", max);
        values.put("unit", unit);
        values.put("description", description);
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
        insertReminder(db, "ثبت عادت‌های امروز", "روزانه", "ساعت 21:00");
        insertReminder(db, "برنامه‌ریزی هفتگی", "هفتگی", "جمعه‌ها - ساعت 18:00");
        insertReminder(db, "پرداخت شهریه باشگاه", "ماهانه", "روز 1 ماه - ساعت 10:00");
        insertReminder(db, "کاشت درخت", "سالانه", "15 آذر - ساعت 09:00");
    }

    private long dbDatabaseCount(SQLiteDatabase db, String table) {
        try (Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + table, null)) {
            return cursor.moveToFirst() ? cursor.getLong(0) : 0;
        }
    }

    private void insertReminder(SQLiteDatabase db, String title, String frequency, String time) {
        ContentValues values = new ContentValues();
        values.put("title", title);
        values.put("frequency", frequency);
        values.put("time", time);
        db.insert("reminders", null, values);
    }

    ArrayList<Habit> habits() {
        ArrayList<Habit> result = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        String date = LocalDate.now().toString();
        try (Cursor cursor = db.rawQuery(
                "SELECT h.id,h.title,h.emoji,h.type,h.min_value,h.max_value,h.unit,h.description,l.completed,l.value " +
                        "FROM habits h LEFT JOIN habit_logs l ON l.habit_id=h.id AND l.date=? " +
                        "WHERE h.active=1 ORDER BY h.id", new String[]{date})) {
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
                habit.value = cursor.isNull(9) ? "" : cursor.getString(9);
                result.add(habit);
            }
        }
        return result;
    }

    void saveLog(long habitId, boolean completed, String value) {
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
        values.put("date", LocalDate.now().toString());
        values.put("completed", completed ? 1 : 0);
        if (value == null || value.trim().isEmpty()) values.putNull("value");
        else values.put("value", Integer.parseInt(value));
        getWritableDatabase().insertWithOnConflict("habit_logs", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    long addHabit(String title, String emoji, String type, int min, int max, String unit, String description) {
        ContentValues values = new ContentValues();
        values.put("title", title);
        values.put("emoji", emoji);
        values.put("type", type);
        values.put("min_value", min);
        values.put("max_value", max);
        values.put("unit", unit);
        values.put("description", description);
        return getWritableDatabase().insert("habits", null, values);
    }

    void updateHabit(long id, String title, String emoji, String type, int min, int max, String unit, String description) {
        ContentValues values = new ContentValues();
        values.put("title", title);
        values.put("emoji", emoji);
        values.put("type", type);
        values.put("min_value", min);
        values.put("max_value", max);
        values.put("unit", unit);
        values.put("description", description);
        getWritableDatabase().update("habits", values, "id=?", new String[]{String.valueOf(id)});
    }

    void deleteHabit(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("habit_logs", "habit_id=?", new String[]{String.valueOf(id)});
        db.delete("habits", "id=?", new String[]{String.valueOf(id)});
    }

    ArrayList<Reminder> reminders() {
        ArrayList<Reminder> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().rawQuery("SELECT id,title,frequency,time FROM reminders ORDER BY id DESC", null)) {
            while (cursor.moveToNext()) {
                Reminder reminder = new Reminder();
                reminder.id = cursor.getLong(0);
                reminder.title = cursor.getString(1);
                reminder.frequency = cursor.getString(2);
                reminder.time = cursor.getString(3);
                result.add(reminder);
            }
        }
        return result;
    }

    void addReminder(String title, String frequency, String time) {
        ContentValues values = new ContentValues();
        values.put("title", title);
        values.put("frequency", frequency);
        values.put("time", time);
        getWritableDatabase().insert("reminders", null, values);
    }

    void updateReminder(long id, String title, String frequency, String time) {
        ContentValues values = new ContentValues();
        values.put("title", title);
        values.put("frequency", frequency);
        values.put("time", time);
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
                "SELECT id,title,color FROM calendar_events WHERE date=? ORDER BY id", new String[]{date})) {
            while (cursor.moveToNext()) {
                CalendarEvent event = new CalendarEvent();
                event.id = cursor.getLong(0);
                event.title = cursor.getString(1);
                event.color = cursor.getInt(2);
                result.add(event);
            }
        }
        return result;
    }

    void deleteCalendarEvent(long id) {
        getWritableDatabase().delete("calendar_events", "id=?", new String[]{String.valueOf(id)});
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
                "SELECT date,value,completed FROM habit_logs WHERE habit_id=? AND date>=? ORDER BY date",
                new String[]{String.valueOf(habitId), start.toString()})) {
            while (cursor.moveToNext()) {
                LocalDate date = LocalDate.parse(cursor.getString(0));
                int index = (int) (date.toEpochDay() - start.toEpochDay());
                if (index >= 0 && index < values.length) {
                    values[index] = cursor.isNull(1) ? cursor.getInt(2) : cursor.getInt(1);
                }
            }
        }
        ArrayList<Integer> result = new ArrayList<>();
        for (Integer value : values) result.add(value);
        return result;
    }

    HashMap<String, Integer> activityCounts(int days) {
        HashMap<String, Integer> result = new HashMap<>();
        LocalDate start = LocalDate.now().minusDays(days - 1L);
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT date,COUNT(*) FROM habit_logs WHERE completed=1 AND date>=? GROUP BY date",
                new String[]{start.toString()})) {
            while (cursor.moveToNext()) result.put(cursor.getString(0), cursor.getInt(1));
        }
        return result;
    }
}
