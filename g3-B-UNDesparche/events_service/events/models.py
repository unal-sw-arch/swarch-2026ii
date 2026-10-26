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
        return f"{self.email} ({self.role})"

    @property
    def uid(self):
        return self.firebase_uid or f"dev_uid_{self.id}"


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
    organizer_name = models.CharField(max_length=150, blank=True)
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
