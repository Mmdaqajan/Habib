import datetime
import re

from django.contrib import messages
from django.core.exceptions import ValidationError
from django.core.validators import validate_email
from django.shortcuts import render, redirect
from django.utils import timezone
from django.utils.http import url_has_allowed_host_and_scheme
import jdatetime

from apps.alerts.models import Alert
from .models import UserProfile
from .achievements import get_achievement_progress

PERSIAN_MONTHS = (
    "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
    "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند",
)


def _settings_return_url(request):
    return_url = request.POST.get("next", "")
    if not url_has_allowed_host_and_scheme(return_url, allowed_hosts={request.get_host()}):
        return_url = "achievements"
    return return_url


def _ascii_digits(value):
    return str(value).translate(str.maketrans("۰۱۲۳۴۵۶۷۸۹٠١٢٣٤٥٦٧٨٩", "01234567890123456789"))


def _birthday_alert(profile):
    birthday_alerts = Alert.objects.filter(is_birthday=True).order_by("id")
    if not profile.birth_date:
        birthday_alerts.delete()
        return

    jalali_birth_date = jdatetime.date.fromgregorian(date=profile.birth_date)
    title = f"تولد {profile.name}"
    time_str = (
        f"{jalali_birth_date.day} {PERSIAN_MONTHS[jalali_birth_date.month - 1]} "
        f"{jalali_birth_date.year} - ساعت 09:00"
    )
    alert = birthday_alerts.first()
    if alert is None:
        alert = Alert(is_birthday=True)
    alert.title = title
    alert.frequency = Alert.FREQ_YEARLY
    alert.time_str = time_str
    alert.advance_notice_value = 7
    alert.save()
    birthday_alerts.exclude(pk=alert.pk).delete()


def achievements_view(request):
    context = get_achievement_progress()
    return render(request, "users/achievements.html", context)


def profile_edit_view(request):
    profile = UserProfile.objects.first() or UserProfile()
    birth_date_parts = {"day": "", "month": "", "year": ""}
    if profile.birth_date:
        jalali_date = jdatetime.date.fromgregorian(date=profile.birth_date)
        birth_date_parts = {
            "day": jalali_date.day,
            "month": jalali_date.month,
            "year": jalali_date.year,
        }
    return render(request, "users/profile_edit.html", {
        "profile": profile,
        "birth_date_parts": birth_date_parts,
    })


def profile_view(request):
    if request.method != "POST":
        return redirect("achievements")

    name = request.POST.get("name", "").strip()
    email = request.POST.get("email", "").strip()
    phone = _ascii_digits(request.POST.get("phone", "").strip())
    birth_date_value = request.POST.get("birth_date", "").strip()

    if not name or len(name) > 100:
        messages.error(request, "نام کاربری را وارد کنید؛ حداکثر ۱۰۰ نویسه.")
        return redirect(_settings_return_url(request))
    if not email or len(email) > 254:
        messages.error(request, "ایمیل را وارد کنید؛ حداکثر ۲۵۴ نویسه.")
        return redirect(_settings_return_url(request))
    try:
        validate_email(email)
    except ValidationError:
        messages.error(request, "ایمیل واردشده معتبر نیست.")
        return redirect(_settings_return_url(request))
    if phone and not re.fullmatch(r"\+?[0-9]{7,15}", phone):
        messages.error(request, "شماره تلفن باید ۷ تا ۱۵ رقم باشد و می‌تواند با + شروع شود.")
        return redirect(_settings_return_url(request))

    birth_date = None
    if birth_date_value:
        try:
            birth_date = datetime.date.fromisoformat(birth_date_value)
        except ValueError:
            messages.error(request, "تاریخ تولد واردشده معتبر نیست.")
            return redirect(_settings_return_url(request))
    else:
        jalali_parts = {
            key: _ascii_digits(request.POST.get(f"birth_{key}", "").strip())
            for key in ("day", "month", "year")
        }
        if any(jalali_parts.values()):
            if not all(jalali_parts.values()):
                messages.error(request, "روز، ماه و سال تولد را کامل وارد کنید.")
                return redirect(_settings_return_url(request))
            try:
                birth_date = jdatetime.date(
                    int(jalali_parts["year"]),
                    int(jalali_parts["month"]),
                    int(jalali_parts["day"]),
                ).togregorian()
            except (TypeError, ValueError, OverflowError):
                messages.error(request, "تاریخ تولد شمسی واردشده معتبر نیست.")
                return redirect(_settings_return_url(request))

    if birth_date:
        today = timezone.localdate()
        age = today.year - birth_date.year - (
            (today.month, today.day) < (birth_date.month, birth_date.day)
        )
        if birth_date > today or age > 120:
            messages.error(request, "تاریخ تولد باید معتبر باشد و سن از ۱۲۰ سال بیشتر نشود.")
            return redirect(_settings_return_url(request))

    profile, _ = UserProfile.objects.get_or_create(id=1)
    profile.name = name
    profile.birth_date = birth_date
    profile.email = email
    profile.phone = phone
    uploaded_picture = request.FILES.get("profile_pic")
    if request.POST.get("remove_profile_pic") == "on" and not uploaded_picture:
        if profile.profile_pic:
            profile.profile_pic.delete(save=False)
        profile.profile_pic = None
    if uploaded_picture:
        profile.profile_pic = request.FILES["profile_pic"]
    profile.save()
    _birthday_alert(profile)

    return redirect(_settings_return_url(request))
