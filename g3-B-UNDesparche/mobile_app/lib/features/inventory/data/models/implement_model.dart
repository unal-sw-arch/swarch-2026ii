import '../../../../core/utils/json_coerce.dart';
import '../../domain/entities/implement_entity.dart';

/// Modelo de datos (DTO) para la serialización y deserialización de implementos deportivos/recreativos
class ImplementModel extends ImplementEntity {
  const ImplementModel({
    required super.id,
    required super.name,
    required super.category,
    required super.facultyName,
    required super.pickupLocation,
    required super.status,
    required super.usageTimeMinutes,
    super.imageUrl,
    required super.description,
  });

  factory ImplementModel.fromJson(Map<String, dynamic> json) {
    return ImplementModel(
      id: asString(json['id']),
      name: asString(json['name']),
      category: _parseCategory(json['category']),
      facultyName: asString(json['faculty'] ?? json['faculty_name'], 'Ingenieria'),
      pickupLocation: asString(json['pickup_location'] ?? json['location'], 'Taquilla Bienestar'),
      status: _parseStatus(json['state'] ?? json['status']),
      usageTimeMinutes: asInt(json['usage_time_minutes'], 120),
      imageUrl: asStringOrNull(json['image_url'] ?? json['image']),
      description: asString(json['description']),
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'name': name,
      'category': toBackendCategory(category),
      'faculty': facultyName,
      'pickup_location': pickupLocation,
      'state': status.name,
      'usage_time_minutes': usageTimeMinutes,
      'image_url': imageUrl,
      'description': description,
    };
  }

  static ImplementCategory _parseCategory(dynamic cat) {
    final str = cat?.toString().toLowerCase() ?? '';
    if (str == 'bal' || str.contains('ball') || str.contains('balon')) return ImplementCategory.balls;
    if (str == 'raq' || str.contains('racket') || str.contains('raqueta') || str.contains('tenis')) return ImplementCategory.rackets;
    if (str == 'jum' || str == 'mes' || str.contains('board') || str.contains('juego') || str.contains('mesa')) return ImplementCategory.boardGames;
    return ImplementCategory.leisure;
  }

  static String toBackendCategory(ImplementCategory cat) {
    switch (cat) {
      case ImplementCategory.balls:
        return 'BAL';
      case ImplementCategory.rackets:
        return 'RAQ';
      case ImplementCategory.boardGames:
        return 'JUM';
      case ImplementCategory.leisure:
        return 'JUR';
    }
  }

  static String? toBackendFaculty(String? faculty) {
    if (faculty == null || faculty == 'Todas' || faculty.isEmpty) return null;
    final lower = faculty.toLowerCase();
    if (lower.contains('ing')) return 'ING';
    if (lower.contains('med')) return 'MED';
    if (lower.contains('art')) return 'ART';
    if (lower.contains('agr')) return 'CIA';
    if (lower.contains('eco')) return 'CIE';
    if (lower.contains('hum')) return 'CHS';
    if (lower.contains('cien')) return 'CCS';
    if (lower.contains('der')) return 'DER';
    if (lower.contains('enf')) return 'ENF';
    if (lower.contains('vet') || lower.contains('mvz')) return 'MVZ';
    return faculty;
  }

  static ImplementStatus _parseStatus(dynamic stat) {
    final str = stat?.toString().toLowerCase() ?? '';
    if (str == 'res' || str.contains('reserv')) return ImplementStatus.reserved;
    if (str == 'pre' || str.contains('prest') || str.contains('borrow') || str.contains('ocup')) return ImplementStatus.borrowed;
    if (str == 'nds' || str.contains('unavail') || str.contains('no_disponible') || str.contains('dañado')) return ImplementStatus.unavailable;
    return ImplementStatus.available;
  }
}
