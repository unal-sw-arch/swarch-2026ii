from django.db import models


class Implement(models.Model):

    class Category(models.TextChoices):
        BALLS = "BAL", "Balones"
        RACKETS = "RAQ", "Raquetas"
        TABLES = "MES", "Mesas"
        RECREATIONAL_GAMES = "JUR", "Juegos recreativos"
        BOARD_GAMES = "JUM", "Juegos de mesa"
        OTHERS = "OTS", "Otros"

    class Faculty(models.TextChoices):
        ART = "ART", "Artes"
        CCS = "CCS", "Ciencias"
        CIA = "CIA", "Ciencias Agrarias"
        CIE = "CIE", "Ciencias Económicas"
        CHS = "CHS", "Ciencias Humanas"
        DER = "DER", "Derecho, Ciencias Políticas y Sociales"
        ENF = "ENF", "Enfermería"
        ING = "ING", "Ingeniería"
        MED = "MED", "Medicina"
        MVZ = "MVZ", "Medicina Veterinaria y Zootecnia"
        ODO = "ODO", "Odontología"

    class State(models.TextChoices):
        AVAILABLE = "DIS", "Disponible"
        NOT_AVAILABLE = "NDS", "No Disponible"
        RESERVED = "RES", "Reservado"
        BORROWED = "PRE", "Prestado"

    name = models.CharField(max_length=100)
    category = models.CharField(max_length=3, choices=Category, blank=True)
    faculty = models.CharField(max_length=3, choices=Faculty, blank=True, null=True)
    state = models.CharField(max_length=3, choices=State, default=State.NOT_AVAILABLE)
    description = models.TextField()
    image = models.URLField(max_length=500, blank=True, null=True)

    class Meta:
        verbose_name = "Implement"
        verbose_name_plural = "Implements"
        ordering = ["name"]

    def __str__(self):
        return f"{self.name} ({self.state})"


class Reserve(models.Model):
    # El usuario vive en el servicio Users: aquí solo se guarda quién es (su firebase_uid) y su correo
    user_id = models.CharField(max_length=128, db_index=True)
    user_email = models.EmailField(blank=True)
    implement = models.ForeignKey(
        Implement, on_delete=models.CASCADE, related_name="reserves"
    )
    datetime_reserved = models.DateTimeField()
    datetime_expiration = models.DateTimeField(null=True, blank=True)
    active = models.BooleanField(default=False)

    class Meta:
        verbose_name = "Reserve"
        verbose_name_plural = "Reserves"
        ordering = ["-datetime_reserved"]
        constraints = [
            models.UniqueConstraint(
                fields=["user_id"],
                condition=models.Q(active=True),
                name="one_active_reserve_per_user",
            )
        ]

    def __str__(self):
        return f"{self.user_email} -> {self.implement.name} ({self.implement.state})"


class Borrowing(models.Model):
    # El usuario vive en el servicio Users: aquí solo se guarda quién es (su firebase_uid) y su correo
    user_id = models.CharField(max_length=128, db_index=True)
    user_email = models.EmailField(blank=True)
    implement = models.ForeignKey(
        Implement, on_delete=models.CASCADE, related_name="borrows"
    )
    datetime_borrowed = models.DateTimeField()
    datetime_return = models.DateTimeField(null=True, blank=True)
    active = models.BooleanField(default=False)

    class Meta:
        verbose_name = "Borrowing"
        verbose_name_plural = "Borrowings"
        ordering = ["-datetime_borrowed"]
        constraints = [
            models.UniqueConstraint(
                fields=["user_id"],
                condition=models.Q(active=True),
                name="one_active_borrowing_per_user",
            )
        ]

    def __str__(self):
        return f"{self.user_email} tiene {self.implement.name} ({self.implement.state})"
