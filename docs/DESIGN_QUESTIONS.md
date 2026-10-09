# Design questions: Direction B redesign

Open points from implementing the Direction B "Catalogue" handoff (D24 in the
[design doc](design/2026-10-06-otium-library-app-design.md)). Each item says what the handoff left
ambiguous or what could not be built as drawn, what the app does today, and what needs a decision.
Items the owner already settled during implementation are listed at the end for the record.

## Needs a decision

1. **Uncategorised books** sit on a final shelf named "Unshelved", which the handoff does not
   name.
2. **How many "Recently finished" books** the Reading tab shows. It shows five.

## Deviations from the handoff, already in the app

- **Muted ink** is `#676665` instead of `#6C6B6A` (4.39:1 on the surface), and the divider is an
  opaque blend rather than ink at 16%, so both read the same on every ground and can be tested.
- **Accent on the surface** is 2.8:1, under 3:1 for graphics, so accent graphics are kept to the
  background and sheet.
- **Hairline inputs** are 1.4:1 at rest. Every field shows its label, and its rule turns accent
  with focus. Secondary buttons rely on their label rather than their faint border.
- **Cormorant Regular under 28 sp** appears in the handoff (the detail percentage at 26 sp, the
  last-backup value at 24 sp). These use the 28 sp style instead.
- **Lining figures everywhere.** Cormorant's default old-style figures make a 1 read as a small
  capital I ("2I2", "+IO"), so every Cormorant style uses lining figures.
- **Generated covers** pick one of twelve fixed board colours by a hash of the title, rather than
  a continuous OKLCH colour, so the contrast test can check every board. The cover is a composable
  rather than a `Painter`, so the title can wrap evenly.
- **Bottom sheets** use Material's enter animation, not the 280 ms settle; dialogs dim with the
  system scrim; toasts sit just above the bar rather than 96 dp from the bottom.
- **Search** shows a clear button while there is text.
- **Particles** are matched only before the last word, so a single-word first name such as "La"
  in "La Fontaine" is never taken for one.
- **Large text.** At 150% text and above, the detail header puts the words below the cover and the
  Drive card puts its button below the status.
- **Progress** saves when the slider is released and at each stepper tap; the handoff assumed a
  debounce that the app never had.

## Settled during implementation

- Shelves are first categories; category and tag filters were removed from the library; "Recently
  added" is no longer offered as a sort; titles file without a leading article and authors by
  surname (sorted in the repository).
- The handoff's copy replaces the old strings where they differ (volumes; Unread, Reading, Read).
- The detail facts table adds ISBN-13 after the handoff's rows; books without a page count keep
  "Set total pages"; there is no way to type an exact page.
- Add sits at the centre of a five-slot bar; the fifth slot is empty, held for a later tab.
- On the Edit screen, a Shelf row of chips sets the first category, and subtitle, ISBN, language,
  categories, tags and description sit under "More details".
- A new passphrase is checked as it is typed ("Good.", a live mismatch, "Turn on" enabled only
  when valid); the ViewModel still checks on submit.
- Scan, ISBN entry and title search share the redesign, and lookups read "Consulting the
  catalogue…".
- Surnames keep their particles ("Le Guin", "van Gogh"), for the author sort and the covers.
- Tags are listed in the detail facts table.
- The launcher icon and the README screenshots use the Classical palette.
