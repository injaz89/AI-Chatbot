package com.ai.studyassistant.model;

/**
 * Discriminates the type of AI-generated study material
 * derived from an uploaded {@link Document}.
 */
public enum MaterialType {

    /** A concise AI-generated summary of the document content. */
    SUMMARY,

    /** A set of multiple-choice or short-answer quiz questions. */
    QUIZ,

    /** A collection of question/answer flashcard pairs. */
    FLASHCARD
}
