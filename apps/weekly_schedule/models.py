from datetime import time

from django.db import models


class WeeklySchedule(models.Model):
    sleep_time = models.TimeField(default=time(22, 0))
    wake_time = models.TimeField(default=time(6, 0))

    class Meta:
        verbose_name = 'تنظیم برنامه هفتگی'
        verbose_name_plural = 'تنظیمات برنامه هفتگی'

    def __str__(self):
        return 'برنامه هفتگی'


class ScheduleDay(models.Model):
    SATURDAY = 0
    SUNDAY = 1
    MONDAY = 2
    TUESDAY = 3
    WEDNESDAY = 4
    THURSDAY = 5
    FRIDAY = 6
    WEEKDAYS = [
        (SATURDAY, 'شنبه'),
        (SUNDAY, 'یکشنبه'),
        (MONDAY, 'دوشنبه'),
        (TUESDAY, 'سه‌شنبه'),
        (WEDNESDAY, 'چهارشنبه'),
        (THURSDAY, 'پنج‌شنبه'),
        (FRIDAY, 'جمعه'),
    ]

    schedule = models.ForeignKey(
        WeeklySchedule,
        on_delete=models.CASCADE,
        related_name='days',
    )
    weekday = models.PositiveSmallIntegerField(choices=WEEKDAYS)
    work_start = models.TimeField(null=True, blank=True)
    work_end = models.TimeField(null=True, blank=True)
    gym_start = models.TimeField(null=True, blank=True)
    gym_end = models.TimeField(null=True, blank=True)

    class Meta:
        ordering = ['weekday']
        constraints = [
            models.UniqueConstraint(
                fields=['schedule', 'weekday'],
                name='weekly_schedule_unique_day',
            ),
        ]

    def __str__(self):
        return self.get_weekday_display()


class WeeklyActivity(models.Model):
    name = models.CharField(max_length=100)

    class Meta:
        ordering = ['name', 'pk']
        verbose_name = 'فعالیت برنامه هفتگی'
        verbose_name_plural = 'فعالیت‌های برنامه هفتگی'

    def __str__(self):
        return self.name


class ActivitySlot(models.Model):
    activity = models.ForeignKey(
        WeeklyActivity,
        on_delete=models.CASCADE,
        related_name='slots',
    )
    weekday = models.PositiveSmallIntegerField(choices=ScheduleDay.WEEKDAYS)
    start_time = models.TimeField()
    end_time = models.TimeField()

    class Meta:
        ordering = ['weekday', 'start_time']
        constraints = [
            models.UniqueConstraint(
                fields=['activity', 'weekday'],
                name='weekly_activity_unique_day',
            ),
        ]

    def __str__(self):
        return f'{self.activity} - {self.get_weekday_display()}'
