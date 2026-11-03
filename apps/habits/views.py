import datetime
import json
import jdatetime
from django.shortcuts import render, redirect, get_object_or_404
from django.http import JsonResponse
from django.views.decorators.csrf import csrf_exempt
from .models import Habit, HabitLog, Quote

DEFAULT_QUOTES = [
    "عادت های تو هویتت رو، میزان ارادتو و آیندتو تعین میکنن",
    "انسان های موفق مرز شفافی بین امور مذاکره پذیر و مذاکره ناپذیر زندگیشون دارن",
    "موفقیت هزارتا گام کوچیکه نه یک گام بزرگ ! این گام میتونه سر زدن به حبیب باشه :)",
    "وقتی راجع به برنامه هات با بقیه صحبت میکنی باعث میشه میلت برای انجام دادنش کم بشه !",
    "داستان بازنده هارو که بشنوی متوجه میشی اوناهم خیلی کارهارو شروع کردن ولی نتونستن نگهش دارن !"
]

def init_defaults():
    if Quote.objects.count() == 0:
        for q in DEFAULT_QUOTES:
            Quote.objects.create(text=q)

    if Habit.objects.filter(is_deleted=False).count() == 0 and Habit.objects.count() == 0:
        defaults = [
            {"title": "مطالعه روزانه", "emoji": "📖", "habit_type": Habit.TYPE_QUANTITATIVE, "min_value": 10, "max_value": 100, "unit": "صفحه", "order": 1, "description": "مطالعه برای رشد فردی و کنکور"},
            {"title": "ورزش", "emoji": "🏋️‍♂️", "habit_type": Habit.TYPE_BOOLEAN, "order": 2, "description": "باشگاه یا پیاده‌روی سریع"},
            {"title": "سحرخیزی (07:00)", "emoji": "☀️", "habit_type": Habit.TYPE_BOOLEAN, "order": 3, "description": "بیداری ساعت ۷"},
            {"title": "Study English", "emoji": "🗽", "habit_type": Habit.TYPE_QUANTITATIVE, "min_value": 10, "max_value": 60, "unit": "دقیقه", "order": 4, "description": "لغات و ریدینگ انگلیسی"},
        ]
        for d in defaults:
            Habit.objects.create(**d)

def home_view(request):
    init_defaults()
    today = datetime.date.today()
    habits = Habit.objects.filter(is_deleted=False)
    quotes = list(Quote.objects.values_list('text', flat=True))

    today_logs = HabitLog.objects.filter(date=today, habit__is_deleted=False)
    logs_dict = {log.habit_id: log for log in today_logs}

    habits_data = []
    completed_habits_names = []
    quant_reports = []

    for h in habits:
        log = logs_dict.get(h.id)
        is_completed = log.completed if log else False
        val = log.value if log else None
        
        if is_completed:
            completed_habits_names.append(h.title)
            if h.habit_type == Habit.TYPE_QUANTITATIVE and val is not None:
                quant_reports.append(f"{val} {h.unit or ''} {h.title}")

        habits_data.append({
            'habit': h,
            'completed': is_completed,
            'value': val if val is not None else ''
        })

    # Summary Generation
    completed_count = len(completed_habits_names)
    total_count = habits.count()
    summary_text = None
    if completed_count > 0:
        ratio = completed_count / total_count if total_count > 0 else 0
        if ratio == 1.0:
            status = "فوق‌العاده و بی‌نظیر"
        elif ratio >= 0.5:
            status = "عالی و پرانرژی"
        else:
            status = "خوب و رو به رشد"
        
        summary_text = {
            'status': status,
            'completed_list': "، ".join(completed_habits_names),
            'quant_list': quant_reports,
        }

    # GitHub Activity Grid (120 Days)
    matrix_days = []
    start_date = today - datetime.timedelta(days=119)
    all_logs_period = HabitLog.objects.filter(date__gte=start_date, date__lte=today, completed=True, habit__is_deleted=False)
    
    logs_by_date = {}
    for l in all_logs_period:
        logs_by_date[l.date] = logs_by_date.get(l.date, 0) + 1

    curr = start_date
    while curr <= today:
        cnt = logs_by_date.get(curr, 0)
        level = min(cnt, 4)
        j_curr = jdatetime.date.fromgregorian(date=curr)
        matrix_days.append({
            'date_str': curr.strftime('%Y-%m-%d'),
            'jdate_str': j_curr.strftime('%Y/%m/%d'),
            'count': cnt,
            'level': level,
        })
        curr += datetime.timedelta(days=1)

    context = {
        'quotes': quotes,
        'habits_data': habits_data,
        'matrix_days': matrix_days,
        'total_habits': total_count,
        'completed_today': len(completed_habits_names),
        'summary_text': summary_text,
    }
    return render(request, 'habits/home.html', context)

def habit_detail_view(request, habit_id):
    habit = get_object_or_404(Habit, id=habit_id, is_deleted=False)
    if request.method == 'POST':
        description = request.POST.get('description', '')
        habit.description = description
        habit.save()
        return redirect('habit_detail', habit_id=habit.id)
    return render(request, 'habits/habit_detail.html', {'habit': habit})

@csrf_exempt
def toggle_habit(request):
    if request.method == 'POST':
        data = json.loads(request.body)
        habit_id = data.get('habit_id')
        completed = data.get('completed', False)
        val = data.get('value', None)
        today = datetime.date.today()

        habit = get_object_or_404(Habit, id=habit_id, is_deleted=False)
        log, created = HabitLog.objects.get_or_create(habit=habit, date=today)
        log.completed = completed
        log.value = int(val) if val is not None and str(val).isdigit() else None
        log.save()

        return JsonResponse({'status': 'success'})
    return JsonResponse({'status': 'error'}, status=400)

def add_habit(request):
    if request.method == 'POST':
        if Habit.objects.filter(is_deleted=False).count() >= 10:
            return JsonResponse({'status': 'error', 'message': 'حداکثر ۱۰ عادت می‌توانید داشته باشید.'}, status=400)
        
        title = request.POST.get('title')
        emoji = request.POST.get('emoji', '📌')
        habit_type = request.POST.get('habit_type', Habit.TYPE_BOOLEAN)
        min_val = request.POST.get('min_value', 0)
        max_val = request.POST.get('max_value', 100)
        unit = request.POST.get('unit', '')
        desc = request.POST.get('description', '')

        Habit.objects.create(
            title=title,
            emoji=emoji,
            habit_type=habit_type,
            min_value=int(min_val) if min_val else 0,
            max_value=int(max_val) if max_val else 100,
            unit=unit,
            description=desc,
            order=Habit.objects.filter(is_deleted=False).count() + 1
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
        habit.description = request.POST.get('description', habit.description)
        habit.save()
        return redirect('home')
    return JsonResponse({'status': 'error'})

def delete_habit(request, habit_id):
    habit = get_object_or_404(Habit, id=habit_id)
    if request.method == 'POST':
        habit.is_deleted = True
        habit.save()
        return redirect('home')
    return redirect('home')

def add_quote(request):
    if request.method == 'POST':
        text = request.POST.get('text', '').strip()
        if text:
            Quote.objects.create(text=text)
    return redirect('home')

def get_habit_chart_data(request, habit_id):
    habit = get_object_or_404(Habit, id=habit_id)
    today = datetime.date.today()
    start_date = today - datetime.timedelta(days=14)
    logs = HabitLog.objects.filter(habit=habit, date__gte=start_date, date__lte=today).order_by('date')

    if habit.habit_type == Habit.TYPE_QUANTITATIVE:
        logs_dict = {log.date: log.value for log in logs}
    else:
        logs_dict = {log.date: 1 if log.completed else 0 for log in logs}

    labels = []
    values = []
    curr = start_date
    while curr <= today:
        jd = jdatetime.date.fromgregorian(date=curr)
        labels.append(f"{jd.month}/{jd.day}")
        values.append(logs_dict.get(curr) if habit.habit_type == Habit.TYPE_QUANTITATIVE else logs_dict.get(curr, 0))
        curr += datetime.timedelta(days=1)

    return JsonResponse({
        'title': habit.title,
        'unit': habit.unit or '',
        'min_value': habit.min_value if habit.habit_type == Habit.TYPE_QUANTITATIVE else None,
        'max_value': habit.max_value if habit.habit_type == Habit.TYPE_QUANTITATIVE else None,
        'labels': labels,
        'values': values
    })
