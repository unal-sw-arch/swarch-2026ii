import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:mobile_app/core/theme/app_theme.dart';
import 'package:mobile_app/core/widgets/filter_sheet.dart';
import 'package:mobile_app/features/auth/domain/entities/user_entity.dart';
import 'package:mobile_app/features/auth/presentation/screens/profile_screen.dart';
import 'package:mobile_app/features/events/domain/entities/event_entity.dart';
import 'package:mobile_app/features/events/domain/repositories/events_repository.dart';
import 'package:mobile_app/features/events/presentation/bloc/events_bloc.dart';
import 'package:mobile_app/features/events/presentation/bloc/events_event.dart';
import 'package:mobile_app/features/events/presentation/screens/events_list_screen.dart';
import 'package:mobile_app/injection_container.dart' as di;

class _FakeEventsRepository implements EventsRepository {
  @override
  Future<List<EventEntity>> getEvents({EventCategory? category, String? searchQuery}) async {
    return [
      EventEntity(
        id: 'ev-01',
        title: 'Torneo Interfacultades',
        description: 'En canchas de minas',
        category: EventCategory.sports,
        venueName: 'Canchas Minas (Ed. 411)',
        latitude: 4.6391,
        longitude: -74.0858,
        startDateTime: DateTime.now().add(const Duration(hours: 3)),
        endDateTime: DateTime.now().add(const Duration(hours: 6)),
        organizer: 'Bienestar Ingenieria',
        status: EventStatus.scheduled,
      ),
    ];
  }

  @override
  Future<EventEntity> getEventById(String eventId) async {
    throw UnimplementedError();
  }

  @override
  Future<EventEntity> toggleSubscription(String eventId, {String? email}) async {
    throw UnimplementedError();
  }
}

void main() {
  setUpAll(() async {
    await initializeDateFormatting('es', null);
    await di.initDependencies(enableFirebaseAuth: false);
  });

  group('Pruebas de Desacoplamiento de Pantallas y Perfil (Fase 3)', () {
    testWidgets('ProfileScreen presenta la información del usuario para estudiante activo', (tester) async {
      const activeUser = UserEntity(
        id: 'usr-001',
        fullName: 'Camilo Gomez',
        email: 'cgomez@unal.edu.co',
        role: UserRole.communityMember,
        status: UserStatus.active,
      );

      await tester.pumpWidget(
        MaterialApp(
          theme: AppTheme.lightTheme,
          home: const ProfileScreen(user: activeUser),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Mi Perfil'), findsOneWidget);
      expect(find.text('Camilo Gomez'), findsOneWidget);
      expect(find.text('cgomez@unal.edu.co'), findsWidgets);
      expect(find.text('Información Institucional'), findsOneWidget);
      expect(find.text('Comunidad UNAL'), findsWidgets);
    });

    testWidgets('ProfileScreen presenta el reporte de sanción para estudiante sancionado', (tester) async {
      const sanctionedUser = UserEntity(
        id: 'usr-002',
        fullName: 'Victor Canon',
        email: 'vcanon@unal.edu.co',
        role: UserRole.communityMember,
        status: UserStatus.sanctioned,
      );

      await tester.pumpWidget(
        MaterialApp(
          theme: AppTheme.lightTheme,
          home: const ProfileScreen(user: sanctionedUser),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Cuenta Sancionada (Regla RN-03)'), findsOneWidget);
      expect(find.byIcon(Icons.warning_amber_rounded), findsOneWidget);
    });

    testWidgets('EventsListScreen desacoplada renderiza buscador y chips de categoría', (tester) async {
      final fakeEventsRepo = _FakeEventsRepository();
      final eventsBloc = EventsBloc(eventsRepository: fakeEventsRepo)..add(const LoadEventsRequested());

      const user = UserEntity(
        id: 'usr-001',
        fullName: 'Camilo Gomez',
        email: 'cgomez@unal.edu.co',
        role: UserRole.communityMember,
        status: UserStatus.active,
      );

      await tester.pumpWidget(
        MaterialApp(
          theme: AppTheme.lightTheme,
          home: BlocProvider.value(
            value: eventsBloc,
            child: const EventsListScreen(currentUser: user),
          ),
        ),
      );

      await tester.pumpAndSettle();

      expect(find.text('Eventos'), findsOneWidget);
      // Los filtros ya no se arrastran: un boton abre la hoja con todas las categorias.
      expect(find.byType(FilterButton), findsOneWidget);
      expect(find.widgetWithText(FilterOptionTile, 'Cultura'), findsNothing);

      await tester.tap(find.byType(FilterButton));
      await tester.pumpAndSettle();

      expect(find.widgetWithText(FilterOptionTile, 'Todos'), findsOneWidget);
      expect(find.widgetWithText(FilterOptionTile, 'Deportes'), findsOneWidget);
      expect(find.widgetWithText(FilterOptionTile, 'Cultura'), findsOneWidget);
      expect(find.widgetWithText(FilterOptionTile, 'Académico'), findsOneWidget);

      // Una categoria sin eventos no se puede aplicar: el boton lo dice.
      await tester.tap(find.widgetWithText(FilterOptionTile, 'Cultura'));
      await tester.pump();
      expect(find.text('Sin resultados'), findsOneWidget);

      // Elegir una categoria con eventos y aplicar: queda una ficha y el boton muestra "1".
      await tester.tap(find.widgetWithText(FilterOptionTile, 'Deportes'));
      await tester.pump();
      expect(find.text('Ver 1 evento'), findsOneWidget);
      await tester.tap(find.textContaining(RegExp(r'^Ver \d+ evento')));
      await tester.pumpAndSettle();

      expect(find.byType(ActiveFilterChips), findsOneWidget);
      expect(find.widgetWithText(ActiveFilterChips, 'Deportes'), findsOneWidget);
      expect(find.text('1'), findsOneWidget);
    });
  });
}
