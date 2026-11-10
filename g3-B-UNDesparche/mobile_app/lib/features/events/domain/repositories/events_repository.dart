import '../entities/event_entity.dart';

/// Contrato abstracto para el modulo de eventos
abstract class EventsRepository {
  /// Obtiene la lista de eventos filtrados opcionalmente por categoria o termino de busqueda
  Future<List<EventEntity>> getEvents({
    EventCategory? category,
    String? searchQuery,
  });

  /// Obtiene los detalles de un evento especifico por su identificador
  Future<EventEntity> getEventById(String eventId);

  /// Suscribe o cancela la suscripcion de un usuario al evento.
  /// Si es visitante, se proporciona el [email]. Si es estudiante, se asocia a su ID.
  Future<EventEntity> toggleSubscription(String eventId, {String? email});
}
