class ClientRoomSummary {
  const ClientRoomSummary({
    required this.id,
    required this.name,
    required this.tableCount,
  });

  factory ClientRoomSummary.fromJson(Map<String, dynamic> json) =>
      ClientRoomSummary(
        id: json['id'] as int,
        name: json['name'] as String,
        tableCount: json['tableCount'] as int,
      );

  final int id;
  final String name;
  final int tableCount;
}

class LiveRoomLayout {
  const LiveRoomLayout({
    required this.roomId,
    required this.roomName,
    required this.tables,
    required this.walls,
    required this.groups,
  });

  factory LiveRoomLayout.fromJson(Map<String, dynamic> json) {
    final room = json['room'] as Map<String, dynamic>;
    return LiveRoomLayout(
      roomId: room['id'] as int,
      roomName: room['name'] as String,
      tables: (json['tables'] as List)
          .map((value) => LiveRoomTable.fromJson(value as Map<String, dynamic>))
          .toList(),
      walls: (json['walls'] as List)
          .map((value) => LiveRoomWall.fromJson(value as Map<String, dynamic>))
          .toList(),
      groups: (json['groups'] as List)
          .map(
            (value) => LiveTableGroup.fromJson(value as Map<String, dynamic>),
          )
          .toList(),
    );
  }

  final int roomId;
  final String roomName;
  final List<LiveRoomTable> tables;
  final List<LiveRoomWall> walls;
  final List<LiveTableGroup> groups;

  Map<String, dynamic> toLiveJson() => {
    'tables': [
      for (final table in tables) {'id': table.id, 'x': table.x, 'y': table.y},
    ],
    'groups': [for (final group in groups) group.toJson()],
  };
}

class LiveRoomTable {
  const LiveRoomTable({
    required this.id,
    required this.identifier,
    required this.x,
    required this.y,
    required this.width,
    required this.height,
    required this.rotation,
    this.status = 'available',
  });

  factory LiveRoomTable.fromJson(Map<String, dynamic> json) => LiveRoomTable(
    id: json['id'] as int,
    identifier: json['identifier'] as String,
    x: (json['x'] as num).toDouble(),
    y: (json['y'] as num).toDouble(),
    width: (json['width'] as num).toDouble(),
    height: (json['height'] as num).toDouble(),
    rotation: (json['rotation'] as num?)?.toDouble() ?? 0,
    status: json['status'] as String? ?? 'available',
  );

  final int id;
  final String identifier;
  final double x;
  final double y;
  final double width;
  final double height;
  final double rotation;
  final String status;

  LiveRoomTable copyWith({double? x, double? y, String? status}) =>
      LiveRoomTable(
        id: id,
        identifier: identifier,
        x: x ?? this.x,
        y: y ?? this.y,
        width: width,
        height: height,
        rotation: rotation,
        status: status ?? this.status,
      );
}

class LiveRoomWall {
  const LiveRoomWall({
    required this.id,
    required this.x,
    required this.y,
    required this.width,
    required this.height,
    required this.rotation,
  });

  factory LiveRoomWall.fromJson(Map<String, dynamic> json) => LiveRoomWall(
    id: json['id'] as int,
    x: (json['x'] as num).toDouble(),
    y: (json['y'] as num).toDouble(),
    width: (json['width'] as num).toDouble(),
    height: (json['height'] as num).toDouble(),
    rotation: (json['rotation'] as num).toDouble(),
  );

  final int id;
  final double x;
  final double y;
  final double width;
  final double height;
  final double rotation;
}

class LiveTableGroup {
  const LiveTableGroup({
    required this.id,
    required this.tableIds,
    this.identifier,
  });

  factory LiveTableGroup.fromJson(Map<String, dynamic> json) => LiveTableGroup(
    id: json['id'] as int,
    identifier: json['identifier'] as String?,
    tableIds: (json['tableIds'] as List).cast<int>(),
  );

  final int id;
  final String? identifier;
  final List<int> tableIds;

  Map<String, dynamic> toJson() => {
    'id': id,
    'identifier': identifier,
    'tableIds': tableIds,
  };
}
