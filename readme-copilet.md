# Habib Project - GitHub Copilot & Cursor Instructions

This file provides architectural guidelines and structure rules for AI coding assistants (GitHub Copilot, Cursor, etc.) working on the **Habib** Django project.

---

## 📂 Project Directory Structure
```text
habib_project/
├── manage.py
├── requirements.txt
├── config/                  # Django Settings & Global URLs
│   ├── settings.py
│   └── urls.py
├── templates/
│   └── base.html            # Global Glassmorphism Master Layout
└── apps/
    ├── habits/              # Habits tracking, logs, quotes, charts
    ├── alerts/              # Notifications, rules, persian calendar
    ├── users/               # User profile management
    └── recovery/            # Soft-delete items restoration module