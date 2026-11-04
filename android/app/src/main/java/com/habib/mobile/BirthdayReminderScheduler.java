package com.habib.mobile;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.icu.util.Calendar;
import android.icu.util.ULocale;
import android.net.Uri;

final class BirthdayReminderScheduler {
    static final String ACTION_BIRTHDAY = "com.habib.mobile.BIRTHDAY_REMINDER";
    static final String ACTION_BIRTHDAY_ADVANCE = "com.habib.mobile.BIRTHDAY_ADVANCE_REMINDER";
    private static final int REQUEST_CODE = 7441;
    private static final int ADVANCE_REQUEST_CODE = 7442;

    private BirthdayReminderScheduler() {
    }

    static void schedule(Context context, String birthDate) {
        int[] parts = parseDate(birthDate);
        if (parts == null) {
            cancel(context);
            return;
        }
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Calendar now = Calendar.getInstance(new ULocale("fa_IR@calendar=persian"));
        Calendar next = nextBirthday(now, parts[0], parts[1], parts[2]);
        Calendar advance = (Calendar) next.clone();
        advance.add(Calendar.DAY_OF_MONTH, -7);
        if (!advance.after(now)) {
            next = nextBirthdayYear(now, parts[1], parts[2], next.get(Calendar.YEAR) + 1);
            advance = (Calendar) next.clone();
            advance.add(Calendar.DAY_OF_MONTH, -7);
        }
        scheduleAlarm(context, alarms, REQUEST_CODE, ACTION_BIRTHDAY, birthDate, next);
        scheduleAlarm(context, alarms, ADVANCE_REQUEST_CODE, ACTION_BIRTHDAY_ADVANCE, birthDate, advance);
    }

    static void cancel(Context context) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        for (int requestCode : new int[]{REQUEST_CODE, ADVANCE_REQUEST_CODE}) {
            PendingIntent pending = PendingIntent.getBroadcast(context, requestCode,
                    birthdayIntent(context, "", requestCode == ADVANCE_REQUEST_CODE
                            ? ACTION_BIRTHDAY_ADVANCE : ACTION_BIRTHDAY),
                    PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
            if (pending != null) {
                alarms.cancel(pending);
                pending.cancel();
            }
        }
    }

    private static void scheduleAlarm(Context context, AlarmManager alarms, int requestCode,
                                      String action, String birthDate, Calendar target) {
        PendingIntent pending = PendingIntent.getBroadcast(context, requestCode,
                birthdayIntent(context, birthDate, action),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.getTimeInMillis(), pending);
    }

    private static Intent birthdayIntent(Context context, String birthDate, String action) {
        return new Intent(context, NotificationReceiver.class)
                .setAction(action)
                .setData(Uri.parse("habib://" + action))
                .putExtra("birth_date", birthDate);
    }

    private static Calendar nextBirthday(Calendar now, int year, int month, int day) {
        Calendar next = nextBirthdayYear(now, month, day, now.get(Calendar.YEAR));
        if (!next.after(now)) {
            next = nextBirthdayYear(now, month, day, now.get(Calendar.YEAR) + 1);
        }
        return next;
    }

    private static Calendar nextBirthdayYear(Calendar now, int month, int day, int targetYear) {
        Calendar next = (Calendar) now.clone();
        setBirthday(next, targetYear, month, day);
        return next;
    }

    private static void setBirthday(Calendar target, int year, int month, int day) {
        target.set(Calendar.YEAR, year);
        target.set(Calendar.MONTH, month);
        target.set(Calendar.DAY_OF_MONTH, 1);
        int validDay = Math.min(day, target.getActualMaximum(Calendar.DAY_OF_MONTH));
        target.set(Calendar.DAY_OF_MONTH, validDay);
        target.set(Calendar.HOUR_OF_DAY, 9);
        target.set(Calendar.MINUTE, 0);
        target.set(Calendar.SECOND, 0);
        target.set(Calendar.MILLISECOND, 0);
    }

    private static int[] parseDate(String birthDate) {
        if (birthDate == null) return null;
        try {
            String[] fields = birthDate.split("-");
            if (fields.length != 3) return null;
            int year = Integer.parseInt(fields[0]);
            int month = Integer.parseInt(fields[1]) - 1;
            int day = Integer.parseInt(fields[2]);
            if (year < 1300 || month < 0 || month > 11 || day < 1 || day > 31) return null;
            return new int[]{year, month, day};
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
