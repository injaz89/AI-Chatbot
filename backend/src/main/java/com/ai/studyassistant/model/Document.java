package com.ai.studyassistant.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Represents an uploaded PDF document stored in the system.
 * Hibernate will map this to the {@code documents} table.
 */
@Entity
@Table(name = "documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Document {

    /** Primary key – auto-incremented by the database. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Original filename as uploaded by the user (e.g. "lecture-notes.pdf"). */
    @Column(name = "file_name", nullable = false, length = 512)
    private String fileName;

    /** File size in bytes. */
    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    /**
     * Timestamp set automatically by Hibernate on INSERT.
     * Stored as DATETIME in MySQL (no manual assignment needed).
     */
    @CreationTimestamp
    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private LocalDateTime uploadedAt;
}
