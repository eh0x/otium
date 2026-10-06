# API fixtures

Recorded responses for the metadata tests (Phase 4). Parsers and the repository are tested against
these files through MockWebServer, so no test touches the network.

## Google Books (`googlebooks/`)

Recorded on 2026-10-06 from `https://www.googleapis.com/books/v1`.

| File | Origin |
|------|--------|
| `volumes-dune.json` | A real volume (Dune, `B1hSG45JCX4C`) wrapped in the list shape |
| `volumes-full.json` | A real volume with every image size (`zyTCAlFPjgYC`) |
| `volumes-search.json` | Both real volumes as a search result |
| `volumes-empty.json` | The real body Google returns for "no results" |
| `error-429.json` | The real body of a quota error (`RESOURCE_EXHAUSTED`) |
| `volumes-no-pages.json` | `volumes-full.json` with `pageCount` removed |
| `volumes-no-cover.json` | `volumes-full.json` with `imageLinks` removed |
| `volumes-no-authors.json` | `volumes-full.json` with `authors` removed |
| `volumes-malformed.json` | `volumes-full.json` with wrong field types, to test tolerance |

The search endpoint was returning empty results and 503 errors while these were recorded, so the
volumes were fetched by id and wrapped in the list shape the search endpoint returns.

## Open Library (`openlibrary/`)

Recorded on 2026-10-06 from `https://openlibrary.org`. Long lists (ISBNs, subjects) are trimmed.

| File | Origin |
|------|--------|
| `edition-dune.json`, `edition-google-story.json` | `/isbn/{isbn}.json`, edition records |
| `work-dune.json` | `/works/{id}.json`, where the description lives |
| `author-dune.json` | `/authors/{id}.json`, where the author name lives |
| `search-dune.json`, `search-empty.json` | `/search.json` |
| `edition-hobbit.json`, `work-hobbit.json`, `author-tolkien.json` | The real Hobbit records: the edition names no authors, only its work does |

An edition record holds author and work keys, not names or a description. Those need two more
requests, which is why the Open Library mapper takes several responses.

## Rules

- A fixture never contains an API key. Keys are stripped or never recorded.
- Edit a fixture only to model a case that real data does not show, and say so in the table above.
