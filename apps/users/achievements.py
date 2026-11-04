import datetime

from django.utils import timezone

from apps.habits.models import Habit, HabitLog
from .models import AchievementUnlock

MILESTONES = (7, 21, 40, 100, 200, 365)
XP_REWARDS = (10, 25, 50, 100, 200, 365)
TROPHY_TIERS = (
    (27000, "تیتانیوم", 3, "#e5e7eb", "#64748b"),
    (25000, "تیتانیوم", 2, "#f3f4f6", "#9ca3af"),
    (23000, "تیتانیوم", 1, "#d1d5db", "#6b7280"),
    (20000, "الماس", 3, "#bae6fd", "#0284c7"),
    (17000, "الماس", 2, "#7dd3fc", "#0369a1"),
    (14000, "الماس", 1, "#38bdf8", "#075985"),
    (11000, "طلا", 3, "#fef08a", "#ca8a04"),
    (8500, "طلا", 2, "#facc15", "#a16207"),
    (6500, "طلا", 1, "#eab308", "#854d0e"),
    (4500, "نقره", 3, "#e2e8f0", "#64748b"),
    (3200, "نقره", 2, "#cbd5e1", "#475569"),
    (2200, "نقره", 1, "#94a3b8", "#334155"),
    (1400, "برنز", 3, "#fdba74", "#c2410c"),
    (900, "برنز", 2, "#fb923c", "#9a3412"),
    (500, "برنز", 1, "#ea580c", "#7c2d12"),
    (250, "چوبی", 3, "#d6a77a", "#7c4a24"),
    (100, "چوبی", 2, "#bb8b5e", "#633b1e"),
    (0, "چوبی", 1, "#a47148", "#4b2e1a"),
)
TROPHY_RANK_NAMES = {
    "چوبی": "تازه‌کار",
    "برنز": "پیگیر",
    "نقره": "قهرمان",
    "طلا": "اسطوره",
    "الماس": "سخت‌جان",
    "تیتانیوم": "افسانه‌ای",
}
TROPHY_ROMAN_LEVELS = ("I", "II", "III")


def _persian_digits(value):
    return str(value).translate(str.maketrans("0123456789", "۰۱۲۳۴۵۶۷۸۹"))


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


def _definitions(habits, progress_values):
    categories = (
        (
            "all_habits_streak",
            "تداوم همهٔ عادت‌ها",
            "روزهایی که همهٔ عادت‌های فعال انجام شده‌اند",
        ),
        (
            "active_days",
            "روزهای فعال در حبیب",
            "روزهایی که دست‌کم یک عادت ثبت شده است",
        ),
    )
    achievements = []
    for category, label, description in categories:
        for streak_type in ("consecutive", "cumulative"):
            for index, target in enumerate(MILESTONES):
                xp_reward = XP_REWARDS[index] * (2 if streak_type == "consecutive" else 1)
                if category == "all_habits_streak":
                    xp_reward = (xp_reward * 3 + 1) // 2
                title_suffix = (
                    f"{_persian_digits(target)} روز پشت سر هم"
                    if streak_type == "consecutive"
                    else f"{_persian_digits(target)} ثبت موفق"
                )
                achievements.append({
                    "code": f"{category}:{streak_type}:{target}",
                    "title": f"{label}؛ {title_suffix}",
                    "description": description,
                    "category": category,
                    "target": target,
                    "progress": progress_values[(category, streak_type)],
                    "xp_reward": xp_reward,
                    "milestone_index": index,
                    "sort_priority": {
                        ("active_days", "cumulative"): 0,
                        ("all_habits_streak", "cumulative"): 3,
                        ("active_days", "consecutive"): 4,
                        ("all_habits_streak", "consecutive"): 5,
                    }[(category, streak_type)],
                    "habit_order": 0,
                })

    for habit_index, habit in enumerate(habits):
        for index, target in enumerate(MILESTONES):
            achievements.append({
                "code": f"habit:{habit.id}:streak:{target}",
                "title": f"{habit.emoji} {habit.title}؛ {_persian_digits(target)} روز پشت سر هم",
                "description": "تداوم انجام همین عادت در روزهای پی‌درپی",
                "category": "habit_streak",
                "target": target,
                "progress": progress_values["habit_streaks"].get(habit.id, 0),
                "xp_reward": XP_REWARDS[index] * 2,
                "milestone_index": index,
                "sort_priority": 2,
                "habit_order": habit_index,
            })
        for index, target in enumerate(MILESTONES):
            achievements.append({
                "code": f"habit:{habit.id}:completed:{target}",
                "title": f"{habit.emoji} {habit.title}؛ {_persian_digits(target)} ثبت موفق",
                "description": "ثبت‌های موفق این عادت، با احتساب روزهای دارای ثبت",
                "category": "habit_completions",
                "target": target,
                "progress": progress_values["habit_completions"].get(habit.id, 0),
                "xp_reward": XP_REWARDS[index],
                "milestone_index": index,
                "sort_priority": 1,
                "habit_order": habit_index,
            })
    return sorted(
        achievements,
        key=lambda item: (
            item["xp_reward"],
            item["milestone_index"],
            item["sort_priority"],
            item["habit_order"],
        ),
    )


def get_achievement_progress():
    today = timezone.localdate()
    habits = list(Habit.objects.filter(is_deleted=False).order_by("order", "id"))
    habit_ids = {habit.id for habit in habits}
    logs_by_habit = {habit_id: set() for habit_id in habit_ids}
    completed_by_date = {}

    logs = HabitLog.objects.filter(
        completed=True,
        date__lte=today,
        habit_id__in=habit_ids,
    ).values_list("habit_id", "date")
    for habit_id, day in logs:
        logs_by_habit[habit_id].add(day)
        completed_by_date.setdefault(day, set()).add(habit_id)

    per_habit_counts = {
        habit_id: len(days)
        for habit_id, days in logs_by_habit.items()
    }
    per_habit_streaks = {
        habit_id: _longest_streak(sorted(days))
        for habit_id, days in logs_by_habit.items()
    }
    active_days = sorted(completed_by_date)
    all_habits_days = sorted(
        day for day, completed_habit_ids in completed_by_date.items()
        if habit_ids and completed_habit_ids >= habit_ids
    )
    progress_values = {
        ("all_habits_streak", "consecutive"): _longest_streak(all_habits_days),
        ("all_habits_streak", "cumulative"): len(all_habits_days),
        ("active_days", "consecutive"): _longest_streak(active_days),
        ("active_days", "cumulative"): len(active_days),
        "habit_completions": per_habit_counts,
        "habit_streaks": per_habit_streaks,
    }
    achievements = _definitions(habits, progress_values)
    valid_codes = {achievement["code"] for achievement in achievements}
    AchievementUnlock.objects.exclude(code__in=valid_codes).delete()
    unlocked_by_code = {
        unlock.code: unlock
        for unlock in AchievementUnlock.objects.all()
    }

    for achievement in achievements:
        if achievement["progress"] < achievement["target"]:
            continue
        unlock, _ = AchievementUnlock.objects.get_or_create(
            code=achievement["code"],
            defaults={
                "title": achievement["title"],
                "description": achievement["description"],
                "xp_reward": achievement["xp_reward"],
            },
        )
        if (
            unlock.title != achievement["title"]
            or unlock.description != achievement["description"]
            or unlock.xp_reward != achievement["xp_reward"]
        ):
            unlock.title = achievement["title"]
            unlock.description = achievement["description"]
            unlock.xp_reward = achievement["xp_reward"]
            unlock.save(update_fields=["title", "description", "xp_reward"])
        unlocked_by_code[achievement["code"]] = unlock

    for achievement in achievements:
        unlock = unlocked_by_code.get(achievement["code"])
        achievement["unlocked"] = unlock is not None
        achievement["unlocked_at"] = unlock.unlocked_at if unlock else None
        achievement["progress_percent"] = min(
            100,
            int(achievement["progress"] * 100 / achievement["target"]),
        )

    unlocked = list(unlocked_by_code.values())
    total_xp = sum(unlock.xp_reward for unlock in unlocked)
    trophy_threshold, trophy_name, trophy_level, trophy_light, trophy_dark = next(
        tier for tier in TROPHY_TIERS if total_xp >= tier[0]
    )
    trophy_cups = []
    current_trophy_index = None
    previous_threshold = 0
    for index, (threshold, name, level, light, dark) in enumerate(reversed(TROPHY_TIERS)):
        earned = total_xp >= threshold
        rank_name = TROPHY_RANK_NAMES[name]
        roman_level = TROPHY_ROMAN_LEVELS[level - 1]
        trophy_cups.append({
            "threshold": threshold,
            "name": name,
            "level": level,
            "rank_name": rank_name,
            "roman_level": roman_level,
            "title": f"{rank_name} ({roman_level})",
            "light": light,
            "dark": dark,
            "earned": earned,
            "xp_needed": max(0, threshold - total_xp),
            "progress_percent": (
                100 if earned else min(
                    100,
                    max(0, int(
                        (total_xp - previous_threshold) * 100
                        / (threshold - previous_threshold)
                    )),
                )
            ) if threshold > previous_threshold else 100,
        })
        if threshold == trophy_threshold:
            current_trophy_index = index
        previous_threshold = threshold

    trophy_next_xp = next(
        (threshold for threshold, *_ in reversed(TROPHY_TIERS) if threshold > total_xp),
        None,
    )
    return {
        "achievements": achievements,
        "achievement_count": len(achievements),
        "unlocked_count": len(unlocked),
        "total_xp": total_xp,
        "trophy_tier": (
            f"{TROPHY_RANK_NAMES[trophy_name]} "
            f"({TROPHY_ROMAN_LEVELS[trophy_level - 1]})"
        ),
        "trophy_name": trophy_name,
        "trophy_level": trophy_level,
        "trophy_next_xp": trophy_next_xp,
        "trophy_progress_percent": (
            100 if trophy_next_xp is None else min(
                100,
                int((total_xp - trophy_threshold) * 100 / (trophy_next_xp - trophy_threshold)),
            )
        ),
        "trophy_light": trophy_light,
        "trophy_dark": trophy_dark,
        "trophy_cups": trophy_cups,
        "current_trophy_index": current_trophy_index or 0,
        "selected_trophy": trophy_cups[current_trophy_index or 0],
        "active_habit_count": len(habits),
        "completed_log_count": sum(per_habit_counts.values()),
        "active_day_count": len(active_days),
    }
