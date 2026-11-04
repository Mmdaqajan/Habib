from .models import UserProfile


def profile_settings(request):
    profile = UserProfile.objects.first() or UserProfile()
    return {
        "settings_profile": profile,
        "settings_age": profile.calculated_age,
        "settings_return_url": request.path,
    }
