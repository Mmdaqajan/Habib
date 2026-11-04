from django.db import migrations, models


def update_early_rising_habit(apps, schema_editor):
    Habit = apps.get_model("habits", "Habit")
    Habit.objects.filter(title="سحرخیزی (07:00)").update(
        title="سحرخیزی (قبل 08:00)",
        habit_type="quantitative",
        min_value=4,
        max_value=9,
        unit="ساعت",
    )


class Migration(migrations.Migration):

    dependencies = [
        ("habits", "0003_update_default_quotes"),
    ]

    operations = [
        migrations.AddField(
            model_name="habit",
            name="default_value",
            field=models.IntegerField(blank=True, null=True, verbose_name="مقدار پیش‌فرض"),
        ),
        migrations.AddField(
            model_name="habitlog",
            name="skipped",
            field=models.BooleanField(default=False, verbose_name="انجام نشده"),
        ),
        migrations.RunPython(update_early_rising_habit, migrations.RunPython.noop),
    ]
