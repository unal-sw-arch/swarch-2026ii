import 'package:flutter/material.dart';

/// Una opcion dentro de un grupo de filtros. [key] es el dato; [label] es lo que se lee.
class FilterOption {
  final String key;
  final String label;
  final IconData? icon;

  const FilterOption(this.key, this.label, {this.icon});
}

/// Un grupo de filtros (por ejemplo "Categoría") con su seleccion actual.
class FilterGroup {
  final String id;
  final String title;
  final List<FilterOption> options;
  final String selectedKey;

  /// Opcion que significa "sin filtro" (por ejemplo "Todos").
  final String defaultKey;

  const FilterGroup({
    required this.id,
    required this.title,
    required this.options,
    required this.selectedKey,
    required this.defaultKey,
  });

  bool get isActive => selectedKey != defaultKey;

  String get selectedLabel => options
      .firstWhere((o) => o.key == selectedKey, orElse: () => options.first)
      .label;
}

/// Cuantos grupos tienen un filtro puesto.
int activeFilterCount(List<FilterGroup> groups) =>
    groups.where((g) => g.isActive).length;
