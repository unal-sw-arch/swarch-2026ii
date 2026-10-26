from django.urls import include, path
from rest_framework.routers import DefaultRouter
from .views import me, login, UserViewSet

app_name = "users"

router = DefaultRouter()
router.register(r"", UserViewSet, basename="users")

urlpatterns = [
    path("me/", me, name="users-me"),
    path("login/", login, name="users-login"),
    path("", include(router.urls)),
]
