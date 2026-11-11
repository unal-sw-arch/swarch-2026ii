import 'package:flutter/material.dart';
import '../theme/app_motion.dart';

/// Encoge el hijo a 0.97 mientras el dedo lo toca. No cambia la zona de toque.
class PressableScale extends StatefulWidget {
  final Widget child;
  final bool enabled;

  const PressableScale({super.key, required this.child, this.enabled = true});

  @override
  State<PressableScale> createState() => _PressableScaleState();
}

class _PressableScaleState extends State<PressableScale> {
  bool _pressed = false;

  void _set(bool v) {
    // El dedo puede soltarse cuando la tarjeta ya salio de la lista y fue descartada.
    if (!mounted || !widget.enabled || _pressed == v) return;
    setState(() => _pressed = v);
  }

  @override
  Widget build(BuildContext context) {
    final scale = _pressed && !AppMotion.reduced(context)
        ? AppMotion.pressScale
        : 1.0;
    return Listener(
      behavior: HitTestBehavior.translucent,
      onPointerDown: (_) => _set(true),
      onPointerUp: (_) => _set(false),
      onPointerCancel: (_) => _set(false),
      child: AnimatedScale(
        scale: scale,
        duration: AppMotion.fast,
        curve: Curves.easeOutCubic,
        child: widget.child,
      ),
    );
  }
}
