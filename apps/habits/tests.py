import datetime
import json

from django.test import TestCase
from django.urls import reverse
from django.utils import timezone

from .models import Habit, HabitLog
from .views import init_defaults


class HabitDateNavigationTests(TestCase):
    def setUp(self):
        self.habit = Habit.objects.create(title="مطالعه")
        self.today = timezone.localdate()

    def test_initial_habits_keep_early_rising_as_a_boolean_habit(self):
        Habit.objects.all().delete()

        init_defaults()

        early_rising = Habit.objects.get(title="سحرخیزی (07:00)")
        self.assertEqual(early_rising.habit_type, Habit.TYPE_BOOLEAN)
        self.assertEqual(early_rising.order, 3)

    def test_home_defaults_to_today_and_offers_date_navigation(self):
        response = self.client.get(reverse("home"))

        self.assertEqual(response.status_code, 200)
        self.assertTrue(response.context["is_selected_today"])
        self.assertEqual(response.context["selected_date_key"], self.today.isoformat())
        self.assertContains(response, "fa-circle-exclamation")
        self.assertContains(response, "روز قبل")
        self.assertContains(response, "روز بعد")
        self.assertContains(response, "برو به امروز")
        self.assertContains(response, "aria-expanded=\"false\"")
        self.assertContains(response, "/habit-date-data/")

    def test_date_endpoint_loads_day_logs_without_rendering_page(self):
        selected_date = self.today - datetime.timedelta(days=2)
        HabitLog.objects.create(habit=self.habit, date=selected_date, completed=True)

        response = self.client.get(
            reverse("habit_date_data"),
            {"date": selected_date.isoformat()},
        )

        self.assertEqual(response.status_code, 200)
        self.assertTrue(response.json()["habits"][str(self.habit.id)]["completed"])
        self.assertEqual(response.json()["date"], selected_date.isoformat())

    def test_date_endpoint_includes_default_value_for_unlogged_quantitative_habit(self):
        quantitative_habit = Habit.objects.create(
            title="مطالعه",
            habit_type=Habit.TYPE_QUANTITATIVE,
            min_value=10,
            max_value=100,
            default_value=25,
        )

        response = self.client.get(
            reverse("habit_date_data"),
            {"date": self.today.isoformat()},
        )

        self.assertEqual(response.status_code, 200)
        self.assertEqual(
            response.json()["habits"][str(quantitative_habit.id)]["default_value"],
            25,
        )

    def test_home_prefills_default_value_and_shows_skip_action(self):
        quantitative_habit = Habit.objects.create(
            title="مطالعه",
            habit_type=Habit.TYPE_QUANTITATIVE,
            min_value=10,
            max_value=100,
            default_value=25,
        )

        response = self.client.get(reverse("home"))

        self.assertEqual(response.status_code, 200)
        item = next(
            item for item in response.context["habits_data"]
            if item["habit"] == quantitative_habit
        )
        self.assertEqual(item["value"], 25)
        self.assertContains(response, 'value="25"')
        self.assertContains(response, "markHabitSkipped")
        self.assertNotContains(response, "نمودار برای عادت‌های تیک‌زدنی نمایش داده نمی‌شود")

    def test_home_loads_selected_past_date_and_links_one_day_each_way(self):
        selected_date = self.today - datetime.timedelta(days=2)
        HabitLog.objects.create(habit=self.habit, date=selected_date, completed=True)

        response = self.client.get(reverse("home"), {"date": selected_date.isoformat()})

        self.assertEqual(response.status_code, 200)
        self.assertFalse(response.context["is_selected_today"])
        self.assertEqual(response.context["completed_today"], 1)
        self.assertEqual(response.context["previous_date_key"], (selected_date - datetime.timedelta(days=1)).isoformat())
        self.assertEqual(response.context["next_date_key"], (selected_date + datetime.timedelta(days=1)).isoformat())
        self.assertContains(response, f'href="?date={(selected_date - datetime.timedelta(days=1)).isoformat()}"')
        self.assertContains(response, f'href="?date={(selected_date + datetime.timedelta(days=1)).isoformat()}"')
        self.assertContains(response, 'href="/" data-habit-date-today')

    def test_refreshing_selected_date_returns_to_today(self):
        selected_date = self.today - datetime.timedelta(days=2)

        response = self.client.get(reverse("home"), {"date": selected_date.isoformat()})

        self.assertContains(response, "pageNavigation.type === 'reload'")
        self.assertContains(response, "window.location.replace")
        self.assertContains(response, 'window.location.search).has(\'date\')')

    def test_home_does_not_navigate_to_a_future_date(self):
        future_date = self.today + datetime.timedelta(days=1)

        response = self.client.get(reverse("home"), {"date": future_date.isoformat()})

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.context["selected_date_key"], self.today.isoformat())
        self.assertTrue(response.context["is_selected_today"])

    def test_habit_can_be_logged_for_a_past_date(self):
        past_date = self.today - datetime.timedelta(days=3)

        response = self.client.post(
            reverse("toggle_habit"),
            data=json.dumps({
                "habit_id": self.habit.id,
                "completed": True,
                "value": None,
                "date": past_date.isoformat(),
            }),
            content_type="application/json",
        )

        self.assertEqual(response.status_code, 200)
        log = HabitLog.objects.get(habit=self.habit, date=past_date)
        self.assertTrue(log.completed)

    def test_habit_logging_rejects_future_dates(self):
        future_date = self.today + datetime.timedelta(days=1)

        response = self.client.post(
            reverse("toggle_habit"),
            data=json.dumps({
                "habit_id": self.habit.id,
                "completed": True,
                "date": future_date.isoformat(),
            }),
            content_type="application/json",
        )

        self.assertEqual(response.status_code, 400)
        self.assertFalse(HabitLog.objects.exists())

    def test_future_date_endpoint_is_rejected(self):
        response = self.client.get(
            reverse("habit_date_data"),
            {"date": (self.today + datetime.timedelta(days=1)).isoformat()},
        )

        self.assertEqual(response.status_code, 400)

    def test_empty_quantitative_value_is_filled_with_minimum_when_checked(self):
        quantitative_habit = Habit.objects.create(
            title="مطالعه",
            habit_type=Habit.TYPE_QUANTITATIVE,
            min_value=10,
            max_value=100,
        )

        response = self.client.post(
            reverse("toggle_habit"),
            data=json.dumps({
                "habit_id": quantitative_habit.id,
                "completed": True,
                "value": "",
                "date": self.today.isoformat(),
            }),
            content_type="application/json",
        )

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()["value"], 10)
        log = HabitLog.objects.get(habit=quantitative_habit, date=self.today)
        self.assertTrue(log.completed)
        self.assertEqual(log.value, 10)

    def test_empty_quantitative_value_uses_default_when_checked(self):
        quantitative_habit = Habit.objects.create(
            title="مطالعه",
            habit_type=Habit.TYPE_QUANTITATIVE,
            min_value=10,
            max_value=100,
            default_value=25,
        )

        response = self.client.post(
            reverse("toggle_habit"),
            data=json.dumps({
                "habit_id": quantitative_habit.id,
                "completed": True,
                "value": "",
                "date": self.today.isoformat(),
            }),
            content_type="application/json",
        )

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()["value"], 25)
        self.assertEqual(
            HabitLog.objects.get(habit=quantitative_habit, date=self.today).value,
            25,
        )

    def test_habit_can_be_marked_skipped_and_later_completed(self):
        response = self.client.post(
            reverse("toggle_habit"),
            data=json.dumps({
                "habit_id": self.habit.id,
                "completed": False,
                "skipped": True,
                "date": self.today.isoformat(),
            }),
            content_type="application/json",
        )

        self.assertEqual(response.status_code, 200)
        log = HabitLog.objects.get(habit=self.habit, date=self.today)
        self.assertTrue(log.skipped)
        self.assertFalse(log.completed)

        response = self.client.post(
            reverse("toggle_habit"),
            data=json.dumps({
                "habit_id": self.habit.id,
                "completed": True,
                "skipped": False,
                "date": self.today.isoformat(),
            }),
            content_type="application/json",
        )

        self.assertEqual(response.status_code, 200)
        log.refresh_from_db()
        self.assertTrue(log.completed)
        self.assertFalse(log.skipped)

    def test_quantitative_value_outside_habit_range_is_rejected(self):
        quantitative_habit = Habit.objects.create(
            title="مطالعه",
            habit_type=Habit.TYPE_QUANTITATIVE,
            min_value=10,
            max_value=100,
        )

        response = self.client.post(
            reverse("toggle_habit"),
            data=json.dumps({
                "habit_id": quantitative_habit.id,
                "completed": True,
                "value": 101,
                "date": self.today.isoformat(),
            }),
            content_type="application/json",
        )

        self.assertEqual(response.status_code, 400)
        self.assertFalse(HabitLog.objects.filter(habit=quantitative_habit).exists())

    def test_boolean_chart_endpoint_returns_only_completion_statistics(self):
        for days_ago in (4, 3, 1):
            HabitLog.objects.create(
                habit=self.habit,
                date=self.today - datetime.timedelta(days=days_ago),
                completed=True,
            )

        response = self.client.get(reverse("get_habit_chart_data", args=[self.habit.id]))

        self.assertEqual(response.status_code, 200)
        self.assertFalse(response.json()["is_quantitative"])
        self.assertEqual(response.json()["labels"], [])
        self.assertEqual(response.json()["stats"]["total_completed_days"], 3)
        self.assertEqual(response.json()["stats"]["longest_streak"], 2)

    def test_quantitative_chart_endpoint_returns_total_ticks_streak_and_value_sum(self):
        quantitative_habit = Habit.objects.create(
            title="مطالعه",
            habit_type=Habit.TYPE_QUANTITATIVE,
            min_value=10,
            max_value=100,
        )
        for days_ago, value, completed in ((3, 10, True), (2, 20, True), (1, 50, False)):
            HabitLog.objects.create(
                habit=quantitative_habit,
                date=self.today - datetime.timedelta(days=days_ago),
                value=value,
                completed=completed,
            )

        response = self.client.get(
            reverse("get_habit_chart_data", args=[quantitative_habit.id])
        )

        self.assertTrue(response.json()["is_quantitative"])
        self.assertEqual(response.json()["stats"]["total_completed_days"], 2)
        self.assertEqual(response.json()["stats"]["longest_streak"], 2)
        self.assertEqual(response.json()["stats"]["total_value"], 30)
