# doc-extract

Extract key fields from contract PDFs with a local LLM, then check every claim against the source text, so each value links to a quote you can verify on the page.

*Em português: extrai campos de contratos em PDF com um LLM local e confere cada trecho citado contra o texto original.*

## What it does

Upload a contract PDF (or open a featured one) and get six fields: parties, agreement date, effective date, expiration date, governing law and renewal term. Each value comes with the quote the model claims supports it, and the UI highlights that quote on its page.

Stack: Spring Boot 4.1.2, Java 21, [Ollama](https://ollama.com) (default model `qwen3.5:9b`), PDFBox. No cloud calls.

## How verification works

The model never gets to mark its own homework. For every quote it returns, the app searches the extracted page text:

| Status | Meaning |
|---|---|
| `VERIFIED` | Quote found exactly after normalization (hyphenated line breaks, curly quotes, whitespace) |
| `FUZZY` | Close match, not exact |
| `WRONG_PAGE` | Found on another page; the page is corrected |
| `UNSUPPORTED` | Not found anywhere, or shares no word with the value it backs (also used for out-of-range page numbers) |

## How scoring works

The eval compares extracted evidence with CUAD gold spans over a small corpus (`corpus/manifest.json`).

- A span is a hit when it overlaps a gold span on the same page by at least 50% of the shorter span.
- Precision, recall and F1 per category, plus micro-averaged overall F1, supported rate (verified, fuzzy or wrong-page evidence) and unsupported rate.
- Gold spans that cannot be located in our extracted text are reported as unmapped and excluded from recall.
- `--eval` exits 1 if any document fails or overall F1 drops below `corpus/baseline.json`.

## Results

10 CUAD contracts (1–6 pages each), `qwen3.5:9b` via local Ollama, prompt `v1`. Reproduce with `--eval` (replays `corpus/recordings/`).

| category | precision | recall | F1 |
|---|---|---|---|
| parties | 0.64 | 0.40 | 0.49 |
| agreement_date | 1.00 | 0.70 | 0.82 |
| effective_date | 1.00 | 0.56 | 0.71 |
| expiration_date | 1.00 | 0.83 | 0.91 |
| governing_law | 1.00 | 1.00 | 1.00 |
| renewal_term | 1.00 | 1.00 | 1.00 |
| **overall** | | | **0.69** |

Supported rate 0.75: 25% of the model's quotes are flagged `UNSUPPORTED`, either because they are not on the cited page or because they share no word with the value they claim to back (e.g. the quote "This Endorsement Agreement (" offered for a date). 0 unmapped gold spans. CI fails below F1 0.67 (`corpus/baseline.json`).

Contracts were picked from CUAD v1 by a fixed rule: labels in at least 4 of the 6 categories, at least 85% of their gold spans locatable in the PDF text layer, one contract per company; the CUAD title of each is kept in `corpus/manifest.json`. Small sample — read the numbers as a regression baseline, not a benchmark.

## Run

Requires JDK 21 and, for live extraction, Ollama with the model pulled (`ollama pull qwen3.5:9b`).

```
# local
./mvnw -q verify
java -jar target/doc-extract-0.1.0-SNAPSHOT.jar
# open http://localhost:8080

# docker (Ollama on the host)
docker build -t doc-extract .
docker run --rm -p 8080:8080 --add-host=host.docker.internal:host-gateway doc-extract

# eval: replay committed recordings, no Ollama needed
java -jar target/doc-extract-0.1.0-SNAPSHOT.jar --eval

# eval (record): calls Ollama for windows that have no recording yet; delete corpus/recordings/ to re-record after changing the prompt or model
java -jar target/doc-extract-0.1.0-SNAPSHOT.jar --eval --record
```

On Windows use `.\mvnw.cmd`. The eval writes `results/featured.json` (served by the UI) and prints a Markdown table.

Configuration: `OLLAMA_URL` (default `http://localhost:11434`; the Docker image defaults to `http://host.docker.internal:11434`), `DOC_EXTRACT_MODEL` (default `qwen3.5:9b`), `docextract.results-file`, `docextract.corpus-dir`.

API: `GET /api/documents`, `GET /api/documents/{id}`, `POST /api/extract` (multipart `file`; PDF, max 10 MB, max 10 pages; 503 when Ollama is down).

## Limitations

- No OCR: scanned PDFs have no text layer and yield nothing.
- A local 9B model misses and misreads things; the verification step exists to expose that, not to fix it.
- CUAD gold spans are located by first occurrence, so repeated text may map to the wrong place.
- Upload is synchronous; a slow model means a slow request.

## Attribution

Corpus excerpts and annotations derive from [CUAD v1](https://www.atticusprojectai.org/cuad) by The Atticus Project, [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). See `corpus/LICENSE-CUAD`.
