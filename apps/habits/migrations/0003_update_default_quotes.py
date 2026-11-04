from django.db import migrations


QUOTE_CHANGES = {
    "عادت های تو هویتت رو، میزان ارادتو و آیندتو تعین میکنن":
        "عادت های تو هویتت و میزان اراده ات و آینده ات رو تعین میکنن . کلمات این جمله با دقت انتخاب شده !",
    "انسان های موفق مرز شفافی بین امور مذاکره پذیر و مذاکره ناپذیر زندگیشون دارن":
        "آدم‌های موفق مرز روشنی بین کارهای قابل مذاکره و غیرقابل مذاکره دارند.",
    "موفقیت هزارتا گام کوچیکه نه یک گام بزرگ ! این گام میتونه سر زدن به حبیب باشه :)":
        "موفقیت هزاران قدم کوچیکه نه یک قدم بزرگ، این قدم میتونه سر زدن به حبیب باشه :)",
    "وقتی راجع به برنامه هات با بقیه صحبت میکنی باعث میشه میلت برای انجام دادنش کم بشه !":
        "وقتی دربارهٔ برنامه‌هایت با دیگران حرف می‌زنی، گاهی انگیزهٔ انجامشان کمتر می‌شود.",
}


def update_quotes(apps, schema_editor):
    Quote = apps.get_model("habits", "Quote")
    for old_text, new_text in QUOTE_CHANGES.items():
        Quote.objects.filter(text=old_text).update(text=new_text)


class Migration(migrations.Migration):
    dependencies = [
        ("habits", "0002_concise_default_descriptions"),
    ]

    operations = [
        migrations.RunPython(update_quotes, migrations.RunPython.noop),
    ]
