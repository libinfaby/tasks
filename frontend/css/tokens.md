# Design tokens

Source of truth: the `:root` blocks in `styles.css`. The Android app mirrors these in
`android/app/src/main/java/dev/libinfaby/tasks/ui/theme/` — change both together.

## Colour

Material 3 colour roles, the monochrome scheme Android's monochrome wallpaper preset produces. They mirror
`TasksColors` in `android/app/src/main/java/dev/libinfaby/tasks/ui/theme/Theme.kt`; error roles keep
Material's defaults on both.

| Token | Light | Dark | Use |
|---|---|---|---|
| `--primary` / `--on-primary` | `#000000` / `#e2e2e2` | `#ffffff` / `#1b1b1b` | Filled buttons, checked state, accents |
| `--primary-container` / `--on-…` | `#3b3b3b` / `#ffffff` | `#d4d4d4` / `#000000` | FAB, icon tiles, empty-state tile |
| `--secondary-container` / `--on-…` | `#d4d4d4` / `#1b1b1b` | `#474747` / `#e2e2e2` | Selected chips, toggles, nav indicator |
| `--surface` | `#f9f9f9` | `#131313` | Page background |
| `--surface-container-low` | `#f3f3f3` | `#1b1b1b` | Drawer, sheets |
| `--surface-container` | `#eeeeee` | `#1f1f1f` | Cards and list items, bottom bar |
| `--surface-container-high` | `#e8e8e8` | `#2a2a2a` | Search bar, dialogs, subtask box |
| `--surface-container-highest` | `#e2e2e2` | `#353535` | Neutral chips |
| `--on-surface` / `--on-surface-variant` | `#1b1b1b` / `#474747` | `#e2e2e2` / `#c6c6c6` | Text / supporting text |
| `--outline` / `--outline-variant` | `#777777` / `#c6c6c6` | `#919191` / `#474747` | Field borders, checkbox rings / chip outlines |
| `--error` / `--error-container` | `#b3261e` / `#f9dedc` | `#f2b8b5` / `#8c1d18` | Urgent, overdue, destructive actions |

**Tonal colours** (tags, groups, tiles): a soft container in the item's hue with darker (light) or lighter
(dark) text of the same hue, mixed in CSS from `--hue` (`.tonal`). Greys, white and black read as the
neutral pair (`.neutral`). Same rules as `tagColors` / `tonalColors` on Android. A tag's stored text colour
and fill are no longer drawn.

## Type

Bricolage Grotesque (display, headlines, titles; optical size pinned at 36) and Figtree (everything else),
on the Android type scale: display-small 36, headline-small 24, title-large 22, title-medium 16/600,
body-large 16, body-medium 14, label-large 14/600, label-small 12/600. Task titles are 17px regular.

## Shape & motion

- Connected lists: 24px outer corners, 6px inner, 3px seams (6px between task cards).
- Chips 8px (tags) / 10px (filters, turning into pills when selected); buttons and toggles are pills;
  dialogs and sheets 28px; FAB 20px.
- Pages slide in and out (shared X axis, emphasized decelerate, 400ms); tabs fade through.
- Phones get a bottom bar and a full-page menu; from 900px wide a navigation drawer replaces both.
