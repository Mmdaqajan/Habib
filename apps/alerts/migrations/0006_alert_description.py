from django.db import migrations, models


DEFAULT_DESCRIPTIONS = {
    "برنامه‌ریزی هفتگی": "بهترین کاری که میتونی جمعه ها انجام بدی نوشتن برنامه ی هفته ی آینده ست",
    "کاشت درخت": "ی تصمیم خوب و ماندگار :)",
}


def add_default_descriptions(apps, schema_editor):
    Alert = apps.get_model("alerts", "Alert")
    for title, description in DEFAULT_DESCRIPTIONS.items():
        Alert.objects.filter(title=title, description="").update(description=description)


class Migration(migrations.Migration):

    dependencies = [
        ("alerts", "0005_alert_is_birthday"),
    ]

    operations = [
        migrations.AddField(
            model_name="alert",
            name="description",
            field=models.TextField(blank=True, default="", verbose_name="توضیحات"),
        ),
        migrations.RunPython(add_default_descriptions, migrations.RunPython.noop),
    ]
