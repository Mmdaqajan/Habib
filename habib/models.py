from django.db import models

class Habit(models.Model):
    TYPE_BOOLEAN = 'boolean'
    TYPE_QUANTITATIVE = 'quantitative'
    TYPE_CHOICES = [
        (TYPE_BOOLEAN, 'تیک زدنی (بله/خیر)'),
        (TYPE_QUANTITATIVE, 'مقدارپذیر (عددی)'),
    ]

    title = models.CharField(max_length=200, verbose_name="عنوان عادت")
    emoji = models.CharField(max_length=10, default="📌", verbose_name="ایموجی / آیکون")
    habit_type = models.CharField(max_length=20, choices=TYPE_CHOICES, default=TYPE_BOOLEAN, verbose_name="نوع عادت")
    min_value = models.IntegerField(default=0, verbose_name="حداقل مقدار")
    max_value = models.IntegerField(default=100, verbose_name="حداکثر مقدار")
    unit = models.CharField(max_length=50, blank=True, null=True, verbose_name="واحد (صفحه، دقیقه، ساعت و...)")
    order = models.PositiveIntegerField(default=0, verbose_name="ترتیب نمایش")
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ['order', 'id']

    def __str__(self):
        return f"{self.emoji} {self.title}"

class HabitLog(models.Model):
    habit = models.ForeignKey(Habit, on_delete=models.CASCADE, related_name='logs')
    date = models.DateField(verbose_name="تاریخ")
    completed = models.BooleanField(default=False, verbose_name="انجام شده")
    value = models.IntegerField(default=0, verbose_name="مقدار ثبت شده")

    class Meta:
        unique_together = ('habit', 'date')

    def __str__(self):
        return f"{self.habit.title} - {self.date}"

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
        FREQ_DAILY: 'blue',
        FREQ_WEEKLY: 'green',
        FREQ_MONTHLY: 'orange',
        FREQ_YEARLY: 'gold',
    }

    title = models.CharField(max_length=255, verbose_name="متن یادآوری")
    frequency = models.CharField(max_length=20, choices=FREQ_CHOICES, default=FREQ_DAILY, verbose_name="بازه زمانی")
    time_str = models.CharField(max_length=50, blank=True, null=True, verbose_name="ساعت / روز یادآوری")
    created_at = models.DateTimeField(auto_now_add=True)

    @property
    def color_code(self):
        return self.COLOR_MAP.get(self.frequency, 'blue')

    def __str__(self):
        return f"[{self.get_frequency_display()}] {self.title}"

class UserProfile(models.Model):
    name = models.CharField(max_length=100, default="محمد", verbose_name="نام")
    age = models.IntegerField(default=23, verbose_name="سن")
    email = models.EmailField(default="user@example.com", verbose_name="ایمیل / جیمیل")
    phone = models.CharField(max_length=20, default="09120000000", verbose_name="شماره تلفن")
    profile_pic = models.ImageField(upload_to='profiles/', blank=True, null=True, verbose_name="تصویر پروفایل")

    def __str__(self):
        return self.name
