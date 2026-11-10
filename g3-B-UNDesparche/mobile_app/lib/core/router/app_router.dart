import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:go_router/go_router.dart';
import '../theme/app_colors.dart';
import '../../features/auth/domain/entities/user_entity.dart';
import '../../features/auth/presentation/bloc/auth_bloc.dart';
import '../../features/auth/presentation/bloc/auth_state.dart';
import '../../features/auth/presentation/screens/welcome_screen.dart';
import '../../features/events/domain/entities/event_entity.dart';
import '../../features/events/presentation/bloc/events_bloc.dart';
import '../../features/events/presentation/bloc/events_state.dart';
import '../../features/events/presentation/screens/event_detail_screen.dart';
import '../../features/home/presentation/screens/main_shell_screen.dart';

/// Argumentos para navegar a la pantalla de detalle de evento.
/// Lleva el [EventsBloc] del shell para que el detalle lo re-provea y para
/// poder resolver el evento por id cuando no viaja el objeto completo (#9).
class EventDetailArgs {
  final EventEntity? event;
  final UserEntity currentUser;
  final EventsBloc eventsBloc;

  const EventDetailArgs({
    this.event,
    required this.currentUser,
    required this.eventsBloc,
  });
}

/// Enrutador declarativo central de la aplicacion movil UNDesparche
/// implementado con [GoRouter] y guards reactivos segun el estado de sesion.
class AppRouter {
  static const String welcomePath = '/welcome';
  static const String homePath = '/';
  static const String eventDetailPath = '/events/:id';

  /// Crea y configura la instancia de [GoRouter] vinculada al [AuthBloc]
  static GoRouter createRouter(AuthBloc authBloc) {
    return GoRouter(
      initialLocation: homePath,
      refreshListenable: _GoRouterRefreshStream(authBloc.stream),
      redirect: (BuildContext context, GoRouterState state) {
        final authState = authBloc.state;
        final isWelcomeRoute = state.matchedLocation == welcomePath;

        // 1. Si no hay sesion activa y no esta en bienvenida, redirigir a bienvenida
        if (authState is Unauthenticated) {
          return isWelcomeRoute ? null : welcomePath;
        }

        // 2. Si el usuario ya esta autenticado e intenta ir a bienvenida, redirigir al home
        if (authState is Authenticated && isWelcomeRoute) {
          return homePath;
        }

        // Permitir navegacion normal
        return null;
      },
      routes: [
        GoRoute(
          path: welcomePath,
          name: 'welcome',
          builder: (context, state) => const WelcomeScreen(),
        ),
        GoRoute(
          path: homePath,
          name: 'home',
          builder: (context, state) {
            return BlocBuilder<AuthBloc, AuthState>(
              builder: (context, authState) {
                if (authState is Authenticated) {
                  return MainShellScreen(currentUser: authState.user);
                }
                return const Scaffold(
                  body: Center(
                    child: CircularProgressIndicator(color: AppColors.mintPrimary),
                  ),
                );
              },
            );
          },
          routes: [
            GoRoute(
              path: 'events/:id',
              name: 'eventDetail',
              builder: (context, state) {
                final args = state.extra;
                final eventId = state.pathParameters['id'];

                if (args is EventDetailArgs) {
                  // Resuelve el evento: el que viajó, o por id desde el bloc (#9).
                  EventEntity? event = args.event;
                  if (event == null && eventId != null) {
                    final bloc = args.eventsBloc;
                    final blocState = bloc.state;
                    if (blocState is EventsLoaded) {
                      for (final e in blocState.allEvents) {
                        if (e.id == eventId) {
                          event = e;
                          break;
                        }
                      }
                    }
                  }

                  if (event == null) {
                    return _notFound(state);
                  }

                  // Re-provee el EventsBloc del shell para esta rama de navegación.
                  return BlocProvider<EventsBloc>.value(
                    value: args.eventsBloc,
                    child: EventDetailScreen(
                      event: event,
                      currentUser: args.currentUser,
                    ),
                  );
                }

                return _notFound(state);
              },
            ),
          ],
        ),
      ],
      errorBuilder: (context, state) => Scaffold(
        appBar: AppBar(title: const Text('Página no encontrada')),
        body: Center(
          child: Text('No existe la ruta: ${state.uri}'),
        ),
      ),
    );
  }

  static Widget _notFound(GoRouterState state) {
    return Scaffold(
      appBar: AppBar(title: const Text('Detalle del Evento')),
      body: const Center(
        child: Text('No se encontraron detalles para este evento.'),
      ),
    );
  }
}

/// Adaptador para convertir un [Stream] de BLoC en un [ChangeNotifier] que [GoRouter] puede escuchar
class _GoRouterRefreshStream extends ChangeNotifier {
  late final StreamSubscription<dynamic> _subscription;

  _GoRouterRefreshStream(Stream<dynamic> stream) {
    notifyListeners();
    _subscription = stream.asBroadcastStream().listen((_) => notifyListeners());
  }

  @override
  void dispose() {
    _subscription.cancel();
    super.dispose();
  }
}
