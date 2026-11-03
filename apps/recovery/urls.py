from django.urls import path
from . import views

urlpatterns = [
    path('', views.recovery_list_view, name='recovery_list'),
    path('restore/<int:habit_id>/', views.restore_habit, name='restore_habit'),
]
