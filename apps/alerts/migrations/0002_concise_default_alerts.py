from django.db import migrations


TITLE_CHANGES = {
    "هرشب ساعت 9 بگه عادت های امروزت رو ثبت کردی ؟": "ثبت عادت‌های امروز",
    "جمعه ها ساعت 6 عصر بگه : برنامه این هفته رو نوشتی ؟": "برنامه‌ریزی هفتگی",
    "شهریه باشگاه یک هفته دیگه تموم میشه !": "پرداخت شهریه باشگاه",
    "15 آذر : به نظر میرسه امروز روز خوبی برای کاشتن یک درخته !": "کاشت درخت",
}


def shorten_default_alerts(apps, schema_editor):
    Alert = apps.get_model("alerts", "Alert")
    for old_title, new_title in TITLE_CHANGES.items():
        Alert.objects.filter(title=old_title).update(title=new_title)


def restore_default_alerts(apps, schema_editor):
    Alert = apps.get_model("alerts", "Alert")
    for old_title, new_title in TITLE_CHANGES.items():
        Alert.objects.filter(title=new_title).update(title=old_title)


class Migration(migrations.Migration):

    dependencies = [
        ("alerts", "0001_initial"),
    ]

    operations = [
        migrations.RunPython(shorten_default_alerts, restore_default_alerts),
    ]
