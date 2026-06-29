# CLAUDE.md — AI-Powered Log Anomaly Summarizer

This file gives Claude Code full context on the project so it can work effectively without re-deriving architecture from scratch each session.

---

## Project Overview

Take-home assessment: build an AI-powered REST API that accepts structured log entries and returns a human-readable anomaly summary, key error signatures, and a recommended action.

**Endpoint:** `POST /api/summarize-logs`

**Input:**
```json
{
  "logs": [
    {
      "timestamp": "2025-10-15T10:00:05Z",
      "level": "ERROR",
      "service": "payment-service",
      "message": "Database connection timed out after 3001ms"
    }
  ]
}
```

**Output:**
```json
{
  "summary": "Human readable summary",
  "key_error_signatures": ["Database connection timed out"],
  "recommendation": "Recommended action"
}
```

---

## Tech Stack

| Item | Detail |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.0.6 (pom.xml on disk; assessment spec says 3.x — design is compatible with both) |
| Build | Maven |
| AI Provider | Ollama (initial); provider-agnostic via `AiClient` interface |
| Lombok | Yes — use `@Data`, `@Builder`, `@RequiredArgsConstructor` throughout |
| Testing | JUnit 5, Mockito, WireMock, `@WebMvcTest`, `@SpringBootTest` |
| Containerisation | Docker multi-stage + docker-compose (app + Ollama) |

---

## Base Package

```
com.neaz.logsummarizer
```

> The assessment spec listed `com.neaz.ai.logsummarizer` but the generated source uses `com.neaz.logsummarizer`. Always use `com.neaz.logsummarizer`.

---

## Architecture

Clean Architecture / Hexagonal (Ports and Adapters) layered on Spring Boot conventions.

```
HTTP (Controller)
    ↓
Service Layer  ←→  AI Port (AiClient interface)
    ↓                        ↓
Validation            AI Adapter (OllamaClient)
                             ↓
                       Ollama REST API
```

**Key rule:** The service layer (`LogSummarizerServiceImpl`) depends only on the `AiClient` interface. It never imports anything from `ai/ollama/` or any other provider sub-package. Adding a new provider (OpenAI, Gemini, Claude) means writing one new class only.

---

## Package Structure

```
src/main/java/com/neaz/logsummarizer/
├── LogSummarizerApplication.java
├── controller/
│   └── LogSummarizerController.java
├── service/
│   ├── LogSummarizerService.java          (interface)
│   └── LogSummarizerServiceImpl.java
├── ai/
│   ├── AiClient.java                      (interface — the AI port)
│   ├── PromptBuilderService.java
│   └── ollama/
│       ├── OllamaClient.java
│       ├── OllamaRequest.java
│       └── OllamaResponse.java
├── config/
│   ├── AiProviderConfig.java              (@ConfigurationProperties)
│   ├── RestClientConfig.java
│   └── JacksonConfig.java
├── dto/
│   ├── LogEntry.java
│   ├── SummarizeLogsRequest.java
│   └── SummarizeLogsResponse.java
├── exception/
│   ├── AiClientException.java
│   ├── InvalidLogRequestException.java
│   ├── ErrorResponse.java
│   └── GlobalExceptionHandler.java
├── util/
│   └── LogLevelUtils.java
└── validator/
    └── LogRequestValidator.java
```

---

## Key Design Decisions

### AiClient Interface
```
String complete(String prompt)
```
- Single method, returns raw String (not a typed DTO)
- Throws `AiClientException` (unchecked) on any transport or provider error
- Concrete implementations live under `ai/<provider>/` sub-packages
- Activated via `@ConditionalOnProperty(name = "ai.provider", havingValue = "ollama")`

### Provider Switching
Change `ai.provider=ollama` in `application.properties` — no code changes needed. Future providers implement `AiClient` and add `@ConditionalOnProperty`.

### Prompt Engineering
`PromptBuilderService` owns all prompt construction. It is pure logic with no Spring dependencies except `@Service`. Prompt instructs Ollama to respond with JSON only, and includes the exact output schema inline.

### Validation — Two Tiers
1. **Bean Validation** (`@Valid` + annotations on DTOs) — structural checks (not blank, not empty)
2. **`LogRequestValidator`** — semantic checks (valid log level enum, parseable ISO-8601 timestamp, max 500 entries)

### Service Orchestration (`LogSummarizerServiceImpl`)
1. `LogRequestValidator.validate(request)`
2. `filterAnomalousLogs` — keep ERROR / WARN / FATAL only
3. `PromptBuilderService.buildPrompt(filteredLogs)`
4. `AiClient.complete(prompt)`
5. `ObjectMapper.readValue(rawJson, SummarizeLogsResponse.class)`

### Exception Handling
All exceptions surface through `GlobalExceptionHandler` (`@RestControllerAdvice`). No try/catch in the controller. `ErrorResponse` DTO always returned — never raw Spring error pages.

| Exception | HTTP Status |
|---|---|
| `InvalidLogRequestException` | 400 |
| `MethodArgumentNotValidException` | 400 |
| `AiClientException` | 502 |
| `Exception` (catch-all) | 500 |

---

## Configuration Properties

All AI-related config is under the `ai.*` namespace, bound via `@ConfigurationProperties`.

```properties
# application.properties
ai.provider=ollama
ai.ollama.base-url=http://localhost:11434
ai.ollama.model=llama3
ai.ollama.timeout-seconds=30
```

Docker profile overrides in `application-docker.properties`:
```properties
ai.ollama.base-url=http://ollama:11434
```

---

## Running the Project

### With Docker Compose (full stack including Ollama)
```bash
docker compose up --build
docker exec -it ollama ollama pull llama3
```
App available at `http://localhost:8080`.

### Locally (requires Ollama running separately)
```bash
ollama pull llama3
ollama serve
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### Build and test
```bash
mvn clean verify
```

---

## Testing Strategy

| Test Class | Type | What It Tests |
|---|---|---|
| `LogSummarizerControllerTest` | `@WebMvcTest` | HTTP layer, request validation, status codes |
| `LogSummarizerServiceImplTest` | JUnit 5 + Mockito | Service orchestration, edge cases |
| `PromptBuilderServiceTest` | JUnit 5 | Prompt structure and content |
| `OllamaClientTest` | JUnit 5 + WireMock | HTTP adapter, error handling |
| `LogRequestValidatorTest` | JUnit 5 | Validation rules |
| `LogSummarizerIntegrationTest` | `@SpringBootTest` | Full round-trip with `TestAiClient` stub |

Integration tests use a `@TestConfiguration` bean that replaces `OllamaClient` with a `MockAiClient` returning fixed JSON. Never call real Ollama in CI.

---

## Logging Rules

- Never log full log message content at INFO or above (may contain PII)
- Never log prompt content at INFO or above
- Add `requestId` (UUID) to MDC at controller entry; remove at exit
- JSON logging active on `prod` profile via `logback-spring.xml`

---

## What NOT to Do

- Do not add try/catch in the controller — let `GlobalExceptionHandler` handle it
- Do not import `OllamaClient` (or any provider class) from the service layer
- Do not add fields to `AiClient` interface without considering all implementors
- Do not log stack traces at WARN level — only at ERROR
- Do not hardcode Ollama URL or model name in `OllamaClient` — always read from config
- Do not return Spring's default error response shape — always use `ErrorResponse`

---

## Commit Convention

Format: `type: description`

Types: `feat`, `test`, `config`, `chore`, `docs`, `fix`

See the implementation roadmap for the full 19-commit plan.

---

## Full Implementation Roadmap

The detailed class-by-class plan, sequence diagram, architecture diagram, and all design rationale were produced in the initial planning session. Ask Claude to recall or regenerate any section if needed. The 19-commit sequence is:

1. `chore:` pom.xml dependencies
2. `chore:` logback + properties
3. `feat:` DTOs
4. `config:` Config classes
5. `feat:` AiClient interface + Ollama internal DTOs
6. `feat:` OllamaClient
7. `test:` OllamaClientTest
8. `feat:` PromptBuilderService
9. `test:` PromptBuilderServiceTest
10. `feat:` LogLevelUtils + LogRequestValidator
11. `test:` LogRequestValidatorTest
12. `feat:` Exceptions + GlobalExceptionHandler
13. `feat:` LogSummarizerService + Impl
14. `test:` LogSummarizerServiceImplTest
15. `feat:` LogSummarizerController
16. `test:` LogSummarizerControllerTest
17. `test:` LogSummarizerIntegrationTest
18. `chore:` Dockerfile + docker-compose
19. `docs:` README.md