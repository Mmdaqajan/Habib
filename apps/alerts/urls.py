from django.urls import path
from . import views

urlpatterns = [
    path('', views.alerts_view, name='alerts'),
    path('add-alert/', views.add_alert, name='add_alert'),
    path('edit-alert/<int:alert_id>/', views.edit_alert, name='edit_alert'),
    path('delete-alert/<int:alert_id>/', views.delete_alert, name='delete_alert'),
    path('add-calendar-event/', views.add_calendar_event, name='add_calendar_event'),
    path('delete-calendar-event/<int:event_id>/', views.delete_calendar_event, name='delete_calendar_event'),
    path('edit-calendar-event/<int:event_id>/', views.edit_calendar_event, name='edit_calendar_event'),
]
