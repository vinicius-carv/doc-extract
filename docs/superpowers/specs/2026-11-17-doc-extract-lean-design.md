# doc-extract — Lean design (supersedes 2026-11-16 design)

Date: 2026-11-17 · Status: draft for approval

## Why the rework
The 2026-11-16 design (4 Maven modules, Postgres, worker, guardrails, OCR, synthetic corpus, typed value scoring, Claude provider, model bake-off, pdf.js highlight, run comparison) needed 3 plans. After 10 reviewed commits Plan 1 had only core plumbing: no LLM call, no eval, no app, no UI. Lean v1 ships the three things that make the repo worth reading, and nothing else:
1. a PDF contract becomes JSON where **every field cites a quote that code has verified on the page**;
2. a **reproducible fidelity number** on public data (CUAD);
3. a page where you **see it**: each field, its quote, its verified/unsupported badge.

## Cut from v1 (each can come back later as its own change)
OCR and the synthetic scanned set · typed value normalization and value-level scoring · Claude provider · model bake-off · Postgres/Flyway/Testcontainers · job worker · rate limit and budget kill-switch · pdf.js highlight · run comparison UI · multi-module build · deploy.

## Kept constraints
Public/CUAD data only (CC BY 4.0, attributed), nothing from client work. Java 21, Spring Boot 4, Maven. Local Ollama `qwen3.5:9b` (spike settings: `format` = JSON schema, `think:false`, `temperature:0`, `num_ctx:16384`). CI never needs Ollama or a key. No GitHub repo or push without the user's yes.

## Design
**One Spring Boot app, one Maven module.** Packages: `pdf`, `extract`, `evidence`, `eval`, `web`.

- **pdf:** PDFBox per-page text; page cap 10 for uploads; encrypted/invalid PDF → typed error. *(Reuse `PdfTextExtractor` from `feat/part1-pipeline`, minus the OCR branch.)*
- **extract:** `Extractor` interface → `OllamaExtractor` (`RestClient`, `POST /api/chat`) and `RecordedExtractor` (replays JSON responses from `corpus/recordings/<sha256>-<window>.json`; `--record` writes them). Pages go in windows of 6 (overlap 1). Per category the model returns `value` (string or null) and `evidence` `[{page, quote}]`. Merge across windows: first non-null value with verified evidence, by page order; else first non-null; else null.
- **Categories (6):** Parties, Agreement Date, Effective Date, Expiration Date, Governing Law, Renewal Term. *(Reuse `Category` enum, trimmed.)*
- **evidence:** quote searched on the cited page after whitespace/case/hyphenation/quote-mark normalization → `VERIFIED` (with char range), `WRONG_PAGE` (found elsewhere, page corrected) or `UNSUPPORTED`. *(Reuse `TextNormalizer` + `EvidenceVerifier` and their tests.)*
- **eval:** 10 CUAD contracts listed in `corpus/manifest.json`; gold = CUAD clause spans mapped to (page, char range) by locating the span text in our page text — unmapped spans are counted and reported, never dropped silently. Score per category: span precision/recall (hit = char overlap ≥ 50% of the shorter span), plus overall verified-evidence rate and unsupported rate. Run with `./mvnw spring-boot:run -Dspring-boot.run.arguments=--eval` (or `--eval --record` against Ollama). Writes `results/featured.json` and prints a Markdown table for the README.
- **web:** results are JSON files, no database. `GET /api/documents`, `GET /api/documents/{id}`, `POST /api/extract` (multipart PDF: magic bytes, ≤ 10 MB, ≤ 10 pages; synchronous; 503 with a clear message when Ollama is unreachable). One static page (`src/main/resources/static/index.html` + one JS file, no build step): document list → fields with value, quote, status badge, and the page text with the quote `<mark>`ed.
- **Tests & CI:** JUnit for verifier, merge, span mapping, scoring, PDF text; one MockMvc test for upload validation; CI (GitHub Actions, Java 21) runs tests + `--eval` on recordings and fails if overall span F1 drops below `corpus/baseline.json`.
- **Packaging:** one Dockerfile (JRE 21); `OLLAMA_URL` env (default `http://localhost:11434`). README: hero line, numbers table, how verification and scoring work, run commands, CUAD attribution, limitations, one pt-BR line.

## Approaches
- **Lean single app (chosen):** smallest thing that shows extraction + verification + measurement end to end.
- Keep the 2026-11-16 design and finish Plans 1–3: much more surface, weeks of work before anything is visible.
- Keep `feat/part1-pipeline` and only trim the later plans: carries typed normalization, OCR, and a multi-module layout that v1 no longer needs.

## Old work
`feat/part1-pipeline` (worktree `C:\wt\doc-extract-part1`) is kept as-is, not deleted. The new build starts from `main`, copying only the four files named above with their tests.

## Left to the builder
Package names, exact normalization rules (keep the reused ones), the prompt text (versioned constant), CSS.
