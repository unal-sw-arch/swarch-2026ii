import 'package:flutter/material.dart';
import '../../../../core/widgets/filter_sheet.dart';
import '../../domain/entities/implement_entity.dart';
import '../bloc/inventory_state.dart';

/// Definición de los grupos de filtro del inventario (facultad, categoría).
/// Fuera de la pantalla para no inflarla (#13).
abstract class InventoryFilters {
  static const List<FilterOption> facultyOptions = [
    FilterOption('Todas', 'Todas', icon: Icons.apps),
    FilterOption('Ingenieria', 'Ingeniería', icon: Icons.engineering_outlined),
    FilterOption('Ciencias Humanas', 'Ciencias Humanas',
        icon: Icons.menu_book_outlined),
    FilterOption('Bienestar Central', 'Bienestar Central',
        icon: Icons.favorite_border),
  ];

  static const List<(ImplementCategory?, String, IconData)> categories = [
    (null, 'Todos', Icons.apps),
    (ImplementCategory.balls, 'Balones', Icons.sports_soccer),
    (ImplementCategory.rackets, 'Raquetas', Icons.sports_tennis),
    (ImplementCategory.boardGames, 'Juegos de Mesa', Icons.extension_outlined),
  ];

  static List<FilterGroup> groups(InventoryLoaded state) {
    final faculty = facultyOptions.firstWhere(
      (o) => o.key.toLowerCase() == state.selectedFaculty.toLowerCase(),
      orElse: () => facultyOptions.first,
    );
    return [
      FilterGroup(
        id: 'faculty',
        title: 'Facultad',
        defaultKey: 'Todas',
        selectedKey: faculty.key,
        options: facultyOptions,
      ),
      FilterGroup(
        id: 'category',
        title: 'Categoría',
        defaultKey: 'all',
        selectedKey: state.selectedCategory?.name ?? 'all',
        options: [
          for (final (c, label, icon) in categories)
            FilterOption(c?.name ?? 'all', label, icon: icon)
        ],
      ),
    ];
  }
}
