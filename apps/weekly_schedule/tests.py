from django.test import TestCase
from django.urls import reverse

from .forms import WeeklySetupForm
from .models import ActivitySlot, ScheduleDay, WeeklyActivity, WeeklySchedule


class WeeklyScheduleTests(TestCase):
    def setup_schedule(self, sleep_time='01:00', wake_time='09:00', **day_values):
        data = {
            'sleep_time': sleep_time,
            'wake_time': wake_time,
        }
        for weekday, _ in ScheduleDay.WEEKDAYS:
            for activity in ('work', 'gym'):
                data[f'{activity}_start_{weekday}'] = ''
                data[f'{activity}_end_{weekday}'] = ''
        data.update(day_values)
        for weekday, _ in ScheduleDay.WEEKDAYS:
            for activity in ('work', 'gym'):
                data[f'{activity}_enabled_{weekday}'] = any(
                    data[f'{activity}_{edge}_{weekday}']
                    for edge in ('start', 'end')
                )
        return self.client.post(reverse('weekly_schedule:setup'), data)

    def add_activity(self, name='مطالعه', days=('2',), start='10:00', end='11:00'):
        data = {'name': name, 'days': list(days)}
        for weekday, _ in ScheduleDay.WEEKDAYS:
            data[f'start_{weekday}'] = ''
            data[f'end_{weekday}'] = ''
        for weekday in days:
            data[f'start_{weekday}'] = start
            data[f'end_{weekday}'] = end
        return self.client.post(reverse('weekly_schedule:add_activity'), data)

    def test_schedule_setup_is_required_and_creates_seven_day_rows(self):
        response = self.client.get(reverse('weekly_schedule:weekly_schedule'))
        self.assertRedirects(response, reverse('weekly_schedule:setup'))

        response = self.setup_schedule()
        self.assertRedirects(response, reverse('weekly_schedule:weekly_schedule'))
        self.assertEqual(WeeklySchedule.objects.count(), 1)
        self.assertEqual(ScheduleDay.objects.count(), 7)
        self.assertEqual(
            list(ScheduleDay.objects.values_list('weekday', flat=True)),
            list(range(7)),
        )

    def test_setup_rejects_missing_sleep_times_and_work_during_sleep(self):
        response = self.client.post(reverse('weekly_schedule:setup'), {})
        self.assertEqual(response.status_code, 200)
        self.assertTrue(response.context['form']['sleep_time'].errors)

        response = self.client.post(
            reverse('weekly_schedule:setup'),
            {'sleep_time': '', 'wake_time': '09:00'},
        )
        self.assertEqual(response.status_code, 200)
        self.assertFalse(WeeklySchedule.objects.exists())

        response = self.setup_schedule(
            work_start_0='01:00',
            work_end_0='02:00',
        )
        self.assertEqual(response.status_code, 200)
        self.assertFalse(WeeklySchedule.objects.exists())

    def test_setup_defaults_are_24_hour_and_sleep_cannot_exceed_twelve_hours(self):
        form = WeeklySetupForm()
        self.assertEqual(form['sleep_time'].value(), '22:00')
        self.assertEqual(form['wake_time'].value(), '06:00')
        self.assertEqual(form.fields['sleep_time'].widget.input_type, 'text')

        response = self.setup_schedule(sleep_time='22:00', wake_time='11:00')
        self.assertEqual(response.status_code, 200)
        self.assertFalse(WeeklySchedule.objects.exists())
        self.assertTrue(response.context['form']['sleep_time'].errors)

        response = self.setup_schedule(sleep_time='22:00', wake_time='10:00')
        self.assertRedirects(response, reverse('weekly_schedule:weekly_schedule'))
        self.assertEqual(WeeklySchedule.objects.get().sleep_time.strftime('%H:%M'), '22:00')

    def test_24_hour_time_input_rejects_am_pm_values(self):
        response = self.setup_schedule(sleep_time='10:00 PM', wake_time='06:00')
        self.assertEqual(response.status_code, 200)
        self.assertFalse(WeeklySchedule.objects.exists())
        self.assertTrue(response.context['form']['sleep_time'].errors)

    def test_unchecked_work_and_gym_days_save_without_time_ranges(self):
        response = self.setup_schedule()
        self.assertRedirects(response, reverse('weekly_schedule:weekly_schedule'))
        day = ScheduleDay.objects.get(weekday=0)
        self.assertIsNone(day.work_start)
        self.assertIsNone(day.gym_start)

    def test_checked_work_and_gym_save_separate_ranges(self):
        response = self.setup_schedule(
            work_start_0='10:00',
            work_end_0='11:00',
            gym_start_0='12:00',
            gym_end_0='13:00',
        )
        self.assertRedirects(response, reverse('weekly_schedule:weekly_schedule'))
        day = ScheduleDay.objects.get(weekday=0)
        self.assertEqual(day.work_start.strftime('%H:%M'), '10:00')
        self.assertEqual(day.gym_start.strftime('%H:%M'), '12:00')

    def test_timetable_uses_waking_window_and_crosses_midnight(self):
        self.setup_schedule(sleep_time='03:00', wake_time='15:00')
        response = self.client.get(reverse('weekly_schedule:weekly_schedule'))

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.context['slot_count'], 48)
        self.assertContains(response, '15:00')
        self.assertContains(response, '23:00')
        self.assertContains(response, '01:00')

    def test_activity_can_cross_midnight_and_be_assigned_to_multiple_days(self):
        self.setup_schedule()
        response = self.add_activity(
            days=('2', '3'),
            start='23:00',
            end='01:00',
        )

        self.assertRedirects(response, reverse('weekly_schedule:weekly_schedule'))
        activity = WeeklyActivity.objects.get(name='مطالعه')
        self.assertEqual(
            set(activity.slots.values_list('weekday', flat=True)),
            {2, 3},
        )
        self.assertEqual(activity.slots.get(weekday=2).end_time.strftime('%H:%M'), '01:00')

    def test_activity_outside_waking_hours_and_overlapping_activity_are_rejected(self):
        self.setup_schedule()
        self.add_activity(start='23:00', end='01:00')
        response = self.add_activity(name='تداخل', start='00:00', end='00:30')

        self.assertEqual(response.status_code, 200)
        self.assertEqual(WeeklyActivity.objects.count(), 1)

        response = self.add_activity(name='هنگام خواب', start='01:00', end='02:00')
        self.assertEqual(response.status_code, 200)
        self.assertEqual(WeeklyActivity.objects.count(), 1)

    def test_activity_edit_and_delete(self):
        self.setup_schedule()
        self.add_activity()
        activity = WeeklyActivity.objects.get(name='مطالعه')

        response = self.client.post(
            reverse('weekly_schedule:edit_activity', args=[activity.pk]),
            {
                'name': 'مطالعه و تمرین',
                'days': ['4'],
                'start_4': '12:00',
                'end_4': '13:00',
            },
        )
        self.assertRedirects(response, reverse('weekly_schedule:weekly_schedule'))
        activity.refresh_from_db()
        self.assertEqual(activity.name, 'مطالعه و تمرین')
        self.assertEqual(
            list(activity.slots.values_list('weekday', flat=True)),
            [4],
        )

        response = self.client.post(
            reverse('weekly_schedule:delete_activity', args=[activity.pk])
        )
        self.assertRedirects(response, reverse('weekly_schedule:manage'))
        self.assertFalse(WeeklyActivity.objects.exists())
        self.assertFalse(ActivitySlot.objects.exists())

    def test_activity_and_management_forms_render(self):
        self.setup_schedule()
        self.assertEqual(
            self.client.get(reverse('weekly_schedule:add_activity')).status_code,
            200,
        )
        self.add_activity()
        activity = WeeklyActivity.objects.get(name='مطالعه')

        management = self.client.get(
            reverse('weekly_schedule:manage'),
            {'activity': activity.pk},
        )
        editing = self.client.get(
            reverse('weekly_schedule:edit_activity', args=[activity.pk])
        )
        self.assertEqual(management.status_code, 200)
        self.assertContains(management, 'ویرایش فعالیت')
        self.assertEqual(editing.status_code, 200)
        self.assertContains(editing, 'name="days"')
        self.assertContains(editing, 'type="time"')
        self.assertContains(editing, 'step="900"')

    def test_sleep_change_that_invalidates_existing_activity_is_rejected(self):
        self.setup_schedule()
        self.add_activity(start='23:00', end='00:30')
        data = {
            'sleep_time': '00:00',
            'wake_time': '09:00',
        }
        for weekday, _ in ScheduleDay.WEEKDAYS:
            for activity in ('work', 'gym'):
                data[f'{activity}_start_{weekday}'] = ''
                data[f'{activity}_end_{weekday}'] = ''

        response = self.client.post(reverse('weekly_schedule:edit_sleep'), data)
        self.assertEqual(response.status_code, 200)
        self.assertContains(response, 'با زمان خواب جدید سازگار نیست')
        self.assertEqual(WeeklySchedule.objects.get().sleep_time.strftime('%H:%M'), '01:00')

    def test_navigation_contains_weekly_schedule_between_habits_and_reminders(self):
        self.setup_schedule()
        response = self.client.get(reverse('weekly_schedule:weekly_schedule'))
        html = response.content.decode()
        navigation = html[html.index('<nav class="bottom-nav">'):]
        self.assertLess(navigation.index('عادت‌ها'), navigation.index('برنامه هفتگی'))
        self.assertLess(navigation.index('برنامه هفتگی'), navigation.index('یادآوری‌ها'))
        self.assertContains(response, '📅')

    def test_empty_hours_use_outline_colors_based_on_gap_duration(self):
        self.setup_schedule(work_start_1='10:00', work_end_1='11:00')
        self.add_activity(start='10:00', end='12:00')
        self.add_activity(name='جلسه', start='16:00', end='17:00')
        response = self.client.get(reverse('weekly_schedule:weekly_schedule'))

        row_blocks = [
            block
            for block in response.context['rows'][2]['blocks']
        ]
        activities = [block for block in row_blocks if not block.get('is_free')]
        empty_hours = [block for block in row_blocks if block.get('is_free')]
        self.assertEqual([block['name'] for block in activities], ['مطالعه', 'جلسه'])
        self.assertEqual(len(empty_hours), 3)
        self.assertEqual(
            [
                (block['start_slot'], block['duration_slots'])
                for block in empty_hours
            ],
            [(0, 4), (12, 16), (32, 32)],
        )
        self.assertEqual(
            [block['color'] for block in empty_hours],
            ['#3b82f6', '#10b981', '#10b981'],
        )
        self.assertTrue(all(block['color'] in block['style'] for block in empty_hours))
        work_block = next(
            block
            for block in response.context['rows'][1]['blocks']
            if block['name'] == 'کار'
        )
        self.assertEqual(work_block['color'], '#818cf8')
        self.assertContains(response, 'فاصله کمتر از ۴ ساعت')
        self.assertContains(response, 'فاصله بیشتر از ۴ ساعت')
        self.assertNotContains(response, 'زمان آزاد')
        self.assertNotIn('overflow-x: auto', response.content.decode())
