from datetime import datetime, timedelta

from django.core.exceptions import ValidationError

from .models import ActivitySlot, ScheduleDay, WeeklySchedule


DAY_MINUTES = 24 * 60
SLOT_MINUTES = 15
WEEKDAY_NAMES = dict(ScheduleDay.WEEKDAYS)


def minutes_since_midnight(value):
    return value.hour * 60 + value.minute


def awake_duration(wake_time, sleep_time):
    duration = (
        minutes_since_midnight(sleep_time) - minutes_since_midnight(wake_time)
    ) % DAY_MINUTES
    if duration == 0:
        raise ValidationError('زمان خواب و بیداری نباید یکسان باشد.')
    return duration


def sleep_duration(wake_time, sleep_time):
    return DAY_MINUTES - awake_duration(wake_time, sleep_time)


def interval_in_awake_window(start_time, end_time, wake_time, sleep_time):
    duration = (
        minutes_since_midnight(end_time) - minutes_since_midnight(start_time)
    ) % DAY_MINUTES
    if duration == 0:
        raise ValidationError('زمان پایان باید با زمان شروع متفاوت باشد.')

    awake_minutes = awake_duration(wake_time, sleep_time)
    start_offset = (
        minutes_since_midnight(start_time) - minutes_since_midnight(wake_time)
    ) % DAY_MINUTES
    if (
        minutes_since_midnight(start_time) % SLOT_MINUTES
        or minutes_since_midnight(end_time) % SLOT_MINUTES
        or start_offset + duration > awake_minutes
    ):
        raise ValidationError('این بازه باید در ساعات بیداری و روی تقسیمات ۱۵ دقیقه‌ای باشد.')
    return start_offset, duration


def intervals_overlap(first, second):
    first_start, first_duration = first
    second_start, second_duration = second
    first_end = first_start + first_duration
    second_end = second_start + second_duration
    return first_start < second_end and second_start < first_end


def _validated_interval(start, end, schedule):
    if start is None and end is None:
        return None
    if start is None or end is None:
        raise ValidationError('زمان شروع و پایان هر فعالیت را کامل وارد کنید.')
    return interval_in_awake_window(
        start, end, schedule.wake_time, schedule.sleep_time
    )


def day_intervals(day, schedule, exclude_activity=None):
    intervals = []
    for start, end in (
        (day.work_start, day.work_end),
        (day.gym_start, day.gym_end),
    ):
        interval = _validated_interval(start, end, schedule)
        if interval:
            intervals.append(interval)

    slots = ActivitySlot.objects.filter(weekday=day.weekday)
    if exclude_activity is not None:
        slots = slots.exclude(activity=exclude_activity)
    for slot in slots:
        intervals.append(
            interval_in_awake_window(
                slot.start_time,
                slot.end_time,
                schedule.wake_time,
                schedule.sleep_time,
            )
        )
    return intervals


def validate_schedule_change(form_data, schedule):
    for day in ScheduleDay.objects.filter(schedule=schedule):
        intervals = []
        values = (
            (form_data[f'work_start_{day.weekday}'], form_data[f'work_end_{day.weekday}']),
            (form_data[f'gym_start_{day.weekday}'], form_data[f'gym_end_{day.weekday}']),
        )
        try:
            intervals = [
                interval
                for start, end in values
                if (interval := _validated_interval(start, end, schedule))
            ]
            for slot in ActivitySlot.objects.filter(weekday=day.weekday):
                intervals.append(
                    interval_in_awake_window(
                        slot.start_time,
                        slot.end_time,
                        schedule.wake_time,
                        schedule.sleep_time,
                    )
                )
        except ValidationError as error:
            raise ValidationError(
                f'برنامه روز {WEEKDAY_NAMES[day.weekday]} با زمان خواب جدید سازگار نیست: {error.messages[0]}'
            ) from error

        for index, interval in enumerate(intervals):
            if any(intervals_overlap(interval, other) for other in intervals[index + 1:]):
                raise ValidationError(
                    f'در برنامه روز {WEEKDAY_NAMES[day.weekday]} تداخل زمانی ایجاد می‌شود.'
                )


def schedule_rows(schedule):
    awake_minutes = awake_duration(schedule.wake_time, schedule.sleep_time)
    slot_count = awake_minutes // SLOT_MINUTES
    tick_interval = 4 if awake_minutes <= 9 * 60 else 8 if awake_minutes <= 18 * 60 else 12
    ticks = []
    for slot_index in range(0, slot_count, tick_interval):
        tick_time = (
            datetime.combine(datetime.min, schedule.wake_time)
            + timedelta(minutes=slot_index * SLOT_MINUTES)
        ).time()
        ticks.append({
            'offset': slot_index * 100 / slot_count,
            'label': tick_time.strftime('%H:%M'),
        })

    days = ScheduleDay.objects.filter(schedule=schedule)
    rows = []
    for day in days:
        blocks = []
        for name, start, end, color in (
            ('کار', day.work_start, day.work_end, '#818cf8'),
            ('باشگاه', day.gym_start, day.gym_end, '#059669'),
        ):
            if start is not None and end is not None:
                start_offset, duration = interval_in_awake_window(
                    start, end, schedule.wake_time, schedule.sleep_time
                )
                blocks.append({
                    'name': name,
                    'start_slot': start_offset // SLOT_MINUTES,
                    'duration_slots': duration // SLOT_MINUTES,
                    'color': color,
                })

        for slot in ActivitySlot.objects.filter(weekday=day.weekday).select_related('activity'):
            start_offset, duration = interval_in_awake_window(
                slot.start_time,
                slot.end_time,
                schedule.wake_time,
                schedule.sleep_time,
            )
            color = f'hsl({(slot.activity_id * 137.508) % 360:.1f}, 68%, 43%)'
            blocks.append({
                'name': slot.activity.name,
                'start_slot': start_offset // SLOT_MINUTES,
                'duration_slots': duration // SLOT_MINUTES,
                'color': color,
            })

        occupied = sorted(
            (
                block['start_slot'],
                block['start_slot'] + block['duration_slots'],
            )
            for block in blocks
        )
        free_ranges = []
        cursor = 0
        for start_slot, end_slot in occupied:
            if start_slot > cursor:
                free_ranges.append((cursor, start_slot))
            cursor = max(cursor, end_slot)
        if cursor < slot_count:
            free_ranges.append((cursor, slot_count))

        for start_slot, end_slot in free_ranges:
            gap_minutes = (end_slot - start_slot) * SLOT_MINUTES
            outline_color = '#3b82f6' if gap_minutes < 4 * 60 else '#10b981'
            blocks.append({
                'name': '',
                'start_slot': start_slot,
                'duration_slots': end_slot - start_slot,
                'color': outline_color,
                'is_free': True,
            })

        for block in blocks:
            block['style'] = (
                f"right:{block['start_slot'] * 100 / slot_count:.4f}%;"
                f"width:{block['duration_slots'] * 100 / slot_count:.4f}%;"
                f"background-color:{block['color']};"
                f"border-color:{block['color'] if block.get('is_free') else 'transparent'}"
            )
        rows.append({
            'name': day.get_weekday_display(),
            'blocks': blocks,
            'slots': [
                {
                    'is_hour': slot_index % 4 == 0,
                    'offset': slot_index * 100 / slot_count,
                }
                for slot_index in range(slot_count)
            ],
        })
    return slot_count, ticks, rows
