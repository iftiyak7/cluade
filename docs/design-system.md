# Hayat — UI Design System

Implemented in `:core:designsystem`. Every color, type style, shape and spacing value comes
from tokens; screens never hard-code hex values.

## Personality
Calm · trustworthy · minimal · premium · peaceful. Deep green brand, warm off-white surfaces,
restrained accents, generous whitespace, no gradients, no decorative clutter.

## Color tokens (Material 3 roles)

| Role | Light | Dark |
|---|---|---|
| primary | `#167C5A` | `#7FD4AE` |
| onPrimary | `#FFFFFF` | `#00382A` |
| primaryContainer | `#B9EBD3` (mint) | `#0D503C` (deep green) |
| onPrimaryContainer | `#002116` | `#B9EBD3` |
| secondary | `#4C6358` | `#B3CCBF` |
| tertiary (accent blue) | `#3B6FD6` | `#AFC6FF` |
| background | `#FAFBF8` (warm) | `#101714` |
| surface | `#FAFBF8` | `#101714` |
| surfaceContainer | `#EEF3EF` | `#1B2621` |
| surfaceContainerHigh | `#E9F5EF` (mint surface) | `#24302A` |
| error | `#C23B3B` | `#FFB4AB` |
| warning (extended) | `#7A5000` text on `#FFE3B0` container | `#FFC86B` on `#4A3200` |
| success (extended) | primary | primary |

The prompt's raw `#3B82F6` and `#D99A28` / `#D64545` were darkened (`#3B6FD6`, `#7A5000`,
`#C23B3B`) so text-on-surface meets WCAG AA 4.5:1 in light mode. A **high-contrast** scheme
(settings toggle) uses pure on-colors and stronger outlines. Dynamic color (Material You) is
optional and off by default to preserve brand identity.

Status is never conveyed by color alone: completion states pair color with an icon and a
text label (e.g. ✓ *Completed*, ⏭ *Skipped*, ⚠ *Missed*).

## Typography
Hind Siliguri (OFL) bundled in 5 weights; Bengali and Latin share metrics. Line heights are
~1.45× for body text to give Bengali conjuncts and vowel signs room.

| Style | Size / line height / weight |
|---|---|
| displaySmall | 34 / 44 / SemiBold |
| headlineMedium | 26 / 36 / SemiBold |
| headlineSmall | 22 / 32 / SemiBold |
| titleLarge | 20 / 28 / SemiBold |
| titleMedium | 16 / 24 / Medium |
| bodyLarge | 16 / 24 / Regular |
| bodyMedium | 14 / 21 / Regular |
| labelLarge | 14 / 20 / Medium |
| labelSmall (caption) | 11 / 16 / Medium |

Bengali numerals: when the app language is Bengali, numbers are formatted with
`NumberFormat` for `bn-BD` (০১২৩…) unless the user picks "Latin digits" in settings.

## Spacing, shape, elevation
* 4 dp base grid, 8 dp rhythm: `xxs 2, xs 4, sm 8, md 12, lg 16, xl 24, xxl 32, xxxl 48`.
* Screen gutter 16 dp (20 dp ≥ 600 dp width). Cards 16 dp inner padding.
* Shapes: small 8 dp, medium 14 dp, large 20 dp, extra-large 28 dp (sheets).
* Tonal elevation only; no drop shadows on cards.
* Minimum touch target 48 × 48 dp.

## Components (`:core:designsystem` / `:core:ui`)
`HayatScaffold`, `HayatTopBar`, `SectionHeader`, `HayatCard`, `ProgressRing`, `LinearMeter`,
`StatTile`, `EmptyState`, `ErrorState`, `ConfirmDialog`, `TimePickerField`,
`DatePickerField`, `WeekdaySelector`, `PriorityChips`, `ColorSwatchPicker`, `StatusChip`,
`CounterButton` (zikr), `HeatmapRow`, `BarChart` (Canvas, accessible summaries),
`SettingsRow`/`SwitchRow`, `PermissionBanner`.

## Motion
150–250 ms standard easing; content-size and fade transitions only. When the system
"remove animations" setting is on (animator duration scale 0) or the in-app *Reduce motion*
setting is on, transitions are disabled.

## Navigation
Bottom bar: **Home · Calendar · Add · Progress · Settings**. *Add* opens a modal sheet
(Task, Reminder, Routine, Water, Medicine dose, Note, Event, Journal…). Feature screens are
reached from dashboard cards, the *All features* grid on Home, and search.
