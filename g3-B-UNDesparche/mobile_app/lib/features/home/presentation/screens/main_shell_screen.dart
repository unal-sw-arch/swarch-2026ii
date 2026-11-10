import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import '../../../../core/widgets/floating_nav_bar.dart';
import '../../../auth/domain/entities/user_entity.dart';
import '../../../auth/presentation/screens/profile_screen.dart';
import '../../../events/presentation/bloc/events_bloc.dart';
import '../../../events/presentation/bloc/events_event.dart';
import '../../../events/presentation/bloc/events_state.dart';
import '../../../events/domain/entities/event_entity.dart';
import '../../../events/presentation/screens/campus_map_screen.dart';
import '../../../events/presentation/screens/events_list_screen.dart';
import '../../../inventory/presentation/bloc/inventory_bloc.dart';
import '../../../inventory/presentation/bloc/inventory_event.dart';
import '../../../inventory/presentation/screens/inventory_catalog_screen.dart';
import '../../../../injection_container.dart' as di;

/// Shell principal con barra flotante inferior (HeroUI Mint).
/// Orquesta las 4 pestañas: Eventos, Implementos, Mapa y Perfil.
class MainShellScreen extends StatelessWidget {
  final UserEntity currentUser;

  const MainShellScreen({super.key, required this.currentUser});

  @override
  Widget build(BuildContext context) {
    return MultiBlocProvider(
      providers: [
        BlocProvider(create: (_) => di.sl<EventsBloc>()..add(const LoadEventsRequested())),
        BlocProvider(create: (_) => di.sl<InventoryBloc>()..add(LoadInventoryRequested(userId: currentUser.id))),
      ],
      child: _MainShellView(currentUser: currentUser),
    );
  }
}

class _MainShellView extends StatefulWidget {
  final UserEntity currentUser;

  const _MainShellView({required this.currentUser});

  @override
  State<_MainShellView> createState() => _MainShellViewState();
}

class _MainShellViewState extends State<_MainShellView> {
  static const int _eventsTab = 0;
  static const int _mapTab = 2;

  static const List<NavDestination> _destinations = [
    NavDestination(
      label: 'Eventos',
      icon: Icons.celebration_outlined,
      selectedIcon: Icons.celebration,
    ),
    NavDestination(
      label: 'Implementos',
      icon: Icons.sports_soccer_outlined,
      selectedIcon: Icons.sports_soccer,
    ),
    NavDestination(
      label: 'Mapa',
      icon: Icons.map_outlined,
      selectedIcon: Icons.map,
    ),
    NavDestination(
      label: 'Perfil',
      icon: Icons.person_outline,
      selectedIcon: Icons.person,
    ),
  ];

  int _currentIndex = _eventsTab;

  /// El mapa se crea la primera vez que se abre la pestaña (Google Maps pesa).
  bool _mapVisited = false;

  /// Cambia cada vez que otra pantalla pide "Ver en mapa", para recrear el mapa
  /// y que la cámara vaya al evento elegido.
  int _mapEpoch = 0;

  /// Evento que pidió "Ver en mapa": el mapa abre con ese evento seleccionado.
  EventEntity? _mapEvent;

  /// Último token de petición de mapa procesado, para no repetir el salto.
  int _lastMapToken = 0;

  void _select(int index) {
    setState(() {
      _currentIndex = index;
      if (index == _mapTab) _mapVisited = true;
    });
  }

  /// Reacciona a una petición "Ver en mapa" emitida por el EventsBloc (#9).
  void _onEventsState(BuildContext context, EventsState state) {
    if (state is! EventsLoaded) return;
    if (state.mapRequestToken == _lastMapToken || state.mapRequest == null) {
      return;
    }
    _lastMapToken = state.mapRequestToken;
    setState(() {
      _mapEvent = state.mapRequest;
      _mapEpoch++;
      _mapVisited = true;
      _currentIndex = _mapTab;
    });
  }

  Widget _buildMapTab() {
    if (!_mapVisited) return const SizedBox.shrink();
    return BlocBuilder<EventsBloc, EventsState>(
      builder: (context, state) {
        if (state is! EventsLoaded) return const SizedBox.shrink();
        return CampusMapScreen(
          key: ValueKey(_mapEpoch),
          events: state.filteredEvents,
          currentUser: widget.currentUser,
          initialSelectedEvent: _mapEvent ?? state.selectedEvent,
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    final pages = [
      EventsListScreen(currentUser: widget.currentUser),
      InventoryCatalogScreen(currentUser: widget.currentUser),
      _buildMapTab(),
      ProfileScreen(user: widget.currentUser),
    ];

    return BlocListener<EventsBloc, EventsState>(
      listenWhen: (prev, curr) =>
          curr is EventsLoaded &&
          (prev is! EventsLoaded ||
              prev.mapRequestToken != curr.mapRequestToken),
      listener: _onEventsState,
      child: Scaffold(
        // El contenido pasa por detras de la barra flotante (el vidrio la deja ver).
        extendBody: true,
        body: IndexedStack(
          index: _currentIndex,
          children: pages,
        ),
        bottomNavigationBar: FloatingNavBar(
          destinations: _destinations,
          currentIndex: _currentIndex,
          onSelected: _select,
        ),
      ),
    );
  }
}
