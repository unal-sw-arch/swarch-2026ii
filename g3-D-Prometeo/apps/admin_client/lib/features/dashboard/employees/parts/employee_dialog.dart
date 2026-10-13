part of '../employees_page.dart';

class _EmployeeDialog extends StatefulWidget {
  const _EmployeeDialog({
    required this.spanish,
    required this.token,
    this.employee,
  });
  final bool spanish;
  final String token;
  final EmployeeAccount? employee;
  @override
  State<_EmployeeDialog> createState() => _EmployeeDialogState();
}

class _EmployeeDialogState extends State<_EmployeeDialog> {
  late final TextEditingController _name = TextEditingController(
    text: widget.employee?.fullName,
  );
  late final TextEditingController _username = TextEditingController(
    text: widget.employee?.username,
  );
  final _password = TextEditingController();
  late String _role = widget.employee?.role ?? 'waiter';
  late final Set<int> _selectedRoomIds = {...?widget.employee?.roomIds};
  List<RoomSummary> _rooms = const [];
  bool _loadingRooms = true;
  String? _roomsError;
  bool _busy = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _loadRooms();
  }

  Future<void> _loadRooms() async {
    if (mounted) {
      setState(() {
        _loadingRooms = true;
        _roomsError = null;
      });
    }
    try {
      final rooms = await getRooms(widget.token);
      if (mounted) {
        setState(() {
          _rooms = rooms;
          _loadingRooms = false;
        });
      }
    } on Object {
      if (mounted) {
        setState(() {
          _loadingRooms = false;
          _roomsError = widget.spanish
              ? 'No se pudieron cargar los salones.'
              : 'Rooms could not be loaded.';
        });
      }
    }
  }

  Future<void> _save() async {
    if (_busy) return;
    if (_loadingRooms || _roomsError != null) {
      setState(
        () => _error = widget.spanish
            ? 'Espera o reintenta la carga de salones.'
            : 'Wait for or retry the room list.',
      );
      return;
    }
    if (widget.employee == null &&
        (_name.text.trim().length < 2 ||
            _username.text.trim().length < 3 ||
            _password.text.length < 12)) {
      setState(
        () => _error = widget.spanish
            ? 'Completa los campos; la contraseña requiere 12 caracteres.'
            : 'Complete all fields; the password requires 12 characters.',
      );
      return;
    }
    if (widget.employee != null &&
        _password.text.isNotEmpty &&
        _password.text.length < 12) {
      setState(
        () => _error = widget.spanish
            ? 'La contraseña requiere 12 caracteres.'
            : 'The password requires 12 characters.',
      );
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      if (widget.employee == null) {
        await createEmployee(
          token: widget.token,
          fullName: _name.text,
          username: _username.text,
          password: _password.text,
          role: _role,
          roomIds: _selectedRoomIds.toList()..sort(),
        );
      } else {
        await updateEmployee(
          token: widget.token,
          id: widget.employee!.id,
          role: _role,
          password: _password.text,
          roomIds: _selectedRoomIds.toList()..sort(),
        );
      }
      if (mounted) {
        Navigator.pop(context, true);
      }
    } on Object catch (error) {
      if (mounted) {
        setState(() {
          _busy = false;
          _error = error.toString().contains('USERNAME_TAKEN')
              ? (widget.spanish
                    ? 'Ese usuario ya existe.'
                    : 'That username already exists.')
              : (widget.spanish
                    ? 'No se pudieron guardar los cambios.'
                    : 'Changes could not be saved.');
        });
      }
    }
  }

  Future<void> _delete() async {
    setState(() => _busy = true);
    try {
      await deleteEmployee(token: widget.token, id: widget.employee!.id);
      if (mounted) {
        Navigator.pop(context, true);
      }
    } on Object {
      if (mounted) {
        setState(() {
          _busy = false;
          _error = widget.spanish
              ? 'No se pudo eliminar el empleado.'
              : 'The employee could not be deleted.';
        });
      }
    }
  }

  @override
  void dispose() {
    _name.dispose();
    _username.dispose();
    _password.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Center(
    child: SingleChildScrollView(
      padding: const EdgeInsets.all(20),
      child: Material(
        color: Colors.white,
        elevation: 24,
        borderRadius: BorderRadius.circular(16),
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 480),
          child: Padding(
            padding: const EdgeInsets.all(26),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Row(
                  children: [
                    Expanded(
                      child: Text(
                        widget.employee == null
                            ? (widget.spanish
                                  ? 'Añadir empleado'
                                  : 'Add Employee')
                            : (widget.spanish
                                  ? 'Editar empleado'
                                  : 'Edit Employee'),
                        style: const TextStyle(
                          color: _ink,
                          fontSize: 23,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    ),
                    IconButton(
                      key: const ValueKey('close-employee-dialog'),
                      onPressed: _busy ? null : () => Navigator.pop(context),
                      icon: const Icon(Icons.close_rounded),
                    ),
                  ],
                ),
                const SizedBox(height: 20),
                if (widget.employee == null) ...[
                  TextField(
                    key: const ValueKey('employee-name'),
                    controller: _name,
                    decoration: InputDecoration(
                      labelText: widget.spanish
                          ? 'Nombre completo'
                          : 'Full Name',
                    ),
                  ),
                  const SizedBox(height: 13),
                  TextField(
                    key: const ValueKey('employee-username'),
                    controller: _username,
                    decoration: InputDecoration(
                      labelText: widget.spanish ? 'Usuario' : 'Username',
                    ),
                  ),
                  const SizedBox(height: 13),
                ] else
                  Text(
                    '${widget.employee!.fullName} · ${widget.employee!.username}',
                    style: const TextStyle(color: _muted),
                  ),
                const SizedBox(height: 13),
                DropdownButtonFormField<String>(
                  initialValue: _role,
                  decoration: InputDecoration(
                    labelText: widget.spanish ? 'Rol' : 'Role',
                  ),
                  items: _roles
                      .map(
                        (role) =>
                            DropdownMenuItem(value: role, child: Text(role)),
                      )
                      .toList(),
                  onChanged: _busy
                      ? null
                      : (value) => setState(() => _role = value!),
                ),
                const SizedBox(height: 13),
                TextField(
                  key: const ValueKey('employee-password'),
                  controller: _password,
                  obscureText: true,
                  decoration: InputDecoration(
                    labelText: widget.employee == null
                        ? (widget.spanish ? 'Contraseña' : 'Password')
                        : (widget.spanish
                              ? 'Nueva contraseña (opcional)'
                              : 'New password (optional)'),
                  ),
                ),
                const SizedBox(height: 20),
                Text(
                  widget.spanish ? 'Salones asignados' : 'Assigned rooms',
                  style: const TextStyle(
                    color: _ink,
                    fontSize: 13,
                    fontWeight: FontWeight.w600,
                  ),
                ),
                const SizedBox(height: 9),
                if (_loadingRooms)
                  const Align(
                    alignment: Alignment.centerLeft,
                    child: SizedBox.square(
                      dimension: 20,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    ),
                  )
                else if (_roomsError != null)
                  Row(
                    children: [
                      Expanded(
                        child: Text(
                          _roomsError!,
                          style: const TextStyle(
                            color: Color(0xFFC94E4E),
                            fontSize: 12,
                          ),
                        ),
                      ),
                      TextButton.icon(
                        key: const ValueKey('retry-employee-rooms'),
                        onPressed: _loadRooms,
                        icon: const Icon(Icons.refresh, size: 17),
                        label: Text(widget.spanish ? 'Reintentar' : 'Retry'),
                      ),
                    ],
                  )
                else if (_rooms.isEmpty)
                  Text(
                    widget.spanish
                        ? 'Todavía no existen salones. El empleado puede quedar sin asignación.'
                        : 'There are no rooms yet. The employee can remain unassigned.',
                    style: const TextStyle(color: _muted, fontSize: 12),
                  )
                else
                  Wrap(
                    spacing: 8,
                    runSpacing: 7,
                    children: [
                      for (final room in _rooms)
                        FilterChip(
                          key: ValueKey('employee-room-${room.id}'),
                          label: Text(room.name),
                          selected: _selectedRoomIds.contains(room.id),
                          onSelected: _busy
                              ? null
                              : (selected) => setState(() {
                                  if (selected) {
                                    _selectedRoomIds.add(room.id);
                                  } else {
                                    _selectedRoomIds.remove(room.id);
                                  }
                                }),
                        ),
                    ],
                  ),
                if (_error != null) ...[
                  const SizedBox(height: 13),
                  Text(
                    _error!,
                    textAlign: TextAlign.center,
                    style: const TextStyle(color: Colors.red),
                  ),
                ],
                const SizedBox(height: 22),
                Row(
                  children: [
                    if (widget.employee != null)
                      TextButton.icon(
                        key: const ValueKey('delete-employee'),
                        onPressed: _busy ? null : _delete,
                        style: TextButton.styleFrom(
                          foregroundColor: Colors.red,
                        ),
                        icon: const Icon(Icons.delete_outline),
                        label: Text(widget.spanish ? 'Eliminar' : 'Delete'),
                      ),
                    const Spacer(),
                    FilledButton(
                      key: const ValueKey('save-employee'),
                      onPressed: _busy ? null : _save,
                      style: FilledButton.styleFrom(backgroundColor: _accent),
                      child: _busy
                          ? const SizedBox.square(
                              dimension: 18,
                              child: CircularProgressIndicator(
                                strokeWidth: 2,
                                color: Colors.white,
                              ),
                            )
                          : Text(widget.spanish ? 'Guardar' : 'Save'),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
      ),
    ),
  );
}
