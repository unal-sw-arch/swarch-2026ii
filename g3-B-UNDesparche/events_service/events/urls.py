from django.urls import include, path
from rest_framework.routers import DefaultRouter
from .views import EventViewSet, unsubscribe_via_token

app_name = "events"

router = DefaultRouter()
router.register(r"", EventViewSet, basename="events")

urlpatterns = [
    path("unsubscribe/", unsubscribe_via_token, name="unsubscribe-link"),
    path("", include(router.urls)),
]