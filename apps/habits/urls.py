from django.urls import path
from . import views

urlpatterns = [
    path('', views.home_view, name='home'),
    path('overview/', views.home_view, name='overview'),
    path('habit/<int:habit_id>/', views.habit_detail_view, name='habit_detail'),
    path('toggle-habit/', views.toggle_habit, name='toggle_habit'),
    path('habit-date-data/', views.habit_date_data, name='habit_date_data'),
    path('get-habit-chart-data/<int:habit_id>/', views.get_habit_chart_data, name='get_habit_chart_data'),
    path('add-habit/', views.add_habit, name='add_habit'),
    path('edit-habit/<int:habit_id>/', views.edit_habit, name='edit_habit'),
    path('delete-habit/<int:habit_id>/', views.delete_habit, name='delete_habit'),
    path('add-quote/', views.add_quote, name='add_quote'),
]
