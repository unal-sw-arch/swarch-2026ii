import 'package:flutter/material.dart';
import 'package:flutter_svg/flutter_svg.dart';

import '../auth/admin_auth_view.dart';
import '../../services/admin_api.dart';
import '../../services/admin_session_store.dart';
import '../auth/login_page.dart';
import '../../services/cloud_api.dart';

/// Punto único para las comprobaciones que deben terminar antes del login.
/// Las verificaciones reales pueden añadirse a esta función sin cambiar la UI.
Future<void> runInitialChecks() async {
  await Future.wait([
    verifyCloudApiIsRunning(),
    // Mantiene visible la animación el tiempo suficiente para evitar un destello.
    Future<void>.delayed(const Duration(milliseconds: 1400)),
  ]);
}

Future<AdminSession?> restoreStoredAdminSession() async {
  try {
    final token = await readAdminSessionToken();
    if (token == null) return null;
    final session = await verifyAdminSession(token);
    if (session == null) await clearAdminSessionToken();
    return session;
  } on Object {
    await clearAdminSessionToken();
    return null;
  }
}

class SplashScreen extends StatefulWidget {
  const SplashScreen({
    super.key,
    required this.strings,
    this.initialChecks = runInitialChecks,
    this.restoreSession = restoreStoredAdminSession,
  });

  final AppStrings strings;
  final Future<void> Function() initialChecks;
  final Future<AdminSession?> Function() restoreSession;

  @override
  State<SplashScreen> createState() => _SplashScreenState();
}

class _SplashScreenState extends State<SplashScreen>
    with SingleTickerProviderStateMixin {
  late final AnimationController _pulseController;
  late final Animation<double> _pulse;
  _SplashState _state = _SplashState.loading;
  AdminSession? _session;

  @override
  void initState() {
    super.initState();
    _pulseController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1050),
    )..repeat(reverse: true);
    _pulse = Tween<double>(begin: .78, end: 1.16).animate(
      CurvedAnimation(parent: _pulseController, curve: Curves.easeInOutCubic),
    );
    _initialize();
  }

  Future<void> _initialize() async {
    try {
      await widget.initialChecks();
      _session = await widget.restoreSession();
      if (!mounted) return;
      _pulseController.stop();
      setState(() => _state = _SplashState.login);
    } on Object {
      if (!mounted) return;
      _pulseController.stop();
      setState(() => _state = _SplashState.notFound);
    }
  }

  void _retry() {
    setState(() => _state = _SplashState.loading);
    _pulseController.repeat(reverse: true);
    _initialize();
  }

  @override
  void dispose() {
    _pulseController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedSwitcher(
      duration: const Duration(milliseconds: 560),
      reverseDuration: const Duration(milliseconds: 560),
      switchInCurve: Curves.easeInOut,
      switchOutCurve: Curves.easeInOut,
      // La pantalla nueva permanece completamente visible debajo mientras la
      // anterior se desvanece. Así nunca aparece el fondo negro del compositor.
      layoutBuilder: (currentChild, previousChildren) => Stack(
        fit: StackFit.expand,
        children: [?currentChild, ...previousChildren],
      ),
      transitionBuilder: (child, animation) =>
          _FadeOutTransition(animation: animation, child: child),
      child: switch (_state) {
        _SplashState.login => AdminAuthView(
          key: const ValueKey('login-screen'),
          strings: widget.strings,
          initialSession: _session,
        ),
        _SplashState.notFound => _NotFoundScreen(
          key: const ValueKey('not-found-screen'),
          strings: widget.strings,
          onRetry: _retry,
        ),
        _SplashState.loading => Scaffold(
          key: const ValueKey('splash-screen'),
          body: Center(
            child: AnimatedBuilder(
              animation: _pulse,
              builder: (context, child) => Transform.scale(
                scale: _pulse.value,
                child: Opacity(
                  opacity: .68 + (((_pulse.value - .78) / .38) * .32),
                  child: child,
                ),
              ),
              child: SizedBox.square(
                dimension: 150,
                child: SvgPicture.asset(
                  'assets/logo.svg',
                  key: const ValueKey('splash-logo'),
                  fit: BoxFit.contain,
                ),
              ),
            ),
          ),
        ),
      },
    );
  }
}

class _FadeOutTransition extends StatelessWidget {
  const _FadeOutTransition({required this.animation, required this.child});

  final Animation<double> animation;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: animation,
      child: child,
      builder: (context, child) {
        if (animation.status != AnimationStatus.reverse) return child!;
        return Opacity(opacity: animation.value, child: child);
      },
    );
  }
}

enum _SplashState { loading, login, notFound }

class _NotFoundScreen extends StatelessWidget {
  const _NotFoundScreen({
    super.key,
    required this.strings,
    required this.onRetry,
  });

  final AppStrings strings;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: Padding(
            padding: const EdgeInsets.all(32),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Text(
                  '404',
                  style: TextStyle(
                    color: Color(0xFF8798AC),
                    fontSize: 72,
                    height: 1,
                    fontWeight: FontWeight.w800,
                    letterSpacing: -3,
                  ),
                ),
                const SizedBox(height: 18),
                Text(
                  strings.cloudUnavailable,
                  textAlign: TextAlign.center,
                  style: const TextStyle(
                    color: Color(0xFF202020),
                    fontSize: 24,
                    fontWeight: FontWeight.w700,
                  ),
                ),
                const SizedBox(height: 8),
                Text(
                  strings.cloudUnavailableDetail,
                  textAlign: TextAlign.center,
                  style: const TextStyle(
                    color: Color(0xFF6D7075),
                    fontSize: 15,
                  ),
                ),
                const SizedBox(height: 28),
                FilledButton.icon(
                  key: const ValueKey('retry-cloud-check'),
                  onPressed: onRetry,
                  icon: const Icon(Icons.refresh, size: 19),
                  label: Text(strings.retry),
                  style: FilledButton.styleFrom(
                    backgroundColor: const Color(0xFF8798AC),
                    padding: const EdgeInsets.symmetric(
                      horizontal: 24,
                      vertical: 14,
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
