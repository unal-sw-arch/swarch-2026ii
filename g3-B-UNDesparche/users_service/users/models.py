from django.db import models
from django.db import models
from django.contrib.auth.models import AbstractUser, UserManager
from django.core.validators import EmailValidator
from django.core.exceptions import ValidationError


def validate_unal_email(value):
    if not value.endswith("@unal.edu.co"):
        raise ValidationError("El correo debe pertenecer al dominio @unal.edu.co")


class MyUserManager(UserManager):

    def _create_user(self, email, password=None, **extra_fields):
        """
        Esta función crea y guarda un User dado un email; password es opcional.
        """
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
        """
        Crea y guarda un User dado un email; password es opcional.
        """
        extra_fields.setdefault("is_staff", False)
        extra_fields.setdefault("is_superuser", False)
        return self._create_user(email, password, **extra_fields)

    def create_superuser(self, email, password=None, **extra_fields):
        """
        Crea y guarda un superuser dado un email; password es opcional.
        """
        if not password:
            raise ValueError("El superusuario debe tener una contraseña.")

        extra_fields.setdefault("is_staff", True)
        extra_fields.setdefault("is_superuser", True)
        user = self._create_user(email, password, **extra_fields)
        return user


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

    email = models.EmailField(
        unique=True, validators=[EmailValidator(), validate_unal_email]
    )
    name = models.CharField(max_length=150, blank=True)
    faculty = models.CharField(max_length=3, choices=Faculty, blank=True, null=True)
    status = models.CharField(max_length=3, choices=Status, default=Status.ACTIVE)
    role = models.CharField(max_length=20, choices=Role, default=Role.COMMUNITY)
    firebase_uid = models.CharField(max_length=128, unique=True, null=True, blank=True)

    USERNAME_FIELD = "email"
    REQUIRED_FIELDS = []

    objects = MyUserManager()

    def __str__(self):
        return self.email
