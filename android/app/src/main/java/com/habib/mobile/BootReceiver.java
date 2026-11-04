package com.habib.mobile;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        DbHelper database = new DbHelper(context);
        String birthDate = database.profile()[1];
        BirthdayReminderScheduler.schedule(context, birthDate);
        database.close();
    }
}
