from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ("users", "0004_achievementunlock"),
    ]

    operations = [
        migrations.AddField(
            model_name="userprofile",
            name="birth_date",
            field=models.DateField(blank=True, null=True, verbose_name="تاریخ تولد"),
        ),
    ]
