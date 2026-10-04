# Design tokens

Source of truth: the `:root` blocks in `styles.css`. The Android app mirrors these in
`android/app/src/main/java/dev/libinfaby/tasks/ui/theme/` — change both together.

## Colour

| Token | Light | Dark | Use |
|---|---|---|---|
| `--bg-primary` | `#ffffff` | `#111113` | App background |
| `--bg-secondary` | `#fafafa` | `#0c0c0e` | Sidebar, modal footer |
| `--bg-tertiary` | `#f4f4f5` | `#222226` | Muted fills, progress track |
| `--bg-card` | `#ffffff` | `#18181b` | Lists, cards, inputs |
| `--bg-card-hover` | `#fafafa` | `#1d1d21` | Row hover |
| `--border-color` | `#e4e4e7` | `#27272a` | Hairlines, card borders |
| `--border-color-hover` | `#d4d4d8` | `#3f3f46` | Input borders |
| `--text-primary` | `#18181b` | `#ededef` | Body text |
| `--text-secondary` | `#52525b` | `#a1a1aa` | Supporting text |
| `--text-tertiary` | `#71717a` | `#7c7c85` | Meta, placeholders |
| `--accent` | `#5b5bd6` | `#5b5bd6` | Primary buttons, checked state |
| `--accent-hover` | `#4f4fc4` | `#6e6ade` | |
| `--accent-text` | `#4c4bbd` | `#a8a4ff` | Accent-coloured text (Today, links) |
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
