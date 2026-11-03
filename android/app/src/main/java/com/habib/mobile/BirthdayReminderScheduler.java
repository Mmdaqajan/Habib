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
    private static final int REQUEST_CODE = 7441;

    private BirthdayReminderScheduler() {
    }

    static void schedule(Context context, String birthDate) {
        int[] parts = parseDate(birthDate);
        if (parts == null) {
            cancel(context);
            return;
        }
        Calendar now = Calendar.getInstance(new ULocale("fa_IR@calendar=persian"));
        Calendar next = (Calendar) now.clone();
        int targetYear = now.get(Calendar.YEAR);
        setBirthday(next, targetYear, parts[1], parts[2]);
        if (!next.after(now)) {
            targetYear++;
            setBirthday(next, targetYear, parts[1], parts[2]);
        }

        Intent intent = birthdayIntent(context, birthDate);
        PendingIntent pending = PendingIntent.getBroadcast(context, REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.getTimeInMillis(), pending);
    }

    static void cancel(Context context) {
        PendingIntent pending = PendingIntent.getBroadcast(context, REQUEST_CODE,
                birthdayIntent(context, ""), PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pending != null) {
            AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            alarms.cancel(pending);
            pending.cancel();
        }
    }

    private static Intent birthdayIntent(Context context, String birthDate) {
        return new Intent(context, NotificationReceiver.class)
                .setAction(ACTION_BIRTHDAY)
                .setData(Uri.parse("habib://birthday"))
                .putExtra("birth_date", birthDate);
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
