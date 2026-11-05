from django.db import migrations


def restore_early_rising_habit(apps, schema_editor):
    Habit = apps.get_model("habits", "Habit")
    Habit.objects.filter(
        title="سحرخیزی (قبل 08:00)",
        emoji="☀️",
        habit_type="quantitative",
        min_value=4,
        max_value=9,
        unit="ساعت",
    ).update(
        title="سحرخیزی (07:00)",
        habit_type="boolean",
        min_value=0,
        max_value=100,
        unit=None,
        default_value=None,
    )


def reapply_updated_early_rising_habit(apps, schema_editor):
    Habit = apps.get_model("habits", "Habit")
    Habit.objects.filter(
        title="سحرخیزی (07:00)",
        emoji="☀️",
        habit_type="boolean",
        min_value=0,
        max_value=100,
        unit__isnull=True,
    ).update(
        title="سحرخیزی (قبل 08:00)",
        habit_type="quantitative",
        min_value=4,
        max_value=9,
        unit="ساعت",
    )


class Migration(migrations.Migration):

    dependencies = [
        ("habits", "0004_habit_defaults_and_skip_status"),
    ]

    operations = [
        migrations.RunPython(
            restore_early_rising_habit,
            reapply_updated_early_rising_habit,
        ),
    ]
