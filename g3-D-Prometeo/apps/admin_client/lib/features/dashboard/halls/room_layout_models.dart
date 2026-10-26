class RoomTableModel {
  const RoomTableModel({
    required this.id,
    required this.identifier,
    required this.x,
    required this.y,
    required this.width,
    required this.height,
    this.rotation = 0,
    this.status = 'available',
  });

  final int id;
  final String identifier;
  final double x;
  final double y;
  final double width;
  final double height;
  final double rotation;
  final String status;

  RoomTableModel copyWith({
    int? id,
    String? identifier,
    double? x,
    double? y,
    double? width,
    double? height,
    double? rotation,
    String? status,
  }) => RoomTableModel(
    id: id ?? this.id,
    identifier: identifier ?? this.identifier,
    x: x ?? this.x,
    y: y ?? this.y,
    width: width ?? this.width,
    height: height ?? this.height,
    rotation: rotation ?? this.rotation,
    status: status ?? this.status,
  );

  factory RoomTableModel.fromJson(Map<String, dynamic> json) => RoomTableModel(
    id: json['id'] as int,
    identifier: json['identifier'] as String,
    x: (json['x'] as num).toDouble(),
    y: (json['y'] as num).toDouble(),
    width: (json['width'] as num).toDouble(),
    height: (json['height'] as num).toDouble(),
    rotation: (json['rotation'] as num?)?.toDouble() ?? 0,
    status: json['status'] as String? ?? 'available',
  );

  Map<String, Object> toJson() => {
    'id': id,
    'identifier': identifier,
    'x': x,
    'y': y,
    'width': width,
    'height': height,
    'rotation': rotation,
    'status': status,
  };
}

class RoomSummary {
  const RoomSummary({
    required this.id,
    required this.name,
    required this.tableCount,
    required this.orderCount,
    required this.totalSales,
    required this.averageSale,
  });

  final int id;
  final String name;
  final int tableCount;
  final int orderCount;
  final double totalSales;
  final double averageSale;

  factory RoomSummary.fromJson(Map<String, dynamic> json) => RoomSummary(
    id: json['id'] as int,
    name: json['name'] as String,
    tableCount: json['tableCount'] as int,
    orderCount: json['orderCount'] as int? ?? 0,
    totalSales: (json['totalSales'] as num?)?.toDouble() ?? 0,
    averageSale: (json['averageSale'] as num?)?.toDouble() ?? 0,
  );
}

class RoomWallModel {
  const RoomWallModel({
    required this.id,
    required this.x,
    required this.y,
    required this.width,
    required this.height,
    required this.rotation,
  });

  final int id;
  final double x;
  final double y;
  final double width;
  final double height;
  final double rotation;

  RoomWallModel copyWith({
    double? x,
    double? y,
    double? width,
    double? height,
    double? rotation,
  }) => RoomWallModel(
    id: id,
    x: x ?? this.x,
    y: y ?? this.y,
    width: width ?? this.width,
    height: height ?? this.height,
    rotation: rotation ?? this.rotation,
  );

  factory RoomWallModel.fromJson(Map<String, dynamic> json) => RoomWallModel(
    id: json['id'] as int,
    x: (json['x'] as num).toDouble(),
    y: (json['y'] as num).toDouble(),
    width: (json['width'] as num).toDouble(),
    height: (json['height'] as num).toDouble(),
    rotation: (json['rotation'] as num).toDouble(),
  );

  Map<String, Object> toJson() => {
    'id': id,
    'x': x,
    'y': y,
    'width': width,
    'height': height,
    'rotation': rotation,
  };
}

class RoomTableGroupModel {
  const RoomTableGroupModel({
    required this.id,
    required this.identifier,
    required this.tableIds,
  });

  final int id;
  final String identifier;
  final List<int> tableIds;

  factory RoomTableGroupModel.fromJson(Map<String, dynamic> json) =>
      RoomTableGroupModel(
        id: json['id'] as int,
        identifier: json['identifier'] as String,
        tableIds: (json['tableIds'] as List).cast<int>(),
      );

  Map<String, Object> toJson() => {
    'id': id,
    'identifier': identifier,
    'tableIds': tableIds,
  };
}

class RoomLayoutModel {
  const RoomLayoutModel({
    required this.roomId,
    required this.roomName,
    required this.tables,
    required this.walls,
    required this.groups,
  });

  final int roomId;
  final String roomName;
  final List<RoomTableModel> tables;
  final List<RoomWallModel> walls;
  final List<RoomTableGroupModel> groups;

  factory RoomLayoutModel.fromJson(Map<String, dynamic> json) {
    final room = json['room'] as Map<String, dynamic>;
    return RoomLayoutModel(
      roomId: room['id'] as int,
      roomName: room['name'] as String,
      tables: (json['tables'] as List)
          .map((item) => RoomTableModel.fromJson(item as Map<String, dynamic>))
          .toList(),
      walls: (json['walls'] as List)
          .map((item) => RoomWallModel.fromJson(item as Map<String, dynamic>))
          .toList(),
      groups: (json['groups'] as List)
          .map(
            (item) =>
                RoomTableGroupModel.fromJson(item as Map<String, dynamic>),
          )
          .toList(),
    );
  }

  Map<String, Object> toJson() => {
    'tables': tables.map((table) => table.toJson()).toList(),
    'walls': walls.map((wall) => wall.toJson()).toList(),
    'groups': groups.map((group) => group.toJson()).toList(),
  };
}
