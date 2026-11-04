import datetime
import json
import os
import tempfile

import jdatetime
from django.core.files.base import ContentFile
from django.test import TestCase
from django.test import override_settings
from django.urls import reverse

from apps.alerts.models import Alert
from apps.alerts.views import _alert_occurs_on_date
from apps.habits.models import Habit, HabitLog
from .models import AchievementUnlock, UserProfile


class UserNavigationTests(TestCase):
    def test_achievements_page_replaces_profile_settings_page(self):
        response = self.client.get(reverse("achievements"))

        self.assertEqual(response.status_code, 200)
        self.assertContains(response, "دستاوردها")
        self.assertContains(response, "id=\"appMenu\"")
        self.assertContains(response, "aria-label=\"باز کردن تنظیمات\"")
        self.assertContains(response, "نام کاربری:")
        self.assertContains(response, "درباره ما")
        self.assertContains(response, "تهیه فایل پشتیبان")
        self.assertContains(response, "بازگردانی اطلاعات")
        self.assertContains(response, "انتقادات و پیشنهادات")
        self.assertContains(response, reverse("profile_edit"))
        self.assertNotContains(response, "ذخیره تغییرات")

    def test_edit_button_opens_separate_page_with_jalali_birth_date_fields(self):
        response = self.client.get(reverse("profile_edit"))

        self.assertEqual(response.status_code, 200)
        self.assertContains(response, "✏️")
        self.assertContains(response, 'name="birth_day"')
        self.assertContains(response, 'name="birth_month"')
        self.assertContains(response, 'name="birth_year"')
        self.assertContains(response, 'name="remove_profile_pic"')
        self.assertNotContains(response, 'name="birth_date"')
        self.assertNotContains(response, 'name="age"')

    def test_old_profile_url_redirects_to_achievements(self):
        response = self.client.get("/profile/")

        self.assertRedirects(response, reverse("achievements"))

    def test_settings_menu_saves_profile_and_returns_to_current_page(self):
        birth_date = datetime.date(1995, 5, 12)
        response = self.client.post(
            reverse("profile"),
            {
                "name": "نام آزمایشی",
                "birth_date": birth_date.isoformat(),
                "email": "test@example.com",
                "phone": "09120000000",
                "next": "/alerts/",
            },
        )

        profile = UserProfile.objects.get(id=1)
        self.assertRedirects(response, "/alerts/", fetch_redirect_response=False)
        self.assertEqual(profile.name, "نام آزمایشی")
        self.assertEqual(profile.birth_date, birth_date)
        self.assertEqual(profile.email, "test@example.com")
        today = datetime.date.today()
        expected_age = today.year - birth_date.year - (
            (today.month, today.day) < (birth_date.month, birth_date.day)
        )
        self.assertEqual(profile.calculated_age, expected_age)

    def test_profile_edit_saves_jalali_birth_date(self):
        response = self.client.post(
            reverse("profile"),
            {
                "name": "نام آزمایشی",
                "birth_day": "۲۲",
                "birth_month": "۳",
                "birth_year": "۱۳۷۴",
                "email": "test@example.com",
                "phone": "09120000000",
                "next": reverse("achievements"),
            },
        )

        self.assertRedirects(response, reverse("achievements"), fetch_redirect_response=False)
        profile = UserProfile.objects.get(id=1)
        self.assertEqual(
            profile.birth_date,
            jdatetime.date(1374, 3, 22).togregorian(),
        )
        self.assertTrue(Alert.objects.filter(is_birthday=True).exists())

    def test_profile_rejects_partial_or_invalid_jalali_birth_date(self):
        valid_profile = {
            "name": "نام آزمایشی",
            "email": "test@example.com",
            "phone": "09120000000",
            "next": reverse("profile_edit"),
        }

        for date_fields in (
            {"birth_day": "22", "birth_month": "", "birth_year": "1374"},
            {"birth_day": "31", "birth_month": "12", "birth_year": "1405"},
        ):
            with self.subTest(date_fields=date_fields):
                response = self.client.post(reverse("profile"), {**valid_profile, **date_fields})
                self.assertRedirects(response, reverse("profile_edit"), fetch_redirect_response=False)
                self.assertFalse(UserProfile.objects.filter(id=1).exists())

    def test_profile_can_delete_current_profile_picture(self):
        with tempfile.TemporaryDirectory() as media_root, override_settings(MEDIA_ROOT=media_root):
            profile = UserProfile.objects.create()
            profile.profile_pic.save("avatar.png", ContentFile(b"test image"), save=True)
            saved_picture_path = profile.profile_pic.path

            response = self.client.post(
                reverse("profile"),
                {
                    "name": profile.name,
                    "email": profile.email,
                    "phone": profile.phone,
                    "birth_day": "",
                    "birth_month": "",
                    "birth_year": "",
                    "remove_profile_pic": "on",
                },
            )

            self.assertRedirects(response, reverse("achievements"), fetch_redirect_response=False)
            profile.refresh_from_db()
            self.assertFalse(profile.profile_pic)
            self.assertFalse(os.path.exists(saved_picture_path))

    def test_saving_birthday_creates_one_yearly_alert_with_one_week_notice(self):
        birthday = datetime.date(1995, 5, 12)
        self.client.post(
            reverse("profile"),
            {
                "name": "نام آزمایشی",
                "birth_date": birthday.isoformat(),
                "email": "test@example.com",
                "phone": "09120000000",
            },
        )

        alert = Alert.objects.get(is_birthday=True)
        jalali_birthday = jdatetime.date.fromgregorian(date=birthday)
        self.assertEqual(alert.frequency, Alert.FREQ_YEARLY)
        self.assertEqual(alert.advance_notice_value, 7)
        self.assertTrue(_alert_occurs_on_date(alert, jalali_birthday))
        self.assertFalse(_alert_occurs_on_date(
            alert,
            jalali_birthday + jdatetime.timedelta(days=1),
        ))
        self.assertIn("09:00", alert.time_str)

    def test_changing_birthday_updates_its_alert_and_clearing_it_removes_alert(self):
        profile_data = {
            "name": "نام آزمایشی",
            "email": "test@example.com",
            "phone": "09120000000",
        }
        self.client.post(
            reverse("profile"),
            {**profile_data, "birth_date": "1995-05-12"},
        )
        first_alert = Alert.objects.get(is_birthday=True)

        self.client.post(
            reverse("profile"),
            {**profile_data, "birth_date": "1996-08-20"},
        )

        self.assertEqual(Alert.objects.filter(is_birthday=True).count(), 1)
        first_alert.refresh_from_db()
        self.assertIn("مرداد", first_alert.time_str)

        self.client.post(
            reverse("profile"),
            {**profile_data, "birth_date": ""},
        )
        self.assertFalse(Alert.objects.filter(is_birthday=True).exists())

    def test_profile_rejects_invalid_email_and_phone(self):
        valid_profile = {
            "name": "نام آزمایشی",
            "birth_date": "1995-05-12",
            "email": "test@example.com",
            "phone": "09120000000",
        }

        for invalid_data in (
            {**valid_profile, "email": "not-an-email"},
            {**valid_profile, "phone": "not-a-phone"},
        ):
            with self.subTest(data=invalid_data):
                response = self.client.post(reverse("profile"), invalid_data)
                self.assertRedirects(response, reverse("achievements"), fetch_redirect_response=False)
                self.assertFalse(UserProfile.objects.filter(id=1).exists())
                self.assertFalse(Alert.objects.filter(is_birthday=True).exists())

    def test_achievements_show_habit_progress(self):
        habit = Habit.objects.create(title="مطالعه")
        HabitLog.objects.create(habit=habit, date="2026-11-04", completed=True)

        response = self.client.get(reverse("achievements"))

        self.assertEqual(response.context["active_habit_count"], 1)
        self.assertEqual(response.context["completed_log_count"], 1)
        self.assertEqual(response.context["active_day_count"], 1)
        self.assertEqual(response.context["achievement_count"], 36)
        self.assertContains(response, "کاپ حبیب")
        self.assertContains(response, "XP")

    def test_cumulative_milestone_unlocks_once_and_awards_xp(self):
        habit = Habit.objects.create(title="مطالعه")
        for days_ago in (14, 12, 10, 8, 6, 4, 2):
            HabitLog.objects.create(
                habit=habit,
                date=datetime.date.today() - datetime.timedelta(days=days_ago),
                completed=True,
            )

        response = self.client.get(reverse("achievements"))

        self.assertEqual(response.status_code, 200)
        self.assertTrue(AchievementUnlock.objects.filter(code="active_days:cumulative:7").exists())
        self.assertTrue(AchievementUnlock.objects.filter(code="all_habits_streak:cumulative:7").exists())
        self.assertTrue(AchievementUnlock.objects.filter(code=f"habit:{habit.id}:completed:7").exists())
        self.assertEqual(response.context["unlocked_count"], 3)
        self.assertEqual(response.context["total_xp"], 35)

        self.client.get(reverse("achievements"))
        self.assertEqual(AchievementUnlock.objects.count(), 3)

    def test_all_habits_streak_achievements_reward_one_and_a_half_xp(self):
        Habit.objects.create(title="مطالعه")

        response = self.client.get(reverse("achievements"))
        all_habits_achievements = [
            achievement for achievement in response.context["achievements"]
            if achievement["category"] == "all_habits_streak"
        ]

        self.assertEqual(
            [achievement["xp_reward"] for achievement in all_habits_achievements
             if achievement["code"].startswith("all_habits_streak:cumulative:")],
            [15, 38, 75, 150, 300, 548],
        )
        self.assertEqual(
            [achievement["xp_reward"] for achievement in all_habits_achievements
             if achievement["code"].startswith("all_habits_streak:consecutive:")],
            [30, 75, 150, 300, 600, 1095],
        )

    def test_individual_completion_achievements_scale_with_active_habits(self):
        Habit.objects.create(title="مطالعه")
        Habit.objects.create(title="ورزش")

        response = self.client.get(reverse("achievements"))

        self.assertEqual(response.context["achievement_count"], 48)

    def test_each_active_habit_has_six_completion_and_six_streak_milestones(self):
        habit = Habit.objects.create(title="ورزش کردن", emoji="🏋️")

        response = self.client.get(reverse("achievements"))

        habit_achievements = [
            achievement for achievement in response.context["achievements"]
            if achievement["code"].startswith(f"habit:{habit.id}:")
        ]
        self.assertEqual(len(habit_achievements), 12)
        self.assertEqual(
            {achievement["target"] for achievement in habit_achievements},
            {7, 21, 40, 100, 200, 365},
        )
        self.assertEqual(
            sum(achievement["category"] == "habit_streak" for achievement in habit_achievements),
            6,
        )
        self.assertEqual(
            sum(achievement["category"] == "habit_completions" for achievement in habit_achievements),
            6,
        )
        self.assertEqual(
            [achievement["xp_reward"] for achievement in habit_achievements if achievement["category"] == "habit_streak"],
            [20, 50, 100, 200, 400, 730],
        )

    def test_add_habit_is_rejected_when_ten_habits_are_active(self):
        for index in range(10):
            Habit.objects.create(title=f"عادت {index}")

        response = self.client.post(
            reverse("add_habit"),
            {"title": "عادت یازدهم"},
        )

        self.assertEqual(response.status_code, 400)
        self.assertEqual(Habit.objects.filter(is_deleted=False).count(), 10)

    def test_completing_a_habit_awards_new_streak_achievements_immediately(self):
        habit = Habit.objects.create(title="مطالعه")
        for days_ago in range(1, 7):
            HabitLog.objects.create(
                habit=habit,
                date=datetime.date.today() - datetime.timedelta(days=days_ago),
                completed=True,
            )

        response = self.client.post(
            reverse("toggle_habit"),
            data=json.dumps({
                "habit_id": habit.id,
                "completed": True,
            }),
            content_type="application/json",
        )

        self.assertEqual(response.status_code, 200)
        self.assertTrue(AchievementUnlock.objects.filter(code="active_days:consecutive:7").exists())
        self.assertTrue(
            AchievementUnlock.objects.filter(code=f"habit:{habit.id}:streak:7").exists()
        )

    def test_achievement_list_is_ordered_by_xp_from_lowest_to_highest(self):
        Habit.objects.create(title="ورزش")

        response = self.client.get(reverse("achievements"))
        achievements = response.context["achievements"]

        self.assertEqual(achievements[0]["code"], "active_days:cumulative:7")
        self.assertEqual(achievements[1]["code"], "habit:1:completed:7")
        self.assertEqual(achievements[2]["code"], "all_habits_streak:cumulative:7")
        self.assertEqual(
            [achievement["xp_reward"] for achievement in achievements],
            sorted(achievement["xp_reward"] for achievement in achievements),
        )
        self.assertNotIn("تداوم یک عادت", " ".join(a["title"] for a in achievements))

    def test_cumulative_achievement_titles_use_successful_record_wording(self):
        Habit.objects.create(title="ورزش")

        response = self.client.get(reverse("achievements"))
        cumulative = [
            achievement for achievement in response.context["achievements"]
            if achievement["code"].endswith(":cumulative:7")
        ]

        self.assertEqual(len(cumulative), 2)
        self.assertTrue(all("۷ ثبت موفق" in achievement["title"] for achievement in cumulative))
        self.assertNotContains(response, "با وقفه")

    def test_deleting_a_habit_removes_its_achievements_and_xp(self):
        habit = Habit.objects.create(title="ورزش")
        for days_ago in range(7):
            HabitLog.objects.create(
                habit=habit,
                date=datetime.date.today() - datetime.timedelta(days=days_ago),
                completed=True,
            )

        response = self.client.get(reverse("achievements"))
        total_xp_before_delete = response.context["total_xp"]
        habit_xp = sum(
            unlock.xp_reward for unlock in AchievementUnlock.objects.filter(
                code__startswith=f"habit:{habit.id}:"
            )
        )
        self.assertGreater(habit_xp, 0)

        self.client.post(reverse("delete_habit", args=[habit.id]))

        self.assertFalse(
            AchievementUnlock.objects.filter(code__startswith=f"habit:{habit.id}:").exists()
        )
        response = self.client.get(reverse("achievements"))
        self.assertEqual(response.context["total_xp"], total_xp_before_delete - habit_xp)
        self.assertEqual(response.context["achievement_count"], 24)

    def test_trophy_tiers_have_three_levels_for_each_rank_name(self):
        from .achievements import TROPHY_RANK_NAMES, TROPHY_TIERS

        self.assertEqual(len(TROPHY_TIERS), 18)
        self.assertEqual(
            set(TROPHY_RANK_NAMES.values()),
            {"تازه‌کار", "پیگیر", "قهرمان", "اسطوره", "سخت‌جان", "افسانه‌ای"},
        )
        self.assertEqual(
            {name: sum(tier[1] == name for tier in TROPHY_TIERS)
             for name in {tier[1] for tier in TROPHY_TIERS}},
            {name: 3 for name in {tier[1] for tier in TROPHY_TIERS}},
        )

    def test_trophy_viewer_uses_requested_names_and_roman_levels(self):
        response = self.client.get(reverse("achievements"))

        cups = response.context["trophy_cups"]
        self.assertEqual(cups[0]["title"], "تازه‌کار (I)")
        self.assertEqual(cups[1]["title"], "تازه‌کار (II)")
        self.assertEqual(cups[2]["title"], "تازه‌کار (III)")
        self.assertEqual(cups[3]["title"], "پیگیر (I)")
        self.assertEqual(cups[-1]["title"], "افسانه‌ای (III)")
        self.assertEqual(len({cup["title"] for cup in cups}), 18)
        self.assertEqual(response.context["selected_trophy"]["title"], "تازه‌کار (I)")
        self.assertContains(response, "تازه‌کار (I)")
        self.assertContains(response, "aria-label=\"نمایش کاپ قبلی\"")
        self.assertContains(response, "aria-label=\"نمایش کاپ بعدی\"")
        self.assertNotContains(response, "مسیر کاپ‌ها")
        self.assertContains(response, "class=\"trophy-lock\"")
        self.assertNotContains(response, "چوبی")
        self.assertNotContains(response, "تیتانیوم")
        self.assertContains(response, "@keyframes trophy-slide-next")
        self.assertContains(response, "@keyframes trophy-slide-previous")
        self.assertContains(response, "prefers-reduced-motion: reduce")

    def test_trophy_viewer_has_remaining_xp_and_locked_cup_data(self):
        response = self.client.get(reverse("achievements"))

        cups = response.context["trophy_cups"]
        self.assertEqual(len(cups), 18)
        self.assertEqual(
            [cup["threshold"] for cup in cups],
            sorted(cup["threshold"] for cup in cups),
        )
        self.assertEqual(response.context["current_trophy_index"], 0)
        self.assertEqual(response.context["trophy_progress_percent"], 0)
        self.assertTrue(cups[0]["earned"])
        self.assertEqual(cups[1]["xp_needed"], 100)
        self.assertEqual(cups[1]["progress_percent"], 0)
        self.assertContains(response, "data-xp-needed=\"100\"")
        self.assertContains(response, "data-earned=\"false\"")
        self.assertContains(response, "XP تا رسیدن")
        self.assertContains(response, "aria-valuenow=\"0\"")

    def test_ten_fully_completed_habits_reach_top_trophy(self):
        habits = [
            Habit.objects.create(title=f"عادت {index}")
            for index in range(10)
        ]
        today = datetime.date.today()
        HabitLog.objects.bulk_create([
            HabitLog(
                habit=habit,
                date=today - datetime.timedelta(days=days_ago),
                completed=True,
            )
            for habit in habits
            for days_ago in range(365)
        ])

        response = self.client.get(reverse("achievements"))

        self.assertEqual(response.context["total_xp"], 28126)
        self.assertEqual(response.context["trophy_tier"], "افسانه‌ای (III)")
        self.assertEqual(response.context["unlocked_count"], 144)
        self.assertEqual(response.context["current_trophy_index"], 17)
        self.assertEqual(response.context["trophy_progress_percent"], 100)
        self.assertTrue(all(cup["earned"] for cup in response.context["trophy_cups"]))
