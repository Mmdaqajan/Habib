from django.shortcuts import render, redirect
from .models import UserProfile

def profile_view(request):
    profile, created = UserProfile.objects.get_or_create(id=1)
    if request.method == 'POST':
        profile.name = request.POST.get('name', profile.name)
        profile.age = int(request.POST.get('age', profile.age))
        profile.email = request.POST.get('email', profile.email)
        profile.phone = request.POST.get('phone', profile.phone)
        if request.FILES.get('profile_pic'):
            profile.profile_pic = request.FILES['profile_pic']
        profile.save()
        return redirect('profile')

    context = {'profile': profile}
    return render(request, 'users/profile.html', context)
