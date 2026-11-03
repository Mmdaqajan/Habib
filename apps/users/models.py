from django.db import models

class UserProfile(models.Model):
    name = models.CharField(max_length=100, default="محمد", verbose_name="نام")
    age = models.IntegerField(default=23, verbose_name="سن")
    email = models.EmailField(default="user@example.com", verbose_name="ایمیل / جیمیل")
    phone = models.CharField(max_length=20, default="09120000000", verbose_name="شماره تلفن")
    profile_pic = models.ImageField(upload_to='profiles/', blank=True, null=True, verbose_name="تصویر پروفایل")

    def __str__(self):
        return self.name
