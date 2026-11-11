import '../entities/event_entity.dart';

/// Criterio de filtrado de eventos. Fuente única compartida por el BLoC y la UI
/// (para contar resultados), eliminando la regla duplicada (#7).
class EventFilter {
  final EventCategory? category;
  final String query;

  const EventFilter({
    this.category,
    this.query = '',
  });

  /// Devuelve true si [event] pasa el criterio.
  bool matches(EventEntity event) {
    if (category != null && event.category != category) {
      return false;
    }
    final q = query.toLowerCase().trim();
    if (q.isEmpty) return true;
    return event.title.toLowerCase().contains(q) ||
        event.description.toLowerCase().contains(q) ||
        event.venueName.toLowerCase().contains(q);
  }

  /// Aplica el criterio y ordena por fecha de inicio (RF-6).
  List<EventEntity> apply(List<EventEntity> list) {
    final result = list.where(matches).toList();
    result.sort((a, b) => a.startDateTime.compareTo(b.startDateTime));
    return result;
  }

  /// Cuántos elementos pasarían el criterio.
  int count(List<EventEntity> list) => list.where(matches).length;
}
