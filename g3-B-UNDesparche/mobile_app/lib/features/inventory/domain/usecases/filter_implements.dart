import '../entities/implement_entity.dart';

/// Criterio de filtrado de implementos. Una sola fuente de verdad que
/// comparten el BLoC (para filtrar la lista) y la UI (para contar resultados
/// antes de aplicar). Antes la regla estaba duplicada en ambos lugares (#7).
class ImplementFilter {
  final String faculty;
  final ImplementCategory? category;
  final String query;

  const ImplementFilter({
    this.faculty = 'Todas',
    this.category,
    this.query = '',
  });

  /// Devuelve true si [item] pasa el criterio.
  bool matches(ImplementEntity item) {
    if (faculty != 'Todas' &&
        item.facultyName.toLowerCase() != faculty.toLowerCase()) {
      return false;
    }
    if (category != null && item.category != category) {
      return false;
    }
    final q = query.toLowerCase().trim();
    if (q.isEmpty) return true;
    return item.name.toLowerCase().contains(q) ||
        item.pickupLocation.toLowerCase().contains(q) ||
        item.description.toLowerCase().contains(q);
  }

  /// Aplica el criterio a una lista completa.
  List<ImplementEntity> apply(List<ImplementEntity> list) =>
      list.where(matches).toList();

  /// Cuántos elementos pasarían el criterio.
  int count(List<ImplementEntity> list) => list.where(matches).length;
}
