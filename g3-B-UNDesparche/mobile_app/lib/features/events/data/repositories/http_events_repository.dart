import 'package:dio/dio.dart';
import '../../../../core/error/failures.dart';
import '../../../../core/network/api_endpoints.dart';
import '../../../../core/network/dio_client.dart';
import '../../domain/entities/event_entity.dart';
import '../../domain/repositories/events_repository.dart';
import '../models/event_model.dart';

/// Implementacion remota de [EventsRepository] lista para conectarse al API Gateway
class HttpEventsRepository implements EventsRepository {
  final DioClient client;

  HttpEventsRepository({required this.client});

  @override
  Future<List<EventEntity>> getEvents({
    EventCategory? category,
    String? searchQuery,
  }) async {
    try {
      final response = await client.dio.get(
        ApiEndpoints.events,
        queryParameters: {
          if (category != null) 'category': EventModel.toBackendCategory(category),
          if (searchQuery != null && searchQuery.isNotEmpty) 'search': searchQuery,
        },
      );

      final List<dynamic> data =
          response.data is List ? response.data as List : ((response.data as Map)['results'] as List? ?? const []);
      return data.map((json) => EventModel.fromJson(json as Map<String, dynamic>)).toList();
    } on DioException catch (e) {
      if (e.type == DioExceptionType.connectionTimeout || e.type == DioExceptionType.connectionError) {
        throw const NetworkFailure();
      }
      throw ServerEventsFailure('Error al obtener eventos del servidor: ${e.message}');
    }
  }

  @override
  Future<EventEntity> getEventById(String eventId) async {
    try {
      final response = await client.dio.get(ApiEndpoints.eventDetail(eventId));
      return EventModel.fromJson(response.data as Map<String, dynamic>);
    } on DioException catch (e) {
      if (e.response?.statusCode == 404) {
        throw const EventNotFoundFailure();
      }
      if (e.type == DioExceptionType.connectionTimeout || e.type == DioExceptionType.connectionError) {
        throw const NetworkFailure();
      }
      throw ServerEventsFailure('Error al consultar el evento: ${e.message}');
    }
  }

  @override
  Future<EventEntity> toggleSubscription(String eventId, {String? email}) async {
    try {
      final currentEvent = await getEventById(eventId);
      final isSubscribed = currentEvent.isSubscribed;

      if (isSubscribed) {
        await client.dio.post(
          ApiEndpoints.eventUnsubscribe(eventId),
          data: email != null ? {'email': email} : null,
        );
      } else {
        await client.dio.post(
          ApiEndpoints.eventSubscribe(eventId),
          data: email != null ? {'email': email} : null,
        );
      }
      return currentEvent.copyWith(isSubscribed: !isSubscribed);
    } on DioException catch (e) {
      if (e.type == DioExceptionType.connectionTimeout || e.type == DioExceptionType.connectionError) {
        throw const NetworkFailure();
      }
      throw ServerEventsFailure('Error al actualizar suscripcion al evento: ${e.message}');
    }
  }
}
