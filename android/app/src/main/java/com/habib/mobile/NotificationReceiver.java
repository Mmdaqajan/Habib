package com.habib.mobile;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.icu.util.Calendar;
import android.icu.util.ULocale;

public final class NotificationReceiver extends BroadcastReceiver {
    private static final String CHANNEL_ID = "habib_reminders";

    @Override
    public void onReceive(Context context, Intent intent) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL_ID, "یادآوری‌های حبیب",
                NotificationManager.IMPORTANCE_DEFAULT));
        boolean birthday = BirthdayReminderScheduler.ACTION_BIRTHDAY.equals(intent.getAction());
        boolean birthdayAdvance = BirthdayReminderScheduler.ACTION_BIRTHDAY_ADVANCE.equals(intent.getAction());
        String title = birthday ? "تولدت مبارک!" : birthdayAdvance
                ? "یادآوری تولد" : intent.getStringExtra("title");
        String birthDate = intent.getStringExtra("birth_date");
        String text = birthday ? birthdayMessage(birthDate) : birthdayAdvance
                ? "یک هفته تا تولدت باقی مانده است." : intent.getStringExtra("text");
        Notification notification = new Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title == null ? "Habib" : title)
                .setContentText(text == null ? "" : text)
                .setStyle(new Notification.BigTextStyle().bigText(text == null ? "" : text))
                .setAutoCancel(true)
                .build();
        manager.notify((int) System.currentTimeMillis(), notification);
        if (birthday) {
            BirthdayReminderScheduler.schedule(context, birthDate);
        }
    }

    private String birthdayMessage(String birthDate) {
        int age = 0;
        if (birthDate != null) {
            try {
                String[] parts = birthDate.split("-");
                if (parts.length != 3) throw new NumberFormatException("Invalid birth date");
                Calendar today = Calendar.getInstance(new ULocale("fa_IR@calendar=persian"));
                age = today.get(Calendar.YEAR) - Integer.parseInt(parts[0]);
            } catch (NumberFormatException exception) {
                age = 0;
            }
        }
        return "امروز " + persianDigits(age) + " ساله شدی؛ تولدت مبارک!";
    }

    private String persianDigits(int value) {
        return String.valueOf(value).replace('0', '۰').replace('1', '۱').replace('2', '۲')
                .replace('3', '۳').replace('4', '۴').replace('5', '۵').replace('6', '۶')
                .replace('7', '۷').replace('8', '۸').replace('9', '۹');
    }
}
