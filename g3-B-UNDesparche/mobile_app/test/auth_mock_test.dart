import 'package:flutter_test/flutter_test.dart';
import 'package:mobile_app/features/auth/data/repositories/mock_users_repository.dart';
import 'package:mobile_app/features/auth/domain/entities/user_entity.dart';

void main() {
  late MockUsersRepository repository;

  setUp(() {
    repository = MockUsersRepository();
  });

  group('Pruebas de Autenticacion y Roles (UNDesparche)', () {
    test('Ingreso con Google (mock) asigna rol de Miembro de la Comunidad', () async {
      final user = await repository.signInWithGoogle();

      expect(user.role, equals(UserRole.communityMember));
      expect(user.status, equals(UserStatus.active));
      expect(user.canBorrowImplements, isTrue);
      expect(user.isGuest, isFalse);
      expect(user.email, endsWith('@unal.edu.co'));
    });

    test('Modo Invitado: acceso a visitantes pero sin préstamos', () async {
      final guest = await repository.continueAsGuest();

      expect(guest.role, equals(UserRole.guest));
      expect(guest.isGuest, isTrue);
      expect(guest.canBorrowImplements, isFalse);
    });

    test('currentToken es null sin sesión y no null tras login', () async {
      await repository.signOut();
      expect(await repository.currentToken(), isNull);

      await repository.signInWithGoogle();
      expect(await repository.currentToken(), isNotNull);
    });
  });
}
