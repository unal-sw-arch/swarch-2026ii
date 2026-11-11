import 'package:flutter/material.dart';
import '../theme/app_motion.dart';

/// Entrada tipo web: sube 12 px y aparece. Sin rebote.
/// Con "menos movimiento" activo, muestra el hijo sin animar.
class FadeUpIn extends StatefulWidget {
  final Widget child;

  /// Posicion en una lista; cada paso suma 50 ms de espera (maximo 8 pasos).
  final int index;

  const FadeUpIn({super.key, required this.child, this.index = 0});

  @override
  State<FadeUpIn> createState() => _FadeUpInState();
}

class _FadeUpInState extends State<FadeUpIn>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller;
  late final Animation<double> _curved;
  bool _started = false;

  Duration get _delay => AppMotion.stagger * widget.index.clamp(0, 8);

  @override
  void initState() {
    super.initState();
    // La espera va dentro del mismo controlador (sin Timer): 0..delay quieto, luego sube.
    _controller = AnimationController(
      vsync: this,
      duration: AppMotion.enter + _delay,
    );
    final start = _delay.inMilliseconds / _controller.duration!.inMilliseconds;
    _curved = CurvedAnimation(
      parent: _controller,
      curve: Interval(start, 1, curve: AppMotion.curve),
    );
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_started) return;
    _started = true;
    if (AppMotion.reduced(context)) {
      _controller.value = 1;
    } else {
      _controller.forward();
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _curved,
      child: widget.child,
      builder: (context, child) => Opacity(
        opacity: _curved.value,
        child: Transform.translate(
          offset: Offset(0, (1 - _curved.value) * AppMotion.enterOffset),
          child: child,
        ),
      ),
    );
  }
}
