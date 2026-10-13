import 'package:flutter/material.dart';

import '../dashboard/admin_dashboard.dart';
import '../../services/admin_api.dart';
import '../../services/admin_session_store.dart';
import 'login_page.dart';
import 'registration_page.dart';

class AdminAuthView extends StatefulWidget {
  const AdminAuthView({
    super.key,
    required this.strings,
    this.initialRegistration = false,
    this.initialSession,
  });

  final AppStrings strings;
  final bool initialRegistration;
  final AdminSession? initialSession;

  @override
  State<AdminAuthView> createState() => _AdminAuthViewState();
}

class _AdminAuthViewState extends State<AdminAuthView> {
  late bool _showRegistration;
  AdminSession? _session;

  @override
  void initState() {
    super.initState();
    _showRegistration = widget.initialRegistration;
    _session = widget.initialSession;
  }

  Future<void> _signIn(String username, String password) async {
    final session = await loginAdmin(username: username, password: password);
    await saveAdminSessionToken(session.token);
    if (mounted) setState(() => _session = session);
  }

  Future<void> _logout() async {
    final token = _session?.token;
    await clearAdminSessionToken();
    if (token != null) await logoutAdmin(token);
    if (mounted) setState(() => _session = null);
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedSwitcher(
      duration: const Duration(milliseconds: 420),
      reverseDuration: const Duration(milliseconds: 420),
      switchInCurve: Curves.easeInOut,
      switchOutCurve: Curves.easeInOut,
      layoutBuilder: (currentChild, previousChildren) => Stack(
        fit: StackFit.expand,
        children: [...previousChildren, ?currentChild],
      ),
      transitionBuilder: (child, animation) =>
          FadeTransition(opacity: animation, child: child),
      child: _session != null
          ? AdminDashboard(
              key: const ValueKey('admin-dashboard'),
              strings: widget.strings,
              adminName: _session!.admin.username,
              sessionToken: _session!.token,
              onLogout: _logout,
            )
          : _showRegistration
          ? RegistrationPage(
              key: const ValueKey('registration-page'),
              strings: widget.strings,
              onSignIn: () => setState(() => _showRegistration = false),
            )
          : LoginPage(
              key: const ValueKey('login-page'),
              strings: widget.strings,
              onRegister: () => setState(() => _showRegistration = true),
              onSignIn: _signIn,
            ),
    );
  }
}
