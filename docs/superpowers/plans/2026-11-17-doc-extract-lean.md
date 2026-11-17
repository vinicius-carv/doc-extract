# doc-extract Lean v1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** One Spring Boot app that turns a contract PDF into fields with code-verified quotes, scores itself on CUAD, and shows results on one static page.

**Architecture:** Single Maven module, packages `pdf`, `extract`, `evidence`, `eval`, `web` under `io.github.viniciuscarv.docextract`. Results are JSON files; no database. LLM = local Ollama; tests and CI replay recorded responses.

**Tech Stack:** Java 21, Spring Boot 4.x (web, `RestClient`), PDFBox 3, Jackson, JUnit 5 + AssertJ, MockMvc, `MockRestServiceServer`, Maven wrapper, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-11-17-doc-extract-lean-design.md`

## Global Constraints

- Worktree `C:\wt\doc-extract-lean`, branch `feat/lean`. Never push; never create the GitHub repo.
- Old code lives on branch `feat/part1-pipeline` (worktree `C:\wt\doc-extract-part1`). Read files there with `git -C C:\wt\doc-extract-part1 show HEAD:<path>`; never modify that worktree.
- `JAVA_HOME = C:\Program Files\Java\jdk-21`. Build with `.\mvnw.cmd -q verify` (copy `mvnw`, `mvnw.cmd`, `.mvn/` from the old branch).
- Categories, exactly 6 (key / CUAD label): `parties`/"Parties", `agreement_date`/"Agreement Date", `effective_date`/"Effective Date", `expiration_date`/"Expiration Date", `governing_law`/"Governing Law", `renewal_term`/"Renewal Term".
- Ollama call: `POST {OLLAMA_URL}/api/chat`, `stream:false`, `format` = JSON schema, `think:false`, `options:{temperature:0, num_ctx:16384}`, model `DOC_EXTRACT_MODEL` default `qwen3.5:9b`, `OLLAMA_URL` default `http://localhost:11434`, read timeout 300 s.
- Windows of 6 pages, overlap 1. Upload limits: PDF magic bytes `%PDF-`, ≤ 10 MB, ≤ 10 pages.
- Span hit = char overlap on the same page ≥ 50% of the shorter span.
- No client-derived artifacts. CUAD (CC BY 4.0) attributed in `corpus/LICENSE-CUAD` and README.
- Commits: Conventional Commits, English, ending with `Generated with AI` / `Co-Authored-By: AI <ai@example.com>`. Git identity `valves8956@gmail.com`.
- Ponytail: smallest complete change; no options or abstractions nobody asked for; every non-trivial branch gets a test.

## Review Focus

1. Quote present on the page but split by a hyphenated line break or curly quotes → must be `VERIFIED` (reused tests cover; keep them).
2. Model returns `evidence` with a page number outside the window or document → `UNSUPPORTED`, never an exception.
3. Model returns invalid JSON or Ollama is down → upload returns 503 with a message; eval fails that document loudly, does not record garbage.
4. Upload of a non-PDF renamed `.pdf`, an encrypted PDF, or 11 pages → 400 with a clear message.
5. CUAD gold span not found in our page text → counted in `unmappedSpans`, excluded from recall, reported.

---

### Task 1: Single-module skeleton with reused core

**Files:** `pom.xml` (Spring Boot 4 parent, `spring-boot-starter-web`, `pdfbox`, test starter), `mvnw*`, `.mvn/`, `.gitignore`, `.gitattributes`, `LICENSE`, `.github/workflows/ci.yml` (Java 21 Temurin, `./mvnw -q verify`), `src/main/java/.../DocExtractApplication.java`, and copied from `feat/part1-pipeline` `core/` into `src/`: `model/{Category,PageText,DocumentText,Evidence,EvidenceStatus,VerifiedEvidence}`, `text/{TextNormalizer,MatchText}`, `evidence/EvidenceVerifier`, `pdf/{PdfTextExtractor,PageLimitExceededException,UnreadablePdfException}`, `corpus/Sha256` and their tests (`EvidenceVerifierTest`, `TextNormalizerTest`, `PdfTextExtractorTest`, `CategoryTest`, `testutil/TestPdfs`).

- [ ] Copy the files; drop the OCR path from `PdfTextExtractor` (no `OcrEngine`/`PageTextResolver`; a page with no text layer just has empty text) and drop OCR-only fields/tests.
- [ ] Trim `Category` to the 6 constants above, removing `FieldType`/`type()`; update `CategoryTest` (6 entries, `fromCuadLabel("Governing Law") == GOVERNING_LAW`, unknown throws).
- [ ] Remove anything that referenced cut classes (`FieldType`, `Confidence`, normalizers, windows) — or reduce `Evidence`/`VerifiedEvidence` to what `EvidenceVerifier` needs.
- [ ] `.\mvnw.cmd -q verify` → BUILD SUCCESS, all copied tests green.
- [ ] Commit `chore: single-module skeleton reusing verified core`.

### Task 2: Extraction (Ollama + recordings + merge)

**Files:** `extract/{Extractor,OllamaExtractor,RecordedExtractor,Windows,Merge,FieldOutcome,ExtractionService}.java` + tests.

**Interfaces:**
- `interface Extractor { String complete(String documentSha, int windowIndex, String prompt) }` — returns the raw JSON content string.
- `Windows.split(int pageCount) -> List<int[]>` (1-based inclusive `[from,to]`, size 6, overlap 1; 0 pages → empty).
- `record FieldOutcome(Category category, String value, List<VerifiedEvidence> evidence)`.
- `ExtractionService.extract(DocumentText doc) -> List<FieldOutcome>` (one per category, enum order): builds a page-tagged prompt `<page n="k">…</page>` per window, calls the extractor, parses `{ "<key>": {"value": string|null, "evidence": [{"page": int, "quote": string}]} }`, verifies every evidence item with `EvidenceVerifier`, then `Merge`.
- `Merge`: per category, first non-null value whose evidence has ≥ 1 `VERIFIED`/`WRONG_PAGE` item, by window order; else first non-null; else null with empty evidence.
- `OllamaExtractor(RestClient.Builder, url, model)`; `RecordedExtractor(Path dir, Extractor delegateOrNull)` reads `<dir>/<sha>-<window>.json`; when the file is missing and a delegate exists, calls it and writes the file (record mode); missing with no delegate → `IllegalStateException` naming the file.
- `PROMPT_VERSION = "v1"` constant; schema built from the 6 categories.

- [ ] Tests first: `WindowsTest` (1→[[1,1]], 6→[[1,6]], 7→[[1,6],[6,7]], 12→[[1,6],[6,11],[11,12]], 0→[]); `MergeTest` (verified beats earlier unverified; all null → null; unverified kept when nothing verified); `OllamaExtractorTest` with `MockRestServiceServer` asserting body has `format`, `think:false`, `temperature:0`, `num_ctx:16384`, model, and returns `message.content`; `RecordedExtractorTest` (replay, record-writes-file, missing-without-delegate throws); `ExtractionServiceTest` with a fake extractor (page out of range → UNSUPPORTED, invalid JSON → `ExtractionException`).
- [ ] Implement; `.\mvnw.cmd -q verify` green.
- [ ] Commit `feat: windowed Ollama extraction with recordings and verified merge`.

### Task 3: Eval (CUAD gold mapping + scoring + `--eval`)

**Files:** `eval/{Manifest,GoldSpan,GoldMapper,Scorer,EvalRunner}.java` + tests; `corpus/manifest.json` (empty `documents` list for now), `corpus/baseline.json` (`{"overallF1": 0.0}`), `corpus/LICENSE-CUAD`.

**Interfaces:**
- `corpus/manifest.json`: `{"documents":[{"id":"...","pdf":"cuad/<id>.pdf","gold":"cuad/<id>.gold.json"}]}`; gold file: `[{"category":"<CUAD label>","text":"<span text>"}]` (prepared in Task 6 from CUAD's JSON).
- `GoldMapper.map(DocumentText, List<goldEntry>) -> Mapped(List<GoldSpan(Category, page, start, end)>, int unmapped)` — locate each span via `TextNormalizer` on any page; spans not found count as unmapped.
- `Scorer.score(predicted evidence per doc, gold spans per doc) -> Report` with per-category precision/recall/F1, overall F1 (micro), verified rate, unsupported rate, unmapped count.
- `EvalRunner` is an `ApplicationRunner` active only with arg `--eval` (`--record` enables Ollama delegate): runs all manifest docs, writes `results/featured.json` (fields + evidence per doc + report + prompt version + model), prints a Markdown table, exits non-zero if overall F1 < `corpus/baseline.json`. Web server not started in eval mode (`spring.main.web-application-type=none` when `--eval`).

- [ ] Tests first: `GoldMapperTest` (exact, whitespace-variant, missing → unmapped); `ScorerTest` (hit at exactly 50% overlap, miss at 49%, different page = miss, empty prediction & empty gold → no division by zero); `EvalRunnerTest` on a 2-page fixture PDF + fake recordings in `src/test/resources/eval/` asserting the table and `featured.json` contents.
- [ ] Implement; verify green.
- [ ] Commit `feat: CUAD span scoring and eval runner with baseline gate`.

### Task 4: Web API, upload validation and static page

**Files:** `web/{DocumentController,UploadValidator}.java`, `src/main/resources/static/{index.html,app.js,app.css}`, tests.

- `GET /api/documents` → `[{id,title,pages,f1}]` from `results/featured.json`; `GET /api/documents/{id}` → fields + evidence + page texts; 404 unknown.
- `POST /api/extract` multipart `file`: validate magic bytes / 10 MB / 10 pages / encrypted → 400 problem detail; Ollama unreachable or bad JSON → 503 problem detail; success → same shape as `GET /api/documents/{id}` (not persisted).
- Page: list documents; selecting one shows a table (category, value, quote, status badge) and the cited page text with the quote wrapped in `<mark>` using the verified char range; an upload form. Accessible labels, keyboard usable, no framework, no build step.

- [ ] Tests first (MockMvc): non-PDF → 400; 11-page PDF → 400; encrypted → 400; extractor throws connect error → 503; happy path with fake extractor → 200 with 6 fields; unknown id → 404.
- [ ] Implement; verify green; run the app (`.\mvnw.cmd spring-boot:run`) and check the page loads with the test fixture results.
- [ ] Commit `feat: results API, validated upload and evidence page`.

### Task 5: Packaging and README

- [ ] `Dockerfile` (multi-stage: Maven build on `eclipse-temurin:21-jdk`, run on `eclipse-temurin:21-jre`), `OLLAMA_URL` env; `.dockerignore`.
- [ ] `README.md`: hero line, what it does, how verification and scoring work, run commands (local, Docker, `--eval`, `--eval --record`), numbers table placeholder `<!-- numbers -->`, CUAD attribution, limitations (no OCR, local 9B model), one pt-BR line.
- [ ] Commit `docs: Dockerfile and README`.

### Task 6: Real CUAD run (user-gated)

- [ ] Ask the user before downloading CUAD v1 (name, source URL, size). Unpack outside the repo.
- [ ] Pick 10 contracts with ≤ 30 pages and labels in ≥ 4 of the 6 categories; copy PDFs + generated gold files into `corpus/cuad/`; fill `manifest.json`.
- [ ] With Ollama running: `--eval --record`; commit recordings, `results/featured.json`, set `baseline.json` to the achieved overall F1 minus 0.02; paste the table into README.
- [ ] Commit `feat: CUAD corpus, recordings and first fidelity numbers`.
