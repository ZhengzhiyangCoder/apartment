# Neumorphism — Style Reference

> Elements share their background's color; only two opposing shadows say whether a surface is raised or pressed in.

**Theme:** light

Neumorphism lights every surface from one fixed direction: the light sits top-left, so each element carries a white highlight there and a cool grey shadow bottom-right. The element shares the background's color — change the tone and the material vanishes. Raised states use an outer shadow pair, pressed states an inset pair, and pressing is literally turning one into the other. There are no borders, no solid fills and no strong contrast: two blur radii carry all depth, so type has to stay conservative or it overwhelms the material.

## Colors

| Name | Value | Token | Role |
|---|---|---|---|
| Base | `#e0e5ec` | `--color-base` | The shared color of the page and every element. Both raised and pressed states start here; nothing may switch grounds. |
| Shadow Grey | `#b8bcc2` | `--color-shadow-grey` | The bottom-right shadow color. The only tone allowed to express recession; never used for text or borders. |
| Highlight | `#ffffff` | `--color-highlight` | The top-left highlight, always applied as a blurred shadow rather than a panel fill. |
| Blurple | `#6d5dfc` | `--color-blurple` | Primary action and selection. The one saturated tone allowed on top of the base, kept inside buttons and icons. |
| Coral | `#ff6b6b` | `--color-coral` | Alerts and destructive actions. Appearing at all signals irreversibility; never decorative. |
| Mint | `#4ecdc4` | `--color-mint` | Success and positive change. When it shares a screen with coral, neither may dominate. |

## Typography

### Inter — The only face, kept at conservative weights. The material is the lead; louder type cheapens it.

- **Substitute:** Nunito, system-ui, -apple-system, sans-serif
- **Weights:** 400, 500, 600
- **Sizes:** 12, 14, 16, 20, 28, 36
- **Line height:** 1.15 (display), 1.3 (headings), 1.6 (body)
- **Letter spacing:** -0.01em on display, 0.08em on uppercase labels

### Type Scale

| Role | Family | Weight | Size | Line Height | Letter Spacing | Token |
|---|---|---|---|---|---|---|
| display | — | 600 | 36px | 1.15 | -0.01em | `--text-display` |
| heading | — | 600 | 28px | 1.3 | — | `--text-heading` |
| subheading | — | 500 | 20px | 1.4 | — | `--text-subheading` |
| body-lg | — | 400 | 16px | 1.6 | — | `--text-body-lg` |
| body | — | 400 | 14px | 1.6 | — | `--text-body` |
| label | — | 500 | 12px | 1.4 | 0.08em | `--text-label` |

## Spacing & Layout

**Base unit:** 8px
**Density:** comfortable

### Spacing Scale

| Name | Value | Token |
|---|---|---|
| 8 | 8px | `--spacing-8` |
| 16 | 16px | `--spacing-16` |
| 24 | 24px | `--spacing-24` |
| 32 | 32px | `--spacing-32` |
| 48 | 48px | `--spacing-48` |
| 64 | 64px | `--spacing-64` |
| 96 | 96px | `--spacing-96` |

### Border Radius

| Element | Value |
|---|---|
| cards | 24px |
| buttons | 16px |
| inputs | 16px |
| pills | 999px |

- **Page max-width:** 1080px
- **Section gap:** 96px
- **Card padding:** 32px
- **Element gap:** 16px

## Components

### Raised Button

**Role:** Primary action

Filled with the base #e0e5ec, no border, 16px radius. The raised state uses the dual shadow pair: 8px 8px 16px #b8bcc2 and -8px -8px 16px #ffffff. Type at 14px weight 500 in dark grey. Hover halves both blurs to 4px, as if a hand approached and cast shade; press flips the pair to inset with no translation. 200ms ease-out.

- **States:** hover: blur 8px to 4px; active: inset pair; focus-visible: blurple outline, 3px offset

### Recessed Input

**Role:** Input and search field

Base-colored fill with the inset pair (8px 8px 16px #b8bcc2 and inset -8px -8px 16px #ffffff), 16px radius, 12px 16px padding. The placeholder sits in mid grey. Focus keeps the recess and only tints the shadow toward blurple at 30% opacity; nothing moves.

- **States:** focus: recess preserved, blurple-tinted shadow

### Soft Card

**Role:** Content card and stat block

Raised dual shadow, 24px radius, 32px padding, no border and no color change. Cards need at least 32px between them or the shadow pairs bleed into each other. Hover halves the blur and lifts 2px to signal clickability.

### Soft Toggle

**Role:** Switch and segmented control

The track is recessed (inset pair, 16px radius, 32px tall) and the knob is raised (outer pair, 24px diameter, same color as the track). On, the knob fills blurple; off, it matches the base. The 200ms transition animates only shadow and position, never scale.

## Do's and Don'ts

### Do

- Keep every element the same color as its background; let shadows carry all tonal difference
- Fix one light source: highlight top-left, shade bottom-right, everywhere
- Raised uses the outer pair, recessed the inset pair; never mix them
- Radius starts at 16px; cards take 24px
- Hover reduces the blur radius, as if a hand approached and cast shade
- Keep weights at 600 or below; the material is the lead

### Don't

- No pure black shadows; they turn the base muddy
- Do not apply neumorphism on a dark ground; there is no light to model
- No solid borders; the material stops being continuous
- Never re-tint an element to signal a level
- No sharp-stroke icons or high-contrast illustration
- Never place two shadowed elements closer than one blur radius

## Surfaces

| Level | Name | Value | Purpose |
|---|---|---|---|
| 0 | Base | `#e0e5ec` | 页面与元素的共同基底 |
| 1 | Raised | `#e0e5ec` | 凸起面板，靠外阴影区分而非换色 |

## Elevation

- **Raised:** `8px 8px 16px #b8bcc2, -8px -8px 16px #ffffff`
- **Recessed:** `inset 8px 8px 16px #b8bcc2, inset -8px -8px 16px #ffffff`
- **Hover (light dimmed):** `4px 4px 8px #b8bcc2, -4px -4px 8px #ffffff`
- **Pressed:** `inset 4px 4px 8px #b8bcc2, inset -4px -4px 8px #ffffff`

## Imagery

No photography. The material is the content: same-tone soft shapes, very light line icons, or product renders tinted to the base. Any high contrast, dark background or pure-white icon breaks the single-light assumption.

## Layout

Capped at 1080px over twelve columns, container padding 24px. Section rhythm 96px, 32px minimum between cards. The layout needs air: elements sit at least one blur radius apart or the shadows muddy each other. Dark text belongs to content only; chrome stays in mid grey.

## Agent Prompt Guide

### Implementation Contract

- Use the requested page, user task and real content as the brief. Implement only the requested scope; identify missing business information instead of inventing data or capabilities.
- Inspect the current project, stack and components first. Reuse its structure, routes, dependencies and components; do not rebuild unrelated pages or features.
- Use the project's breakpoints to adapt columns and spacing for narrow and desktop screens. Preserve hierarchy and prevent accidental horizontal overflow.
- For interactions in scope, support keyboard focus and reduced motion, with applicable disabled, loading, empty and error states.
- Run the project's existing checks after implementation. Report only tests and visual checks actually run, and list unverified behavior.

QUICK COLOR REFERENCE:

- base: `#e0e5ec`
- shadow: `#b8bcc2`
- highlight: `#ffffff`
- text: `#3d4451`
- primary: `#6d5dfc`
- danger: `#ff6b6b`

EXAMPLE COMPONENT PROMPTS:

1. Build a neumorphic button: background #e0e5ec, no border, 16px radius, 14px/500 dark grey label. Raised state uses box-shadow: 8px 8px 16px #b8bcc2, -8px -8px 16px #ffffff. Hover halves both blurs to 4px; press switches to inset 8px 8px 16px #b8bcc2, inset -8px -8px 16px #ffffff. The page ground is the same #e0e5ec.

## Similar Brands

- **Apple** — Same single-light material modelling, applied more sparingly and only to controls
- **Skeuomorphism** — The literal ancestor of this shadow logic; neumorphism is its minimal abstraction

## Quick Start

```css

:root {
  /* Colors */
  --color-base: #e0e5ec;
  --color-shadow-grey: #b8bcc2;
  --color-highlight: #ffffff;
  --color-blurple: #6d5dfc;
  --color-coral: #ff6b6b;
  --color-mint: #4ecdc4;

  /* Typography — Font Families */
  --font-sans: 'Inter', ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;

  /* Typography — Scale */
  --text-display: 36px;
  --leading-display: 1.15;
  --tracking-display: -0.01em;
  --text-heading: 28px;
  --leading-heading: 1.3;
  --text-subheading: 20px;
  --leading-subheading: 1.4;
  --text-body-lg: 16px;
  --leading-body-lg: 1.6;
  --text-body: 14px;
  --leading-body: 1.6;
  --text-label: 12px;
  --leading-label: 1.4;
  --tracking-label: 0.08em;

  /* Typography — Weights */
  --font-weight-regular: 400;
  --font-weight-medium: 500;
  --font-weight-semibold: 600;

  /* Spacing */
  --spacing-unit: 8px;
  --spacing-8: 8px;
  --spacing-16: 16px;
  --spacing-24: 24px;
  --spacing-32: 32px;
  --spacing-48: 48px;
  --spacing-64: 64px;
  --spacing-96: 96px;

  /* Layout */
  --page-max-width: 1080px;
  --section-gap: 96px;
  --card-padding: 32px;
  --element-gap: 16px;
  --density: comfortable;

  /* Border Radius */
  --radius-cards: 24px;
  --radius-buttons: 16px;
  --radius-inputs: 16px;
  --radius-pills: 999px;

  /* Surfaces */
  --surface-base: #e0e5ec;
  --surface-raised: #e0e5ec;
}

```
