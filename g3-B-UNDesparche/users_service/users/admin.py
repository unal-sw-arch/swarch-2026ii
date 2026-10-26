from django.contrib import admin
from .models import User


@admin.register(User)
class UserAdmin(admin.ModelAdmin):
    
    model = User

    list_display = ["email", "status", "is_active", "role"]
    search_fields = ["email", "name"]

    readonly_fields = [
        "last_login",
        "date_joined",
        "email",
        "firebase_uid",
    ]

    fieldsets = (
        (
            None,
            {
                "fields": (
                    "email",
                    "status",
                    "is_active",
                    "is_staff",
                    "is_superuser",
                )
            },
        ),
        (
            "Datos básicos",
            {
                "fields": (
                    "name",
                    "faculty",
                    "password",
                    "role",
                    "firebase_uid",
                )
            },
        ),
        (
            "Fechas",
            {
                "fields": (
                    "last_login",
                    "date_joined",
                )
            },
        ),
    )