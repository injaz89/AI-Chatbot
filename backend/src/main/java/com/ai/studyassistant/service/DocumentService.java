package com.ai.studyassistant.service;

import com.ai.studyassistant.exception.InvalidFileTypeException;
import com.ai.studyassistant.model.Document;
import com.ai.studyassistant.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * Service responsible for ingesting an uploaded PDF file:
 * <ol>
 *   <li>Validates that the file is a genuine PDF (MIME type + magic bytes).</li>
 *   <li>Persists a {@link Document} record to the database.</li>
 *   <li>Extracts plain text from pages 1–5 using Apache PDFBox 3.x.</li>
 * </ol>
 *
 * <p><strong>Page cap rationale:</strong> parsing only the first 5 pages keeps
 * the text payload small enough to stay within AI API token limits while still
 * capturing the most important content of a lecture document.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    /** Maximum number of pages to extract text from. */
    private static final int MAX_PAGES = 5;

    /**
     * The 4-byte PDF magic number: {@code %PDF}.
     * Checking raw bytes prevents a malicious file from bypassing validation
     * by simply renaming its extension to ".pdf".
     */
    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F'};

    private final DocumentRepository documentRepository;

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Validates, persists, and extracts text from the uploaded PDF.
     *
     * @param file the {@link MultipartFile} received from the HTTP request
     * @return extracted plain text from the first {@value #MAX_PAGES} pages
     * @throws InvalidFileTypeException if the file is not a valid PDF
     * @throws IOException              if PDFBox cannot read the file bytes
     */
    @Transactional
    public String processUpload(MultipartFile file) throws IOException {

        // 1. Validate ─ MIME type first (fast, cheap)
        validateMimeType(file);

        // 2. Validate ─ magic bytes (protects against spoofed Content-Type headers)
        validateMagicBytes(file);

        // 3. Persist document metadata to the DB
        Document savedDocument = persistDocument(file);
        log.info("Document saved: id={}, fileName={}, size={} bytes",
                savedDocument.getId(), savedDocument.getFileName(), savedDocument.getFileSize());

        // 4. Extract text from pages 1 – MAX_PAGES
        String extractedText = extractText(file);
        log.debug("Text extracted for documentId={}: {} characters",
                savedDocument.getId(), extractedText.length());

        return extractedText;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Checks the MIME type declared by the HTTP client.
     * This is the first, cheapest line of defence.
     */
    private void validateMimeType(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || !contentType.equalsIgnoreCase("application/pdf")) {
            throw new InvalidFileTypeException(
                    "Invalid file type: only PDF files are accepted. " +
                    "Received Content-Type: '" + contentType + "'.");
        }
    }

    /**
     * Reads the first 4 bytes of the file and compares them to the PDF
     * magic number {@code %PDF}. This catches files with a renamed extension.
     */
    private void validateMagicBytes(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            byte[] header = new byte[PDF_MAGIC.length];
            int bytesRead = inputStream.read(header);

            if (bytesRead < PDF_MAGIC.length) {
                throw new InvalidFileTypeException(
                        "The uploaded file is too small to be a valid PDF.");
            }

            for (int i = 0; i < PDF_MAGIC.length; i++) {
                if (header[i] != PDF_MAGIC[i]) {
                    throw new InvalidFileTypeException(
                            "The uploaded file does not appear to be a valid PDF " +
                            "(magic bytes mismatch). Only genuine PDF files are accepted.");
                }
            }
        } catch (InvalidFileTypeException e) {
            throw e; // re-throw as-is — do not wrap in IOException handler below
        } catch (IOException e) {
            throw new InvalidFileTypeException(
                    "Could not read the uploaded file to verify its format.", e);
        }
    }

    /**
     * Builds and saves a {@link Document} entity from the uploaded file's metadata.
     * The {@code uploadedAt} timestamp is set automatically by Hibernate
     * via {@code @CreationTimestamp}.
     */
    private Document persistDocument(MultipartFile file) {
        Document document = Document.builder()
                .fileName(file.getOriginalFilename())
                .fileSize(file.getSize())
                .build();
        return documentRepository.save(document);
    }

    /**
     * Uses Apache PDFBox 3.x ({@link Loader#loadPDF}) to open the PDF and
     * strip plain text from pages 1 through {@value #MAX_PAGES}.
     *
     * <p><strong>Why {@code Loader.loadPDF} instead of {@code PDDocument.load}?</strong>
     * PDFBox 3.0 removed the static {@code PDDocument.load()} factory methods in favour of
     * the new {@link Loader} utility class. Using the {@code byte[]} overload avoids
     * keeping an {@link InputStream} open while PDFBox is reading, which is safer for
     * {@link MultipartFile} implementations backed by temporary files.
     *
     * @param file the already-validated PDF file
     * @return extracted plain text, trimmed of leading/trailing whitespace
     */
    private String extractText(MultipartFile file) throws IOException {
        // Read all bytes once; PDFBox needs random access internally
        byte[] pdfBytes = file.getBytes();

        try (PDDocument document = Loader.loadPDF(pdfBytes)) {

            int totalPages = document.getNumberOfPages();
            int endPage    = Math.min(MAX_PAGES, totalPages);

            log.debug("PDF has {} page(s); extracting pages 1–{}", totalPages, endPage);

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(1);       // PDFBox pages are 1-indexed
            stripper.setEndPage(endPage);
            stripper.setSortByPosition(true); // preserves natural reading order

            String rawText = stripper.getText(document);

            if (rawText == null || rawText.isBlank()) {
                log.warn("No text extracted from PDF '{}'. It may be image-based (scanned).",
                         file.getOriginalFilename());
                return "";
            }

            return rawText.trim();
        }
    }
}
