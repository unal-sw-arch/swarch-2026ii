import 'package:flutter/material.dart';
import '../../domain/entities/event_entity.dart';

/// Nombres en español de las categorías y estados de evento, como en frontend_web.
String eventCategoryLabel(EventCategory category) {
  switch (category) {
    case EventCategory.sports:
      return 'Deportes';
    case EventCategory.culture:
      return 'Cultura';
    case EventCategory.academic:
      return 'Académico';
    case EventCategory.welfare:
      return 'Bienestar';
    case EventCategory.social:
      return 'Social';
  }
}

IconData eventCategoryIcon(EventCategory category) {
  switch (category) {
    case EventCategory.sports:
      return Icons.sports_soccer;
    case EventCategory.culture:
      return Icons.theater_comedy_outlined;
    case EventCategory.academic:
      return Icons.school_outlined;
    case EventCategory.welfare:
      return Icons.favorite_border;
    case EventCategory.social:
      return Icons.groups_outlined;
  }
}

String eventStatusLabel(EventStatus status) {
  switch (status) {
    case EventStatus.scheduled:
      return 'Programado';
    case EventStatus.inProgress:
      return 'En curso';
    case EventStatus.completed:
      return 'Finalizado';
    case EventStatus.cancelled:
      return 'Cancelado';
  }
}
