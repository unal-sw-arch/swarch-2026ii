import '../../../../core/utils/json_coerce.dart';
import '../../domain/entities/event_entity.dart';

/// Modelo de datos (DTO) para la serialización y deserialización de eventos del campus
class EventModel extends EventEntity {
  const EventModel({
    required super.id,
    required super.title,
    required super.description,
    required super.category,
    required super.venueName,
    required super.latitude,
    required super.longitude,
    required super.startDateTime,
    required super.endDateTime,
    required super.organizer,
    required super.status,
    super.imageUrl,
    super.isSubscribed = false,
  });

  factory EventModel.fromJson(Map<String, dynamic> json) {
    String organizerStr = 'UNAL';
    if (json['organizer'] is Map) {
      final orgMap = json['organizer'] as Map;
      organizerStr = asString(orgMap['name'] ?? orgMap['email'] ?? orgMap['id'], 'UNAL');
    } else if (json['organizer'] != null) {
      organizerStr = asString(json['organizer'], 'UNAL');
    }

    return EventModel(
      id: asString(json['id']),
      title: asString(json['title'] ?? json['name']),
      description: asString(json['description']),
      category: _parseCategory(json['category']),
      venueName: asString(json['venue_name'] ?? json['place'], 'Campus UNAL'),
      latitude: asDouble(json['latitude'], 4.6372),
      longitude: asDouble(json['longitude'], -74.0841),
      startDateTime: DateTime.tryParse(asString(json['datetime_start'] ?? json['start_time'])) ?? DateTime.now(),
      endDateTime: DateTime.tryParse(asString(json['datetime_end'] ?? json['end_time'])) ?? DateTime.now().add(const Duration(hours: 2)),
      organizer: organizerStr,
      status: _parseStatus(json['status']),
      imageUrl: asStringOrNull(json['image_url'] ?? json['image']),
      isSubscribed: asBool(json['is_subscribed']),
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'title': title,
      'description': description,
      'category': toBackendCategory(category),
      'venue_name': venueName,
      'latitude': latitude,
      'longitude': longitude,
      'datetime_start': startDateTime.toIso8601String(),
      'datetime_end': endDateTime.toIso8601String(),
      'organizer': organizer,
      'status': status.name,
      'image_url': imageUrl,
      'is_subscribed': isSubscribed,
    };
  }

  static EventCategory _parseCategory(dynamic cat) {
    final str = cat?.toString().toLowerCase() ?? '';
    if (str == 'dep' || str.contains('sport') || str.contains('deport')) return EventCategory.sports;
    if (str == 'cul' || str.contains('cultur')) return EventCategory.culture;
    if (str == 'aca' || str.contains('academ')) return EventCategory.academic;
    if (str == 'soc' || str == 'par' || str == 'asa' || str.contains('social') || str.contains('parche')) return EventCategory.social;
    return EventCategory.welfare;
  }

  static String toBackendCategory(EventCategory cat) {
    switch (cat) {
      case EventCategory.sports:
        return 'DEP';
      case EventCategory.culture:
        return 'CUL';
      case EventCategory.academic:
        return 'ACA';
      case EventCategory.social:
        return 'PAR';
      case EventCategory.welfare:
        return 'OTR';
    }
  }

  static EventStatus _parseStatus(dynamic stat) {
    final str = stat?.toString().toLowerCase() ?? '';
    if (str == 'ecu' || str.contains('progress') || str.contains('curso')) return EventStatus.inProgress;
    if (str == 'can' || str.contains('cancel')) return EventStatus.cancelled;
    if (str == 'fin' || str.contains('complete') || str.contains('final')) return EventStatus.completed;
    return EventStatus.scheduled;
  }
}
