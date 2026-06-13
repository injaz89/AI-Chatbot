package com.ai.studyassistant.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Stores AI-generated study material (summary, quiz, or flashcard set)
 * that was produced from an uploaded {@link Document}.
 *
 * <p>Maps to the {@code study_materials} table with a FK to {@code documents}.
 */
@Entity
@Table(name = "study_materials")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudyMaterial {

    /** Primary key – auto-incremented by the database. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The source document this material was generated from.
     * Stored as a FK column {@code document_id} in the DB.
     * Using a {@link ManyToOne} join so we can navigate Document ↔ StudyMaterial
     * without loading all materials eagerly.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_study_material_document"))
    private Document document;

    /**
     * Discriminator for the kind of content stored in {@code generatedContent}.
     * Persisted as a VARCHAR so the DB stores readable strings (e.g. "QUIZ"),
     * not opaque ordinal integers.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "material_type", nullable = false, length = 20)
    private MaterialType materialType;

    /**
     * The full AI-generated text output.
     * Mapped to {@code LONGTEXT} to handle large quiz/flashcard sets.
     */
    @Lob
    @Column(name = "generated_content", nullable = false, columnDefinition = "LONGTEXT")
    private String generatedContent;

    /**
     * Timestamp set automatically by Hibernate on INSERT.
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
