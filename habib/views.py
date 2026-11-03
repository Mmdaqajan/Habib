import datetime
import json
import jdatetime
from django.shortcuts import render, redirect, get_object_or_404
from django.http import JsonResponse
from django.views.decorators.csrf import csrf_exempt
from .models import Habit, HabitLog, Alert, UserProfile

MOTIVATIONAL_QUOTES = [
    "۱- عادت‌های تو هویتت رو، میزان ارادتو و آیندتو تعین میکنن",
    "۲- انسان‌های موفق مرز شفافی بین امور مذاکره‌پذیر و مذاکره‌ناپذیر زندگیشون دارن",
    "۳- موفقیت هزارتا گام کوچیکه نه یک گام بزرگ ! این گام میتونه سر زدن به حبیب باشه :)",
    "۴- وقتی راجع به برنامه‌هات با بقیه صحبت میکنی باعث میشه میلت برای انجام دادنش کم بشه !",
    "۵- داستان بازنده‌ها رو که بشنوی متوجه میشی اوناهم خیلی کارها رو شروع کردن ولی نتونستن نگهش دارن !"
]

def init_defaults():
    if Habit.objects.count() == 0:
        defaults = [
            {"title": "مطالعه روزانه", "emoji": "📖", "habit_type": Habit.TYPE_QUANTITATIVE, "min_value": 10, "max_value": 100, "unit": "صفحه", "order": 1},
            {"title": "ورزش", "emoji": "🏋️‍♂️", "habit_type": Habit.TYPE_BOOLEAN, "order": 2},
            {"title": "سحرخیزی (07:00)", "emoji": "☀️", "habit_type": Habit.TYPE_BOOLEAN, "order": 3},
            {"title": "Study English", "emoji": "🗽", "habit_type": Habit.TYPE_QUANTITATIVE, "min_value": 10, "max_value": 60, "unit": "دقیقه", "order": 4},
        ]
        for d in defaults:
            Habit.objects.create(**d)

    if Alert.objects.count() == 0:
        default_alerts = [
            {"title": "هرشب ساعت 9 بگه عادت های امروزت رو ثبت کردی ؟", "frequency": Alert.FREQ_DAILY, "time_str": "21:00"},
            {"title": "جمعه ها ساعت 6 عصر بگه : برنامه این هفته رو نوشتی ؟", "frequency": Alert.FREQ_WEEKLY, "time_str": "جمعه 18:00"},
            {"title": "شهریه باشگاه یک هفته دیگه تموم میشه !", "frequency": Alert.FREQ_MONTHLY, "time_str": "ماهانه"},
            {"title": "15 آذر : به نظر میرسه امروز روز خوبی برای کاشتن یک درخته !", "frequency": Alert.FREQ_YEARLY, "time_str": "15 آذر"},
        ]
        for a in default_alerts:
            Alert.objects.create(**a)

    if not UserProfile.objects.exists():
        UserProfile.objects.create()

def home_view(request):
    init_defaults()
    today = datetime.date.today()
    habits = Habit.objects.all()

    today_logs = HabitLog.objects.filter(date=today)
    logs_dict = {log.habit_id: log for log in today_logs}

    habits_data = []
    completed_count = 0
    for h in habits:
        log = logs_dict.get(h.id)
        is_completed = log.completed if log else False
        val = log.value if log else (h.min_value if h.habit_type == Habit.TYPE_QUANTITATIVE else 0)
        if is_completed:
            completed_count += 1
        habits_data.append({
            'habit': h,
            'completed': is_completed,
            'value': val
        })

    # GitHub-style matrix (120 days)
    matrix_days = []
    start_date = today - datetime.timedelta(days=119)
    all_logs_period = HabitLog.objects.filter(date__gte=start_date, date__lte=today, completed=True)
    
    logs_by_date = {}
    for l in all_logs_period:
        logs_by_date[l.date] = logs_by_date.get(l.date, 0) + 1

    curr = start_date
    while curr <= today:
        cnt = logs_by_date.get(curr, 0)
        level = min(cnt, 4) # 4 habits = full score level 4
        j_curr = jdatetime.date.fromgregorian(date=curr)
        matrix_days.append({
            'date_str': curr.strftime('%Y-%m-%d'),
            'jdate_str': j_curr.strftime('%Y/%m/%d'),
            'count': cnt,
            'level': level,
            'is_today': curr == today
        })
        curr += datetime.timedelta(days=1)

    default_chart_habit = habits.first() if habits.exists() else None

    context = {
        'quotes': MOTIVATIONAL_QUOTES,
        'habits_data': habits_data,
        'matrix_days': matrix_days,
        'total_habits': habits.count(),
        'default_chart_habit': default_chart_habit,
        'completed_today': completed_count,
    }
    return render(request, 'habib/home.html', context)

@csrf_exempt
def toggle_habit(request):
    if request.method == 'POST':
        data = json.loads(request.body)
        habit_id = data.get('habit_id')
        completed = data.get('completed', False)
        value = data.get('value', 0)
        today = datetime.date.today()

        habit = get_object_or_404(Habit, id=habit_id)
        log, created = HabitLog.objects.get_or_create(habit=habit, date=today)
        log.completed = completed
        log.value = int(value) if value else 0
        log.save()

        return JsonResponse({'status': 'success'})
    return JsonResponse({'status': 'error'}, status=400)

def add_habit(request):
    if request.method == 'POST':
        if Habit.objects.count() >= 10:
            return JsonResponse({'status': 'error', 'message': 'حداکثر ۱۰ عادت می‌توانید داشته باشید.'}, status=400)
        
        title = request.POST.get('title')
        emoji = request.POST.get('emoji', '📌')
        habit_type = request.POST.get('habit_type', Habit.TYPE_BOOLEAN)
        min_val = request.POST.get('min_value', 0)
        max_val = request.POST.get('max_value', 100)
        unit = request.POST.get('unit', '')

        Habit.objects.create(
            title=title,
            emoji=emoji,
            habit_type=habit_type,
            min_value=int(min_val) if min_val else 0,
            max_value=int(max_val) if max_val else 100,
            unit=unit,
            order=Habit.objects.count() + 1
        )
        return redirect('home')
    return redirect('home')

def edit_habit(request, habit_id):
    habit = get_object_or_404(Habit, id=habit_id)
    if request.method == 'POST':
        habit.title = request.POST.get('title', habit.title)
        habit.emoji = request.POST.get('emoji', habit.emoji)
        habit.habit_type = request.POST.get('habit_type', habit.habit_type)
        habit.min_value = int(request.POST.get('min_value', habit.min_value))
        habit.max_value = int(request.POST.get('max_value', habit.max_value))
        habit.unit = request.POST.get('unit', habit.unit)
        habit.save()
        return redirect('home')
    return JsonResponse({'status': 'error'})

def delete_habit(request, habit_id):
    habit = get_object_or_404(Habit, id=habit_id)
    if request.method == 'POST':
        habit.delete()
        return redirect('home')
    return redirect('home')

@csrf_exempt
def reorder_habits(request):
    if request.method == 'POST':
        data = json.loads(request.body)
        order_list = data.get('order', []) # list of habit IDs
        for idx, h_id in enumerate(order_list):
            Habit.objects.filter(id=h_id).update(order=idx + 1)
        return JsonResponse({'status': 'success'})
    return JsonResponse({'status': 'error'}, status=400)

def alerts_view(request):
    init_defaults()
    filter_type = request.GET.get('filter', 'all')
    if filter_type != 'all':
        alerts = Alert.objects.filter(frequency=filter_type)
    else:
        alerts = Alert.objects.all()

    today = datetime.date.today()
    calendar_days = []
    
    persian_weekdays = {
        0: 'دوشنبه',
        1: 'سه‌شنبه',
        2: 'چهارشنبه',
        3: 'پنج‌شنبه',
        4: 'جمعه',
        5: 'شنبه',
        6: 'یکشنبه',
    }
    
    persian_months = [
        "", "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    ]

    for i in range(-14, 16):
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
            'day_num': jd.day,
        })

    context = {
        'alerts': alerts,
        'current_filter': filter_type,
        'calendar_days': calendar_days,
    }
    return render(request, 'habib/alerts.html', context)

def add_alert(request):
    if request.method == 'POST':
        title = request.POST.get('title')
        frequency = request.POST.get('frequency', Alert.FREQ_DAILY)
        time_str = request.POST.get('time_str', '')
        Alert.objects.create(title=title, frequency=frequency, time_str=time_str)
        return redirect('alerts')
    return redirect('alerts')

def delete_alert(request, alert_id):
    alert = get_object_or_404(Alert, id=alert_id)
    if request.method == 'POST':
        alert.delete()
        return redirect('alerts')
    return redirect('alerts')

def profile_view(request):
    init_defaults()
    profile = UserProfile.objects.first()
    if request.method == 'POST':
        profile.name = request.POST.get('name', profile.name)
        profile.age = int(request.POST.get('age', profile.age))
        profile.email = request.POST.get('email', profile.email)
        profile.phone = request.POST.get('phone', profile.phone)
        if request.FILES.get('profile_pic'):
            profile.profile_pic = request.FILES['profile_pic']
        profile.save()
        return redirect('profile')

    context = {'profile': profile}
    return render(request, 'habib/profile.html', context)

def get_habit_chart_data(request, habit_id):
    habit = get_object_or_404(Habit, id=habit_id)
    today = datetime.date.today()
    start_date = today - datetime.timedelta(days=14)
    logs = HabitLog.objects.filter(habit=habit, date__gte=start_date, date__lte=today).order_by('date')

    logs_dict = {l.date: (l.value if habit.habit_type == Habit.TYPE_QUANTITATIVE else (1 if l.completed else 0)) for l in logs}

    labels = []
    values = []
    curr = start_date
    while curr <= today:
        jd = jdatetime.date.fromgregorian(date=curr)
        labels.append(f"{jd.month}/{jd.day}")
        values.append(logs_dict.get(curr, 0))
        curr += datetime.timedelta(days=1)

    return JsonResponse({
        'title': habit.title,
        'unit': habit.unit or '',
        'labels': labels,
        'values': values
    })
