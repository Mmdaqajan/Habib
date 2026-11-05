import datetime
import json
import jdatetime
from django.shortcuts import render, redirect, get_object_or_404
from django.http import JsonResponse
from django.utils import timezone
from django.views.decorators.csrf import csrf_exempt
from .models import Habit, HabitLog, Quote

DEFAULT_QUOTES = [
    "عادت های تو هویتت و میزان اراده ات و آینده ات رو تعین میکنن . کلمات این جمله با دقت انتخاب شده !",
    "آدم‌های موفق مرز روشنی بین کارهای قابل مذاکره و غیرقابل مذاکره دارند.",
    "موفقیت هزاران قدم کوچیکه نه یک قدم بزرگ، این قدم میتونه سر زدن به حبیب باشه :)",
    "وقتی دربارهٔ برنامه‌هایت با دیگران حرف می‌زنی، گاهی انگیزهٔ انجامشان کمتر می‌شود.",
    "داستان بازنده هارو که بشنوی متوجه میشی اوناهم خیلی کارهارو شروع کردن ولی نتونستن نگهش دارن !"
]
PERSIAN_MONTHS = (
    "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
    "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند",
)


def _jalali_date_label(value):
    jalali_date = jdatetime.date.fromgregorian(date=value)
    return (
        f"{jalali_date.day} {PERSIAN_MONTHS[jalali_date.month - 1]} "
        f"{jalali_date.year}"
    )


def _longest_streak(days):
    if not days:
        return 0
    longest = current = 1
    previous = days[0]
    for day in days[1:]:
        current = current + 1 if day == previous + datetime.timedelta(days=1) else 1
        longest = max(longest, current)
        previous = day
    return longest


def _selected_habits_for_date(selected_date):
    habits = list(Habit.objects.filter(is_deleted=False))
    logs = HabitLog.objects.filter(
        date=selected_date,
        habit__is_deleted=False,
    ).values("habit_id", "completed", "skipped", "value")
    logs_by_habit = {log["habit_id"]: log for log in logs}
    completed_count = sum(
        bool(logs_by_habit.get(habit.id, {}).get("completed"))
        for habit in habits
    )
    return habits, logs_by_habit, completed_count


def init_defaults():
    if Quote.objects.count() == 0:
        for q in DEFAULT_QUOTES:
            Quote.objects.create(text=q)

    if Habit.objects.filter(is_deleted=False).count() == 0 and Habit.objects.count() == 0:
        defaults = [
            {"title": "مطالعه روزانه", "emoji": "📖", "habit_type": Habit.TYPE_QUANTITATIVE, "min_value": 10, "max_value": 100, "unit": "صفحه", "order": 1, "description": "مطالعه برای رشد فردی و کنکور"},
            {"title": "ورزش", "emoji": "🏋️‍♂️", "habit_type": Habit.TYPE_BOOLEAN, "order": 2, "description": "باشگاه یا پیاده‌روی سریع"},
            {"title": "سحرخیزی (07:00)", "emoji": "☀️", "habit_type": Habit.TYPE_BOOLEAN, "order": 3},
            {"title": "Study English", "emoji": "🗽", "habit_type": Habit.TYPE_QUANTITATIVE, "min_value": 10, "max_value": 60, "unit": "دقیقه", "order": 4, "description": "لغات و ریدینگ انگلیسی"},
        ]
        for d in defaults:
            Habit.objects.create(**d)

def home_view(request):
    init_defaults()
    today = timezone.localdate()
    try:
        selected_date = datetime.date.fromisoformat(request.GET.get("date", ""))
    except (TypeError, ValueError):
        selected_date = today
    if selected_date > today:
        selected_date = today

    habits, logs_dict, completed_count = _selected_habits_for_date(selected_date)
    quotes = list(Quote.objects.values_list('text', flat=True))

    habits_data = []
    completed_habits_names = []
    quant_reports = []

    for h in habits:
        log = logs_dict.get(h.id)
        is_completed = log["completed"] if log else False
        val = log["value"] if log else None
        
        if is_completed:
            completed_habits_names.append(h.title)
            if h.habit_type == Habit.TYPE_QUANTITATIVE and val is not None:
                quant_reports.append(f"{val} {h.unit or ''} {h.title}")

        habits_data.append({
            'habit': h,
            'completed': is_completed,
            'skipped': log["skipped"] if log else False,
            'value': (
                val if val is not None
                else h.default_value if not is_completed and not (log and log["skipped"])
                else ''
            )
        })

    # Summary Generation
    completed_count = len(completed_habits_names)
    total_count = len(habits)
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
        'completed_today': completed_count,
        'selected_date': selected_date,
        'selected_date_key': selected_date.isoformat(),
        'today_date_key': today.isoformat(),
        'selected_date_label': (
            "امروز" if selected_date == today else _jalali_date_label(selected_date)
        ),
        'is_selected_today': selected_date == today,
        'previous_date_key': (selected_date - datetime.timedelta(days=1)).isoformat(),
        'next_date_key': min(selected_date + datetime.timedelta(days=1), today).isoformat(),
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
        try:
            data = json.loads(request.body)
        except (json.JSONDecodeError, UnicodeDecodeError):
            return JsonResponse({'status': 'error', 'message': 'درخواست معتبر نیست.'}, status=400)
        habit_id = data.get('habit_id')
        completed = data.get('completed', False)
        skipped = data.get('skipped', False)
        val = data.get('value', None)
        if not isinstance(completed, bool) or not isinstance(skipped, bool) or (completed and skipped):
            return JsonResponse({'status': 'error', 'message': 'وضعیت عادت معتبر نیست.'}, status=400)
        selected_date_value = data.get('date')
        try:
            selected_date = (
                datetime.date.fromisoformat(selected_date_value)
                if selected_date_value else timezone.localdate()
            )
        except (TypeError, ValueError):
            return JsonResponse({'status': 'error', 'message': 'تاریخ انتخاب‌شده معتبر نیست.'}, status=400)
        if selected_date > timezone.localdate():
            return JsonResponse(
                {'status': 'error', 'message': 'ثبت عادت برای روزهای آینده امکان‌پذیر نیست.'},
                status=400,
            )

        habit = get_object_or_404(Habit, id=habit_id, is_deleted=False)
        if skipped:
            completed = False
            val = None
        if habit.habit_type == Habit.TYPE_QUANTITATIVE:
            if val in (None, "") and completed:
                val = habit.default_value if habit.default_value is not None else habit.min_value
            elif val not in (None, ""):
                normalized_value = str(val).translate(
                    str.maketrans("۰۱۲۳۴۵۶۷۸۹٠١٢٣٤٥٦٧٨٩", "01234567890123456789")
                )
                try:
                    val = int(normalized_value)
                except (TypeError, ValueError):
                    return JsonResponse(
                        {'status': 'error', 'message': 'مقدار واردشده معتبر نیست.'},
                        status=400,
                    )
                if not habit.min_value <= val <= habit.max_value:
                    return JsonResponse(
                        {
                            'status': 'error',
                            'message': f'مقدار باید بین {habit.min_value} تا {habit.max_value} باشد.',
                        },
                        status=400,
                    )
            else:
                val = None
        else:
            val = None
        log, created = HabitLog.objects.get_or_create(habit=habit, date=selected_date)
        log.completed = completed
        log.skipped = skipped
        log.value = val
        log.save()

        from apps.users.achievements import get_achievement_progress
        get_achievement_progress()

        _, _, completed_count = _selected_habits_for_date(selected_date)
        return JsonResponse({
            'status': 'success',
            'completed': log.completed,
            'skipped': log.skipped,
            'value': log.value,
            'completed_count': completed_count,
            'total_habits': Habit.objects.filter(is_deleted=False).count(),
            'date': selected_date.isoformat(),
        })
    return JsonResponse({'status': 'error'}, status=400)


def habit_date_data(request):
    today = timezone.localdate()
    try:
        selected_date = datetime.date.fromisoformat(request.GET.get("date", ""))
    except (TypeError, ValueError):
        return JsonResponse(
            {'status': 'error', 'message': 'تاریخ انتخاب‌شده معتبر نیست.'},
            status=400,
        )
    if selected_date > today:
        return JsonResponse(
            {'status': 'error', 'message': 'روزهای آینده در دسترس نیستند.'},
            status=400,
        )

    habits, logs_by_habit, completed_count = _selected_habits_for_date(selected_date)
    return JsonResponse({
        'status': 'success',
        'date': selected_date.isoformat(),
        'date_label': "امروز" if selected_date == today else _jalali_date_label(selected_date),
        'is_today': selected_date == today,
        'previous_date': (selected_date - datetime.timedelta(days=1)).isoformat(),
        'next_date': min(selected_date + datetime.timedelta(days=1), today).isoformat(),
        'completed_count': completed_count,
        'total_habits': len(habits),
        'habits': {
            str(habit.id): {
                'completed': bool(logs_by_habit.get(habit.id, {}).get("completed")),
                'skipped': bool(logs_by_habit.get(habit.id, {}).get("skipped")),
                'value': logs_by_habit.get(habit.id, {}).get("value"),
                'default_value': habit.default_value,
            }
            for habit in habits
        },
    })

def add_habit(request):
    if request.method == 'POST':
        if Habit.objects.filter(is_deleted=False).count() >= 10:
            return JsonResponse({'status': 'error', 'message': 'حداکثر ۱۰ عادت می‌توانید داشته باشید.'}, status=400)
        
        title = request.POST.get('title')
        emoji = request.POST.get('emoji', '📌')
        habit_type = request.POST.get('habit_type', Habit.TYPE_BOOLEAN)
        min_val = request.POST.get('min_value', 0)
        max_val = request.POST.get('max_value', 100)
        default_value = request.POST.get('default_value', '').strip()
        unit = request.POST.get('unit', '')
        desc = request.POST.get('description', '')

        try:
            min_val = int(min_val) if min_val else 0
            max_val = int(max_val) if max_val else 100
            default_value = int(default_value) if default_value else None
        except ValueError:
            return JsonResponse({'status': 'error', 'message': 'مقدارهای عددی عادت معتبر نیستند.'}, status=400)
        if min_val > max_val or (
            default_value is not None and not min_val <= default_value <= max_val
        ):
            return JsonResponse(
                {'status': 'error', 'message': 'مقدار پیش‌فرض باید در بازهٔ حداقل و حداکثر باشد.'},
                status=400,
            )

        Habit.objects.create(
            title=title,
            emoji=emoji,
            habit_type=habit_type,
            min_value=min_val,
            max_value=max_val,
            default_value=default_value if habit_type == Habit.TYPE_QUANTITATIVE else None,
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
        default_value = request.POST.get('default_value', '').strip()
        try:
            habit.default_value = int(default_value) if default_value else None
        except ValueError:
            return JsonResponse({'status': 'error', 'message': 'مقدار پیش‌فرض معتبر نیست.'}, status=400)
        if habit.default_value is not None and not habit.min_value <= habit.default_value <= habit.max_value:
            return JsonResponse(
                {'status': 'error', 'message': 'مقدار پیش‌فرض باید در بازهٔ حداقل و حداکثر باشد.'},
                status=400,
            )
        if habit.habit_type == Habit.TYPE_BOOLEAN:
            habit.default_value = None
        habit.description = request.POST.get('description', habit.description)
        habit.save()
        return redirect('home')
    return JsonResponse({'status': 'error'})

def delete_habit(request, habit_id):
    habit = get_object_or_404(Habit, id=habit_id)
    if request.method == 'POST':
        habit.is_deleted = True
        habit.save()
        from apps.users.models import AchievementUnlock
        AchievementUnlock.objects.filter(code__startswith=f"habit:{habit.id}:").delete()
        return redirect('home')
    return redirect('home')

def add_quote(request):
    if request.method == 'POST':
        text = request.POST.get('text', '').strip()
        if text:
            Quote.objects.create(text=text)
    return redirect('home')

def get_habit_chart_data(request, habit_id):
    habit = get_object_or_404(Habit, id=habit_id, is_deleted=False)
    today = timezone.localdate()
    all_completed_logs = list(
        HabitLog.objects.filter(
            habit=habit,
            completed=True,
            date__lte=today,
        ).order_by("date")
    )
    completed_days = [log.date for log in all_completed_logs]
    total_value = sum(
        log.value or 0
        for log in all_completed_logs
        if habit.habit_type == Habit.TYPE_QUANTITATIVE
    )
    stats = {
        'total_completed_days': len(completed_days),
        'longest_streak': _longest_streak(completed_days),
        'total_value': total_value,
    }

    if habit.habit_type != Habit.TYPE_QUANTITATIVE:
        return JsonResponse({
            'title': habit.title,
            'unit': '',
            'min_value': None,
            'max_value': None,
            'labels': [],
            'values': [],
            'is_quantitative': False,
            'stats': stats,
        })

    start_date = today - datetime.timedelta(days=14)
    logs = HabitLog.objects.filter(habit=habit, date__gte=start_date, date__lte=today).order_by('date')

    logs_dict = {
        log.date: log.value if log.completed else None
        for log in logs
    }

    labels = []
    values = []
    curr = start_date
    while curr <= today:
        jd = jdatetime.date.fromgregorian(date=curr)
        labels.append(f"{jd.month}/{jd.day}")
        values.append(logs_dict.get(curr))
        curr += datetime.timedelta(days=1)

    return JsonResponse({
        'title': habit.title,
        'unit': habit.unit or '',
        'min_value': habit.min_value if habit.habit_type == Habit.TYPE_QUANTITATIVE else None,
        'max_value': habit.max_value if habit.habit_type == Habit.TYPE_QUANTITATIVE else None,
        'labels': labels,
        'values': values,
        'is_quantitative': True,
        'stats': stats,
    })
