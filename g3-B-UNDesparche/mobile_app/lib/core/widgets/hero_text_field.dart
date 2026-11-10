import 'package:flutter/material.dart';
import '../theme/app_colors.dart';
import '../theme/app_text_styles.dart';

/// Campo de texto atómico adaptado al sistema de diseño HeroUI Mint.
/// Soporta tema claro/oscuro, bordes hairline de 1px, acento Mint en foco,
/// prefijos/sufijos personalizados y botón interactivo para limpiar texto.
class HeroTextField extends StatefulWidget {
  final TextEditingController? controller;
  final String? hintText;
  final String? labelText;
  final IconData? prefixIcon;
  final Widget? suffix;
  final Widget? suffixIcon;
  final bool showClearButton;
  final bool obscureText;
  final TextInputType keyboardType;
  final ValueChanged<String>? onChanged;
  final ValueChanged<String>? onSubmitted;
  final FormFieldValidator<String>? validator;
  final bool enabled;
  final bool autofocus;
  final int maxLines;
  final FocusNode? focusNode;
  final EdgeInsetsGeometry? contentPadding;

  const HeroTextField({
    super.key,
    this.controller,
    this.hintText,
    this.labelText,
    this.prefixIcon,
    this.suffix,
    this.suffixIcon,
    this.showClearButton = false,
    this.obscureText = false,
    this.keyboardType = TextInputType.text,
    this.onChanged,
    this.onSubmitted,
    this.validator,
    this.enabled = true,
    this.autofocus = false,
    this.maxLines = 1,
    this.focusNode,
    this.contentPadding,
  });

  /// Constructor de conveniencia para barras de búsqueda de eventos e implementos
  factory HeroTextField.search({
    Key? key,
    required TextEditingController controller,
    required String hintText,
    required ValueChanged<String> onChanged,
    VoidCallback? onClear,
  }) {
    return HeroTextField(
      key: key,
      controller: controller,
      hintText: hintText,
      prefixIcon: Icons.search,
      showClearButton: true,
      onChanged: onChanged,
    );
  }

  @override
  State<HeroTextField> createState() => _HeroTextFieldState();
}

class _HeroTextFieldState extends State<HeroTextField> {
  late final TextEditingController _effectiveController;
  bool _hasText = false;

  @override
  void initState() {
    super.initState();
    _effectiveController = widget.controller ?? TextEditingController();
    _hasText = _effectiveController.text.isNotEmpty;
    _effectiveController.addListener(_handleTextChange);
  }

  @override
  void dispose() {
    if (widget.controller == null) {
      _effectiveController.dispose();
    } else {
      _effectiveController.removeListener(_handleTextChange);
    }
    super.dispose();
  }

  void _handleTextChange() {
    final hasText = _effectiveController.text.isNotEmpty;
    if (_hasText != hasText) {
      setState(() => _hasText = hasText);
    }
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    final bgColor = isDark ? AppColors.darkSurface : AppColors.lightSurface;
    final borderColor = isDark ? AppColors.darkBorder : AppColors.lightBorder;
    final textStyle = context.bodyMedium;
    final hintStyle = context.bodyMedium.copyWith(
      color: isDark ? AppColors.darkTextSecondary : AppColors.lightTextSecondary,
    );

    Widget? effectiveSuffixIcon = widget.suffixIcon;
    if (widget.showClearButton && _hasText) {
      effectiveSuffixIcon = IconButton(
        icon: const Icon(Icons.clear, size: 18),
        color: isDark ? AppColors.darkTextSecondary : AppColors.lightTextSecondary,
        tooltip: 'Limpiar texto',
        onPressed: () {
          _effectiveController.clear();
          widget.onChanged?.call('');
        },
      );
    }

    return TextFormField(
      controller: _effectiveController,
      focusNode: widget.focusNode,
      obscureText: widget.obscureText,
      keyboardType: widget.keyboardType,
      enabled: widget.enabled,
      autofocus: widget.autofocus,
      maxLines: widget.maxLines,
      style: textStyle,
      onChanged: widget.onChanged,
      onFieldSubmitted: widget.onSubmitted,
      validator: widget.validator,
      cursorColor: AppColors.mintPrimary,
      decoration: InputDecoration(
        hintText: widget.hintText,
        hintStyle: hintStyle,
        labelText: widget.labelText,
        labelStyle: hintStyle,
        filled: true,
        fillColor: bgColor,
        contentPadding: widget.contentPadding ??
            const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
        prefixIcon: widget.prefixIcon != null
            ? Icon(
                widget.prefixIcon,
                size: 20,
                color: AppColors.mintPrimary,
              )
            : null,
        suffix: widget.suffix,
        suffixIcon: effectiveSuffixIcon,
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide: BorderSide(color: borderColor, width: 1),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide: BorderSide(color: borderColor, width: 1),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide: const BorderSide(color: AppColors.mintPrimary, width: 1.5),
        ),
        errorBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide: const BorderSide(color: AppColors.danger, width: 1),
        ),
        focusedErrorBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide: const BorderSide(color: AppColors.danger, width: 1.5),
        ),
      ),
    );
  }
}
