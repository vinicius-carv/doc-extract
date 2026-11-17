# doc-extract Part 1: Extraction Pipeline, Corpus and Eval Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A headless Java pipeline that turns a contract PDF into evidence-backed fields, plus a corpus (CUAD subset + synthetic scans) and an `eval` CLI that scores extraction against ground truth and writes reproducible run reports.

**Architecture:** Maven multi-module. `core` is plain Java 21 (no Spring): PDF text, OCR, windowing, LLM providers behind `LlmExtractor`, evidence verification, normalization, scoring, corpus I/O. `eval` is a picocli CLI on top of `core`. Runs are written as JSON files under `corpus/runs/`; Part 2 (Spring app) imports them into Postgres via the startup seeder.

**Tech Stack:** Java 21, Maven (wrapper), PDFBox 3.x, Tess4J 5.x, Jackson 2.x, picocli 4.7.x, anthropic-java 3.3.0+, JUnit 5, AssertJ. Ollama 0.40.x with `qwen3.5:9b` on the dev machine.

**Spec:** `docs/superpowers/specs/2026-11-16-doc-extract-design.md`

**Scope split:** This is Plan 1 of 3. Plan 2 = `app` (Spring Boot 4, Postgres/Flyway, API, worker, guardrails, seeder, Testcontainers). Plan 3 = `web` (React + react-pdf highlight), Dockerfile/compose, README with numbers and GIF. Plans 2 and 3 are written after Plan 1 ships, against its real interfaces.

## Global Constraints

- Java 21; root package `io.github.viniciuscarv.docextract`; `core` has no Spring dependency.
- No client-derived artifacts anywhere (documents, field names, prompts, code). Data = CUAD v1 (CC BY 4.0, attribute in `corpus/LICENSE-CUAD`) + repo-generated synthetic docs.
- CI never needs `ANTHROPIC_API_KEY` or Ollama; CI uses committed recordings.
- Default provider `ollama`, default model `qwen3.5:9b`, `num_ctx` 16384, `temperature` 0, `think` false, window 6 pages with 1 page overlap (Claude: whole document).
- Claude defaults: model `claude-opus-5-5`, effort `medium` set explicitly, adaptive thinking (never `disabled`), refusal fallback `fallbacks: "default"` (beta `server-side-fallback-2026-07-01`), check `stop_reason` before reading content.
- Every run records: corpus manifest hash, prompt version, provider, model, window pages, overlap, effort (Claude only), timestamp.
- Thresholds are named constants in `core` and are documented in the README later: OCR trigger < 20 non-whitespace chars on a page; fuzzy evidence ≥ 0.90 similarity; span hit = overlap ≥ 50% of the shorter span; text CORRECT ≥ 0.8 token F1, PARTIAL ≥ 0.4.
- Commits: Conventional Commits in English, ending with the two attribution lines `Generated with AI` / `Co-Authored-By: AI <ai@example.com>`. Never push; the GitHub repo does not exist yet and is created only on the user's explicit yes.
- Downloads (Maven itself if missing, CUAD dataset, `eng.traineddata`, extra Ollama models) need the user's explicit OK at the moment they happen: state name, source and size.

## Review Focus

1. **Encrypted, corrupt or non-PDF upload** → a typed `UnreadablePdfException` with a clear message, never a stack trace from PDFBox. Test in Task 3.
2. **Quote differs from page text only by typography** (curly vs straight quotes, en-dash vs hyphen, line-break hyphenation `termi-\nnation`, double spaces) → still `VERIFIED`, with the char range in the original page text. Test in Task 5.
3. **Ollama down, slow first call, or returns invalid/truncated JSON** → one retry for invalid JSON, typed `ExtractionException` after that; connection refused fails fast with the URL in the message; read timeout is long enough for model load (≥ 300 s). Test in Task 8.
4. **Same field found in several overlapping windows** (parties on page 1 and repeated on page 6; same date twice) → merged once, parties deduplicated after name normalization, evidence items deduplicated by (page, start). Test in Task 7.
5. **Category absent from both gold and prediction / present in only one** → `TRUE_EMPTY`, `MISSED`, `SPURIOUS` counted correctly and never divide by zero in precision/recall (empty denominators → metric reported as `null`, not 0 or NaN). Test in Task 12.

---

## File Structure

```
doc-extract/
  pom.xml                                  parent: Java 21, dependencyManagement, surefire, modules core+eval
  mvnw, mvnw.cmd, .mvn/wrapper/            Maven wrapper
  .github/workflows/ci.yml                 build + test (Tesseract via apt)
  .gitignore, LICENSE (MIT), README.md     README is a stub in Part 1
  core/pom.xml
  core/src/main/java/io/github/viniciuscarv/docextract/
    model/        Category, FieldType, PageText, DocumentText, Evidence, FieldCandidate, WindowResult,
                  VerifiedEvidence, EvidenceStatus, FieldResult, DocumentExtraction, Usage, Confidence
    pdf/          PdfTextExtractor, UnreadablePdfException, PageLimitExceededException
    ocr/          OcrEngine, TesseractOcrEngine, PageTextResolver
    text/         MatchText (normalized text + offset map), TextNormalizer
    evidence/     EvidenceVerifier
    normalize/    ValueNormalizer, NormalizedValue (sealed), PartyNames
    window/       Window, PageWindows, WindowPrompt, CandidateMerger
    llm/          LlmExtractor, ExtractionRequest, ExtractionSchema, Prompts, ExtractionException,
                  ResponseParser
    llm/ollama/   OllamaExtractor, OllamaConfig
    llm/claude/   ClaudeExtractor, ClaudeConfig
    llm/record/   RecordingKey, RecordingExtractor, ReplayExtractor, RecordingMissingException
    pipeline/     ExtractionPipeline
    corpus/       GoldLabel, GoldFile, Manifest, CorpusDoc, CorpusLoader, Sha256
    score/        Outcome, SpanScorer, ValueScorer, CategoryScore, RunReport, RunAggregator
  core/src/test/java/...                   mirrors main packages
  eval/pom.xml
  eval/src/main/java/io/github/viniciuscarv/docextract/eval/
    EvalMain, RunCommand, CompareCommand, ImportCuadCommand, GenerateSyntheticCommand, ProviderFactory
  eval/src/main/java/io/github/viniciuscarv/docextract/eval/cuad/      CuadImporter, CuadSelector
  eval/src/main/java/io/github/viniciuscarv/docextract/eval/synthetic/ SyntheticContractGenerator,
                                                                      ContractTemplate, ScanDegrader
  corpus/
    manifest.json                          doc list, featured run id, baseline F1
    cuad/<docId>/document.pdf, gold.json
    synthetic/<docId>/document.pdf, gold.json
    recordings/<provider>/<model-slug>/<docSha256>/<window>.json
    runs/<runId>.json
    LICENSE-CUAD
```

Decisions made by this plan (not in the spec): `core` uses the JDK `HttpClient` for Ollama instead of Spring `RestClient` (keeps `core` Spring-free); eval runs are JSON files, imported into Postgres in Part 2; the synthetic generator lives in `eval`, not `core`.

---

### Task 1: Repository skeleton, wrapper, CI

**Files:**
- Create: `pom.xml`, `core/pom.xml`, `eval/pom.xml`, `mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties`, `.github/workflows/ci.yml`, `.gitignore`, `LICENSE`, `README.md`
- Test: `core/src/test/java/io/github/viniciuscarv/docextract/SmokeTest.java`

**Interfaces:**
- Produces: `./mvnw -q verify` builds both modules; later tasks add dependencies to `core/pom.xml` / `eval/pom.xml` only.

- [ ] **Step 1:** Maven is not installed on the dev machine. Ask the user before installing (e.g. `winget install Apache.Maven` or `scoop install maven`), then run `mvn -N wrapper:wrapper -Dmaven=3.9.11` to create the wrapper. After that, use only `./mvnw` (Git Bash) / `.\mvnw.cmd` (PowerShell).
- [ ] **Step 2:** Parent `pom.xml`: `groupId io.github.viniciuscarv`, `artifactId doc-extract-parent`, `version 0.1.0-SNAPSHOT`, packaging `pom`, `maven.compiler.release 21`, UTF-8, modules `core`, `eval`; `dependencyManagement` for junit-bom, assertj-core, jackson-bom, pdfbox, tess4j, picocli, anthropic-java (latest stable of each at implementation time; anthropic-java ≥ 3.3.0); surefire with JUnit 5. `core` and `eval` (eval depends on core) as jar modules. `eval` builds a runnable fat jar (maven-shade, main class `...eval.EvalMain`).
- [ ] **Step 3:** Write `SmokeTest` with `assertThat(Runtime.version().feature()).isGreaterThanOrEqualTo(21)`.
- [ ] **Step 4:** Run `./mvnw -q verify`. Expected: BUILD SUCCESS, 1 test.
- [ ] **Step 5:** `ci.yml`: on push and pull_request; ubuntu-latest; `actions/setup-java` (temurin 21, cache maven); `sudo apt-get install -y tesseract-ocr tesseract-ocr-eng`; `./mvnw -B verify`. `.gitignore`: `target/`, `.idea/`, `*.iml`, `.env`, `corpus/raw/`. `LICENSE`: MIT, "Copyright (c) 2026 Vinicius Carvalho". `README.md`: title, one-line description, "work in progress", pt-BR line placeholder.
- [ ] **Step 6:** Commit: `chore: scaffold maven multi-module build and CI`.

---

### Task 2: Domain model and v1 category set

**Files:**
- Create: `core/src/main/java/.../model/*.java` (see File Structure)
- Test: `core/src/test/java/.../model/CategoryTest.java`

**Interfaces:**
- Produces:
  - `enum FieldType { TEXT, DATE, DURATION, PARTY_LIST, JURISDICTION, MONEY }`
  - `enum Category` with `String key()`, `String cuadLabel()`, `FieldType type()`, `String description()`; constants (key / CUAD label / type):
    `DOCUMENT_NAME "document_name" "Document Name" TEXT`, `PARTIES "parties" "Parties" PARTY_LIST`, `AGREEMENT_DATE "agreement_date" "Agreement Date" DATE`, `EFFECTIVE_DATE "effective_date" "Effective Date" DATE`, `EXPIRATION_DATE "expiration_date" "Expiration Date" DATE`, `RENEWAL_TERM "renewal_term" "Renewal Term" DURATION`, `NOTICE_TO_TERMINATE_RENEWAL "notice_period_to_terminate_renewal" "Notice Period To Terminate Renewal" DURATION`, `GOVERNING_LAW "governing_law" "Governing Law" JURISDICTION`, `TERMINATION_FOR_CONVENIENCE "termination_for_convenience" "Termination For Convenience" TEXT`, `MINIMUM_COMMITMENT "minimum_commitment" "Minimum Commitment" MONEY`, `CAP_ON_LIABILITY "cap_on_liability" "Cap On Liability" TEXT`, `LICENSE_GRANT "license_grant" "License Grant" TEXT`. `static Category fromKey(String)`, `static Category fromCuadLabel(String)` (throw `IllegalArgumentException` on unknown).
  - `record PageText(int number, String text, Source source)` with `enum Source { TEXT_LAYER, OCR }`; `number` is 1-based.
  - `record DocumentText(String sha256, List<PageText> pages)`; `PageText page(int number)`.
  - `enum Confidence { LOW, MEDIUM, HIGH }`
  - `record Evidence(int page, String quote)`
  - `record FieldCandidate(Category category, List<String> values, List<Evidence> evidence, Confidence confidence)` — `values` empty = not present; scalar categories hold at most one value.
  - `record Usage(long inputTokens, long outputTokens, long latencyMillis, BigDecimal costUsd)` with `Usage plus(Usage)` and `static Usage ZERO`.
  - `record WindowResult(int windowIndex, List<FieldCandidate> candidates, Usage usage)`
  - `enum EvidenceStatus { VERIFIED, FUZZY, WRONG_PAGE, UNSUPPORTED }`
  - `record VerifiedEvidence(Evidence claimed, EvidenceStatus status, int page, int start, int end)` — `page/start/end` are where it was found (original page text offsets, end exclusive); `-1` when UNSUPPORTED.
  - `record FieldResult(Category category, List<String> values, List<VerifiedEvidence> evidence, Confidence confidence)`
  - `record DocumentExtraction(String documentSha256, List<FieldResult> fields, Usage usage, String provider, String model, String promptVersion)`

- [ ] **Step 1:** Write `CategoryTest`: `Category.values()` has 12 entries; `Category.fromCuadLabel("Cap On Liability") == CAP_ON_LIABILITY`; `Category.fromKey("parties").type() == PARTY_LIST`; all keys unique and match `[a-z_]+`; `fromKey("nope")` throws `IllegalArgumentException`.
- [ ] **Step 2:** Run `./mvnw -q -pl core test -Dtest=CategoryTest`. Expected: compilation failure.
- [ ] **Step 3:** Implement the types above (records with compact constructors that copy lists with `List.copyOf` and reject nulls).
- [ ] **Step 4:** Run the test. Expected: PASS.
- [ ] **Step 5:** Commit: `feat(core): add domain model and v1 category set`.

---

### Task 3: PDF text extraction

**Files:**
- Create: `core/.../pdf/PdfTextExtractor.java`, `UnreadablePdfException.java`, `PageLimitExceededException.java`
- Test: `core/src/test/java/.../pdf/PdfTextExtractorTest.java`, test helper `core/src/test/java/.../testutil/TestPdfs.java`

**Interfaces:**
- Consumes: `PageText`, `DocumentText` (Task 2).
- Produces:
  - `PdfTextExtractor(int maxPages)`; `RawPdf read(Path pdf)` where `record RawPdf(String sha256, int pageCount, List<String> textLayerPages)`; throws `UnreadablePdfException` (not a PDF, corrupt, encrypted) and `PageLimitExceededException(int pages, int max)`.
  - `static final int OCR_TRIGGER_MIN_CHARS = 20`; `static boolean needsOcr(String pageText)` = fewer than 20 non-whitespace chars.
  - `TestPdfs.textPdf(List<String> pages) -> Path` and `TestPdfs.imageOnlyPdf(List<String> pages) -> Path` (renders text to a BufferedImage per page and embeds it) for tests in later tasks.

- [ ] **Step 1:** Write tests: `readsTextPerPage` (3-page text PDF → `pageCount 3`, page 2 text contains "Governing Law"); `sha256IsStableForSameBytes`; `imageOnlyPageNeedsOcr` (`needsOcr` true for image-only page text, false for text page); `rejectsPageCountAboveMax` (`new PdfTextExtractor(2).read(3-page)` throws `PageLimitExceededException` with `pages()==3`); `rejectsNonPdf` (a `.pdf` file containing `"hello"` → `UnreadablePdfException`, message contains "not a readable PDF"); `rejectsEncryptedPdf` (PDF saved with PDFBox `StandardProtectionPolicy` and a user password → `UnreadablePdfException`, message contains "encrypted").
- [ ] **Step 2:** Run `./mvnw -q -pl core test -Dtest=PdfTextExtractorTest`. Expected: FAIL (compile).
- [ ] **Step 3:** Implement with PDFBox 3 `Loader.loadPDF(File)` and `PDFTextStripper` per page (`setStartPage/setEndPage`, `setSortByPosition(true)`); sha256 over file bytes (`corpus/Sha256.of(Path)` helper can be created here and reused in Task 13). Catch `InvalidPasswordException` → encrypted message; `IOException` → not-readable message.
- [ ] **Step 4:** Run test. Expected: PASS.
- [ ] **Step 5:** Commit: `feat(core): extract per-page text from PDFs with typed errors`.

---

### Task 4: OCR fallback

**Files:**
- Create: `core/.../ocr/OcrEngine.java`, `TesseractOcrEngine.java`, `PageTextResolver.java`
- Test: `core/src/test/java/.../ocr/PageTextResolverTest.java`, `TesseractOcrEngineIT.java`

**Interfaces:**
- Consumes: `PdfTextExtractor.RawPdf`, `needsOcr`, `TestPdfs` (Task 3).
- Produces:
  - `interface OcrEngine { String ocrPage(Path pdf, int pageNumber) throws OcrException; }`
  - `TesseractOcrEngine(Path tessdataDir, String language)`; renders page at 300 DPI (PDFBox `PDFRenderer.renderImageWithDPI`), OCR via Tess4J `Tesseract.doOCR(BufferedImage)`. `static Optional<Path> defaultTessdata()` reads env `TESSDATA_PREFIX`, falls back to `/usr/share/tesseract-ocr/5/tessdata` and `/usr/share/tesseract-ocr/4.00/tessdata`, returns empty if no `eng.traineddata` found.
  - `PageTextResolver(PdfTextExtractor, Optional<OcrEngine>)`; `DocumentText resolve(Path pdf)` — text layer for pages that don't need OCR, OCR otherwise (`Source.OCR`). If a page needs OCR and no engine is configured → `UnreadablePdfException("page N has no text layer and OCR is not configured")`.

- [ ] **Step 1:** Write `PageTextResolverTest` with a fake `OcrEngine` returning `"OCR TEXT p" + n`: mixed PDF (page 1 text, page 2 image) → page 1 `TEXT_LAYER` with original text, page 2 `OCR` with `"OCR TEXT p2"`, fake called exactly once; `noEngineAndImagePageFailsClearly` asserts the message above.
- [ ] **Step 2:** Write `TesseractOcrEngineIT` (JUnit `@EnabledIf("tessdataAvailable")`): image-only PDF of `"GOVERNING LAW. This Agreement shall be governed by the laws of the State of New York."` → OCR output (lowercased, whitespace-collapsed) contains `"governed by the laws of the state of new york"`.
- [ ] **Step 3:** Run `./mvnw -q -pl core test -Dtest='PageTextResolverTest,TesseractOcrEngineIT'`. Expected: FAIL (compile).
- [ ] **Step 4:** Implement. Surefire must include `*IT` classes in the normal `test` phase (configure `<includes>` in the parent pom) so CI runs it where Tesseract is installed.
- [ ] **Step 5:** Run tests. Expected locally: resolver PASS, IT skipped unless `eng.traineddata` is present (ask the user before downloading it from `github.com/tesseract-ocr/tessdata_fast`, ~4 MB). In CI both run.
- [ ] **Step 6:** Commit: `feat(core): add tesseract OCR fallback for pages without text layer`.

---

### Task 5: Match-text normalization and evidence verification

**Files:**
- Create: `core/.../text/MatchText.java`, `TextNormalizer.java`, `core/.../evidence/EvidenceVerifier.java`
- Test: `core/src/test/java/.../text/TextNormalizerTest.java`, `core/src/test/java/.../evidence/EvidenceVerifierTest.java`

**Interfaces:**
- Consumes: `DocumentText`, `Evidence`, `VerifiedEvidence`, `EvidenceStatus` (Task 2).
- Produces:
  - `record MatchText(String normalized, int[] originalOffsets)` — `originalOffsets[i]` = index in the original string of normalized char `i`; `int originalStart(int i)`, `int originalEnd(int iExclusive)`.
  - `TextNormalizer.forMatching(String original) -> MatchText`: lowercase; curly quotes → straight; en/em dash and minus → `-`; `-\n` + letter → join (remove hyphen and newline); all whitespace runs → single space; trim.
  - `EvidenceVerifier.verify(DocumentText doc, Evidence ev) -> VerifiedEvidence`; constants `FUZZY_MIN_SIMILARITY = 0.90`.

- [ ] **Step 1:** Write `TextNormalizerTest`: `"Termi-\nnation  for\tConvenience"` → `"termination for convenience"`; `"“Agreement”"` → `"\"agreement\""`; `"12–24"` → `"12-24"`; offsets map normalized index of `"c"` in "convenience" back to original index of `"C"`.
- [ ] **Step 2:** Write `EvidenceVerifierTest` on a 2-page `DocumentText`:
  - exact quote on claimed page → `VERIFIED`, `page` = claimed, `doc.page(p).text().substring(start,end)` equals the original-cased quote span;
  - quote with curly quotes / different spacing / hyphenated line break vs page text → `VERIFIED` (Review Focus 2);
  - quote with one wrong character in 40 → `FUZZY`;
  - quote present only on the other page → `WRONG_PAGE`, `page` = actual page;
  - invented quote → `UNSUPPORTED`, `start == -1`;
  - quote present on both pages, claimed page 2 → `VERIFIED` on page 2 (claimed page wins).
- [ ] **Step 3:** Run `./mvnw -q -pl core test -Dtest='TextNormalizerTest,EvidenceVerifierTest'`. Expected: FAIL.
- [ ] **Step 4:** Implement. Order: exact normalized `indexOf` on claimed page → on other pages (ascending) → fuzzy on claimed page → fuzzy on other pages. Fuzzy: slide a window of the quote's normalized length (±10%) across the page in steps of 1 word boundary; similarity = `1 - levenshtein/max(len)`; take the best; accept ≥ 0.90. Quotes shorter than 8 normalized chars are never fuzzy-matched (exact only).
- [ ] **Step 5:** Run tests. Expected: PASS.
- [ ] **Step 6:** Commit: `feat(core): verify evidence quotes against page text`.

---

### Task 6: Value normalization

**Files:**
- Create: `core/.../normalize/NormalizedValue.java`, `ValueNormalizer.java`, `PartyNames.java`
- Test: `core/src/test/java/.../normalize/ValueNormalizerTest.java`

**Interfaces:**
- Consumes: `FieldType` (Task 2).
- Produces:
  - `sealed interface NormalizedValue` with records `DateValue(LocalDate date)`, `DurationValue(int months, int days)`, `MoneyValue(BigDecimal amount, String currency)`, `PartyValue(String canonical)`, `JurisdictionValue(String canonical)`, `TextValue(String text)`, `Unparsed(String raw)`; each has `String display()`.
  - `ValueNormalizer.normalize(FieldType type, String raw) -> NormalizedValue` (never throws; unparseable → `Unparsed`).
  - `PartyNames.canonical(String) -> String`.

- [ ] **Step 1:** Write table-driven tests (`@ParameterizedTest @CsvSource`):
  - DATE: `"2021-03-03"`, `"March 3, 2021"`, `"3rd day of March, 2021"`, `"03/03/2021"` → `2021-03-03`; `"sometime"` → `Unparsed`.
  - DURATION: `"12 months"`, `"twelve (12) months"`, `"1 year"`, `"one year"` → 12 months 0 days; `"90 days"`, `"ninety (90) days"` → 0 months 90 days; `"36 months"` → 36.
  - MONEY: `"US$250,000"`, `"250,000 USD"`, `"$250,000.00"`, `"Two Hundred Fifty Thousand U.S. Dollars (US$250,000)"` → `250000 USD`.
  - PARTY_LIST (via `PartyNames.canonical`): `"Northwind Analytics, Inc."`, `"NORTHWIND ANALYTICS INC"` → `"northwind analytics"`; `"Bluewater Clinical Partners LLC"` → `"bluewater clinical partners"`; suffixes stripped: inc, incorporated, llc, l.l.c., ltd, limited, corp, corporation, co, company, plc, gmbh, s.a., ltda.
  - JURISDICTION: `"New York"`, `"the State of New York"`, `"laws of the State of New York"` → `"new york"`.
  - TEXT: trimmed, whitespace-collapsed.
- [ ] **Step 2:** Run `./mvnw -q -pl core test -Dtest=ValueNormalizerTest`. Expected: FAIL.
- [ ] **Step 3:** Implement. Number words up to "one hundred" plus "thousand"/"million" multipliers; when a parenthesized digit form is present (`twelve (12)`), the digits win. Dates: try ISO, `MMMM d, uuuu`, `d MMMM uuuu`, ordinal-day pattern, `MM/dd/uuuu` (US order).
- [ ] **Step 4:** Run tests. Expected: PASS.
- [ ] **Step 5:** Commit: `feat(core): normalize dates, durations, money, parties and jurisdictions`.

---

### Task 7: Page windows, prompt rendering and candidate merge

**Files:**
- Create: `core/.../window/Window.java`, `PageWindows.java`, `WindowPrompt.java`, `CandidateMerger.java`
- Test: `core/src/test/java/.../window/PageWindowsTest.java`, `CandidateMergerTest.java`

**Interfaces:**
- Consumes: `DocumentText`, `WindowResult`, `FieldCandidate`, `VerifiedEvidence`, `FieldResult`, `EvidenceVerifier` (Task 5), `PartyNames` (Task 6).
- Produces:
  - `record Window(int index, List<PageText> pages)`
  - `PageWindows.split(DocumentText doc, int size, int overlap) -> List<Window>`; `size <= 0` means one window with all pages; requires `0 <= overlap < size`.
  - `WindowPrompt.render(Window w) -> String`: pages as `<page n="N">\n{text}\n</page>` joined by `\n`.
  - `CandidateMerger.merge(DocumentText doc, List<WindowResult> results, EvidenceVerifier verifier) -> List<FieldResult>` — one `FieldResult` per `Category`, in enum order.

- [ ] **Step 1:** Write `PageWindowsTest`: 14 pages, size 6, overlap 1 → windows `[1-6],[6-11],[11-14]`; 5 pages size 6 → one window; size 0 → one window of all; `overlap >= size` → `IllegalArgumentException`; render contains `<page n="6">` and keeps page text verbatim.
- [ ] **Step 2:** Write `CandidateMergerTest`:
  - scalar category: window A value with `UNSUPPORTED` evidence, window B value with `VERIFIED` → B wins;
  - both verified, A `LOW`, B `HIGH` confidence → B; tie on both → earliest evidence page;
  - all windows empty → `values` empty, evidence empty;
  - parties `["Northwind Analytics, Inc."]` in window 1 and `["NORTHWIND ANALYTICS INC", "Bluewater Clinical Partners LLC"]` in window 2 → 2 values, first-seen spelling kept; duplicate evidence (same page+start) kept once (Review Focus 4);
  - a candidate whose every evidence item is `UNSUPPORTED` is still returned only if no other candidate exists, and keeps the `UNSUPPORTED` status visible.
- [ ] **Step 3:** Run `./mvnw -q -pl core test -Dtest='PageWindowsTest,CandidateMergerTest'`. Expected: FAIL.
- [ ] **Step 4:** Implement. Candidate rank key for scalar categories: (has any VERIFIED/FUZZY/WRONG_PAGE evidence desc, confidence desc, min evidence page asc).
- [ ] **Step 5:** Run tests. Expected: PASS.
- [ ] **Step 6:** Commit: `feat(core): split documents into page windows and merge candidates`.

---

### Task 8: LLM contract, schema and Ollama provider

**Files:**
- Create: `core/.../llm/LlmExtractor.java`, `ExtractionRequest.java`, `ExtractionSchema.java`, `Prompts.java`, `ResponseParser.java`, `ExtractionException.java`, `core/.../llm/ollama/OllamaConfig.java`, `OllamaExtractor.java`
- Test: `core/src/test/java/.../llm/ExtractionSchemaTest.java`, `ResponseParserTest.java`, `core/src/test/java/.../llm/ollama/OllamaExtractorTest.java`

**Interfaces:**
- Consumes: `Window`, `WindowPrompt` (Task 7), model types (Task 2).
- Produces:
  - `record ExtractionRequest(String documentSha256, Window window, List<Category> categories)`
  - `interface LlmExtractor { WindowResult extract(ExtractionRequest req) throws ExtractionException; String provider(); String model(); }`
  - `ExtractionSchema.forCategories(List<Category>) -> ObjectNode`: object with one required property per category key; each field `{value: string|null, evidence: [{page: integer, quote: string}], confidence: "low"|"medium"|"high"}`, except PARTY_LIST where `value` is `array of string`; `additionalProperties: false` at every object level; all properties required.
  - `Prompts.PROMPT_VERSION = "p1"`; `Prompts.system(List<Category>) -> String` lists each category key with its `description()` and says: short normalized value or null if not stated; `evidence[].quote` must be exact verbatim text copied from the given pages, at most 300 characters, with the page number from the `<page n>` tag; empty evidence when value is null; never paraphrase inside quotes.
  - `ResponseParser.parse(String json, List<Category>) -> List<FieldCandidate>` (throws `ExtractionException` on invalid JSON or missing keys; trims, drops blank values, maps confidence case-insensitively).
  - `record OllamaConfig(URI baseUrl, String model, int numCtx, Duration readTimeout)` with `static OllamaConfig defaults()` = `http://localhost:11434`, `qwen3.5:9b`, `16384`, `PT5M`.
  - `OllamaExtractor(OllamaConfig, HttpClient)`: `POST {baseUrl}/api/chat` body `{model, stream:false, think:false, format:<schema>, options:{temperature:0, num_ctx}, messages:[system, user=WindowPrompt.render]}`; usage from `prompt_eval_count`, `eval_count`, wall-clock latency, cost `0`. Invalid JSON → one retry, then `ExtractionException`. Non-2xx → `ExtractionException` with status and body excerpt. `ConnectException` → `ExtractionException("Ollama not reachable at <url>")` with no retry.

- [ ] **Step 1:** Write `ExtractionSchemaTest`: required list equals all 12 keys; `parties.properties.value.type == "array"`; `governing_law.properties.value.type == ["string","null"]`; every object node has `additionalProperties: false`.
- [ ] **Step 2:** Write `ResponseParserTest`: the spike's response shape (copy a JSON sample with `parties` as array, `expiration_date` null) parses to 12 candidates, `EXPIRATION_DATE` has empty values; malformed JSON throws `ExtractionException`; missing key throws.
- [ ] **Step 3:** Write `OllamaExtractorTest` with a JDK `com.sun.net.httpserver.HttpServer` stub on a random port: asserts request body has `format`, `think:false`, `options.temperature==0`, `options.num_ctx==16384`, model; returns a canned Ollama response (`message.content` = JSON string, `prompt_eval_count: 629`, `eval_count: 622`) → `usage.inputTokens()==629`; first response invalid JSON then valid → success after 2 calls; two invalid → `ExtractionException`; 500 → exception message contains `500`; server stopped → message contains `"Ollama not reachable at"` (Review Focus 3).
- [ ] **Step 4:** Run `./mvnw -q -pl core test -Dtest='ExtractionSchemaTest,ResponseParserTest,OllamaExtractorTest'`. Expected: FAIL.
- [ ] **Step 5:** Implement with Jackson + `java.net.http.HttpClient` (connect timeout 5 s, request timeout = `readTimeout`).
- [ ] **Step 6:** Run tests. Expected: PASS.
- [ ] **Step 7:** Manual check against the real local model (Ollama running, `qwen3.5:9b` pulled): a throwaway JUnit test `OllamaLiveIT` annotated `@EnabledIfEnvironmentVariable(named = "OLLAMA_LIVE", matches = "1")` extracts the 2-page fixture from Task 5 and asserts `GOVERNING_LAW` value normalizes to `new york`. Run `OLLAMA_LIVE=1 ./mvnw -q -pl core test -Dtest=OllamaLiveIT`. Expected: PASS in ~30–150 s.
- [ ] **Step 8:** Commit: `feat(core): add LLM extraction contract and Ollama provider`.

---

### Task 9: Claude provider (optional, mocked)

**Files:**
- Create: `core/.../llm/claude/ClaudeConfig.java`, `ClaudeExtractor.java`
- Test: `core/src/test/java/.../llm/claude/ClaudeExtractorTest.java`, `ClaudeLiveIT.java`

**Interfaces:**
- Consumes: `LlmExtractor`, `ExtractionRequest`, `ExtractionSchema`, `Prompts`, `ResponseParser` (Task 8).
- Produces:
  - `record ClaudeConfig(String model, String effort, int maxTokens)` with `defaults()` = `claude-opus-5-5`, `medium`, `16000`.
  - `ClaudeExtractor(ClaudeConfig, AnthropicClient)`; package-private `MessageCreateParams buildParams(ExtractionRequest)` and `WindowResult toResult(int windowIndex, Message message, long latencyMillis)` so both are unit-testable without network. Cost from a price table constant: Opus 5.5 $4 / $20 per MTok, Sonnet 5.5 $2 / $10, Haiku 5.5 $0.10 / $0.50; unknown model → cost `null` recorded as zero with a warning.

- [ ] **Step 1:** Before writing code, read the Java section of the Claude API skill (`claude-api` skill → `java/claude-api/README.md` and `tool-use.md`, sections Structured Output and Refusal Fallbacks) for exact builder names: manual schema via `OutputConfig.builder().format(JsonOutputFormat.builder().schema(...))`, effort in `output_config`, the refusal-fallback beta. Do not guess SDK names; compile and fix.
- [ ] **Step 2:** Write `ClaudeExtractorTest`: `buildParams` has model `claude-opus-5-5`, effort `medium`, no `thinking` disabled, the system prompt from `Prompts.system`, a single user message with the rendered window, output schema equal to `ExtractionSchema.forCategories`; `toResult` on a message with `stop_reason` `refusal` throws `ExtractionException` containing `"refusal"`; on `end_turn` with JSON text parses 12 candidates and computes cost for 1,000,000 input + 100,000 output tokens = `6.00` USD.
- [ ] **Step 3:** Write `ClaudeLiveIT` with `@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")` running the same assertion as `OllamaLiveIT`. It is skipped everywhere in v1.
- [ ] **Step 4:** Run `./mvnw -q -pl core test -Dtest='ClaudeExtractorTest'`. Expected: FAIL, then implement, then PASS.
- [ ] **Step 5:** Commit: `feat(core): add optional Claude provider with structured outputs`.

---

### Task 10: Recording and replay

**Files:**
- Create: `core/.../llm/record/RecordingKey.java`, `RecordingExtractor.java`, `ReplayExtractor.java`, `RecordingMissingException.java`
- Test: `core/src/test/java/.../llm/record/RecordReplayTest.java`

**Interfaces:**
- Consumes: `LlmExtractor`, `ExtractionRequest`, `WindowResult`, `Prompts.PROMPT_VERSION`.
- Produces:
  - `record RecordingKey(String provider, String model, String documentSha256, int windowIndex, String promptVersion)`; `Path resolve(Path recordingsRoot)` = `<root>/<provider>/<modelSlug>/<sha256>/<promptVersion>-w<windowIndex>.json`, `modelSlug` = model with `[^a-z0-9.-]` → `_`.
  - `RecordingExtractor(LlmExtractor delegate, Path root)` — delegates, writes `{key, candidates, usage, recordedAt}` JSON (pretty-printed, stable field order).
  - `ReplayExtractor(String provider, String model, Path root)` — reads; missing file → `RecordingMissingException` naming the path; `provider()`/`model()` return the recorded identity.

- [ ] **Step 1:** Write test: record through a fake delegate into `@TempDir`, then replay → equal `WindowResult`; replay of an unrecorded window throws with the expected path in the message; model `qwen3.5:9b` → slug `qwen3.5_9b`; writing the same recording twice yields byte-identical files.
- [ ] **Step 2:** Run `./mvnw -q -pl core test -Dtest=RecordReplayTest`. Expected: FAIL; implement; PASS.
- [ ] **Step 3:** Commit: `feat(core): record and replay provider responses`.

---

### Task 11: Extraction pipeline

**Files:**
- Create: `core/.../pipeline/ExtractionPipeline.java`
- Test: `core/src/test/java/.../pipeline/ExtractionPipelineTest.java`

**Interfaces:**
- Consumes: `PageTextResolver` (Task 4), `PageWindows`, `CandidateMerger` (Task 7), `LlmExtractor` (Task 8), `EvidenceVerifier` (Task 5).
- Produces: `ExtractionPipeline(PageTextResolver, LlmExtractor, int windowPages, int windowOverlap, List<Category>)`; `DocumentExtraction run(Path pdf)` and `DocumentExtraction run(DocumentText doc)`; usage summed over windows.

- [ ] **Step 1:** Write test with a fake `LlmExtractor` that returns, per window, candidates whose quotes come from that window's pages: 8-page text PDF, windows of 6/overlap 1 → fake called twice with window indexes 0 and 1; result has 12 fields; a field quoted in window 1 carries `VERIFIED` evidence with correct page; usage equals the sum of both windows.
- [ ] **Step 2:** Run `./mvnw -q -pl core test -Dtest=ExtractionPipelineTest`. Expected: FAIL; implement; PASS.
- [ ] **Step 3:** Commit: `feat(core): wire ingest, windows, extraction and verification into a pipeline`.

---

### Task 12: Scoring and run aggregation

**Files:**
- Create: `core/.../score/Outcome.java`, `SpanScorer.java`, `ValueScorer.java`, `CategoryScore.java`, `RunReport.java`, `RunAggregator.java`, `core/.../corpus/GoldLabel.java`
- Test: `core/src/test/java/.../score/SpanScorerTest.java`, `ValueScorerTest.java`, `RunAggregatorTest.java`

**Interfaces:**
- Consumes: `FieldResult`, `VerifiedEvidence`, `ValueNormalizer` (Task 6), `Category`.
- Produces:
  - `record GoldLabel(Category category, List<String> values, List<GoldSpan> spans)` with `record GoldSpan(int page, int start, int end, String text)`; CUAD gold has spans and no values; synthetic gold has values and spans.
  - `enum Outcome { CORRECT, PARTIAL, WRONG, MISSED, SPURIOUS, TRUE_EMPTY }`
  - `record CategoryScore(String docId, Category category, Outcome outcome, int predictedQuotes, int predictedHits, int goldSpans, int goldHit, boolean evidenceHit)`
  - `SpanScorer.score(String docId, FieldResult pred, GoldLabel gold) -> CategoryScore` — CUAD mode. Constant `SPAN_OVERLAP_MIN = 0.5`. Hit = same page and overlap length ≥ 50% of the shorter span. Outcome: both empty → TRUE_EMPTY; gold empty & pred non-empty → SPURIOUS; gold non-empty & pred empty → MISSED; else CORRECT if every gold span is hit, PARTIAL if some, WRONG if none.
  - `ValueScorer.score(String docId, FieldResult pred, GoldLabel gold) -> CategoryScore` — synthetic mode. Typed compare via `ValueNormalizer` (DATE/DURATION/MONEY/JURISDICTION equality → CORRECT else WRONG); PARTY_LIST set compare (all → CORRECT, some → PARTIAL, none → WRONG; extra parties do not downgrade CORRECT, they count as spurious quotes); TEXT token F1 (constants `TEXT_CORRECT_F1 = 0.8`, `TEXT_PARTIAL_F1 = 0.4`). `evidenceHit` = any predicted evidence page equals a gold span page.
  - `record RunReport(String runId, String manifestHash, String provider, String model, String promptVersion, int windowPages, int windowOverlap, String effort, Instant startedAt, Instant finishedAt, List<CategoryScore> scores, Aggregates cuad, Aggregates synthetic, Usage usage)` with `record Aggregates(Double precision, Double recall, Double f1, Double evidenceVerifiedRate, Double unsupportedRate, Map<Category, CategoryAggregate> byCategory)`; Jackson-serializable.
  - `RunAggregator.aggregate(List<CategoryScore> cuadScores, List<CategoryScore> syntheticScores, List<DocumentExtraction> extractions) -> (Aggregates cuad, Aggregates synthetic)`. CUAD precision = Σ predictedHits / Σ predictedQuotes, recall = Σ goldHit / Σ goldSpans. Synthetic precision = CORRECT + 0.5·PARTIAL over (non-empty predictions), recall = same numerator over (non-empty gold). F1 harmonic mean. Any empty denominator → `null` (Review Focus 5). `evidenceVerifiedRate` = VERIFIED+FUZZY+WRONG_PAGE over all evidence items; `unsupportedRate` = UNSUPPORTED over all.

- [ ] **Step 1:** Write `SpanScorerTest`: one gold span `[100,200)` page 3, predicted `[150,260)` page 3 → hit (overlap 50 ≥ 0.5·100) → CORRECT; predicted on page 4 → WRONG; gold 2 spans, one hit → PARTIAL; all four empty/non-empty combinations give TRUE_EMPTY / SPURIOUS / MISSED as specified.
- [ ] **Step 2:** Write `ValueScorerTest`: date `"March 3, 2021"` vs gold `"2021-03-03"` → CORRECT; `"60 days"` vs gold `"sixty (60) days"` → CORRECT; parties `[Northwind Analytics]` vs gold `[Northwind Analytics, Inc., Bluewater Clinical Partners LLC]` → PARTIAL; text token F1 0.85 → CORRECT, 0.5 → PARTIAL, 0.2 → WRONG.
- [ ] **Step 3:** Write `RunAggregatorTest`: hand-computed P/R/F1 for 3 docs; all scores TRUE_EMPTY → precision, recall and f1 are `null` (not NaN); `RunReport` round-trips through Jackson unchanged.
- [ ] **Step 4:** Run `./mvnw -q -pl core test -Dtest='SpanScorerTest,ValueScorerTest,RunAggregatorTest'`. Expected: FAIL; implement; PASS.
- [ ] **Step 5:** Commit: `feat(core): score extractions against gold spans and values`.

---

### Task 13: Corpus format and loader

**Files:**
- Create: `core/.../corpus/GoldFile.java`, `Manifest.java`, `CorpusDoc.java`, `CorpusLoader.java`, `Sha256.java` (if not created in Task 3), `corpus/manifest.json` (empty doc list), `corpus/LICENSE-CUAD`
- Test: `core/src/test/java/.../corpus/CorpusLoaderTest.java`

**Interfaces:**
- Consumes: `GoldLabel` (Task 12).
- Produces:
  - `gold.json` format: `{"docId": "...", "source": "CUAD"|"SYNTHETIC", "title": "...", "labels": [{"category": "<key>", "values": [...], "spans": [{"page":1,"start":0,"end":10,"text":"..."}]}], "unmappedSpans": [{"category":"<key>","text":"..."}]}` — every category key present (empty lists when absent).
  - `manifest.json` format: `{"version": 1, "categories": ["<key>", ...], "documents": [{"docId":"...","source":"CUAD","path":"cuad/<docId>/document.pdf","sha256":"..."}], "featuredRunId": null, "baselineF1": {"cuad": null, "synthetic": null}}`.
  - `record CorpusDoc(String docId, Source source, Path pdf, String sha256, String title, Map<Category, GoldLabel> gold)` with `enum Source { CUAD, SYNTHETIC }`.
  - `CorpusLoader.load(Path corpusRoot) -> Manifest` and `List<CorpusDoc> documents(Manifest)`; verifies each PDF's sha256 matches the manifest (mismatch → `IllegalStateException` naming the doc); `String manifestHash()` = sha256 of the canonical manifest JSON excluding `featuredRunId` and `baselineF1`.

- [ ] **Step 1:** Write test with a temp corpus of 2 docs (PDFs from `TestPdfs`): loads both, gold labels keyed by category, all 12 categories present; tampered PDF → `IllegalStateException`; changing `featuredRunId` does not change `manifestHash()`, adding a doc does.
- [ ] **Step 2:** Run `./mvnw -q -pl core test -Dtest=CorpusLoaderTest`. Expected: FAIL; implement; PASS.
- [ ] **Step 3:** `corpus/LICENSE-CUAD`: CUAD v1 attribution (The Atticus Project, CC BY 4.0, link to the dataset and license, note that a subset of documents and labels is redistributed and converted).
- [ ] **Step 4:** Commit: `feat(core): define corpus manifest and gold file formats`.

---

### Task 14: Eval CLI (run, compare) with regression gate

**Files:**
- Create: `eval/.../EvalMain.java`, `RunCommand.java`, `CompareCommand.java`, `ProviderFactory.java`
- Test: `eval/src/test/java/.../eval/RunCommandTest.java`, `eval/src/test/java/.../eval/RegressionGateTest.java`

**Interfaces:**
- Consumes: `CorpusLoader` (13), `ExtractionPipeline` (11), extractors (8–10), scorers and `RunAggregator` (12).
- Produces CLI (picocli):
  - `eval run [--corpus corpus] [--provider ollama|claude|replay] [--model M] [--record] [--replay-provider P --replay-model M] [--docs id1,id2] [--limit N] [--window-pages 6] [--window-overlap 1] [--out corpus/runs]` → writes `<out>/<runId>.json` (`runId` = `yyyyMMdd-HHmmss-<provider>-<modelSlug>`) and prints a summary table (per source: P, R, F1, verified rate, unsupported rate; total tokens; wall time). `--record` wraps the live provider in `RecordingExtractor` under `corpus/recordings`. Window defaults: ollama 6/1, claude 0/0.
  - `eval compare <runA.json> <runB.json>` prints per-category F1 deltas.
  - Exit codes: 0 ok, 2 usage error, 3 provider unreachable, 4 recording missing.
  - `ProviderFactory.create(options) -> LlmExtractor` reading env `OLLAMA_URL`, `DOC_EXTRACT_MODEL`, `ANTHROPIC_API_KEY`.

- [ ] **Step 1:** Write `RunCommandTest`: temp corpus (1 CUAD-style + 1 synthetic doc) + pre-written recordings for a fake provider identity → `eval run --provider replay --replay-provider fake --replay-model m1` exits 0, writes one run file whose `cuad.f1` and `synthetic.f1` equal hand-computed values; missing recording → exit code 4 and message contains the expected path; `--docs` filters to one doc.
- [ ] **Step 2:** Write `RegressionGateTest`: loads the real `corpus/manifest.json`; if `featuredRunId` is null → `Assumptions.abort("no featured run yet")`; else replays the featured run's provider/model from `corpus/recordings` and asserts `cuad.f1 >= baselineF1.cuad - 0.001` and the same for synthetic.
- [ ] **Step 3:** Run `./mvnw -q -pl eval test`. Expected: FAIL; implement; PASS (gate aborted until Task 17).
- [ ] **Step 4:** Commit: `feat(eval): add eval run/compare CLI with regression gate`.

---

### Task 15: Synthetic contract generator and scan degrader

**Files:**
- Create: `eval/.../eval/synthetic/SyntheticContractGenerator.java`, `ContractTemplate.java`, `ScanDegrader.java`, `eval/.../GenerateSyntheticCommand.java`
- Test: `eval/src/test/java/.../eval/synthetic/SyntheticContractGeneratorTest.java`, `ScanDegraderTest.java`

**Interfaces:**
- Consumes: `CorpusLoader` formats (13), `ValueNormalizer` (6), `PdfTextExtractor` (3).
- Produces:
  - `SyntheticContractGenerator.generate(long seed) -> GeneratedContract(String docId, String title, List<String> pageTexts, Map<Category, List<String>> values)`. Values drawn from fixed fictional pools (company names built from neutral word lists + suffix; US states for governing law; dates 2015–2025; terms 12/24/36/60 months; notice 30/60/90/180 days; money 50,000–2,000,000 step 25,000); each category written in one of 2–3 phrasings (e.g. `"twelve (12) months"` vs `"12 months"`), some categories omitted at random (~15%) so TRUE_EMPTY/SPURIOUS exist. 3–6 pages with neutral filler clauses (confidentiality, notices, assignment). Fictional service domains only (e.g. logistics, software support, facilities); nothing healthcare- or client-shaped.
  - `ScanDegrader.degrade(Path textPdf, Path outPdf, long seed)`: render each page at 200 DPI grayscale, rotate uniformly in ±1.5°, Gaussian noise σ=12, box blur radius 1, JPEG quality 40, write an image-only PDF.
  - `eval generate-synthetic --count 15 --seed-start 1 --out corpus/synthetic` writes `document.pdf` (degraded) and `gold.json` with values and spans (spans located by finding each value's phrasing in the **clean** page text), and appends docs to `manifest.json`.

- [ ] **Step 1:** Write `SyntheticContractGeneratorTest`: same seed → identical output; different seeds → different values; every non-omitted category's phrasing appears verbatim in exactly one page text; generated gold values normalize (via `ValueNormalizer`) to non-`Unparsed`.
- [ ] **Step 2:** Write `ScanDegraderTest`: output PDF page count equals input; `PdfTextExtractor.needsOcr` is true for every output page; same seed → byte-identical output.
- [ ] **Step 3:** Run `./mvnw -q -pl eval test -Dtest='SyntheticContractGeneratorTest,ScanDegraderTest'`. Expected: FAIL; implement; PASS.
- [ ] **Step 4:** Run `java -jar eval/target/eval.jar generate-synthetic --count 15 --seed-start 1 --out corpus/synthetic`. Expected: 15 folders, manifest lists 15 SYNTHETIC docs. Open 2 PDFs and confirm they look like skewed noisy scans that are still human-readable.
- [ ] **Step 5:** Commit generator code and generated corpus: `feat(eval): generate synthetic degraded contracts with exact gold`.

---

### Task 16: CUAD import and subset selection

**Files:**
- Create: `eval/.../eval/cuad/CuadImporter.java`, `CuadSelector.java`, `eval/.../ImportCuadCommand.java`
- Test: `eval/src/test/java/.../eval/cuad/CuadImporterTest.java`, `CuadSelectorTest.java`, fixture `eval/src/test/resources/cuad-mini.json`

**Interfaces:**
- Consumes: `Category.fromCuadLabel` (2), `PdfTextExtractor` + `TextNormalizer` (3, 5), corpus formats (13).
- Produces:
  - `CuadImporter.importDoc(String contractTitle, JsonNode cuadEntry, Path pdf) -> GoldFile`: for each v1 category, take CUAD answers for the question whose label (text inside the question's first quotes, e.g. `"Cap On Liability"`) maps via `fromCuadLabel`; locate each answer text in the PDF's normalized page text (`TextNormalizer.forMatching`), producing a `GoldSpan` with original offsets; answers not found go to `unmappedSpans`; `values` stay empty (CUAD = span scoring).
  - `CuadSelector.select(List<CandidateDoc> docs, int count, int maxPages) -> List<String>` where `record CandidateDoc(String title, int pages, int labeledCategories, int unmappedSpans)`: keep `pages <= 40` and `unmappedSpans == 0`, sort by `labeledCategories` desc then title asc, take `count`.
  - `eval import-cuad --cuad-dir corpus/raw/CUAD_v1 --count 25 --out corpus/cuad` → copies selected PDFs, writes `gold.json`, appends to `manifest.json`, prints a mapping report (per category: gold spans mapped / unmapped).

- [ ] **Step 1:** Write `CuadImporterTest` with `cuad-mini.json` (SQuAD-format entry hand-written for a 2-page `TestPdfs` contract: answers for Parties, Governing Law, and one answer not in the PDF) → 2 categories with spans on the right pages, text of span equals the answer modulo whitespace, 1 unmapped span.
- [ ] **Step 2:** Write `CuadSelectorTest`: a 50-page doc and a doc with unmapped spans are excluded; ordering by labeled categories then title; returns at most `count`.
- [ ] **Step 3:** Run `./mvnw -q -pl eval test -Dtest='CuadImporterTest,CuadSelectorTest'`. Expected: FAIL; implement; PASS.
- [ ] **Step 4:** Ask the user before downloading CUAD v1 (source: the Atticus Project's official release on Zenodo / GitHub `TheAtticusProject/cuad`, ~100 MB+ zip; check the size on the page and state it). Unzip into `corpus/raw/CUAD_v1` (git-ignored).
- [ ] **Step 5:** Run `java -jar eval/target/eval.jar import-cuad --cuad-dir corpus/raw/CUAD_v1 --count 25 --out corpus/cuad`. Expected: 25 docs imported, mapping report printed. If any v1 category has fewer than 5 gold spans across the 25 docs, remove it from `manifest.categories` (record which and why in the commit message).
- [ ] **Step 6:** Commit: `feat(eval): import CUAD subset with page-mapped gold spans`.

---

### Task 17: First real runs, model bake-off, featured run

**Files:**
- Modify: `corpus/manifest.json` (featuredRunId, baselineF1)
- Create: `corpus/recordings/**`, `corpus/runs/*.json`, `docs/results/2026-11-bakeoff.md`

**Interfaces:**
- Consumes: everything above; Ollama running locally with `qwen3.5:9b`.

- [ ] **Step 1:** Smoke: `java -jar eval/target/eval.jar run --provider ollama --model qwen3.5:9b --limit 3`. Expected: run file written, summary printed, no exceptions. Look at 3 documents' fields by hand; fix prompt issues (bump `PROMPT_VERSION` to `p2` etc. on any prompt change).
- [ ] **Step 2:** Full recorded run: `java -jar eval/target/eval.jar run --provider ollama --model qwen3.5:9b --record` (expect 1–2 h; run in background). Expected: run file for all 40 docs.
- [ ] **Step 3:** Ask the user before pulling each challenger (`ollama pull gemma4:e4b`, a Ministral 3 8B build; state size from `ollama show`/library page). Run the same recorded full run for each.
- [ ] **Step 4:** `eval compare` the runs; write `docs/results/2026-11-bakeoff.md` with a table (model, CUAD P/R/F1, synthetic P/R/F1, verified %, unsupported %, avg s/window) and 3–5 sentences of honest findings.
- [ ] **Step 5:** Set `featuredRunId` to the best run by overall F1 (ties: verified-evidence rate, then latency) and `baselineF1` to its numbers. Run `./mvnw -q verify`. Expected: `RegressionGateTest` now runs (not aborted) and PASSES using replay only.
- [ ] **Step 6:** Commit: `feat(corpus): record bake-off runs and pin featured run`.

---

## Self-review notes

- Spec coverage for Part 1: §6.2 pipeline (Tasks 3–11), §6.2.1 multi-value evidence (2, 7, 12), §6.3 categories (2, 16 Step 5), §6.4 split scoring + bake-off (12, 17), §6.5 corpus (13, 15, 16), §6.11 core/eval tests, recordings and CI gate (1, 10, 14, 17). Deferred to Plan 2: §6.2.2 seeder, §6.6 persistence, §6.7 API, §6.8 worker, §6.9 guardrails (incl. the 1-in-flight Ollama limit). Deferred to Plan 3: §6.10 UI, §6.12 packaging, §6.13 README.
- Spec deviation, flagged: `OllamaExtractor` uses the JDK `HttpClient` rather than Spring `RestClient` so `core` stays Spring-free (spec §6.1 requires `core` to have no Spring).
