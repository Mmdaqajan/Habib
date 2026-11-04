from django.db import models

class Alert(models.Model):
    FREQ_DAILY = 'daily'
    FREQ_WEEKLY = 'weekly'
    FREQ_MONTHLY = 'monthly'
    FREQ_YEARLY = 'yearly'
    FREQ_CHOICES = [
        (FREQ_DAILY, 'روزانه'),
        (FREQ_WEEKLY, 'هفتگی'),
        (FREQ_MONTHLY, 'ماهانه'),
        (FREQ_YEARLY, 'سالانه'),
    ]

    COLOR_MAP = {
        FREQ_DAILY: 'light-blue',
        FREQ_WEEKLY: 'pale-blue',
        FREQ_MONTHLY: 'strong-blue',
        FREQ_YEARLY: 'purple',
    }

    title = models.CharField(max_length=255, verbose_name="متن یادآوری")
    description = models.TextField(blank=True, default="", verbose_name="توضیحات")
    frequency = models.CharField(max_length=20, choices=FREQ_CHOICES, default=FREQ_DAILY, verbose_name="بازه زمانی")
    time_str = models.CharField(max_length=100, blank=True, null=True, verbose_name="زمان یادآوری")
    advance_notice_value = models.PositiveSmallIntegerField(
        default=0,
        verbose_name="مقدار پیش‌آگاهی (ساعت یا روز)",
    )
    is_birthday = models.BooleanField(default=False, verbose_name="یادآوری تولد")
    created_at = models.DateTimeField(auto_now_add=True)

    @property
    def color_code(self):
        return self.COLOR_MAP.get(self.frequency, 'blue')

    def __str__(self):
        return f"[{self.get_frequency_display()}] {self.title}"


class CalendarEvent(models.Model):
    date = models.DateField(verbose_name="تاریخ")
    title = models.CharField(max_length=255, verbose_name="عنوان رویداد")
    color = models.CharField(max_length=7, verbose_name="رنگ")
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ["date", "id"]

    def __str__(self):
        return f"{self.date} - {self.title}"
