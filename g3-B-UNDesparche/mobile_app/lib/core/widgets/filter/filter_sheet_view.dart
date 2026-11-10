import 'package:flutter/material.dart';
import '../../theme/app_colors.dart';
import '../../theme/app_motion.dart';
import '../../theme/app_spacing.dart';
import '../hero_solid_button.dart';
import '../pressable_scale.dart';
import 'filter_group.dart';

/// Abre la hoja de filtros. Devuelve `{idDelGrupo: claveElegida}` al pulsar el boton de abajo,
/// o null si se cierra sin aplicar.
///
/// [resultCount] calcula cuantos resultados daria la seleccion en curso; con [resultNoun]
/// (singular, plural) el boton dice "Ver 3 eventos". Sin el, dice "Aplicar filtros".
Future<Map<String, String>?> showFilterSheet(
  BuildContext context, {
  required List<FilterGroup> groups,
  String title = 'Filtros',
  int Function(Map<String, String> selection)? resultCount,
  (String, String) resultNoun = ('resultado', 'resultados'),
}) {
  return showModalBottomSheet<Map<String, String>>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    builder: (_) => _FilterSheet(
      groups: groups,
      title: title,
      resultCount: resultCount,
      resultNoun: resultNoun,
    ),
  );
}

class _FilterSheet extends StatefulWidget {
  final List<FilterGroup> groups;
  final String title;
  final int Function(Map<String, String> selection)? resultCount;
  final (String, String) resultNoun;

  const _FilterSheet({
    required this.groups,
    required this.title,
    required this.resultCount,
    required this.resultNoun,
  });

  @override
  State<_FilterSheet> createState() => _FilterSheetState();
}

class _FilterSheetState extends State<_FilterSheet> {
  late final Map<String, String> _draft = {
    for (final g in widget.groups) g.id: g.selectedKey
  };

  bool get _isClean => widget.groups.every((g) => _draft[g.id] == g.defaultKey);

  void _clear() => setState(() {
        for (final g in widget.groups) {
          _draft[g.id] = g.defaultKey;
        }
      });

  String get _applyLabel {
    final counter = widget.resultCount;
    if (counter == null) return 'Aplicar filtros';
    final n = counter(_draft);
    if (n == 0) return 'Sin resultados';
    return 'Ver $n ${n == 1 ? widget.resultNoun.$1 : widget.resultNoun.$2}';
  }

  bool get _canApply =>
      widget.resultCount == null || widget.resultCount!(_draft) > 0;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;
    final border = isDark ? AppColors.darkBorder : AppColors.lightBorder;
    final accent = isDark ? AppColors.mintLime : AppColors.mintInk;

    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        // Cabecera: titulo a la izquierda, "Limpiar" a la derecha
        Padding(
          padding: const EdgeInsets.fromLTRB(
              AppSpacing.xl, 0, AppSpacing.md, AppSpacing.sm),
          child: Row(
            children: [
              Expanded(
                  child:
                      Text(widget.title, style: theme.textTheme.headlineSmall)),
              TextButton(
                onPressed: _isClean ? null : _clear,
                style: TextButton.styleFrom(
                  foregroundColor: accent,
                  minimumSize: const Size(AppSpacing.minTap, AppSpacing.minTap),
                ),
                child: const Text('Limpiar'),
              ),
            ],
          ),
        ),

        // Grupos
        Flexible(
          child: SingleChildScrollView(
            padding: const EdgeInsets.fromLTRB(
                AppSpacing.xl, 0, AppSpacing.xl, AppSpacing.lg),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                for (var i = 0; i < widget.groups.length; i++) ...[
                  if (i > 0) const SizedBox(height: AppSpacing.xl),
                  _GroupSection(
                    group: widget.groups[i],
                    selectedKey: _draft[widget.groups[i].id]!,
                    onSelect: (key) =>
                        setState(() => _draft[widget.groups[i].id] = key),
                  ),
                ],
              ],
            ),
          ),
        ),

        // Pie fijo con el boton principal
        DecoratedBox(
          decoration:
              BoxDecoration(border: Border(top: BorderSide(color: border))),
          child: Padding(
            padding: const EdgeInsets.fromLTRB(
                AppSpacing.xl, AppSpacing.lg, AppSpacing.xl, AppSpacing.lg),
            child: HeroSolidButton(
              text: _applyLabel,
              onPressed: _canApply
                  ? () => Navigator.pop(context, Map<String, String>.of(_draft))
                  : null,
            ),
          ),
        ),
      ],
    );
  }
}

/// Titulo del grupo y sus opciones en una cuadricula de 2 columnas.
class _GroupSection extends StatelessWidget {
  final FilterGroup group;
  final String selectedKey;
  final ValueChanged<String> onSelect;

  const _GroupSection(
      {required this.group, required this.selectedKey, required this.onSelect});

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final titleColor =
        isDark ? AppColors.darkTextPrimary : AppColors.lightTextPrimary;

    return Semantics(
      container: true,
      label: group.title,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(
            group.title,
            style: TextStyle(
                fontSize: 15, fontWeight: FontWeight.w700, color: titleColor),
          ),
          const SizedBox(height: AppSpacing.md),
          LayoutBuilder(
            builder: (context, constraints) {
              const gap = AppSpacing.sm;
              final tileWidth = (constraints.maxWidth - gap) / 2;
              return Wrap(
                spacing: gap,
                runSpacing: gap,
                children: [
                  for (final option in group.options)
                    SizedBox(
                      width: tileWidth,
                      child: FilterOptionTile(
                        option: option,
                        selected: option.key == selectedKey,
                        onTap: () => onSelect(option.key),
                      ),
                    ),
                ],
              );
            },
          ),
        ],
      ),
    );
  }
}

/// Casilla de una opcion: icono y nombre. La elegida lleva tinte y borde menta.
class FilterOptionTile extends StatelessWidget {
  final FilterOption option;
  final bool selected;
  final VoidCallback onTap;

  const FilterOptionTile(
      {super.key,
      required this.option,
      required this.selected,
      required this.onTap});

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final accent = isDark ? AppColors.mintLime : AppColors.mintInk;
    final idleText =
        isDark ? AppColors.darkTextPrimary : AppColors.lightTextPrimary;
    final idleIcon =
        isDark ? AppColors.darkTextSecondary : AppColors.lightTextMuted;
    final idleBg =
        isDark ? AppColors.darkBackground : AppColors.lightBackground;
    final idleBorder = isDark ? AppColors.darkBorder : AppColors.lightBorder;

    return Semantics(
      button: true,
      inMutuallyExclusiveGroup: true,
      selected: selected,
      label: option.label,
      excludeSemantics: true,
      child: PressableScale(
        child: GestureDetector(
          behavior: HitTestBehavior.opaque,
          onTap: onTap,
          child: AnimatedContainer(
            duration: AppMotion.ui,
            curve: AppMotion.curve,
            // Alto fijo: dos lineas de texto caben y todas las casillas miden igual.
            height: 60,
            padding: const EdgeInsets.symmetric(horizontal: AppSpacing.md),
            decoration: BoxDecoration(
              color: selected ? AppColors.mintTint : idleBg,
              borderRadius: BorderRadius.circular(14),
              border: Border.all(
                color: selected ? AppColors.mintBorder : idleBorder,
                width: selected ? 1.5 : 1,
              ),
            ),
            child: Row(
              children: [
                if (option.icon != null) ...[
                  Icon(option.icon,
                      size: 20, color: selected ? accent : idleIcon),
                  const SizedBox(width: AppSpacing.sm),
                ],
                Expanded(
                  child: Text(
                    option.label,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                      fontSize: 14,
                      fontWeight: selected ? FontWeight.w700 : FontWeight.w600,
                      height: 1.2,
                      color: selected ? accent : idleText,
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
