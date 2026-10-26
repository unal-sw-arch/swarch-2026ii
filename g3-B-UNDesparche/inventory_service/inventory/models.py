from django.db import models
from django.contrib.auth.models import AbstractUser, UserManager
from django.core.validators import EmailValidator


class MyUserManager(UserManager):
    def _create_user(self, email, password=None, **extra_fields):
        if not email:
            raise ValueError("El usuario debe tener un correo electrónico.")
        email = self.normalize_email(email)
        user = self.model(email=email, **extra_fields)
        if password:
            user.set_password(password)
        else:
            user.set_unusable_password()
        user.save(using=self._db)
        return user

    def create_user(self, email, password=None, **extra_fields):
        extra_fields.setdefault("is_staff", False)
        extra_fields.setdefault("is_superuser", False)
        return self._create_user(email, password, **extra_fields)

    def create_superuser(self, email, password=None, **extra_fields):
        if not password:
            raise ValueError("El superusuario debe tener una contraseña.")
        extra_fields.setdefault("is_staff", True)
        extra_fields.setdefault("is_superuser", True)
        return self._create_user(email, password, **extra_fields)


class User(AbstractUser):
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

    class Status(models.TextChoices):
        ACTIVE = "ACT", "Activo"
        SANCTIONED = "SAN", "Sancionado"

    class Role(models.TextChoices):
        SYS_ADMIN = "SYS_ADMIN", "Administrador del Sistema"
        EVENT_ADMIN = "EVENT_ADMIN", "Administrador de Eventos"
        IMPLEMENT_ADMIN = "IMPLEMENT_ADMIN", "Administrador de Implementos"
        COMMUNITY = "COMMUNITY", "Miembro de la Comunidad"

    username = None
    first_name = None
    last_name = None

    email = models.EmailField(unique=True, validators=[EmailValidator()])
    name = models.CharField(max_length=150, blank=True)
    faculty = models.CharField(max_length=3, choices=Faculty, blank=True, null=True)
    status = models.CharField(max_length=3, choices=Status, default=Status.ACTIVE)
    role = models.CharField(max_length=20, choices=Role, default=Role.COMMUNITY)
    firebase_uid = models.CharField(max_length=128, unique=True, null=True, blank=True)

    USERNAME_FIELD = "email"
    REQUIRED_FIELDS = []

    objects = MyUserManager()

    def __str__(self):
        return f"{self.name or self.email} ({self.role})"

    @property
    def is_sys_admin(self):
        return self.role == self.Role.SYS_ADMIN

    @property
    def is_implement_admin(self):
        return self.role == self.Role.IMPLEMENT_ADMIN


class Implement(models.Model):
    CATEGORY_CHOICES = [
        ("BAL", "Balones"),
        ("RAQ", "Raquetas"),
        ("MES", "Mesas"),
        ("JUR", "Juegos recreativos"),
        ("JUM", "Juegos de mesa"),
        ("OTS", "Otros"),
    ]

    FACULTIES = User.Faculty.choices

    STATE_CHOICES = [
        ("DIS", "Disponible"),
        ("NDS", "No Disponible"),
        ("RES", "Reservado"),
        ("PRE", "Prestado"),
    ]

    name = models.CharField(max_length=100)
    category = models.CharField(max_length=3, choices=CATEGORY_CHOICES, blank=True)
    faculty = models.CharField(max_length=3, choices=FACULTIES, blank=True, null=True)
    state = models.CharField(max_length=3, choices=STATE_CHOICES, default="NDS")
    description = models.TextField()
    image = models.URLField(max_length=500, blank=True, null=True)

    class Meta:
        verbose_name = "Implement"
        verbose_name_plural = "Implements"
        ordering = ["name"]

    def __str__(self):
        return f"{self.name} ({self.state})"


class Reserve(models.Model):
    user = models.ForeignKey(User, on_delete=models.CASCADE, related_name="reserves")
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
                fields=["user"],
                condition=models.Q(active=True),
                name="one_active_reserve_per_user",
            )
        ]

    def __str__(self):
        return f"{self.user.email} -> {self.implement.name} ({self.implement.state})"


class Borrowing(models.Model):
    user = models.ForeignKey(User, on_delete=models.CASCADE, related_name="borrows")
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
                fields=["user"],
                condition=models.Q(active=True),
                name="one_active_borrowing_per_user",
            )
        ]

    def __str__(self):
        return f"{self.user.email} tiene {self.implement.name} ({self.implement.state})"
