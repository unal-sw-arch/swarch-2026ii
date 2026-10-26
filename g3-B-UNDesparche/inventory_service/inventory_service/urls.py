from django.urls import include, path

urlpatterns = [
    path("inventory/", include("inventory.urls", namespace="inventory-prefixed")),
    path("", include("inventory.urls", namespace="inventory")),
]
