from django.urls import include, path
from rest_framework.routers import DefaultRouter
from .views import BorrowingViewSet, ImplementViewSet, ReserveViewSet

app_name = "inventory"

router = DefaultRouter()
router.register(r"implements", ImplementViewSet, basename="implements")
router.register(r"reserves", ReserveViewSet, basename="reserves")
router.register(r"borrowings", BorrowingViewSet, basename="borrowings")

urlpatterns = [
    path("", include(router.urls)),
]
