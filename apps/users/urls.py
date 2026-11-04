from django.urls import path
from django.views.generic import RedirectView
from . import views

urlpatterns = [
    path('achievements/', views.achievements_view, name='achievements'),
    path('profile/edit/', views.profile_edit_view, name='profile_edit'),
    path('profile/settings/', views.profile_view, name='profile'),
    path('profile/', RedirectView.as_view(pattern_name='achievements', permanent=False)),
]
