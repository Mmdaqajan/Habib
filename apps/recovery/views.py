from django.shortcuts import render, redirect, get_object_or_404
from apps.habits.models import Habit

def recovery_list_view(request):
    deleted_habits = Habit.objects.filter(is_deleted=True)
    return render(request, 'recovery/recovery.html', {'deleted_habits': deleted_habits})

def restore_habit(request, habit_id):
    habit = get_object_or_404(Habit, id=habit_id)
    if request.method == 'POST':
        habit.is_deleted = False
        habit.save()
        return redirect('recovery_list')
    return redirect('recovery_list')
