from django.urls import path
from . import views

urlpatterns = [
    path('', views.alerts_view, name='alerts'),
    path('add-alert/', views.add_alert, name='add_alert'),
    path('delete-alert/<int:alert_id>/', views.delete_alert, name='delete_alert'),
]
