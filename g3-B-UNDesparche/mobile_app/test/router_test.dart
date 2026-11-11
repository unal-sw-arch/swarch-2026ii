import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:mobile_app/core/router/app_router.dart';
import 'package:mobile_app/features/auth/domain/entities/user_entity.dart';
import 'package:mobile_app/features/auth/domain/repositories/users_repository.dart';
import 'package:mobile_app/features/auth/presentation/bloc/auth_bloc.dart';
import 'package:mobile_app/features/auth/presentation/bloc/auth_event.dart';
import 'package:mobile_app/features/auth/presentation/screens/welcome_screen.dart';
import 'package:mobile_app/features/events/domain/entities/event_entity.dart';
import 'package:mobile_app/features/events/data/repositories/mock_events_repository.dart';
import 'package:mobile_app/features/events/presentation/bloc/events_bloc.dart';
import 'package:mobile_app/features/events/presentation/screens/event_detail_screen.dart';
import 'package:mobile_app/injection_container.dart' as di;

class _FakeUsersRepository implements UsersRepository {
  UserEntity? user;

  _FakeUsersRepository({this.user});

  @override
  Future<UserEntity?> getCurrentUser() async => user;

  @override
  Future<UserEntity> signInWithGoogle() async {
    user = const UserEntity(
      id: 'usr-1',
      fullName: 'Estudiante UNAL',
      email: 'estudiante@unal.edu.co',
      role: UserRole.communityMember,
      status: UserStatus.active,
    );
    return user!;
  }

  @override
  Future<UserEntity> continueAsGuest() async {
    user = const UserEntity(
      id: 'usr-guest',
      fullName: 'Visitante',
      email: 'guest@externo.com',
      role: UserRole.guest,
      status: UserStatus.active,
    );
    return user!;
  }

  @override
  Future<void> signOut() async {
    user = null;
  }

  @override
  Future<String?> currentToken() async => user == null ? null : 'fake-token';

  @override
  Stream<UserEntity?> authStateChanges() => Stream.value(user);
}

void main() {
  setUpAll(() async {
    await initializeDateFormatting('es', null);
  });

  group('Pruebas de Enrutador Declarativo GoRouter (Fase 2)', () {
    testWidgets('Redirige a /welcome si el usuario NO está autenticado', (tester) async {
      final fakeRepo = _FakeUsersRepository(user: null);
      final authBloc = AuthBloc(usersRepository: fakeRepo);
      authBloc.add(const AuthCheckRequested());
      await tester.pump();

      final router = AppRouter.createRouter(authBloc);

      await tester.pumpWidget(
        BlocProvider<AuthBloc>.value(
          value: authBloc,
          child: MaterialApp.router(
            routerConfig: router,
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.byType(WelcomeScreen), findsOneWidget);
    });

    testWidgets('Permite acceso al Shell / si el usuario está autenticado', (tester) async {
      await di.initDependencies(enableFirebaseAuth: false);

      final fakeRepo = _FakeUsersRepository(
        user: const UserEntity(
          id: 'usr-auth',
          fullName: 'Estudiante UNAL',
          email: 'estudiante@unal.edu.co',
          role: UserRole.communityMember,
          status: UserStatus.active,
        ),
      );

      final authBloc = AuthBloc(usersRepository: fakeRepo);
      authBloc.add(const AuthCheckRequested());
      await tester.pump();

      final router = AppRouter.createRouter(authBloc);

      await tester.pumpWidget(
        BlocProvider<AuthBloc>.value(
          value: authBloc,
          child: MaterialApp.router(
            routerConfig: router,
          ),
        ),
      );
      await tester.pumpAndSettle();

      // Debe estar en el Home, no en la pantalla de bienvenida
      expect(find.byType(WelcomeScreen), findsNothing);
    });

    testWidgets('Navega a /events/:id y presenta EventDetailScreen con los argumentos', (tester) async {
      await di.initDependencies(enableFirebaseAuth: false);

      const currentUser = UserEntity(
        id: 'usr-auth',
        fullName: 'Estudiante UNAL',
        email: 'estudiante@unal.edu.co',
        role: UserRole.communityMember,
        status: UserStatus.active,
      );

      final fakeRepo = _FakeUsersRepository(user: currentUser);
      final authBloc = AuthBloc(usersRepository: fakeRepo);
      authBloc.add(const AuthCheckRequested());
      await tester.pump();

      final router = AppRouter.createRouter(authBloc);

      final sampleEvent = EventEntity(
        id: 'ev-test',
        title: 'Torneo Ajedrez UNAL',
        description: 'Torneo en CyT',
        category: EventCategory.sports,
        venueName: 'Ed. CyT',
        latitude: 4.6382,
        longitude: -74.0882,
        startDateTime: DateTime.now().add(const Duration(hours: 2)),
        endDateTime: DateTime.now().add(const Duration(hours: 4)),
        organizer: 'Bienestar',
        status: EventStatus.scheduled,
      );

      await tester.pumpWidget(
        BlocProvider<AuthBloc>.value(
          value: authBloc,
          child: MaterialApp.router(
            routerConfig: router,
          ),
        ),
      );
      await tester.pumpAndSettle();

      // Navegar a la ruta de detalle
      unawaited(router.push(
        '/events/${sampleEvent.id}',
        extra: EventDetailArgs(
          event: sampleEvent,
          currentUser: currentUser,
          eventsBloc: EventsBloc(eventsRepository: MockEventsRepository()),
        ),
      ));
      await tester.pumpAndSettle();

      expect(find.byType(EventDetailScreen), findsOneWidget);
      expect(find.text('Torneo Ajedrez UNAL'), findsWidgets);
    });
  });
}
