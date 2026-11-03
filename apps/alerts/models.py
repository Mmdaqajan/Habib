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
        (FREQ_YEARLY, 'سالانه (بنفش)'),
    ]

    COLOR_MAP = {
        FREQ_DAILY: 'blue',
        FREQ_WEEKLY: 'green',
        FREQ_MONTHLY: 'orange',
        FREQ_YEARLY: 'purple',
    }

    title = models.CharField(max_length=255, verbose_name="متن یادآوری")
    frequency = models.CharField(max_length=20, choices=FREQ_CHOICES, default=FREQ_DAILY, verbose_name="بازه زمانی")
    time_str = models.CharField(max_length=100, blank=True, null=True, verbose_name="زمان یادآوری")
    created_at = models.DateTimeField(auto_now_add=True)

    @property
    def color_code(self):
        return self.COLOR_MAP.get(self.frequency, 'blue')

    def __str__(self):
        return f"[{self.get_frequency_display()}] {self.title}"
