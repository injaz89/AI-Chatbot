package com.ai.studyassistant.repository;

import com.ai.studyassistant.model.MaterialType;
import com.ai.studyassistant.model.StudyMaterial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link StudyMaterial} entities.
 *
 * <p>All finder methods are auto-implemented by Spring Data at startup —
 * no SQL or JPQL needed.
 */
@Repository
public interface StudyMaterialRepository extends JpaRepository<StudyMaterial, Long> {

    /**
     * Fetch every study material generated for a given document.
     * Used to populate the "materials" panel after a document is processed.
     *
     * @param documentId the PK of the parent {@link com.ai.studyassistant.model.Document}
     */
    List<StudyMaterial> findByDocumentId(Long documentId);

    /**
     * Fetch all materials of a specific type across all documents.
     * Useful for a filtered view (e.g. "show me all quizzes").
     *
     * @param materialType one of {@link MaterialType#SUMMARY}, {@link MaterialType#QUIZ},
     *                     or {@link MaterialType#FLASHCARD}
     */
    List<StudyMaterial> findByMaterialType(MaterialType materialType);

    /**
     * Fetch all materials for a specific document filtered by type.
     * Primary use: "generate QUIZ for document #5" — check if one already exists.
     *
     * @param documentId   PK of the parent document
     * @param materialType the desired material type
     */
    List<StudyMaterial> findByDocumentIdAndMaterialType(Long documentId, MaterialType materialType);

    /**
     * Delete all study materials belonging to a document.
     * Called before re-processing or when a document is deleted.
     *
     * @param documentId PK of the parent document
     */
    void deleteByDocumentId(Long documentId);
}
