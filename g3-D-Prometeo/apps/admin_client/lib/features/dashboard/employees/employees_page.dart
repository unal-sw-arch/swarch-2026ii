import 'package:flutter/material.dart';

import '../../../services/admin_api.dart';
import '../halls/room_layout_models.dart';

part 'parts/employee_table.dart';
part 'parts/employee_dialog.dart';

const _ink = Color(0xFF222326);
const _muted = Color(0xFF7A7D82);
const _accent = Color(0xFF8798AC);
const _roles = ['waiter', 'kitchen', 'cashier', 'manager'];

class EmployeesPage extends StatefulWidget {
  const EmployeesPage({super.key, required this.spanish, required this.token});
  final bool spanish;
  final String token;

  @override
  State<EmployeesPage> createState() => _EmployeesPageState();
}

class _EmployeesPageState extends State<EmployeesPage> {
  List<EmployeeAccount> _employees = const [];
  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final employees = await getEmployees(widget.token);
      if (mounted) {
        setState(() {
          _employees = employees;
          _loading = false;
          _error = null;
        });
      }
    } on Object {
      if (mounted) {
        setState(() {
          _loading = false;
          _error = widget.spanish
              ? 'No se pudieron cargar los empleados.'
              : 'Employees could not be loaded.';
        });
      }
    }
  }

  Future<void> _openEditor([EmployeeAccount? employee]) async {
    final changed = await showGeneralDialog<bool>(
      context: context,
      barrierDismissible: true,
      barrierLabel: widget.spanish ? 'Cerrar' : 'Close',
      barrierColor: const Color(0x99000000),
      transitionDuration: const Duration(milliseconds: 260),
      pageBuilder: (context, animation, secondaryAnimation) => _EmployeeDialog(
        spanish: widget.spanish,
        token: widget.token,
        employee: employee,
      ),
      transitionBuilder: (context, animation, secondaryAnimation, child) =>
          FadeTransition(
            opacity: CurvedAnimation(
              parent: animation,
              curve: Curves.easeOutCubic,
              reverseCurve: Curves.easeInCubic,
            ),
            child: child,
          ),
    );
    if (changed == true) {
      setState(() => _loading = true);
      await _load();
    }
  }

  @override
  Widget build(BuildContext context) => SingleChildScrollView(
    padding: const EdgeInsets.fromLTRB(24, 28, 24, 40),
    child: Center(
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 1180),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            LayoutBuilder(
              builder: (context, constraints) {
                final compact = constraints.maxWidth < 570;
                final heading = Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      widget.spanish
                          ? 'Administrar empleados'
                          : 'Manage Employees',
                      style: const TextStyle(
                        color: _ink,
                        fontSize: 29,
                        fontWeight: FontWeight.w700,
                        letterSpacing: -.8,
                      ),
                    ),
                    const SizedBox(height: 5),
                    Text(
                      widget.spanish
                          ? 'Consulta y administra las cuentas del personal.'
                          : 'View and manage staff accounts.',
                      style: const TextStyle(color: _muted, fontSize: 13),
                    ),
                  ],
                );
                final button = FilledButton.icon(
                  key: const ValueKey('add-employee'),
                  onPressed: _openEditor,
                  icon: const Icon(Icons.add, size: 19),
                  label: Text(
                    widget.spanish ? 'Añadir empleado' : 'Add Employee',
                  ),
                  style: FilledButton.styleFrom(
                    backgroundColor: _accent,
                    padding: const EdgeInsets.symmetric(
                      horizontal: 18,
                      vertical: 15,
                    ),
                  ),
                );
                return compact
                    ? Column(
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [heading, const SizedBox(height: 18), button],
                      )
                    : Row(
                        children: [
                          Expanded(child: heading),
                          button,
                        ],
                      );
              },
            ),
            const SizedBox(height: 30),
            if (_loading)
              const Center(
                child: Padding(
                  padding: EdgeInsets.all(50),
                  child: CircularProgressIndicator(),
                ),
              )
            else if (_error != null)
              Center(
                child: Text(_error!, style: const TextStyle(color: Colors.red)),
              )
            else
              _EmployeeTable(
                spanish: widget.spanish,
                employees: _employees,
                onTap: _openEditor,
              ),
          ],
        ),
      ),
    ),
  );
}
