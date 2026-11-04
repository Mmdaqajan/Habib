import datetime
import re
import jdatetime
from urllib.parse import urlencode
from django.contrib import messages
from django.shortcuts import render, redirect, get_object_or_404
from django.urls import reverse
from .models import Alert, CalendarEvent

EVENT_COLORS = {
    "قرمز": "#ef4444",
    "نارنجی پررنگ": "#f97316",
    "نارنجی کمرنگ": "#fb923c",
    "زرد": "#facc15",
}
ALERT_COLORS = {
    Alert.FREQ_DAILY: "#38bdf8",
    Alert.FREQ_WEEKLY: "#93c5fd",
    Alert.FREQ_MONTHLY: "#2563eb",
    Alert.FREQ_YEARLY: "#a855f7",
}
PERSIAN_MONTHS = [
    "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
    "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند",
]
PERSIAN_WEEKDAYS = ["شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنج‌شنبه", "جمعه"]
PERSIAN_ORDINALS = {
    "اول": 1, "یکم": 1, "دوم": 2, "سوم": 3, "چهارم": 4, "پنجم": 5,
    "ششم": 6, "هفتم": 7, "هشتم": 8, "نهم": 9, "دهم": 10,
    "یازدهم": 11, "دوازدهم": 12, "سیزدهم": 13, "چهاردهم": 14,
    "پانزدهم": 15, "شانزدهم": 16, "هفدهم": 17, "هجدهم": 18,
    "نوزدهم": 19, "بیستم": 20, "بیست و یکم": 21, "بیست و دوم": 22,
    "بیست و سوم": 23, "بیست و چهارم": 24, "بیست و پنجم": 25,
    "بیست و ششم": 26, "بیست و هفتم": 27, "بیست و هشتم": 28,
    "بیست و نهم": 29, "سی‌ام": 30, "سی ام": 30, "سی و یکم": 31,
}


def _persian_digits(value):
    return str(value).translate(str.maketrans("0123456789", "۰۱۲۳۴۵۶۷۸۹"))


def _ascii_digits(value):
    return str(value).translate(str.maketrans("۰۱۲۳۴۵۶۷۸۹٠١٢٣٤٥٦٧٨٩", "01234567890123456789"))


def _alert_day_number(time_str):
    normalized = time_str.replace("\u200c", " ")
    for ordinal, number in sorted(PERSIAN_ORDINALS.items(), key=lambda item: -len(item[0])):
        if ordinal in normalized:
            return number
    match = re.search(r"\d{1,2}", _ascii_digits(normalized))
    return int(match.group()) if match else None


def _alert_weekdays(time_str):
    schedule = (time_str or "").split(" - ساعت ", 1)[0]
    schedule = re.sub(r"^(?:هر|روزهای)\s*", "", schedule)
    weekdays = []
    for day in re.split(r"[،,]", schedule):
        day = day.replace("\u200c", "").strip()
        if day.endswith("ها"):
            day = day[:-2]
        if day in PERSIAN_WEEKDAYS and day not in weekdays:
            weekdays.append(day)
    return weekdays


def _alert_occurs_on_date(alert, selected_jalali):
    time_str = alert.time_str or ""
    if alert.frequency == Alert.FREQ_DAILY:
        return True
    if alert.frequency == Alert.FREQ_WEEKLY:
        return PERSIAN_WEEKDAYS[selected_jalali.weekday()] in _alert_weekdays(time_str)
    if alert.frequency == Alert.FREQ_MONTHLY:
        return _alert_day_number(time_str) == selected_jalali.day
    if alert.frequency == Alert.FREQ_YEARLY:
        month_index = next(
            (index + 1 for index, name in enumerate(PERSIAN_MONTHS) if name in time_str),
            None,
        )
        return month_index == selected_jalali.month and _alert_day_number(time_str) == selected_jalali.day
    return False


def _alert_form_values(alert):
    time_str = alert.time_str or ""
    normalized = _ascii_digits(time_str)
    time_match = re.search(r"(?<!\d)([01]?\d|2[0-3]):([0-5]\d)(?!\d)", normalized)
    values = {
        "time": time_match.group() if time_match else "09:00",
        "weekday": "شنبه",
        "weekdays": ["شنبه"],
        "month_day": 1,
        "year_day": 1,
        "year_month": 1,
        "year_value": jdatetime.date.today().year,
        "advance_notice_value": alert.advance_notice_value,
    }

    if alert.frequency == Alert.FREQ_WEEKLY:
        values["weekdays"] = _alert_weekdays(time_str) or ["شنبه"]
    elif alert.frequency == Alert.FREQ_MONTHLY:
        values["month_day"] = _alert_day_number(time_str) or 1
    elif alert.frequency == Alert.FREQ_YEARLY:
        values["year_day"] = _alert_day_number(time_str) or 1
        values["year_month"] = next(
            (index for index, name in enumerate(PERSIAN_MONTHS, start=1) if name in time_str),
            1,
        )
        year_match = re.search(r"(?<!\d)\d{4}(?!\d)", normalized)
        if year_match:
            values["year_value"] = int(year_match.group())
    return values


def _build_advance_notice_value(data, frequency):
    enabled = data.get("advance_notice_enabled") in {"on", "true", "1"}
    if not enabled:
        return 0, None

    try:
        value = int(_ascii_digits(data.get("advance_notice_value", "")))
    except (TypeError, ValueError):
        return None, "مقدار پیش‌آگاهی را وارد کنید."

    unit = "ساعت" if frequency == Alert.FREQ_DAILY else "روز"
    maximum = 23 if frequency == Alert.FREQ_DAILY else 365
    if not 1 <= value <= maximum:
        return None, f"پیش‌آگاهی باید بین ۱ تا {_persian_digits(maximum)} {unit} باشد."
    return value, None


def _advance_notice_message(alert):
    if not alert.advance_notice_value:
        return ""
    unit = "ساعت" if alert.frequency == Alert.FREQ_DAILY else "روز"
    return (
        f"{_persian_digits(alert.advance_notice_value)} {unit} دیگه باید "
        f"{alert.title} بدی"
    )


def _build_alert_time_string(data, frequency):
    time_val = _ascii_digits(data.get("time_val", "")).strip()
    if not re.fullmatch(r"(?:[01]?\d|2[0-3]):[0-5]\d", time_val):
        return None, "زمان یادآوری را به شکل ساعت و دقیقه وارد کنید."

    if frequency == Alert.FREQ_DAILY:
        return f"ساعت {time_val}", None
    if frequency == Alert.FREQ_WEEKLY:
        weekdays = data.getlist("weekday") if hasattr(data, "getlist") else data.get("weekday", [])
        if isinstance(weekdays, str):
            weekdays = [weekdays]
        weekdays = list(dict.fromkeys(weekdays))
        if not weekdays or any(day not in PERSIAN_WEEKDAYS for day in weekdays):
            return None, "حداقل یک روز معتبر برای یادآوری هفتگی انتخاب کنید."
        if len(weekdays) == len(PERSIAN_WEEKDAYS):
            return None, "این که شد الارم روزانه عزیزم"
        schedule = "، ".join(day for day in PERSIAN_WEEKDAYS if day in weekdays)
        return f"روزهای {schedule} - ساعت {time_val}", None
    if frequency == Alert.FREQ_MONTHLY:
        try:
            day = int(_ascii_digits(data.get("month_day", "")))
        except (TypeError, ValueError):
            return None, "روز ماه معتبر نیست."
        if not 1 <= day <= 31:
            return None, "روز ماه باید بین ۱ تا ۳۱ باشد."
        return f"روز {_persian_digits(day)} ماه - ساعت {time_val}", None
    if frequency == Alert.FREQ_YEARLY:
        try:
            day = int(_ascii_digits(data.get("year_day", "")))
            month = int(_ascii_digits(data.get("year_month", "")))
            year = int(_ascii_digits(data.get("year_value", "")))
            jdatetime.date(year, month, day)
        except (TypeError, ValueError):
            return None, "تاریخ شمسی واردشده معتبر نیست."
        return (
            f"{_persian_digits(day)} {PERSIAN_MONTHS[month - 1]} "
            f"{_persian_digits(year)} - ساعت {time_val}",
            None,
        )
    return None, "نوع یادآوری معتبر نیست."


def _calendar_url(month_offset, selected_date, filter_type):
    query = {
        "month": month_offset,
        "date": selected_date.isoformat(),
    }
    if filter_type:
        query["filter"] = filter_type
    return f"{reverse('alerts')}?{urlencode(query)}"


def _calendar_redirect(month_offset, selected_date, filter_type):
    return redirect(_calendar_url(month_offset, selected_date, filter_type))

def init_default_alerts():
    if Alert.objects.count() == 0:
        defaults = [
            {"title": "ثبت عادت‌های امروز", "frequency": Alert.FREQ_DAILY, "time_str": "ساعت 21:00"},
            {
                "title": "برنامه‌ریزی هفتگی",
                "description": "بهترین کاری که میتونی جمعه ها انجام بدی نوشتن برنامه ی هفته ی آینده ست",
                "frequency": Alert.FREQ_WEEKLY,
                "time_str": "جمعه‌ها - ساعت 18:00",
            },
            {"title": "پرداخت شهریه باشگاه", "frequency": Alert.FREQ_MONTHLY, "time_str": "روز 1 ماه - ساعت 10:00"},
            {
                "title": "کاشت درخت",
                "description": "ی تصمیم خوب و ماندگار :)",
                "frequency": Alert.FREQ_YEARLY,
                "time_str": "15 آذر - ساعت 09:00",
            },
        ]
        for a in defaults:
            Alert.objects.create(**a)

def alerts_view(request):
    init_default_alerts()
    valid_filters = {choice[0] for choice in Alert.FREQ_CHOICES} | {"all"}
    requested_filter = request.GET.get("filter", "")
    filter_type = requested_filter if requested_filter in valid_filters else ""
    alerts = Alert.objects.all()
    for alert in alerts:
        alert.form_values = _alert_form_values(alert)
        alert.advance_notice_message = _advance_notice_message(alert)
        alert.calendar_color = ALERT_COLORS[alert.frequency]

    try:
        month_offset = max(-120, min(120, int(request.GET.get("month", "0"))))
    except (TypeError, ValueError):
        month_offset = 0

    today = datetime.date.today()
    today_jalali = jdatetime.date.fromgregorian(date=today)
    month_index = today_jalali.year * 12 + today_jalali.month - 1 + month_offset
    jalali_year, zero_based_month = divmod(month_index, 12)
    month_start = jdatetime.date(jalali_year, zero_based_month + 1, 1)
    next_month = (
        jdatetime.date(jalali_year + 1, 1, 1)
        if month_start.month == 12 else jdatetime.date(jalali_year, month_start.month + 1, 1)
    )
    days_in_month = (next_month - month_start).days
    month_start_gregorian = month_start.togregorian()
    month_end_gregorian = (next_month - jdatetime.timedelta(days=1)).togregorian()

    selected_date = None
    try:
        requested_date = datetime.date.fromisoformat(request.GET.get("date", ""))
        requested_jalali = jdatetime.date.fromgregorian(date=requested_date)
        if (requested_jalali.year, requested_jalali.month) == (month_start.year, month_start.month):
            selected_date = requested_date
    except (TypeError, ValueError):
        pass
    if selected_date is None:
        selected_date = today if month_offset == 0 else month_start_gregorian

    events_by_date = {}
    events = CalendarEvent.objects.filter(date__range=(month_start_gregorian, month_end_gregorian))
    for event in events:
        events_by_date.setdefault(event.date.isoformat(), []).append(event)

    alert_rows = list(alerts)
    weekday_labels = ["شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنج‌شنبه", "جمعه"]
    first_weekday = (month_start_gregorian.weekday() + 2) % 7
    calendar_days = [{"empty": True} for _ in range(first_weekday)]
    for day_number in range(1, days_in_month + 1):
        jalali_date = month_start + jdatetime.timedelta(days=day_number - 1)
        gregorian_date = jalali_date.togregorian()
        date_key = gregorian_date.isoformat()
        day_alerts = [
            alert for alert in alert_rows
            if alert.frequency != Alert.FREQ_DAILY
            and _alert_occurs_on_date(alert, jalali_date)
        ]
        calendar_days.append({
            "gdate": date_key,
            "day": day_number,
            "day_display": _persian_digits(day_number),
            "status": "past" if gregorian_date < today else ("today" if gregorian_date == today else "future"),
            "events": events_by_date.get(date_key, []),
            "alerts": day_alerts,
            "selected": gregorian_date == selected_date,
        })
    calendar_days.extend({"empty": True} for _ in range((-len(calendar_days)) % 7))
    selected_jalali = jdatetime.date.fromgregorian(date=selected_date)
    selected_alerts = [
        alert for alert in alert_rows
        if _alert_occurs_on_date(alert, selected_jalali)
    ]

    def month_offset_for(date):
        jalali_date = jdatetime.date.fromgregorian(date=date)
        return (jalali_date.year - today_jalali.year) * 12 + jalali_date.month - today_jalali.month

    previous_date = selected_date - datetime.timedelta(days=1)
    next_date = selected_date + datetime.timedelta(days=1)
    selected_date_links = {
        "previous": _calendar_url(month_offset_for(previous_date), previous_date, filter_type),
        "next": _calendar_url(month_offset_for(next_date), next_date, filter_type),
        "today": _calendar_url(0, today, filter_type),
    }

    now_time = datetime.datetime.now().strftime("%H:%M")

    context = {
        'alerts': alerts,
        'current_filter': filter_type,
        'calendar_days': calendar_days,
        'weekday_labels': weekday_labels,
        'month_offset': month_offset,
        'month_title': f"{PERSIAN_MONTHS[month_start.month - 1]} {_persian_digits(month_start.year)}",
        'selected_date': selected_date,
        'selected_date_key': selected_date.isoformat(),
        'selected_date_jalali': (
            f"{_persian_digits(selected_jalali.day)} "
            f"{PERSIAN_MONTHS[selected_jalali.month - 1]} {_persian_digits(selected_jalali.year)}"
        ),
        'selected_events': events_by_date.get(selected_date.isoformat(), []),
        'selected_alerts': selected_alerts,
        'selected_date_links': selected_date_links,
        'event_colors': EVENT_COLORS,
        'jalali_months': PERSIAN_MONTHS,
        'has_filter': bool(filter_type),
        'now_time': now_time,
        'current_jalali_year': today_jalali.year,
    }
    return render(request, 'alerts/alerts.html', context)


def _calendar_post_context(request):
    try:
        month_offset = max(-120, min(120, int(request.POST.get("month", "0"))))
    except (TypeError, ValueError):
        month_offset = 0
    filter_type = request.POST.get("filter", "")
    if filter_type not in {choice[0] for choice in Alert.FREQ_CHOICES} | {"all"}:
        filter_type = ""
    return month_offset, filter_type


def add_calendar_event(request):
    if request.method != "POST":
        return redirect("alerts")

    try:
        selected_date = datetime.date.fromisoformat(request.POST.get("date", ""))
    except (TypeError, ValueError):
        messages.error(request, "تاریخ رویداد معتبر نیست.")
        return redirect("alerts")

    title = request.POST.get("title", "").strip()
    color = request.POST.get("color", "")
    if not title or len(title) > 255:
        messages.error(request, "عنوان رویداد باید بین ۱ تا ۲۵۵ نویسه باشد.")
    elif color not in EVENT_COLORS.values():
        messages.error(request, "رنگ انتخاب‌شده معتبر نیست.")
    else:
        CalendarEvent.objects.create(date=selected_date, title=title, color=color)

    month_offset, filter_type = _calendar_post_context(request)
    return _calendar_redirect(month_offset, selected_date, filter_type)


def delete_calendar_event(request, event_id):
    event = get_object_or_404(CalendarEvent, id=event_id)
    if request.method == "POST":
        selected_date = event.date
        event.delete()
        month_offset, filter_type = _calendar_post_context(request)
        return _calendar_redirect(month_offset, selected_date, filter_type)
    return redirect("alerts")


def edit_calendar_event(request, event_id):
    event = get_object_or_404(CalendarEvent, id=event_id)
    if request.method == "POST":
        title = request.POST.get("title", "").strip()
        color = request.POST.get("color", "")
        if not title or len(title) > 255:
            messages.error(request, "عنوان رویداد باید بین ۱ تا ۲۵۵ نویسه باشد.")
        elif color not in EVENT_COLORS.values():
            messages.error(request, "رنگ انتخاب‌شده معتبر نیست.")
        else:
            event.title = title
            event.color = color
            event.save(update_fields=["title", "color"])
        month_offset, filter_type = _calendar_post_context(request)
        return _calendar_redirect(month_offset, event.date, filter_type)
    return redirect("alerts")


def add_alert(request):
    if request.method == 'POST':
        title = request.POST.get("title", "").strip()
        description = request.POST.get("description", "").strip()
        frequency = request.POST.get("frequency", Alert.FREQ_DAILY)
        valid_frequencies = {choice[0] for choice in Alert.FREQ_CHOICES}
        detail_time, error = _build_alert_time_string(request.POST, frequency)
        if not title or len(title) > 255:
            messages.error(request, "عنوان یادآوری باید بین ۱ تا ۲۵۵ نویسه باشد.")
        elif len(description) > 1000:
            messages.error(request, "توضیحات یادآوری نمی‌تواند بیشتر از ۱۰۰۰ نویسه باشد.")
        elif frequency not in valid_frequencies:
            messages.error(request, "نوع یادآوری معتبر نیست.")
        elif error:
            messages.error(request, error)
        else:
            advance_notice_value, notice_error = _build_advance_notice_value(request.POST, frequency)
            if notice_error:
                messages.error(request, notice_error)
            else:
                Alert.objects.create(
                    title=title,
                    description=description,
                    frequency=frequency,
                    time_str=detail_time,
                    advance_notice_value=advance_notice_value,
                )
        return redirect('alerts')
    return redirect('alerts')


def edit_alert(request, alert_id):
    alert = get_object_or_404(Alert, id=alert_id)
    if request.method == "POST":
        title = request.POST.get("title", "").strip()
        description = request.POST.get("description", "").strip()
        frequency = request.POST.get("frequency", "")
        valid_frequencies = {choice[0] for choice in Alert.FREQ_CHOICES}
        time_str, error = _build_alert_time_string(request.POST, frequency)
        advance_notice_value, notice_error = _build_advance_notice_value(request.POST, frequency)

        if not title or len(title) > 255:
            messages.error(request, "عنوان یادآوری باید بین ۱ تا ۲۵۵ نویسه باشد.")
        elif len(description) > 1000:
            messages.error(request, "توضیحات یادآوری نمی‌تواند بیشتر از ۱۰۰۰ نویسه باشد.")
        elif frequency not in valid_frequencies:
            messages.error(request, "نوع یادآوری معتبر نیست.")
        elif error:
            messages.error(request, error)
        elif notice_error:
            messages.error(request, notice_error)
        else:
            alert.title = title
            alert.description = description
            alert.frequency = frequency
            alert.time_str = time_str
            alert.advance_notice_value = advance_notice_value
            alert.save(update_fields=[
                "title", "description", "frequency", "time_str", "advance_notice_value",
            ])

        try:
            month_offset = max(-120, min(120, int(request.POST.get("month", "0"))))
        except (TypeError, ValueError):
            month_offset = 0
        try:
            selected_date = datetime.date.fromisoformat(request.POST.get("date", ""))
        except (TypeError, ValueError):
            selected_date = datetime.date.today()
        return _calendar_redirect(month_offset, selected_date, frequency if frequency in valid_frequencies else "")
    return redirect("alerts")


def delete_alert(request, alert_id):
    alert = get_object_or_404(Alert, id=alert_id)
    if request.method == 'POST':
        alert.delete()
        return redirect('alerts')
    return redirect('alerts')
