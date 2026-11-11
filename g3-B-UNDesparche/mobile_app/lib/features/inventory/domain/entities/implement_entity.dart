import 'package:equatable/equatable.dart';

enum ImplementStatus {
  available,    // Disponible para reserva
  reserved,     // Reservado (ventana de 10 min activa)
  borrowed,     // Prestado / En uso
  unavailable,  // Fuera de servicio o en reparacion
}

enum ImplementCategory {
  balls,         // Balones de futbol, baloncesto, voleibol
  rackets,       // Ping pong, tenis, badminton
  boardGames,    // Ajedrez, Jenga, Scrabble
  leisure,       // Cuerdas, conos, frisbees
}

/// Entidad que representa un implemento deportivo o recreativo de una facultad UNAL
class ImplementEntity extends Equatable {
  final String id;
  final String name;
  final ImplementCategory category;
  final String facultyName;
  final String pickupLocation;
  final ImplementStatus status;
  final int usageTimeMinutes;
  final String? imageUrl;
  final String description;

  const ImplementEntity({
    required this.id,
    required this.name,
    required this.category,
    required this.facultyName,
    required this.pickupLocation,
    required this.status,
    required this.usageTimeMinutes,
    this.imageUrl,
    required this.description,
  });

  ImplementEntity copyWith({
    String? id,
    String? name,
    ImplementCategory? category,
    String? facultyName,
    String? pickupLocation,
    ImplementStatus? status,
    int? usageTimeMinutes,
    String? imageUrl,
    String? description,
  }) {
    return ImplementEntity(
      id: id ?? this.id,
      name: name ?? this.name,
      category: category ?? this.category,
      facultyName: facultyName ?? this.facultyName,
      pickupLocation: pickupLocation ?? this.pickupLocation,
      status: status ?? this.status,
      usageTimeMinutes: usageTimeMinutes ?? this.usageTimeMinutes,
      imageUrl: imageUrl ?? this.imageUrl,
      description: description ?? this.description,
    );
  }

  @override
  List<Object?> get props => [
    id,
    name,
    category,
    facultyName,
    pickupLocation,
    status,
    usageTimeMinutes,
    imageUrl,
    description,
  ];
}
