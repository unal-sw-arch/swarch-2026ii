part of '../employees_page.dart';

class _EmployeeTable extends StatelessWidget {
  const _EmployeeTable({
    required this.spanish,
    required this.employees,
    required this.onTap,
  });
  final bool spanish;
  final List<EmployeeAccount> employees;
  final ValueChanged<EmployeeAccount> onTap;

  @override
  Widget build(BuildContext context) {
    if (employees.isEmpty) {
      return Container(
        width: double.infinity,
        padding: const EdgeInsets.all(45),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(12),
        ),
        child: Text(
          spanish ? 'Todavía no hay empleados.' : 'There are no employees yet.',
          textAlign: TextAlign.center,
          style: const TextStyle(color: _muted),
        ),
      );
    }
    return Container(
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: const Color(0xFFE4E6E8)),
      ),
      clipBehavior: Clip.antiAlias,
      child: Column(
        children: [
          _EmployeeRow(spanish: spanish, header: true),
          for (final employee in employees)
            _EmployeeRow(
              spanish: spanish,
              employee: employee,
              onTap: () => onTap(employee),
            ),
        ],
      ),
    );
  }
}

class _EmployeeRow extends StatelessWidget {
  const _EmployeeRow({
    required this.spanish,
    this.employee,
    this.header = false,
    this.onTap,
  });
  final bool spanish;
  final EmployeeAccount? employee;
  final bool header;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final row = Padding(
      padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 17),
      child: Row(
        children: [
          Expanded(
            flex: 5,
            child: header
                ? Text(spanish ? 'Nombre' : 'Name', style: _headerStyle)
                : Row(
                    children: [
                      CircleAvatar(
                        radius: 19,
                        backgroundColor: const Color(0xFFE5EDF8),
                        child: Text(
                          _initials(employee!.fullName),
                          style: const TextStyle(color: _ink),
                        ),
                      ),
                      const SizedBox(width: 14),
                      Flexible(
                        child: Text(
                          employee!.fullName,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(color: _ink, fontSize: 13),
                        ),
                      ),
                    ],
                  ),
          ),
          Expanded(
            flex: 4,
            child: Text(
              header ? (spanish ? 'Usuario' : 'Username') : employee!.username,
              overflow: TextOverflow.ellipsis,
              style: header
                  ? _headerStyle
                  : const TextStyle(color: Color(0xFF4D5055), fontSize: 13),
            ),
          ),
          Expanded(
            flex: 3,
            child: Align(
              alignment: Alignment.centerLeft,
              child: header
                  ? Text(spanish ? 'Rol' : 'Role', style: _headerStyle)
                  : _RoleChip(role: employee!.role),
            ),
          ),
          if (!header)
            const Icon(Icons.chevron_right_rounded, color: _muted, size: 19),
        ],
      ),
    );
    return Material(
      color: header ? const Color(0xFFFAFAF9) : Colors.white,
      child: InkWell(
        onTap: onTap,
        child: Column(
          children: [
            row,
            const Divider(height: 1, color: Color(0xFFE4E6E8)),
          ],
        ),
      ),
    );
  }

  static const _headerStyle = TextStyle(
    color: Color(0xFF4D5055),
    fontSize: 11,
    fontWeight: FontWeight.w600,
    letterSpacing: .5,
  );
  String _initials(String name) => name
      .trim()
      .split(RegExp(r'\s+'))
      .take(2)
      .map((part) => part.isEmpty ? '' : part[0].toUpperCase())
      .join();
}

class _RoleChip extends StatelessWidget {
  const _RoleChip({required this.role});
  final String role;
  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 11, vertical: 5),
    decoration: BoxDecoration(
      color: const Color(0xFFE4EED8),
      borderRadius: BorderRadius.circular(20),
    ),
    child: Text(
      role,
      style: const TextStyle(color: Color(0xFF59634D), fontSize: 11),
    ),
  );
}
