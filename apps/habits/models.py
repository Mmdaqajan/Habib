from django.db import models

class Quote(models.Model):
    text = models.TextField(verbose_name="متن بنر انگیزشی")

    def __str__(self):
        return self.text[:30]

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
    unit = models.CharField(max_length=50, blank=True, null=True, verbose_name="واحد")
    description = models.TextField(blank=True, null=True, verbose_name="توضیحات و روتین شخصی")
    is_deleted = models.BooleanField(default=False, verbose_name="حذف منطقی")
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
    value = models.IntegerField(blank=True, null=True, verbose_name="مقدار ثبت شده")

    class Meta:
        unique_together = ('habit', 'date')

    def __str__(self):
        return f"{self.habit.title} - {self.date}"
