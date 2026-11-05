from django.urls import path

from . import views


app_name = 'weekly_schedule'

urlpatterns = [
    path('', views.weekly_schedule, name='weekly_schedule'),
    path('setup/', views.setup, name='setup'),
    path('sleep/', views.edit_sleep, name='edit_sleep'),
    path('activities/add/', views.add_activity, name='add_activity'),
    path('activities/manage/', views.manage_activities, name='manage'),
    path('activities/<int:activity_id>/edit/', views.edit_activity, name='edit_activity'),
    path('activities/<int:activity_id>/delete/', views.delete_activity, name='delete_activity'),
]
