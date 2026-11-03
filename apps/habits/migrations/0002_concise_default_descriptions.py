from django.db import migrations


DESCRIPTION_CHANGES = {
    "روزانه حداقل ۱۰ صفحه مطالعه برای رشد فردی و کنکور.": "مطالعه برای رشد فردی و کنکور",
    "تمرین باشگاه یا پیاده‌روی سریع روزانه.": "باشگاه یا پیاده‌روی سریع",
    "بیدار شدن رأس ساعت ۷ صبح برای شروع پرانرژی روز.": "بیداری ساعت ۷",
    "تمرین زبان انگلیسی، لغات و ریدینگ.": "لغات و ریدینگ انگلیسی",
}


def shorten_default_descriptions(apps, schema_editor):
    Habit = apps.get_model("habits", "Habit")
    for old_description, new_description in DESCRIPTION_CHANGES.items():
        Habit.objects.filter(description=old_description).update(description=new_description)


def restore_default_descriptions(apps, schema_editor):
    Habit = apps.get_model("habits", "Habit")
    for old_description, new_description in DESCRIPTION_CHANGES.items():
        Habit.objects.filter(description=new_description).update(description=old_description)


class Migration(migrations.Migration):

    dependencies = [
        ("habits", "0001_initial"),
    ]

    operations = [
        migrations.RunPython(shorten_default_descriptions, restore_default_descriptions),
    ]
