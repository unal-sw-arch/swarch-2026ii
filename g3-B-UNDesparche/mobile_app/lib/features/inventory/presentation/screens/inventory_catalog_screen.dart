import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_spacing.dart';
import '../../../../core/widgets/brand_header.dart';
import '../../../../core/widgets/empty_state.dart';
import '../../../../core/widgets/fade_up_in.dart';
import '../../../../core/widgets/filter_sheet.dart';
import '../../../../core/widgets/hero_text_field.dart';
import '../../../../core/widgets/skeleton_card.dart';
import '../../../auth/domain/entities/user_entity.dart';
import '../../domain/entities/implement_entity.dart';
import '../../domain/usecases/filter_implements.dart';
import '../bloc/inventory_bloc.dart';
import '../bloc/inventory_event.dart';
import '../bloc/inventory_state.dart';
import '../widgets/active_reservation_banner.dart';
import '../widgets/disclaimer_dialog.dart';
import '../widgets/implement_card.dart';
import '../widgets/inventory_notice.dart';
import 'inventory_filters.dart';
import '../../../../core/services/notification_service.dart';

/// Catalogo completo de inventario con filtros por facultad, categoria y reserva con temporizador (RN-02)
class InventoryCatalogScreen extends StatefulWidget {
  final UserEntity currentUser;

  const InventoryCatalogScreen({super.key, required this.currentUser});

  @override
  State<InventoryCatalogScreen> createState() => _InventoryCatalogScreenState();
}

class _InventoryCatalogScreenState extends State<InventoryCatalogScreen> {
  final TextEditingController _searchController = TextEditingController();

  Future<void> _openFilters(
      BuildContext context, List<FilterGroup> groups) async {
    final state = context.read<InventoryBloc>().state;
    final result = await showFilterSheet(
      context,
      groups: groups,
      resultNoun: ('implemento', 'implementos'),
      resultCount:
          state is InventoryLoaded ? (sel) => _countFor(state, sel) : null,
    );
    if (result == null || !context.mounted) return;
    _applyFilters(context, result);
  }

  /// Cuantos implementos quedarian con esa seleccion y la busqueda actual (misma regla que el bloc).
  int _countFor(InventoryLoaded state, Map<String, String> sel) {
    final filter = ImplementFilter(
      faculty: sel['faculty']!,
      category: sel['category'] == 'all'
          ? null
          : ImplementCategory.values.byName(sel['category']!),
      query: state.searchQuery,
    );
    return filter.count(state.allImplements);
  }

  void _applyFilters(BuildContext context, Map<String, String> selection) {
    final bloc = context.read<InventoryBloc>();
    final faculty = selection['faculty'];
    if (faculty != null) bloc.add(FilterInventoryFacultyChanged(faculty));
    final category = selection['category'];
    if (category != null) {
      bloc.add(FilterInventoryCategoryChanged(category == 'all'
          ? null
          : ImplementCategory.values.byName(category)));
    }
  }

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  void _onReserve(ImplementEntity item) async {
    final accepted = await DisclaimerDialog.show(context, item);
    if (accepted == true && mounted) {
      context.read<InventoryBloc>().add(
            CreateReservationRequested(
              implementId: item.id,
              userId: widget.currentUser.id,
              userEmail: widget.currentUser.email,
              disclaimerAccepted: true,
            ),
          );
    }
  }

  PreferredSizeWidget _header(BuildContext context) {
    return BrandHeader(
      title: 'Inventario UNAL',
      trailing: InitialsAvatar(name: widget.currentUser.fullName),
    );
  }

  @override
  Widget build(BuildContext context) {
    return BlocConsumer<InventoryBloc, InventoryState>(
      listener: (context, state) {
        if (state is InventoryLoaded) {
          if (state.userMessage != null) {
            final isSuccess = state.userMessage!.contains('exitosa') ||
                state.userMessage!.contains('correctamente');
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(
                content: Text(state.userMessage!),
                backgroundColor: isSuccess ? null : AppColors.danger,
              ),
            );
          }

          // Alerta preventiva al restar 3 minutos (180 segundos)
          if (state.remainingSeconds == 180 &&
              state.activeReservation != null) {
            context.read<NotificationService>().notifyReservationExpiringSoon(
                  context: context,
                  implementName: state.activeReservation!.implementName,
                  pickupLocation: state.activeReservation!.pickupLocation,
                  secondsLeft: state.remainingSeconds,
                );
          }
        }
      },
      builder: (context, state) {
        if (state is InventoryLoaded) {
          return Scaffold(
            appBar: _header(context),
            body: Column(
              children: [
                // Busqueda y boton de filtros
                Padding(
                  padding: const EdgeInsets.fromLTRB(
                    AppSpacing.screenH,
                    AppSpacing.md,
                    AppSpacing.screenH,
                    AppSpacing.xs,
                  ),
                  child: IntrinsicHeight(
                    child: Row(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        Expanded(
                          child: HeroTextField.search(
                            controller: _searchController,
                            hintText:
                                'Buscar implemento (ej. balón, ajedrez, tenis)...',
                            onChanged: (val) {
                              context
                                  .read<InventoryBloc>()
                                  .add(SearchInventoryQueryChanged(val));
                            },
                            onClear: () {
                              context
                                  .read<InventoryBloc>()
                                  .add(const SearchInventoryQueryChanged(''));
                            },
                          ),
                        ),
                        const SizedBox(width: AppSpacing.sm),
                        FilterButton(
                          activeCount: activeFilterCount(InventoryFilters.groups(state)),
                          onTap: () => _openFilters(context, InventoryFilters.groups(state)),
                        ),
                      ],
                    ),
                  ),
                ),

                // Filtros puestos (tocar uno lo quita)
                ActiveFilterChips(
                  groups: InventoryFilters.groups(state),
                  onRemove: (id) => _applyFilters(
                      context, {id: id == 'faculty' ? 'Todas' : 'all'}),
                ),
                // Lista de implementos con banner reactivo
                Expanded(
                  child: RefreshIndicator(
                    color: AppColors.mintPrimary,
                    onRefresh: () async {
                      context.read<InventoryBloc>().add(LoadInventoryRequested(
                          userId: widget.currentUser.id));
                    },
                    child: ListView(
                      physics: const AlwaysScrollableScrollPhysics(),
                      padding: EdgeInsets.fromLTRB(
                        AppSpacing.screenH,
                        AppSpacing.screenH,
                        AppSpacing.screenH,
                        AppSpacing.screenH +
                            MediaQuery.paddingOf(context).bottom,
                      ),
                      children: [
                        // Banner de reserva activa con temporizador de 10 min (RN-02)
                        if (state.activeReservation != null) ...[
                          ActiveReservationBanner(
                            reservation: state.activeReservation!,
                            remainingSeconds: state.remainingSeconds,
                            onCancel: () {
                              context.read<InventoryBloc>().add(
                                    CancelReservationRequested(
                                        state.activeReservation!.id),
                                  );
                            },
                          ),
                          const SizedBox(height: AppSpacing.lg),
                        ],

                        if (widget.currentUser.isGuest) ...[
                          const InventoryNotice(
                            icon: Icons.info_outline,
                            color: AppColors.occupied,
                            text:
                                'Modo Visitante: puedes consultar la disponibilidad en taquillas. Los préstamos son exclusivos para miembros UNAL (@unal.edu.co).',
                          ),
                          const SizedBox(height: AppSpacing.lg),
                        ],

                        if (widget.currentUser.isSanctioned) ...[
                          const InventoryNotice(
                            icon: Icons.block,
                            color: AppColors.danger,
                            text:
                                'Cuenta Sancionada (RN-03): no puedes solicitar préstamos debido a un implemento dañado. Resuelve tu sanción en taquilla.',
                          ),
                          const SizedBox(height: AppSpacing.lg),
                        ],

                        if (state.filteredImplements.isEmpty)
                          SizedBox(
                            height: 320,
                            child: EmptyState(
                              icon: Icons.sports_soccer_outlined,
                              title: 'Sin implementos',
                              description:
                                  'No se encontraron implementos disponibles para este filtro.',
                              actionLabel: 'Ver todos',
                              onAction: () {
                                _searchController.clear();
                                final bloc = context.read<InventoryBloc>();
                                bloc.add(const SearchInventoryQueryChanged(''));
                                bloc.add(const FilterInventoryFacultyChanged(
                                    'Todas'));
                                bloc.add(
                                    const FilterInventoryCategoryChanged(null));
                              },
                            ),
                          )
                        else
                          for (var i = 0;
                              i < state.filteredImplements.length;
                              i++)
                            Padding(
                              padding:
                                  const EdgeInsets.only(bottom: AppSpacing.md),
                              child: FadeUpIn(
                                index: i,
                                child: ImplementCard(
                                  implement: state.filteredImplements[i],
                                  currentUser: widget.currentUser,
                                  onReserve: () =>
                                      _onReserve(state.filteredImplements[i]),
                                ),
                              ),
                            ),
                      ],
                    ),
                  ),
                ),
              ],
            ),
          );
        }

        // Cargando (o aun sin datos): esqueletos en lugar de un spinner
        return Scaffold(
          appBar: _header(context),
          body: const SkeletonList(),
        );
      },
    );
  }
}
