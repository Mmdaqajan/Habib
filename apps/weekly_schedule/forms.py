from django import forms
from django.core.exceptions import ValidationError

from .models import ScheduleDay
from .services import (
    SLOT_MINUTES,
    awake_duration,
    day_intervals,
    interval_in_awake_window,
    intervals_overlap,
    minutes_since_midnight,
)


TIME_WIDGET = forms.TimeInput(
    format='%H:%M',
    attrs={
        'type': 'text',
        'inputmode': 'numeric',
        'autocomplete': 'off',
        'placeholder': '07:00',
        'pattern': '(?:[01][0-9]|2[0-3]):[0-5][0-9]',
        'class': 'form-control time-24h',
        'dir': 'ltr',
    },
)


def _time_field(label, required=True, use_time_picker=False):
    widget = (
        forms.TimeInput(
            format='%H:%M',
            attrs={
                'type': 'time',
                'step': SLOT_MINUTES * 60,
                'class': 'form-control time-24h',
                'dir': 'ltr',
            },
        )
        if use_time_picker
        else TIME_WIDGET
    )
    return forms.TimeField(
        label=label,
        required=required,
        input_formats=['%H:%M'],
        widget=widget,
        error_messages={'invalid': 'زمان را به شکل ۲۴ساعته، مانند 07:00 وارد کنید.'},
    )


class WeeklySetupForm(forms.Form):
    sleep_time = _time_field('زمان خواب')
    wake_time = _time_field('زمان بیداری')

    def __init__(self, *args, schedule=None, **kwargs):
        self.schedule = schedule
        super().__init__(*args, **kwargs)
        if schedule is None:
            self.initial.setdefault('sleep_time', '22:00')
            self.initial.setdefault('wake_time', '06:00')
        for weekday, day_name in ScheduleDay.WEEKDAYS:
            day = None
            if schedule is not None:
                day = schedule.days.filter(weekday=weekday).first()
            self.fields[f'work_enabled_{weekday}'] = forms.BooleanField(
                label='سر کار می‌روم',
                required=False,
                widget=forms.CheckboxInput(attrs={'class': 'schedule-toggle'}),
            )
            self.fields[f'gym_enabled_{weekday}'] = forms.BooleanField(
                label='به باشگاه می‌روم',
                required=False,
                widget=forms.CheckboxInput(attrs={'class': 'schedule-toggle'}),
            )
            for activity in ('work', 'gym'):
                label = 'کار' if activity == 'work' else 'باشگاه'
                self.fields[f'{activity}_start_{weekday}'] = _time_field(
                    f'{label} - شروع', required=False
                )
                self.fields[f'{activity}_end_{weekday}'] = _time_field(
                    f'{label} - پایان', required=False
                )
                if day is not None:
                    self.initial[f'{activity}_enabled_{weekday}'] = getattr(
                        day, f'{activity}_start'
                    ) is not None or getattr(day, f'{activity}_end') is not None
                    self.initial[f'{activity}_start_{weekday}'] = getattr(
                        day, f'{activity}_start'
                    )
                    self.initial[f'{activity}_end_{weekday}'] = getattr(
                        day, f'{activity}_end'
                    )

    @property
    def day_rows(self):
        return [
            {
                'name': day_name,
                'weekday': weekday,
                'work_enabled': self[f'work_enabled_{weekday}'],
                'work_start': self[f'work_start_{weekday}'],
                'work_end': self[f'work_end_{weekday}'],
                'gym_enabled': self[f'gym_enabled_{weekday}'],
                'gym_start': self[f'gym_start_{weekday}'],
                'gym_end': self[f'gym_end_{weekday}'],
            }
            for weekday, day_name in ScheduleDay.WEEKDAYS
        ]

    def clean(self):
        cleaned_data = super().clean()
        wake_time = cleaned_data.get('wake_time')
        sleep_time = cleaned_data.get('sleep_time')
        if wake_time is None or sleep_time is None:
            return cleaned_data

        try:
            actual_sleep_minutes = 24 * 60 - awake_duration(wake_time, sleep_time)
        except ValidationError:
            self.add_error('sleep_time', 'زمان خواب و بیداری نباید یکسان باشد.')
            actual_sleep_minutes = 0
        if actual_sleep_minutes > 12 * 60:
            self.add_error('sleep_time', 'مدت خواب نباید بیشتر از ۱۲ ساعت باشد.')
        if (
            minutes_since_midnight(wake_time) % SLOT_MINUTES
            or minutes_since_midnight(sleep_time) % SLOT_MINUTES
        ):
            self.add_error('sleep_time', 'زمان‌ها باید مضربی از ۱۵ دقیقه باشند.')

        for weekday, day_name in ScheduleDay.WEEKDAYS:
            intervals = []
            for activity in ('work', 'gym'):
                start = cleaned_data.get(f'{activity}_start_{weekday}')
                end = cleaned_data.get(f'{activity}_end_{weekday}')
                enabled = cleaned_data.get(f'{activity}_enabled_{weekday}', False)
                if not enabled:
                    cleaned_data[f'{activity}_start_{weekday}'] = None
                    cleaned_data[f'{activity}_end_{weekday}'] = None
                    continue
                if start is None or end is None:
                    self.add_error(
                        f'{activity}_start_{weekday}',
                        'زمان شروع و پایان هر دو الزامی هستند.',
                    )
                    continue
                try:
                    intervals.append(
                        interval_in_awake_window(start, end, wake_time, sleep_time)
                    )
                except ValidationError as error:
                    self.add_error(f'{activity}_start_{weekday}', error.messages[0])

            if len(intervals) == 2 and intervals_overlap(intervals[0], intervals[1]):
                self.add_error(
                    f'work_start_{weekday}',
                    f'ساعت کار و باشگاه در روز {day_name} نباید هم‌پوشانی داشته باشند.',
                )
        return cleaned_data


class WeeklyActivityForm(forms.Form):
    name = forms.CharField(
        label='نام فعالیت',
        max_length=100,
        widget=forms.TextInput(attrs={'class': 'form-control', 'maxlength': 100}),
    )
    days = forms.MultipleChoiceField(
        label='روزها',
        choices=ScheduleDay.WEEKDAYS,
        widget=forms.CheckboxSelectMultiple,
        error_messages={'required': 'حداقل یک روز را انتخاب کنید.'},
    )

    def __init__(self, *args, schedule, activity=None, **kwargs):
        self.schedule = schedule
        self.activity = activity
        super().__init__(*args, **kwargs)
        slots = {}
        if activity is not None:
            slots = {slot.weekday: slot for slot in activity.slots.all()}
            self.initial['name'] = activity.name
            self.initial['days'] = [str(day) for day in slots]

        for weekday, day_name in ScheduleDay.WEEKDAYS:
            self.fields[f'start_{weekday}'] = _time_field(
                f'شروع - {day_name}', required=False, use_time_picker=True
            )
            self.fields[f'end_{weekday}'] = _time_field(
                f'پایان - {day_name}', required=False, use_time_picker=True
            )
            slot = slots.get(weekday)
            if slot is not None:
                self.initial[f'start_{weekday}'] = slot.start_time
                self.initial[f'end_{weekday}'] = slot.end_time

    def clean(self):
        cleaned_data = super().clean()
        selected_days = cleaned_data.get('days', [])
        day_times = {}
        days = {
            day.weekday: day
            for day in ScheduleDay.objects.filter(schedule=self.schedule)
        }

        for weekday_value in selected_days:
            weekday = int(weekday_value)
            start = cleaned_data.get(f'start_{weekday}')
            end = cleaned_data.get(f'end_{weekday}')
            if start is None or end is None:
                self.add_error(
                    f'start_{weekday}',
                    'برای هر روز انتخاب‌شده زمان شروع و پایان را وارد کنید.',
                )
                continue
            try:
                interval = interval_in_awake_window(
                    start, end, self.schedule.wake_time, self.schedule.sleep_time
                )
                existing = day_intervals(
                    days[weekday], self.schedule, exclude_activity=self.activity
                )
            except ValidationError as error:
                self.add_error(f'start_{weekday}', error.messages[0])
                continue

            if any(intervals_overlap(interval, other) for other in existing):
                self.add_error(
                    f'start_{weekday}',
                    'این زمان با کار، باشگاه یا فعالیت دیگری تداخل دارد.',
                )
                continue
            day_times[weekday] = (start, end)

        if len(day_times) == len(selected_days):
            cleaned_data['day_times'] = day_times
        return cleaned_data
