import datetime

import jdatetime
from django.test import TestCase
from django.urls import reverse

from .models import Alert, CalendarEvent
from .views import _alert_occurs_on_date, init_default_alerts


class AlertsCalendarTests(TestCase):
    def setUp(self):
        Alert.objects.create(
            title="یادآوری روزانه",
            frequency=Alert.FREQ_DAILY,
            time_str="ساعت ۹",
        )
        Alert.objects.create(
            title="یادآوری هفتگی",
            frequency=Alert.FREQ_WEEKLY,
            time_str="جمعه",
        )

    def test_calendar_is_first_and_alerts_are_hidden_until_filtered(self):
        response = self.client.get(reverse("alerts"))

        self.assertEqual(response.status_code, 200)
        self.assertFalse(response.context["has_filter"])
        self.assertEqual(response.context["alerts"].count(), 2)
        self.assertContains(response, "calendar-grid")
        self.assertContains(response, "یادآوری‌های این روز")
        self.assertContains(response, 'data-frequency="daily"')
        self.assertContains(response, 'data-frequency="weekly"')
        self.assertRegex(response.content.decode(), r'data-frequency="daily"\s+style="display:none;"')

    def test_frequency_filter_only_shows_matching_alerts(self):
        response = self.client.get(reverse("alerts"), {"filter": Alert.FREQ_DAILY})

        self.assertEqual(response.context["alerts"].count(), 2)
        self.assertContains(response, "یادآوری روزانه")
        self.assertContains(response, 'data-frequency="daily"')
        self.assertRegex(response.content.decode(), r'data-frequency="daily"\s+style="display:flex;"')
        self.assertRegex(response.content.decode(), r'data-frequency="weekly"\s+style="display:none;"')

    def test_all_filter_shows_every_reminder(self):
        response = self.client.get(reverse("alerts"), {"filter": "all"})

        self.assertTrue(response.context["has_filter"])
        self.assertContains(response, 'data-reminder-filter="all"')
        self.assertRegex(response.content.decode(), r'data-frequency="daily"\s+style="display:flex;"')
        self.assertRegex(response.content.decode(), r'data-frequency="weekly"\s+style="display:flex;"')

    def test_alert_can_be_edited_from_its_card(self):
        alert = Alert.objects.get(frequency=Alert.FREQ_DAILY)
        response = self.client.get(reverse("alerts"))
        self.assertContains(response, 'aria-label="ویرایش یادآوری"')
        self.assertContains(response, 'name="description"')

        response = self.client.post(
            reverse("edit_alert", args=[alert.id]),
            {
                "title": "عنوان ویرایش‌شده",
                "description": "شرح یادآوری ویرایش‌شده",
                "frequency": Alert.FREQ_WEEKLY,
                "weekday": "جمعه",
                "time_val": "12:00",
                "month": "0",
                "date": datetime.date.today().isoformat(),
            },
        )

        alert.refresh_from_db()
        self.assertEqual(response.status_code, 302)
        self.assertEqual(alert.title, "عنوان ویرایش‌شده")
        self.assertEqual(alert.description, "شرح یادآوری ویرایش‌شده")
        self.assertEqual(alert.frequency, Alert.FREQ_WEEKLY)
        self.assertEqual(alert.time_str, "روزهای جمعه - ساعت 12:00")

    def test_alert_description_can_be_added_and_is_displayed(self):
        response = self.client.post(
            reverse("add_alert"),
            {
                "title": "برنامه‌ریزی هفتگی جدید",
                "description": "برای هفته آینده برنامه بنویس",
                "frequency": Alert.FREQ_WEEKLY,
                "weekday": ["جمعه"],
                "time_val": "18:00",
            },
        )

        self.assertEqual(response.status_code, 302)
        alert = Alert.objects.get(title="برنامه‌ریزی هفتگی جدید")
        self.assertEqual(alert.description, "برای هفته آینده برنامه بنویس")
        response = self.client.get(reverse("alerts"), {"filter": "weekly"})
        self.assertContains(response, "برای هفته آینده برنامه بنویس")
        self.assertContains(response, 'data-alert-description="برای هفته آینده برنامه بنویس"')

    def test_default_alerts_include_requested_descriptions(self):
        Alert.objects.all().delete()

        init_default_alerts()

        weekly = Alert.objects.get(title="برنامه‌ریزی هفتگی")
        tree_planting = Alert.objects.get(title="کاشت درخت")
        self.assertEqual(
            weekly.description,
            "بهترین کاری که میتونی جمعه ها انجام بدی نوشتن برنامه ی هفته ی آینده ست",
        )
        self.assertEqual(tree_planting.description, "ی تصمیم خوب و ماندگار :)")

    def test_daily_alert_can_store_advance_notice_in_hours(self):
        response = self.client.post(
            reverse("add_alert"),
            {
                "title": "تمرین",
                "frequency": Alert.FREQ_DAILY,
                "time_val": "20:00",
                "advance_notice_enabled": "on",
                "advance_notice_value": "۲",
            },
        )

        self.assertEqual(response.status_code, 302)
        alert = Alert.objects.get(title="تمرین")
        self.assertEqual(alert.advance_notice_value, 2)

        response = self.client.get(reverse("alerts"))
        self.assertContains(response, "۲ ساعت دیگه باید تمرین بدی")
        self.assertContains(response, 'data-alert-advance-notice="2"')

    def test_non_daily_alert_can_edit_advance_notice_in_days(self):
        alert = Alert.objects.create(
            title="پرداخت شهریه باشگاه",
            frequency=Alert.FREQ_MONTHLY,
            time_str="روز ۱۵ ماه - ساعت 10:00",
            advance_notice_value=2,
        )

        response = self.client.get(reverse("alerts"))
        self.assertContains(response, "۲ روز دیگه باید پرداخت شهریه باشگاه بدی")
        self.assertContains(response, 'data-alert-advance-notice="2"')

        self.client.post(
            reverse("edit_alert", args=[alert.id]),
            {
                "title": "پرداخت شهریه باشگاه",
                "frequency": Alert.FREQ_MONTHLY,
                "month_day": "۱۵",
                "time_val": "10:00",
                "advance_notice_enabled": "on",
                "advance_notice_value": "۵",
                "month": "0",
                "date": datetime.date.today().isoformat(),
            },
        )

        alert.refresh_from_db()
        self.assertEqual(alert.advance_notice_value, 5)

    def test_advance_notice_validation_rejects_out_of_range_value(self):
        response = self.client.post(
            reverse("add_alert"),
            {
                "title": "تمرین",
                "frequency": Alert.FREQ_DAILY,
                "time_val": "20:00",
                "advance_notice_enabled": "on",
                "advance_notice_value": "24",
            },
            follow=True,
        )

        self.assertFalse(Alert.objects.filter(title="تمرین").exists())
        self.assertContains(response, "پیش‌آگاهی باید بین ۱ تا ۲۳ ساعت باشد.")

    def test_weekly_alert_supports_multiple_days_and_edit_form_values(self):
        response = self.client.post(
            reverse("add_alert"),
            {
                "title": "برنامه‌ریزی",
                "frequency": Alert.FREQ_WEEKLY,
                "weekday": ["چهارشنبه", "جمعه"],
                "time_val": "18:15",
            },
        )
        self.assertEqual(response.status_code, 302)
        alert = Alert.objects.get(title="برنامه‌ریزی")
        self.assertEqual(alert.time_str, "روزهای چهارشنبه، جمعه - ساعت 18:15")
        self.assertTrue(_alert_occurs_on_date(alert, jdatetime.date(1405, 8, 13)))
        self.assertFalse(_alert_occurs_on_date(alert, jdatetime.date(1405, 8, 14)))

        response = self.client.get(reverse("alerts"))
        self.assertContains(response, 'data-alert-weekdays="چهارشنبه,جمعه"')
        self.assertContains(response, 'type="checkbox" name="weekday"')

    def test_weekly_alert_rejects_all_weekdays(self):
        response = self.client.post(
            reverse("add_alert"),
            {
                "title": "هر روز",
                "frequency": Alert.FREQ_WEEKLY,
                "weekday": ["شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنج‌شنبه", "جمعه"],
                "time_val": "09:00",
            },
            follow=True,
        )

        self.assertFalse(Alert.objects.filter(title="هر روز").exists())
        self.assertContains(response, "این که شد الارم روزانه عزیزم")

    def test_yearly_reminder_uses_persian_date_fields_for_add_and_edit(self):
        response = self.client.post(
            reverse("add_alert"),
            {
                "title": "سالگرد",
                "frequency": Alert.FREQ_YEARLY,
                "time_val": "07:30",
                "year_day": "13",
                "year_month": "8",
                "year_value": "1405",
            },
        )
        self.assertEqual(response.status_code, 302)
        alert = Alert.objects.get(title="سالگرد")
        self.assertEqual(alert.time_str, "۱۳ آبان ۱۴۰۵ - ساعت 07:30")
        self.assertTrue(_alert_occurs_on_date(alert, jdatetime.date(1406, 8, 13)))

        response = self.client.get(reverse("alerts"))
        html = response.content.decode()
        self.assertIn('data-alert-day="13"', html)
        self.assertIn('data-alert-month="8"', html)
        self.assertIn('data-alert-year="1405"', html)
        self.assertContains(response, 'name="year_day"')
        self.assertContains(response, 'name="year_month"')
        self.assertContains(response, 'name="year_value"')

        response = self.client.post(
            reverse("edit_alert", args=[alert.id]),
            {
                "title": "سالگرد",
                "frequency": Alert.FREQ_YEARLY,
                "time_val": "08:45",
                "year_day": "14",
                "year_month": "9",
                "year_value": "1404",
            },
        )
        alert.refresh_from_db()
        self.assertEqual(response.status_code, 302)
        self.assertEqual(alert.time_str, "۱۴ آذر ۱۴۰۴ - ساعت 08:45")

    def test_calendar_event_is_saved_shown_with_color_and_deletable(self):
        selected_date = datetime.date.today()
        response = self.client.post(
            reverse("add_calendar_event"),
            {
                "date": selected_date.isoformat(),
                "title": "اتمام بیمه ماشین",
                "color": "#ef4444",
                "month": "0",
            },
        )

        event = CalendarEvent.objects.get()
        self.assertEqual(response.status_code, 302)
        self.assertEqual(event.date, selected_date)
        self.assertEqual(event.color, "#ef4444")

        response = self.client.get(
            reverse("alerts"),
            {"date": selected_date.isoformat(), "month": "0"},
        )
        self.assertContains(response, "اتمام بیمه ماشین")
        self.assertContains(response, "background-color:#ef4444")

        response = self.client.post(
            reverse("delete_calendar_event", args=[event.id]),
            {"month": "0"},
        )
        self.assertEqual(response.status_code, 302)
        self.assertFalse(CalendarEvent.objects.exists())

    def test_calendar_marks_reminders_and_day_summary_uses_reminder_colors(self):
        selected_date = datetime.date.today()
        CalendarEvent.objects.create(
            date=selected_date,
            title="قرار",
            color="#facc15",
        )

        response = self.client.get(
            reverse("alerts"),
            {"date": selected_date.isoformat(), "month": "0"},
        )

        self.assertContains(response, 'data-marker-type="reminder"')
        self.assertContains(response, 'data-marker-type="event"')
        self.assertContains(response, 'style="background-color:#38bdf8;"')
        self.assertContains(response, 'class="selected-alert-title" style="color:#38bdf8;"')
        self.assertEqual(len(response.context["event_colors"]), 4)
        self.assertEqual(
            set(response.context["event_colors"].values()),
            {"#facc15", "#fb923c", "#f97316", "#ef4444"},
        )

    def test_calendar_event_can_be_edited(self):
        event = CalendarEvent.objects.create(
            date=datetime.date.today(),
            title="عنوان قبلی",
            color="#ef4444",
        )

        response = self.client.post(
            reverse("edit_calendar_event", args=[event.id]),
            {
                "title": "عنوان اصلاح‌شدهٔ طولانی‌تر",
                "color": "#f97316",
                "month": "0",
            },
        )

        event.refresh_from_db()
        self.assertEqual(response.status_code, 302)
        self.assertEqual(event.title, "عنوان اصلاح‌شدهٔ طولانی‌تر")
        self.assertEqual(event.color, "#f97316")

    def test_selected_day_shows_alerts_matching_their_recurrence(self):
        selected_jalali = jdatetime.date(1405, 8, 13)
        daily = Alert.objects.create(title="هر روز", frequency=Alert.FREQ_DAILY)
        weekly_match = Alert.objects.create(
            title="چهارشنبه‌ها",
            frequency=Alert.FREQ_WEEKLY,
            time_str="هر چهارشنبه‌ها - ساعت 10:00",
        )
        weekly_mismatch = Alert.objects.create(
            title="جمعه‌ها",
            frequency=Alert.FREQ_WEEKLY,
            time_str="هر جمعه‌ها - ساعت 10:00",
        )
        monthly = Alert.objects.create(
            title="روز سیزدهم",
            frequency=Alert.FREQ_MONTHLY,
            time_str="روز ۱۳ ماه - ساعت 10:00",
        )
        yearly = Alert.objects.create(
            title="قرار سالانه",
            frequency=Alert.FREQ_YEARLY,
            time_str="۱۳ آبان - ساعت 10:00",
        )

        self.assertTrue(_alert_occurs_on_date(daily, selected_jalali))
        self.assertTrue(_alert_occurs_on_date(weekly_match, selected_jalali))
        self.assertFalse(_alert_occurs_on_date(weekly_mismatch, selected_jalali))
        self.assertTrue(_alert_occurs_on_date(monthly, selected_jalali))
        self.assertTrue(_alert_occurs_on_date(yearly, selected_jalali))

    def test_selected_day_controls_and_today_link_are_rendered(self):
        today_jalali = jdatetime.date.fromgregorian(date=datetime.date.today())
        month_offset = 0
        selected_date = datetime.date.today()
        response = self.client.get(
            reverse("alerts"),
            {"month": month_offset, "date": selected_date.isoformat()},
        )

        self.assertEqual(response.status_code, 200)
        self.assertContains(response, response.context["selected_date_jalali"])
        self.assertContains(response, "روز قبل")
        self.assertContains(response, "روز بعد")
        self.assertContains(response, "رفتن به امروز")
        self.assertContains(response, "افزودن رویداد")
        self.assertContains(response, 'aria-label="افزودن یادآوری جدید"')
        html = response.content.decode()
        self.assertLess(html.index('id="noFilteredAlerts"'), html.index('class="reminder-actions"'))
        selected_day_start = html.index("selected-day-card")
        self.assertNotIn('aria-label="افزودن یادآوری جدید"', html[selected_day_start:html.index('id="addEventModal"')])
