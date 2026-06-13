package com.ai.studyassistant.service;

import com.ai.studyassistant.exception.GeminiApiException;
import com.ai.studyassistant.model.MaterialType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * AI orchestration client that wraps the Gemini 1.5 Flash REST API.
 *
 * <h2>Transport</h2>
 * Uses Java 11+ native {@link HttpClient} — no extra HTTP client dependency needed.
 * A single {@code HttpClient} instance is shared across all requests (thread-safe).
 *
 * <h2>Structured output</h2>
 * {@code generationConfig.responseMimeType = "application/json"} instructs Gemini
 * to return raw JSON with no markdown fences, no prose — just the array.
 * The system instruction for each {@link MaterialType} further reinforces the schema.
 *
 * <h2>API key security</h2>
 * The key is injected via {@code @Value("${gemini.api.key}")} and resolved at runtime
 * from the {@code GEMINI_API_KEY} environment variable (see {@code application.properties}).
 * It is appended only to the request URI at call time; it is never logged.
 */
@Slf4j
@Service
public class GeminiService {

    // ─── Configuration (injected from application.properties) ─────────────────

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.base-url}")
    private String baseUrl;

    @Value("${gemini.api.model}")
    private String model;

    // ─── Infrastructure ───────────────────────────────────────────────────────

    private final ObjectMapper objectMapper;
    private HttpClient httpClient;

    public GeminiService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Builds the shared {@link HttpClient} after Spring has injected all {@code @Value} fields.
     * Separating construction from Spring wiring avoids NPE races in tests.
     */
    @PostConstruct
    void initHttpClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .version(HttpClient.Version.HTTP_2)  // prefer HTTP/2; falls back to HTTP/1.1
                .build();
        log.info("GeminiService initialised – model={}, baseUrl={}", model, baseUrl);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Calls the Gemini API and returns a structured JSON string (no markdown wrapping).
     *
     * @param extractedText the raw text extracted from the PDF (pages 1-5)
     * @param materialType  determines which system prompt and schema to use
     * @return a valid JSON array string — ready to be stored as {@code LONGTEXT} or
     *         parsed directly by the frontend
     * @throws GeminiApiException if the API returns a non-200 status, an empty
     *                            response, or malformed JSON
     */
    public String generate(String extractedText, MaterialType materialType) {
        String endpoint = buildEndpoint();
        String requestBody = buildRequestBody(extractedText, materialType);

        log.debug("Sending Gemini request: endpoint={}, materialType={}, textLength={}",
                endpoint, materialType, extractedText.length());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(60))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());

            log.debug("Gemini response status: {}", response.statusCode());

            if (response.statusCode() != 200) {
                log.error("Gemini API non-200 response: status={}, body={}",
                        response.statusCode(), response.body());
                throw new GeminiApiException(
                        "Gemini API responded with HTTP " + response.statusCode(),
                        response.statusCode());
            }

            return parseGeneratedJson(response.body(), materialType);

        } catch (GeminiApiException e) {
            throw e; // re-throw domain exceptions as-is
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new GeminiApiException("Network error communicating with Gemini API.", e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Endpoint builder
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Assembles the full Gemini REST endpoint URI.
     * The API key is appended as a query parameter (never logged at INFO level).
     *
     * <p>Pattern: {@code {baseUrl}/{model}:generateContent?key={apiKey}}
     */
    private String buildEndpoint() {
        return baseUrl + "/" + model + ":generateContent?key=" + apiKey;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Request body builder
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Constructs the Gemini {@code generateContent} request JSON using Jackson.
     *
     * <pre>
     * {
     *   "system_instruction": {
     *     "parts": [{ "text": "...role-specific prompt..." }]
     *   },
     *   "contents": [
     *     { "parts": [{ "text": "...extracted PDF text..." }] }
     *   ],
     *   "generationConfig": {
     *     "responseMimeType": "application/json",
     *     "temperature":      0.4,
     *     "maxOutputTokens":  8192,
     *     "topP":             0.95
     *   }
     * }
     * </pre>
     */
    private String buildRequestBody(String extractedText, MaterialType materialType) {
        try {
            ObjectNode root = objectMapper.createObjectNode();

            // ── system_instruction ────────────────────────────────────────────
            ObjectNode sysInstruction = root.putObject("system_instruction");
            ArrayNode sysParts = sysInstruction.putArray("parts");
            sysParts.addObject().put("text", resolveSystemPrompt(materialType));

            // ── contents (the user turn) ──────────────────────────────────────
            ArrayNode contents = root.putArray("contents");
            ObjectNode userTurn = contents.addObject();
            userTurn.put("role", "user");
            ArrayNode userParts = userTurn.putArray("parts");
            userParts.addObject().put("text", extractedText);

            // ── generationConfig ──────────────────────────────────────────────
            ObjectNode genConfig = root.putObject("generationConfig");
            // Force raw JSON output — no markdown, no code fences, no prose
            genConfig.put("responseMimeType", "application/json");
            // Lower temperature = more deterministic, schema-adherent output
            genConfig.put("temperature", 0.4);
            genConfig.put("topP", 0.95);
            genConfig.put("maxOutputTokens", 8192);

            return objectMapper.writeValueAsString(root);

        } catch (IOException e) {
            // ObjectMapper.writeValueAsString only throws on serialisation bugs
            // (e.g. circular references) — cannot happen with our simple nodes.
            throw new GeminiApiException("Failed to serialise Gemini request body.", e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // System prompts
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the system instruction for the given {@link MaterialType}.
     *
     * <p>Each prompt:
     * <ul>
     *   <li>Declares the AI's role explicitly.</li>
     *   <li>Specifies the <em>exact</em> JSON array schema to return.</li>
     *   <li>Forbids markdown fences and prose — reinforcing the {@code responseMimeType} setting.</li>
     * </ul>
     */
    private String resolveSystemPrompt(MaterialType materialType) {
        return switch (materialType) {

            case SUMMARY -> """
                    You are an expert educational content summarizer.
                    Analyse the provided text and produce a comprehensive, section-by-section summary.

                    OUTPUT RULES (strictly enforced):
                    - Return ONLY a valid JSON array. No markdown, no prose, no code fences.
                    - Each element must be a JSON object with exactly two string fields:
                        "heading" — a concise, descriptive section title
                        "content" — a detailed paragraph explaining that section

                    EXAMPLE (schema only):
                    [
                      {"heading": "Introduction", "content": "This section covers..."},
                      {"heading": "Key Concepts",  "content": "The main ideas are..."}
                    ]

                    Generate between 4 and 8 sections depending on content richness.
                    """;

            case QUIZ -> """
                    You are an expert educational quiz designer.
                    Based on the provided text, create exactly 5 multiple-choice questions
                    that test conceptual understanding, not just memorisation.

                    OUTPUT RULES (strictly enforced):
                    - Return ONLY a valid JSON array of exactly 5 objects. No markdown, no prose.
                    - Each object must have exactly these four fields:
                        "question"    — the quiz question (String)
                        "options"     — an array of exactly 4 distinct answer choices (String[])
                        "answer"      — the correct answer, copied verbatim from options (String)
                        "explanation" — a one-sentence explanation of why the answer is correct (String)

                    EXAMPLE (schema only):
                    [
                      {
                        "question": "What is ...?",
                        "options": ["A", "B", "C", "D"],
                        "answer": "B",
                        "explanation": "B is correct because ..."
                      }
                    ]
                    """;

            case FLASHCARD -> """
                    You are an expert flashcard creator for active recall studying.
                    Based on the provided text, generate exactly 10 flashcards
                    covering the most important concepts, definitions, and relationships.

                    OUTPUT RULES (strictly enforced):
                    - Return ONLY a valid JSON array of exactly 10 objects. No markdown, no prose.
                    - Each object must have exactly two string fields:
                        "front" — a clear, focused question or concept prompt
                        "back"  — a concise, accurate answer or explanation (2–3 sentences max)

                    EXAMPLE (schema only):
                    [
                      {"front": "What is photosynthesis?", "back": "The process by which..."},
                      {"front": "Define osmosis.",         "back": "The movement of water..."}
                    ]
                    """;
        };
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Response parser
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Navigates the Gemini response envelope and extracts the generated JSON array.
     *
     * <p>Response shape from Gemini:
     * <pre>
     * {
     *   "candidates": [{
     *     "content": {
     *       "parts": [{ "text": "[{...}, ...]" }],
     *       "role": "model"
     *     },
     *     "finishReason": "STOP"
     *   }]
     * }
     * </pre>
     *
     * @param responseBody raw JSON string from Gemini
     * @param materialType used for logging context
     * @return the extracted, validated JSON array string
     * @throws GeminiApiException if the response is structurally invalid or empty
     */
    private String parseGeneratedJson(String responseBody, MaterialType materialType) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            // ── Safety: check for API-level error block ────────────────────────
            JsonNode errorNode = root.path("error");
            if (!errorNode.isMissingNode()) {
                String apiError = errorNode.path("message").asText("Unknown Gemini API error");
                int apiCode    = errorNode.path("code").asInt(-1);
                throw new GeminiApiException("Gemini API error: " + apiError, apiCode);
            }

            // ── Navigate: candidates[0].content.parts[0].text ─────────────────
            JsonNode candidates = root.path("candidates");
            if (!candidates.isArray() || candidates.isEmpty()) {
                throw new GeminiApiException(
                        "Gemini returned no candidates — content may have been blocked.", -1);
            }

            JsonNode firstCandidate = candidates.get(0);

            // Log finish reason for debugging content-filtering issues
            String finishReason = firstCandidate.path("finishReason").asText("UNKNOWN");
            if (!"STOP".equals(finishReason)) {
                log.warn("Gemini finishReason='{}' for materialType={}", finishReason, materialType);
            }

            JsonNode parts = firstCandidate.path("content").path("parts");
            if (!parts.isArray() || parts.isEmpty()) {
                throw new GeminiApiException(
                        "Gemini candidate contained no parts in its content.", -1);
            }

            String generatedText = parts.get(0).path("text").asText("").trim();

            if (generatedText.isBlank()) {
                throw new GeminiApiException(
                        "Gemini returned an empty text part for materialType=" + materialType, -1);
            }

            // ── Validate: confirm the text is parseable JSON ───────────────────
            // responseMimeType="application/json" should already guarantee this,
            // but we validate defensively before persisting to the database.
            JsonNode parsedJson = objectMapper.readTree(generatedText);
            if (!parsedJson.isArray()) {
                throw new GeminiApiException(
                        "Gemini response was valid JSON but not a JSON array as required. " +
                        "materialType=" + materialType, -1);
            }

            log.info("Gemini generation successful: materialType={}, itemCount={}",
                    materialType, parsedJson.size());

            return generatedText;

        } catch (GeminiApiException e) {
            throw e;
        } catch (IOException e) {
            throw new GeminiApiException(
                    "Failed to parse Gemini response body as JSON.", e);
        }
    }
}
