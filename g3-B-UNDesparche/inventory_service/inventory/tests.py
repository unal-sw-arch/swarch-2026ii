from datetime import timedelta
from unittest.mock import patch

from django.test import TestCase
from django.utils import timezone
from rest_framework import status
from rest_framework.test import APIClient

from .models import Borrowing, Implement, Reserve, User
from .tasks import release_expired_reserves


class InventoryAPITests(TestCase):
    def setUp(self):
        self.client = APIClient()

        # Usuarios
        self.sys_admin = User.objects.create_superuser(
            email="admin@unal.edu.co",
            password="pass",
            role=User.Role.SYS_ADMIN,
            name="Admin General",
            faculty="ING",
        )
        self.imp_admin = User.objects.create_user(
            email="impadmin@unal.edu.co",
            password="pass",
            role=User.Role.IMPLEMENT_ADMIN,
            name="Admin Implementos",
            faculty="ING",
        )
        self.student = User.objects.create_user(
            email="student@unal.edu.co",
            password="pass",
            role=User.Role.COMMUNITY,
            name="Estudiante",
            faculty="ING",
        )

        # Implemento disponible
        self.implement = Implement.objects.create(
            name="Balón de Fútbol Sala",
            description="Balón de fútbol reglamentario para interiores",
            category="BAL",
            faculty="ING",
            state="DIS",
        )

    def test_list_implements(self):
        response = self.client.get("/inventory/implements/")
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertTrue(len(response.data) >= 1)

    def test_filter_implements_by_category(self):
        response = self.client.get("/inventory/implements/?category=BAL")
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        for item in response.data:
            self.assertEqual(item["category"], "BAL")

    def test_student_can_reserve_available_implement(self):
        self.client.force_authenticate(user=self.student)
        response = self.client.post(
            "/inventory/reserves/",
            {"implement": self.implement.id},
            format="json",
        )
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)

        self.implement.refresh_from_db()
        self.assertEqual(self.implement.state, "RES")

        reserve = Reserve.objects.get(user=self.student, implement=self.implement)
        self.assertTrue(reserve.active)

    def test_student_cannot_have_two_active_reserves(self):
        self.client.force_authenticate(user=self.student)
        # Primera reserva
        self.client.post("/inventory/reserves/", {"implement": self.implement.id}, format="json")

        # Segundo implemento
        imp2 = Implement.objects.create(
            name="Raqueta de Tenis",
            category="RAQ",
            faculty="ING",
            state="DIS",
            description="Raqueta de tenis Donic",
        )
        # Segunda reserva debe fallar
        response = self.client.post("/inventory/reserves/", {"implement": imp2.id}, format="json")
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)

    def test_confirm_reserve_creates_borrowing(self):
        # Crear reserva
        reserve = Reserve.objects.create(
            user=self.student,
            implement=self.implement,
            datetime_reserved=timezone.now(),
            datetime_expiration=timezone.now() + timedelta(minutes=10),
            active=True,
        )
        self.implement.state = "RES"
        self.implement.save()

        # Confirmar como admin de implementos
        self.client.force_authenticate(user=self.imp_admin)
        response = self.client.post(f"/inventory/reserves/{reserve.id}/confirm/")
        self.assertEqual(response.status_code, status.HTTP_200_OK)

        reserve.refresh_from_db()
        self.assertFalse(reserve.active)

        self.implement.refresh_from_db()
        self.assertEqual(self.implement.state, "PRE")

        borrowing = Borrowing.objects.get(user=self.student, implement=self.implement)
        self.assertTrue(borrowing.active)

    def test_return_borrowing_frees_implement(self):
        borrowing = Borrowing.objects.create(
            user=self.student,
            implement=self.implement,
            datetime_borrowed=timezone.now(),
            active=True,
        )
        self.implement.state = "PRE"
        self.implement.save()

        self.client.force_authenticate(user=self.imp_admin)
        response = self.client.post(f"/inventory/borrowings/{borrowing.id}/return/")
        self.assertEqual(response.status_code, status.HTTP_200_OK)

        borrowing.refresh_from_db()
        self.assertFalse(borrowing.active)
        self.assertIsNotNone(borrowing.datetime_return)

        self.implement.refresh_from_db()
        self.assertEqual(self.implement.state, "DIS")

    def test_release_expired_reserves_task(self):
        # Crear reserva expirada hace 5 minutos
        expired_reserve = Reserve.objects.create(
            user=self.student,
            implement=self.implement,
            datetime_reserved=timezone.now() - timedelta(minutes=15),
            datetime_expiration=timezone.now() - timedelta(minutes=5),
            active=True,
        )
        self.implement.state = "RES"
        self.implement.save()

        released = release_expired_reserves()
        self.assertEqual(released, 1)

        expired_reserve.refresh_from_db()
        self.assertFalse(expired_reserve.active)

        self.implement.refresh_from_db()
        self.assertEqual(self.implement.state, "DIS")
