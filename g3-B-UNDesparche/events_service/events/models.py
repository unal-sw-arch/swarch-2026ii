from django.db import models
from django.core.validators import EmailValidator


class Event(models.Model):

    class Status(models.TextChoices):
        PRO = "PRO", "Programado"
        ECU = "ECU", "En Curso"
        CAN = "CAN", "Cancelado"
        FIN = "FIN", "Finalizado"

    class Category(models.TextChoices):
        ACA = "ACA", "Académico"
        CUL = "CUL", "Cultural"
        DEP = "DEP", "Deportes"
        ASA = "ASA", "Asamblea"
        PAR = "PAR", "Parche"
        OTR = "OTR", "Otro"

    name = models.CharField(max_length=100)
    description = models.TextField()
    published = models.BooleanField(default=False)
    place = models.CharField(max_length=200)
    latitude = models.DecimalField(max_digits=10, decimal_places=7)
    longitude = models.DecimalField(max_digits=10, decimal_places=7)
    datetime_start = models.DateTimeField()
    datetime_end = models.DateTimeField()
    organizer_id = models.CharField(max_length=128, null=False, db_index=True)
    organizer_email = models.EmailField(blank=True)
    status = models.CharField(max_length=3, choices=Status, default="PRO")
    category = models.CharField(max_length=3, choices=Category, blank=True, null=True)
    image = models.URLField(max_length=500, null=True, blank=True)

    class Meta:
        verbose_name = "Event"
        verbose_name_plural = "Events"
        ordering = ["datetime_start"]

    def __str__(self):
        return f"{self.name} ({self.status})"

    def is_editable(self):
        if not self.published:
            return True

        return self.status not in [
            "CAN",
            "FIN",
        ]


class Subscription(models.Model):

    event = models.ForeignKey(
        Event, on_delete=models.CASCADE, related_name="subscriptions"
    )
    user_id = models.CharField(max_length=128, null=True, blank=True, db_index=True)
    notification_email = models.EmailField(validators=[EmailValidator()], blank=True)

    class Meta:
        verbose_name = "Subscription"
        verbose_name_plural = "Subscriptions"

        # Un usuario registrado no puede suscribirse dos veces al mismo evento
        constraints = [
            models.UniqueConstraint(
                fields=["event", "user_id"],
                condition=models.Q(user_id__isnull=False),
                name="unique_subscription_per_user",
            ),
            models.UniqueConstraint(
                fields=["event", "notification_email"],
                condition=models.Q(notification_email__gt=""),
                name="unique_subscription_per_email",
            ),
        ]
