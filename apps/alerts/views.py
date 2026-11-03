import datetime
import jdatetime
from django.shortcuts import render, redirect, get_object_or_404
from .models import Alert

def init_default_alerts():
    if Alert.objects.count() == 0:
        defaults = [
            {"title": "ثبت عادت‌های امروز", "frequency": Alert.FREQ_DAILY, "time_str": "ساعت 21:00"},
            {"title": "برنامه‌ریزی هفتگی", "frequency": Alert.FREQ_WEEKLY, "time_str": "جمعه‌ها - ساعت 18:00"},
            {"title": "پرداخت شهریه باشگاه", "frequency": Alert.FREQ_MONTHLY, "time_str": "روز 1 ماه - ساعت 10:00"},
            {"title": "کاشت درخت", "frequency": Alert.FREQ_YEARLY, "time_str": "15 آذر - ساعت 09:00"},
        ]
        for a in defaults:
            Alert.objects.create(**a)

def alerts_view(request):
    init_default_alerts()
    filter_type = request.GET.get('filter', 'all')
    if filter_type != 'all':
        alerts = Alert.objects.filter(frequency=filter_type)
    else:
        alerts = Alert.objects.all()

    today = datetime.date.today()
    calendar_days = []
    
    persian_weekdays = {
        0: 'دوشنبه', 1: 'سه‌شنبه', 2: 'چهارشنبه', 3: 'پنج‌شنبه', 4: 'جمعه', 5: 'شنبه', 6: 'یکشنبه',
    }
    persian_months = [
        "", "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    ]

    # Only 6 past days, today, and future days
    for i in range(-6, 10):
        d = today + datetime.timedelta(days=i)
        jd = jdatetime.date.fromgregorian(date=d)
        
        status = 'past' if d < today else ('today' if d == today else 'future')
        day_name = persian_weekdays[d.weekday()]
        month_name = persian_months[jd.month]
        jdate_formatted = f"{day_name} {jd.day} {month_name}"

        event = None
        if jd.month == 9 and jd.day == 15:
            event = "کاشت درخت 🌳"
        elif d.weekday() == 4:
            event = "بررسی هفتگی 📝"

        calendar_days.append({
            'gdate': d.strftime('%Y-%m-%d'),
            'jdate_str': jdate_formatted,
            'status': status,
            'event': event,
        })

    now_time = datetime.datetime.now().strftime("%H:%M")

    context = {
        'alerts': alerts,
        'current_filter': filter_type,
        'calendar_days': calendar_days,
        'now_time': now_time,
    }
    return render(request, 'alerts/alerts.html', context)

def add_alert(request):
    if request.method == 'POST':
        title = request.POST.get('title')
        frequency = request.POST.get('frequency', Alert.FREQ_DAILY)
        
        # User Friendly Custom Fields construction
        time_val = request.POST.get('time_val', '12:00')
        detail_time = ""

        if frequency == Alert.FREQ_DAILY:
            detail_time = f"ساعت {time_val}"
        elif frequency == Alert.FREQ_WEEKLY:
            day_week = request.POST.get('weekday', 'شنبه')
            detail_time = f"هر {day_week}‌ها - ساعت {time_val}"
        elif frequency == Alert.FREQ_MONTHLY:
            day_month = request.POST.get('month_day', 'یکم')
            detail_time = f"روز {day_month} ماه - ساعت {time_val}"
        elif frequency == Alert.FREQ_YEARLY:
            m_name = request.POST.get('year_month', 'فروردین')
            m_day = request.POST.get('year_day', 'یکم')
            detail_time = f"{m_day} {m_name} - ساعت {time_val}"

        Alert.objects.create(title=title, frequency=frequency, time_str=detail_time)
        return redirect('alerts')
    return redirect('alerts')

def delete_alert(request, alert_id):
    alert = get_object_or_404(Alert, id=alert_id)
    if request.method == 'POST':
        alert.delete()
        return redirect('alerts')
    return redirect('alerts')
