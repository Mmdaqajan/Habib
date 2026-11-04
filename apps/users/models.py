import datetime

from django.db import models
from django.utils import timezone

class UserProfile(models.Model):
    PROFILE_PIC_POSITION_CHOICES = [
        ("center", "Center"),
        ("top", "Top"),
        ("bottom", "Bottom"),
        ("left", "Left"),
        ("right", "Right"),
    ]

    name = models.CharField(max_length=100, default="محمد", verbose_name="نام")
    age = models.IntegerField(default=23, verbose_name="سن")
    birth_date = models.DateField(blank=True, null=True, verbose_name="تاریخ تولد")
    email = models.EmailField(default="user@example.com", verbose_name="ایمیل / جیمیل")
    phone = models.CharField(max_length=20, default="09120000000", verbose_name="شماره تلفن")
    profile_pic = models.ImageField(upload_to='profiles/', blank=True, null=True, verbose_name="تصویر پروفایل")
    profile_pic_position = models.CharField(
        max_length=10,
        choices=PROFILE_PIC_POSITION_CHOICES,
        default="center",
    )
    profile_pic_position_x = models.PositiveSmallIntegerField(default=50)
    profile_pic_position_y = models.PositiveSmallIntegerField(default=50)

    def __str__(self):
        return self.name

    @property
    def calculated_age(self):
        if not self.birth_date:
            return self.age
        today = timezone.localdate()
        return today.year - self.birth_date.year - (
            (today.month, today.day) < (self.birth_date.month, self.birth_date.day)
        )


class AchievementUnlock(models.Model):
    code = models.CharField(max_length=100, unique=True)
    title = models.CharField(max_length=255)
    description = models.CharField(max_length=255)
    xp_reward = models.PositiveIntegerField()
    unlocked_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ["unlocked_at", "id"]

    def __str__(self):
        return f"{self.title} (+{self.xp_reward} XP)"
