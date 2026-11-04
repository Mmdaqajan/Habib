from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ("alerts", "0004_alert_advance_notice_value"),
    ]

    operations = [
        migrations.AddField(
            model_name="alert",
            name="is_birthday",
            field=models.BooleanField(default=False, verbose_name="یادآوری تولد"),
        ),
    ]
