package com.ai.studyassistant.repository;

import com.ai.studyassistant.model.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Document} entities.
 *
 * <p>JpaRepository provides CRUD + pagination out of the box.
 * Custom query methods follow Spring's derived-query naming convention.
 */
@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    /**
     * Find a document by its original filename.
     * Useful for duplicate-upload detection.
     */
    Optional<Document> findByFileName(String fileName);

    /**
     * Returns all documents ordered by upload date descending
     * (most recent first) for the document history view.
     */
    List<Document> findAllByOrderByUploadedAtDesc();
}
