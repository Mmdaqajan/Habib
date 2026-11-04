package com.habib.mobile;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

final class AchievementTracker {
    static final class Item {
        String code;
        String title;
        String description;
        String category;
        int target;
        int progress;
        int xpReward;
        int progressPercent;
        boolean unlocked;
    }

    static final class Trophy {
        int threshold;
        String title;
        int progressPercent;
        int xpNeeded;
        boolean earned;
    }

    static final class Summary {
        ArrayList<Item> items = new ArrayList<>();
        ArrayList<Trophy> trophies = new ArrayList<>();
        int totalXp;
        int currentTrophyIndex;
        int activeHabitCount;
        int completedLogCount;
        int activeDayCount;
    }

    private static final int[] MILESTONES = {7, 21, 40, 100, 200, 365};
    private static final int[] XP_REWARDS = {10, 25, 50, 100, 200, 365};
    private static final int[] TROPHY_THRESHOLDS = {
            0, 100, 250, 500, 900, 1400, 2200, 3200, 4500,
            6500, 8500, 11000, 14000, 17000, 20000, 23000, 25000, 27000
    };
    private static final String[] TROPHY_RANKS = {
            "تازه‌کار", "تازه‌کار", "تازه‌کار", "پیگیر", "پیگیر", "پیگیر",
            "قهرمان", "قهرمان", "قهرمان", "اسطوره", "اسطوره", "اسطوره",
            "سخت‌جان", "سخت‌جان", "سخت‌جان", "افسانه‌ای", "افسانه‌ای", "افسانه‌ای"
    };
    private static final String[] ROMAN_LEVELS = {"I", "II", "III"};

    private AchievementTracker() {
    }

    static Summary refresh(DbHelper database) {
        ArrayList<DbHelper.Habit> habits = database.habits();
        ArrayList<LocalDate> activeDays = database.completedDates();
        ArrayList<LocalDate> allHabitsDays = database.allHabitsCompletedDates(habits.size());
        Summary summary = new Summary();
        summary.totalXp = database.totalXp();
        summary.activeHabitCount = habits.size();
        summary.completedLogCount = database.totalCompletedLogs();
        summary.activeDayCount = activeDays.size();

        addGroup(database, summary, "active_days", "روزهای فعال در حبیب",
                "روزهایی که دست‌کم یک عادت ثبت شده است", activeDays.size(), longestStreak(activeDays), 0);
        addGroup(database, summary, "all_habits_streak", "تداوم همهٔ عادت‌ها",
                "روزهایی که همهٔ عادت‌های فعال انجام شده‌اند", allHabitsDays.size(),
                longestStreak(allHabitsDays), 1);
        for (int habitIndex = 0; habitIndex < habits.size(); habitIndex++) {
            DbHelper.Habit habit = habits.get(habitIndex);
            ArrayList<LocalDate> dates = database.completedDates(habit.id);
            addHabitGroup(database, summary, habit, dates, habitIndex);
        }

        summary.items.sort(Comparator.comparingInt((Item item) -> item.xpReward)
                .thenComparing(item -> item.code));
        Set<String> validCodes = new HashSet<>();
        for (Item item : summary.items) validCodes.add(item.code);
        database.removeStaleAchievements(validCodes);
        summary.totalXp = database.totalXp();
        buildTrophies(summary);
        return summary;
    }

    private static void addGroup(
            DbHelper database, Summary summary, String category, String label,
            String description, int cumulative, int consecutive, int order
    ) {
        for (int i = 0; i < MILESTONES.length; i++) {
            addItem(database, summary, category + ":cumulative:" + MILESTONES[i],
                    label + "؛ " + persianDigits(MILESTONES[i]) + " ثبت موفق",
                    description, category, MILESTONES[i], cumulative, XP_REWARDS[i],
                    order * 2);
            int reward = XP_REWARDS[i] * 2;
            if ("all_habits_streak".equals(category)) reward = (reward * 3 + 1) / 2;
            addItem(database, summary, category + ":consecutive:" + MILESTONES[i],
                    label + "؛ " + persianDigits(MILESTONES[i]) + " روز پشت سر هم",
                    description, category, MILESTONES[i], consecutive, reward,
                    order * 2 + 1);
        }
    }

    private static void addHabitGroup(
            DbHelper database, Summary summary, DbHelper.Habit habit,
            ArrayList<LocalDate> dates, int habitOrder
    ) {
        int consecutive = longestStreak(dates);
        for (int i = 0; i < MILESTONES.length; i++) {
            addItem(database, summary, "habit:" + habit.id + ":completed:" + MILESTONES[i],
                    habit.emoji + " " + habit.title + "؛ " + persianDigits(MILESTONES[i]) + " ثبت موفق",
                    "ثبت‌های موفق این عادت، با احتساب روزهای دارای ثبت", "habit_completions",
                    MILESTONES[i], dates.size(), XP_REWARDS[i], habitOrder * 100 + 1);
            addItem(database, summary, "habit:" + habit.id + ":streak:" + MILESTONES[i],
                    habit.emoji + " " + habit.title + "؛ " + persianDigits(MILESTONES[i]) + " روز پشت سر هم",
                    "تداوم انجام همین عادت در روزهای پی‌درپی", "habit_streak",
                    MILESTONES[i], consecutive, XP_REWARDS[i] * 2, habitOrder * 100);
        }
    }

    private static void addItem(
            DbHelper database, Summary summary, String code, String title,
            String description, String category, int target, int progress,
            int xpReward, int order
    ) {
        Item item = new Item();
        item.code = code;
        item.title = title;
        item.description = description;
        item.category = category;
        item.target = target;
        item.progress = progress;
        item.xpReward = xpReward;
        item.progressPercent = Math.min(100, (int) ((long) progress * 100 / target));
        item.unlocked = progress >= target;
        if (item.unlocked) {
            database.unlockAchievement(code, title, description, xpReward);
        }
        summary.items.add(item);
    }

    private static void buildTrophies(Summary summary) {
        int currentIndex = 0;
        for (int i = 0; i < TROPHY_THRESHOLDS.length; i++) {
            if (summary.totalXp >= TROPHY_THRESHOLDS[i]) currentIndex = i;
        }
        summary.currentTrophyIndex = currentIndex;
        for (int i = 0; i < TROPHY_THRESHOLDS.length; i++) {
            int threshold = TROPHY_THRESHOLDS[i];
            int lowerThreshold = i == 0 ? 0 : TROPHY_THRESHOLDS[i - 1];
            int upperThreshold = i + 1 < TROPHY_THRESHOLDS.length
                    ? TROPHY_THRESHOLDS[i + 1] : threshold;
            Trophy trophy = new Trophy();
            trophy.threshold = threshold;
            trophy.title = TROPHY_RANKS[i] + " (" + ROMAN_LEVELS[i % 3] + ")";
            trophy.earned = summary.totalXp >= threshold;
            trophy.xpNeeded = Math.max(0, threshold - summary.totalXp);
            trophy.progressPercent = trophy.earned ? 100 : upperThreshold == lowerThreshold
                    ? 100 : Math.min(100, Math.max(0,
                    (summary.totalXp - lowerThreshold) * 100 / (upperThreshold - lowerThreshold)));
            summary.trophies.add(trophy);
        }
    }

    private static int longestStreak(ArrayList<LocalDate> dates) {
        if (dates.isEmpty()) return 0;
        Collections.sort(dates);
        int longest = 1;
        int current = 1;
        for (int i = 1; i < dates.size(); i++) {
            current = dates.get(i - 1).plusDays(1).equals(dates.get(i)) ? current + 1 : 1;
            longest = Math.max(longest, current);
        }
        return longest;
    }

    private static String persianDigits(int value) {
        return String.valueOf(value).replace('0', '۰').replace('1', '۱').replace('2', '۲')
                .replace('3', '۳').replace('4', '۴').replace('5', '۵').replace('6', '۶')
                .replace('7', '۷').replace('8', '۸').replace('9', '۹');
    }
}
