import 'package:equatable/equatable.dart';

enum EventStatus {
  scheduled,
  inProgress,
  completed,
  cancelled,
}

enum EventCategory {
  sports,
  culture,
  academic,
  welfare,
  social,
}

/// Entidad central de Evento en el campus UNAL
class EventEntity extends Equatable {
  final String id;
  final String title;
  final String description;
  final EventCategory category;
  final String venueName;
  final double latitude;
  final double longitude;
  final DateTime startDateTime;
  final DateTime endDateTime;
  final String organizer;
  final EventStatus status;
  final String? imageUrl;
  final bool isSubscribed;

  const EventEntity({
    required this.id,
    required this.title,
    required this.description,
    required this.category,
    required this.venueName,
    required this.latitude,
    required this.longitude,
    required this.startDateTime,
    required this.endDateTime,
    required this.organizer,
    required this.status,
    this.imageUrl,
    this.isSubscribed = false,
  });

  EventEntity copyWith({
    String? id,
    String? title,
    String? description,
    EventCategory? category,
    String? venueName,
    double? latitude,
    double? longitude,
    DateTime? startDateTime,
    DateTime? endDateTime,
    String? organizer,
    EventStatus? status,
    String? imageUrl,
    bool? isSubscribed,
  }) {
    return EventEntity(
      id: id ?? this.id,
      title: title ?? this.title,
      description: description ?? this.description,
      category: category ?? this.category,
      venueName: venueName ?? this.venueName,
      latitude: latitude ?? this.latitude,
      longitude: longitude ?? this.longitude,
      startDateTime: startDateTime ?? this.startDateTime,
      endDateTime: endDateTime ?? this.endDateTime,
      organizer: organizer ?? this.organizer,
      status: status ?? this.status,
      imageUrl: imageUrl ?? this.imageUrl,
      isSubscribed: isSubscribed ?? this.isSubscribed,
    );
  }

  @override
  List<Object?> get props => [
    id,
    title,
    description,
    category,
    venueName,
    latitude,
    longitude,
    startDateTime,
    endDateTime,
    organizer,
    status,
    imageUrl,
    isSubscribed,
  ];
}
