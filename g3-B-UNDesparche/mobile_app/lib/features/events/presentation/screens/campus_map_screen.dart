import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:go_router/go_router.dart';
import 'package:google_maps_flutter/google_maps_flutter.dart';
import 'package:google_maps_flutter_android/google_maps_flutter_android.dart';
import 'package:google_maps_flutter_platform_interface/google_maps_flutter_platform_interface.dart';
import '../../../../core/router/app_router.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_text_styles.dart';
import '../../../../core/widgets/hero_card.dart';
import '../../../../core/widgets/hero_solid_button.dart';
import '../../../../core/widgets/hero_status_chip.dart';
import '../../../auth/domain/entities/user_entity.dart';
import '../../domain/entities/event_entity.dart';
import '../bloc/events_bloc.dart';
import '../widgets/event_labels.dart';
import '../../../../core/widgets/campus_tag.dart';

/// Mapa Interactivo y Funcional del Campus UNAL Bogota (Requerimiento RF-4)
/// Integra Google Maps SDK con soporte satelital, navegacion a edificios y marcadores en vivo.
class CampusMapScreen extends StatefulWidget {
  final List<EventEntity> events;
  final UserEntity currentUser;
  final EventEntity? initialSelectedEvent;

  const CampusMapScreen({
    super.key,
    required this.events,
    required this.currentUser,
    this.initialSelectedEvent,
  });

  @override
  State<CampusMapScreen> createState() => _CampusMapScreenState();
}

class _CampusMapScreenState extends State<CampusMapScreen> {
  GoogleMapController? _mapController;
  EventEntity? _selectedEvent;
  MapType _currentMapType = MapType.normal;

  // Coordenadas centrales de la Ciudad Universitaria UNAL Bogota
  static const LatLng _unalBogotaCenter = LatLng(4.6372, -74.0841);

  // Lugares y edificios iconicos de la Ciudad Universitaria
  final List<_CampusQuickLocation> _landmarks = const [
    _CampusQuickLocation(name: 'Minas (Ed. 411)', lat: 4.6391, lng: -74.0858, icon: Icons.sports_soccer),
    _CampusQuickLocation(name: 'CyT (Ed. 453)', lat: 4.6382, lng: -74.0882, icon: Icons.biotech),
    _CampusQuickLocation(name: 'Plaza Ché (Ed. 101)', lat: 4.6360, lng: -74.0830, icon: Icons.account_balance),
    _CampusQuickLocation(name: 'Jardín Freud (Ed. 224)', lat: 4.6358, lng: -74.0845, icon: Icons.park),
    _CampusQuickLocation(name: 'León de Greiff (Ed. 104)', lat: 4.6366, lng: -74.0827, icon: Icons.theater_comedy),
    _CampusQuickLocation(name: 'Polideportivo (Ed. 412)', lat: 4.6375, lng: -74.0850, icon: Icons.fitness_center),
  ];

  @override
  void initState() {
    super.initState();
    _selectedEvent = widget.initialSelectedEvent;
    final mapsImpl = GoogleMapsFlutterPlatform.instance;
    if (mapsImpl is GoogleMapsFlutterAndroid) {
      mapsImpl.useAndroidViewSurface = true;
    }
  }

  void _onMapCreated(GoogleMapController controller) {
    _mapController = controller;

    // Si viene un evento seleccionado inicialmente, animar la camara hacia el
    if (widget.initialSelectedEvent != null) {
      _animateToLocation(
        LatLng(widget.initialSelectedEvent!.latitude, widget.initialSelectedEvent!.longitude),
        zoom: 17.5,
      );
    }
  }

  void _animateToLocation(LatLng target, {double zoom = 16.5}) {
    _mapController?.animateCamera(
      CameraUpdate.newCameraPosition(
        CameraPosition(target: target, zoom: zoom),
      ),
    );
  }

  void _toggleMapType() {
    setState(() {
      if (_currentMapType == MapType.normal) {
        _currentMapType = MapType.satellite;
      } else if (_currentMapType == MapType.satellite) {
        _currentMapType = MapType.terrain;
      } else {
        _currentMapType = MapType.normal;
      }
    });
  }

  Set<Marker> _buildMarkers() {
    return widget.events.map((event) {
      final isSelected = _selectedEvent?.id == event.id;

      return Marker(
        markerId: MarkerId(event.id),
        position: LatLng(event.latitude, event.longitude),
        icon: BitmapDescriptor.defaultMarkerWithHue(_getCategoryHue(event.category)),
        infoWindow: InfoWindow(
          title: event.title,
          snippet: event.venueName,
        ),
        onTap: () {
          setState(() => _selectedEvent = event);
          _animateToLocation(LatLng(event.latitude, event.longitude), zoom: 17.0);
        },
        zIndexInt: isSelected ? 2 : 1,
      );
    }).toSet();
  }

  double _getCategoryHue(EventCategory category) {
    switch (category) {
      case EventCategory.sports:
        return BitmapDescriptor.hueRed;
      case EventCategory.culture:
        return BitmapDescriptor.hueOrange;
      case EventCategory.academic:
        return BitmapDescriptor.hueAzure;
      case EventCategory.welfare:
        return BitmapDescriptor.hueViolet;
      case EventCategory.social:
        return BitmapDescriptor.hueGreen;
    }
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    // Comprobar soporte de Google Maps nativo
    final bool isDesktopWithoutGoogleMaps = !kIsWeb &&
        (defaultTargetPlatform == TargetPlatform.windows ||
            defaultTargetPlatform == TargetPlatform.linux ||
            defaultTargetPlatform == TargetPlatform.macOS);

    return Scaffold(
      appBar: AppBar(
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Campus UNAL Bogotá', style: context.displaySmall),
            Text('Ciudad Universitaria · Vista en vivo', style: context.bodySmall),
          ],
        ),
        actions: [
          IconButton(
            icon: Icon(
              _currentMapType == MapType.normal
                  ? Icons.layers_outlined
                  : (_currentMapType == MapType.satellite ? Icons.satellite_alt : Icons.terrain),
            ),
            tooltip: 'Cambiar tipo de mapa (Normal / Satelital / Terreno)',
            onPressed: _toggleMapType,
          ),
          IconButton(
            icon: const Icon(Icons.my_location),
            tooltip: 'Re-centrar en el campus',
            onPressed: () {
              setState(() => _selectedEvent = null);
              _animateToLocation(_unalBogotaCenter, zoom: 16.0);
            },
          ),
        ],
      ),
      body: Stack(
        children: [
          // 1. Google Maps Funcional o Fallback para Windows Desktop
          if (isDesktopWithoutGoogleMaps)
            _buildDesktopFallback(isDark)
          else
            GoogleMap(
              initialCameraPosition: const CameraPosition(
                target: _unalBogotaCenter,
                zoom: 16.0,
              ),
              mapType: _currentMapType,
              markers: _buildMarkers(),
              onMapCreated: _onMapCreated,
              myLocationButtonEnabled: false,
              zoomControlsEnabled: true,
              // Sube los controles del mapa para que la barra flotante no los tape.
              padding: EdgeInsets.only(bottom: MediaQuery.paddingOf(context).bottom),
              compassEnabled: true,
              onTap: (_) => setState(() => _selectedEvent = null),
            ),

          // 2. Barra Superior con Atajos Rápidos a Edificios del Campus
          Positioned(
            top: 10,
            left: 0,
            right: 0,
            child: SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.symmetric(horizontal: 12),
              child: Row(
                children: _landmarks.map((loc) {
                  return Padding(
                    padding: const EdgeInsets.only(right: 8),
                    child: Material(
                      color: (isDark ? AppColors.darkSurface : AppColors.lightSurface).withValues(alpha: 0.94),
                      shape: StadiumBorder(
                        side: BorderSide(color: isDark ? AppColors.darkBorder : AppColors.lightBorder),
                      ),
                      clipBehavior: Clip.antiAlias,
                      child: InkWell(
                        onTap: () {
                          _animateToLocation(LatLng(loc.lat, loc.lng), zoom: 17.5);
                        },
                        child: ConstrainedBox(
                          constraints: const BoxConstraints(minHeight: 40),
                          child: Padding(
                            padding: const EdgeInsets.symmetric(horizontal: 14),
                            child: Row(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                Icon(loc.icon, size: 16, color: isDark ? AppColors.mintLime : AppColors.mintFocus),
                                const SizedBox(width: 6),
                                Text(
                                  loc.name,
                                  style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600),
                                ),
                              ],
                            ),
                          ),
                        ),
                      ),
                    ),
                  );
                }).toList(),
              ),
            ),
          ),

          // 3. Tarjeta Inferior de Evento Seleccionado
          if (_selectedEvent != null)
            Positioned(
              bottom: 16 + MediaQuery.paddingOf(context).bottom,
              left: 16,
              right: 16,
              child: HeroCard(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Row(
                          children: [
                            HeroStatusChip(
                              label: eventCategoryLabel(_selectedEvent!.category),
                              baseColor: AppColors.mintPrimary,
                            ),
                            const SizedBox(width: 8),
                            if (CampusTag.fromPlace(_selectedEvent!.venueName) != null)
                              CampusTag.fromPlace(_selectedEvent!.venueName)!,
                          ],
                        ),
                        IconButton(
                          icon: const Icon(Icons.close, size: 20),
                          tooltip: 'Cerrar',
                          onPressed: () => setState(() => _selectedEvent = null),
                        ),
                      ],
                    ),
                    const SizedBox(height: 6),
                    Text(_selectedEvent!.title, style: context.displaySmall),
                    const SizedBox(height: 4),
                    Row(
                      children: [
                        const Icon(Icons.place_outlined, size: 14, color: AppColors.mintFocus),
                        const SizedBox(width: 4),
                        Expanded(
                          child: Text(_selectedEvent!.venueName, style: context.bodySmall),
                        ),
                      ],
                    ),
                    const SizedBox(height: 12),
                    Row(
                      children: [
                        Expanded(
                          child: HeroSolidButton(
                            text: 'Ver Detalles del Evento',
                            icon: Icons.info_outline,
                            onPressed: () {
                              context.push(
                                '/events/${_selectedEvent!.id}',
                                extra: EventDetailArgs(
                                  event: _selectedEvent!,
                                  currentUser: widget.currentUser,
                                  eventsBloc: context.read<EventsBloc>(),
                                ),
                              );
                            },
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
        ],
      ),
    );
  }

  Widget _buildDesktopFallback(bool isDark) {
    return Container(
      color: isDark ? AppColors.darkBackground : AppColors.lightBackground,
      padding: const EdgeInsets.all(24),
      child: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Icon(Icons.map_outlined, size: 64, color: AppColors.mintPrimary),
            const SizedBox(height: 16),
            Text('Google Maps Activo en Web y Móvil', style: context.displayMedium),
            const SizedBox(height: 8),
            Text(
              'El SDK oficial de Google Maps se ejecuta nativamente en Google Chrome (Web), Android e iOS. En Windows de escritorio está disponible el modo lista.',
              style: context.bodyMedium,
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 16),
            Text(
              'Ejecuta la app con: flutter run -d chrome para explorar el mapa interactivo con imágenes satelitales.',
              style: context.bodySmall.copyWith(color: AppColors.mintFocus),
              textAlign: TextAlign.center,
            ),
          ],
        ),
      ),
    );
  }
}

class _CampusQuickLocation {
  final String name;
  final double lat;
  final double lng;
  final IconData icon;

  const _CampusQuickLocation({
    required this.name,
    required this.lat,
    required this.lng,
    required this.icon,
  });
}
