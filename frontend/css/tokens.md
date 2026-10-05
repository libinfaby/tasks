# Design tokens

Source of truth: the `:root` blocks in `styles.css`. The Android app mirrors these in
`android/app/src/main/java/dev/libinfaby/tasks/ui/theme/` — change both together.

## Colour

Monochrome, taken from Android's monochrome Material You preset (the app's `TasksColors`). Surfaces map to
M3 roles: background → `--bg-primary`, surface container lowest/low → cards/sidebar, outline variant → input borders.

| Token | Light | Dark | Use |
|---|---|---|---|
| `--bg-primary` | `#f9f9f9` | `#131313` | App background |
| `--bg-secondary` | `#f3f3f3` | `#0e0e0e` | Sidebar, modal footer |
| `--bg-tertiary` | `#eeeeee` | `#2a2a2a` | Muted fills, progress track |
| `--bg-card` | `#ffffff` | `#1b1b1b` | Lists, cards, inputs |
| `--bg-card-hover` | `#f3f3f3` | `#1f1f1f` | Row hover |
| `--border-color` | `#e2e2e2` | `#2a2a2a` | Hairlines, card borders |
| `--border-color-hover` | `#c6c6c6` | `#474747` | Input borders |
| `--text-primary` | `#1b1b1b` | `#e2e2e2` | Body text |
| `--text-secondary` | `#474747` | `#c6c6c6` | Supporting text |
| `--text-tertiary` | `#777777` | `#919191` | Meta, placeholders |
| `--accent` | `#000000` | `#ffffff` | Primary buttons, checked state |
| `--accent-hover` | `#3b3b3b` | `#d4d4d4` | |
| `--accent-text` | `#000000` | `#ffffff` | Accent-coloured text (Today, links) |
| `--on-accent` | `#e2e2e2` | `#1b1b1b` | Text, icons and ticks on accent fills |
| `--priority-urgent` | `#e5484d` | `#ff6369` | Urgent flag + checkbox ring |
| `--success` | `#30a46c` | `#3dd68c` | |
| `--danger` | `#e5484d` | `#ff6369` | Destructive actions, overdue |

Soft fills (badges, active filter chips) are the matching colour at 10% (light) / 14–16% (dark) alpha.

## Type

Inter for UI (JetBrains Mono reserved for code-like values).
Sizes: 12 / 13 / 14 / 16 / 20 / 24 px. Body is 14px, task titles 14px/500, page titles 16px/600.

## Shape & spacing

- Radii: 6 (controls, chips), 8 (cards, lists), 12 (modals, sheets), full (pills).
- Spacing scale: 4 / 8 / 16 / 24 / 32 / 48.
- Control height 36px (30px small). Rows: 12px vertical padding, hairline separators.

## Tag chips

User-chosen colours (`color`, `fg_color`, `has_bg`) are kept as-is. Unfilled chips use `color` as text,
falling back to `--text-secondary` when its contrast against the card surface is below 2.2:1
(e.g. white text in light mode). Every chip has a 1px inset edge (`--chip-edge`).
