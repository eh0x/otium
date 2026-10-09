# Design questions: Direction B redesign

Open points from implementing the Direction B "Catalogue" handoff (D24 in the
[design doc](design/2026-10-06-otium-library-app-design.md)). Each item says what the handoff left
ambiguous or what could not be built as drawn, what the app does today, and what needs a decision.
Items the owner already settled during implementation are listed at the end for the record.

## Needs a decision

1. **Add is not centred in the bar.** With Library, Reading, Add and Settings in four equal slots,
   Add sits in the third slot, right of centre. Centring it needs an even number of tabs on each
   side, for example a fourth tab once Almanac exists.
2. **Shelf chips on the Edit screen were not built.** The handoff shows "SHELF" chips. A book's
   shelf is its first category, and offering chips needs the list of known categories in the
   confirm ViewModel, which is a logic change. Today the shelf is set through the Categories field
   (the first entry wins).
3. **The Edit form keeps the fields the handoff leaves out:** subtitle, ISBN, language,
   categories, tags and description. Drop them, or move them under "More details"?
4. **Passphrase validation is still checked on submit.** The handoff enables "Turn on" only once
   the passphrase is long enough, both entries match and the box is ticked, with a live "Good."
   Today the existing state machine reports "too short" and "do not match" after submitting.
   Moving the checks into the UI is a logic change in the encryption flow.
5. **Scan, ISBN entry and title search were not restyled.** They are outside the step list, so
   they keep Material top bars and outlined fields; only their toasts changed. The handoff's
   "Consulting the catalogue…" modal would map to their existing loading states.
6. **Surnames with particles.** The generated cover and the author sort use the last word of the
   first author, so "Ursula K. Le Guin" files under GUIN and "Vincent van Gogh" under GOGH. A
   particle list (le, de, van, von, da) would fix the common cases.
7. **Uncategorised books** sit on a final shelf named "Unshelved", which the handoff does not
   name.
8. **How many "Recently finished" books** the Reading tab shows. It shows five.
9. **Tags are no longer visible** outside the Edit screen: the detail facts table and the filters
   no longer list them. Keep them only as search terms, or show them in the facts table?
10. **Launcher background** is still Parchment `#F4EBD9`, from the old palette.
11. **README screenshots** in `docs/media/readme` show the old theme and need new renders.

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
