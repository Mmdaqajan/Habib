from django.urls import path
from . import views

urlpatterns = [
    path('', views.home_view, name='home'),
    path('overview/', views.home_view, name='overview'),
    path('toggle-habit/', views.toggle_habit, name='toggle_habit'),
    path('add-habit/', views.add_habit, name='add_habit'),
    path('edit-habit/<int:habit_id>/', views.edit_habit, name='edit_habit'),
    path('delete-habit/<int:habit_id>/', views.delete_habit, name='delete_habit'),
    path('reorder-habits/', views.reorder_habits, name='reorder_habits'),
    path('alerts/', views.alerts_view, name='alerts'),
    path('add-alert/', views.add_alert, name='add_alert'),
    path('delete-alert/<int:alert_id>/', views.delete_alert, name='delete_alert'),
    path('profile/', views.profile_view, name='profile'),
    path('get-habit-chart-data/<int:habit_id>/', views.get_habit_chart_data, name='get_habit_chart_data'),
]
