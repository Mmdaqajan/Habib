from django.contrib import messages
from django.core.exceptions import ValidationError
from django.db import transaction
from django.shortcuts import get_object_or_404, redirect, render
from django.views.decorators.http import require_POST

from .forms import WeeklyActivityForm, WeeklySetupForm
from .models import ActivitySlot, ScheduleDay, WeeklyActivity, WeeklySchedule
from .services import schedule_rows, validate_schedule_change


def _ensure_days(schedule):
    existing = set(
        ScheduleDay.objects.filter(schedule=schedule).values_list('weekday', flat=True)
    )
    ScheduleDay.objects.bulk_create([
        ScheduleDay(schedule=schedule, weekday=weekday)
        for weekday, _ in ScheduleDay.WEEKDAYS
        if weekday not in existing
    ])


def _save_day_values(form, schedule):
    days = []
    for weekday, _ in ScheduleDay.WEEKDAYS:
        day, _ = ScheduleDay.objects.get_or_create(
            schedule=schedule,
            weekday=weekday,
        )
        day.work_start = form.cleaned_data[f'work_start_{weekday}']
        day.work_end = form.cleaned_data[f'work_end_{weekday}']
        day.gym_start = form.cleaned_data[f'gym_start_{weekday}']
        day.gym_end = form.cleaned_data[f'gym_end_{weekday}']
        days.append(day)
    ScheduleDay.objects.bulk_update(
        days,
        ['work_start', 'work_end', 'gym_start', 'gym_end'],
    )


def weekly_schedule(request):
    schedule = WeeklySchedule.objects.filter(pk=1).first()
    if schedule is None:
        return redirect('weekly_schedule:setup')

    _ensure_days(schedule)
    slot_count, ticks, rows = schedule_rows(schedule)
    return render(request, 'weekly_schedule/schedule.html', {
        'schedule': schedule,
        'slot_count': slot_count,
        'ticks': ticks,
        'rows': rows,
        'activities': [
            {
                'pk': activity.pk,
                'name': activity.name,
                'color': f'hsl({(activity.pk * 137.508) % 360:.1f}, 68%, 43%)',
            }
            for activity in WeeklyActivity.objects.all()
        ],
    })


def setup(request):
    if WeeklySchedule.objects.filter(pk=1).exists():
        return redirect('weekly_schedule:weekly_schedule')

    form = WeeklySetupForm(
        request.POST if request.method == 'POST' else None
    )
    if request.method == 'POST' and form.is_valid():
        with transaction.atomic():
            schedule = WeeklySchedule.objects.create(
                pk=1,
                sleep_time=form.cleaned_data['sleep_time'],
                wake_time=form.cleaned_data['wake_time'],
            )
            _save_day_values(form, schedule)
        messages.success(request, 'برنامه هفتگی شما ذخیره شد.')
        return redirect('weekly_schedule:weekly_schedule')

    return render(request, 'weekly_schedule/setup.html', {
        'form': form,
        'title': 'تنظیم برنامه هفتگی',
        'submit_label': 'ذخیره و نمایش برنامه',
    })


def edit_sleep(request):
    schedule = WeeklySchedule.objects.filter(pk=1).first()
    if schedule is None:
        return redirect('weekly_schedule:setup')
    _ensure_days(schedule)
    form = WeeklySetupForm(
        request.POST if request.method == 'POST' else None,
        schedule=schedule,
        initial={
            'sleep_time': schedule.sleep_time,
            'wake_time': schedule.wake_time,
        },
    )

    if request.method == 'POST' and form.is_valid():
        schedule.sleep_time = form.cleaned_data['sleep_time']
        schedule.wake_time = form.cleaned_data['wake_time']
        try:
            validate_schedule_change(form.cleaned_data, schedule)
        except ValidationError as error:
            form.add_error(None, error)
        else:
            with transaction.atomic():
                schedule.save(update_fields=['sleep_time', 'wake_time'])
                _save_day_values(form, schedule)
            messages.success(request, 'زمان خواب، بیداری و برنامه روزها به‌روزرسانی شد.')
            return redirect('weekly_schedule:weekly_schedule')

    return render(request, 'weekly_schedule/setup.html', {
        'form': form,
        'title': 'ویرایش خواب و برنامه روزها',
        'submit_label': 'ذخیره تغییرات',
        'is_edit': True,
    })


def _activity_form_context(form):
    return [
        {
            'weekday': weekday,
            'day_name': day_name,
            'start': form[f'start_{weekday}'],
            'end': form[f'end_{weekday}'],
        }
        for weekday, day_name in ScheduleDay.WEEKDAYS
    ]


def add_activity(request):
    schedule = WeeklySchedule.objects.filter(pk=1).first()
    if schedule is None:
        return redirect('weekly_schedule:setup')
    _ensure_days(schedule)

    form = WeeklyActivityForm(
        request.POST if request.method == 'POST' else None,
        schedule=schedule,
    )
    if request.method == 'POST' and form.is_valid():
        with transaction.atomic():
            activity = WeeklyActivity.objects.create(name=form.cleaned_data['name'])
            ActivitySlot.objects.bulk_create([
                ActivitySlot(
                    activity=activity,
                    weekday=weekday,
                    start_time=start,
                    end_time=end,
                )
                for weekday, (start, end) in form.cleaned_data['day_times'].items()
            ])
        messages.success(request, 'فعالیت به برنامه هفتگی اضافه شد.')
        return redirect('weekly_schedule:weekly_schedule')

    return render(request, 'weekly_schedule/activity_form.html', {
        'form': form,
        'day_forms': _activity_form_context(form),
        'title': 'افزودن فعالیت',
        'submit_label': 'ذخیره فعالیت',
    })


def edit_activity(request, activity_id):
    schedule = WeeklySchedule.objects.filter(pk=1).first()
    if schedule is None:
        return redirect('weekly_schedule:setup')
    _ensure_days(schedule)
    activity = get_object_or_404(WeeklyActivity, pk=activity_id)
    form = WeeklyActivityForm(
        request.POST if request.method == 'POST' else None,
        schedule=schedule,
        activity=activity,
    )
    if request.method == 'POST' and form.is_valid():
        activity.name = form.cleaned_data['name']
        with transaction.atomic():
            activity.save(update_fields=['name'])
            activity.slots.all().delete()
            ActivitySlot.objects.bulk_create([
                ActivitySlot(
                    activity=activity,
                    weekday=weekday,
                    start_time=start,
                    end_time=end,
                )
                for weekday, (start, end) in form.cleaned_data['day_times'].items()
            ])
        messages.success(request, 'فعالیت ویرایش شد.')
        return redirect('weekly_schedule:weekly_schedule')

    return render(request, 'weekly_schedule/activity_form.html', {
        'form': form,
        'day_forms': _activity_form_context(form),
        'title': 'ویرایش فعالیت',
        'submit_label': 'ذخیره تغییرات',
        'activity': activity,
    })


def manage_activities(request):
    if not WeeklySchedule.objects.filter(pk=1).exists():
        return redirect('weekly_schedule:setup')
    activities = WeeklyActivity.objects.all()
    selected_id = request.GET.get('activity')
    selected = None
    if selected_id:
        selected = get_object_or_404(activities, pk=selected_id)
    activities = [
        {
            'pk': activity.pk,
            'name': activity.name,
            'color': f'hsl({(activity.pk * 137.508) % 360:.1f}, 68%, 43%)',
        }
        for activity in activities
    ]
    return render(request, 'weekly_schedule/manage.html', {
        'activities': activities,
        'selected': selected,
    })


@require_POST
def delete_activity(request, activity_id):
    activity = get_object_or_404(WeeklyActivity, pk=activity_id)
    activity.delete()
    messages.success(request, 'فعالیت از برنامه هفتگی حذف شد.')
    return redirect('weekly_schedule:manage')
