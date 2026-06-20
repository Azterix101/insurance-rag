package com.example.insurancerag;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class PolicyIndexingService {

    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingModel embeddingModel;

    public PolicyIndexingService(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingModel = embeddingModel;
    }

    public void parseAndIndexPolicy(String markdownContent, String state, String policyType, String effectiveYear) {
        // 1. Split the document into Parent sections using Markdown header boundaries "## "
        String[] parentSections = markdownContent.split("\n## ");

        for (String section : parentSections) {
            if (section.trim().isEmpty()) continue;

            UUID parentId = UUID.randomUUID();
            
            // Extract the first line of the section as the "Section Heading/Context"
            String[] lines = section.split("\n", 2);
            String sectionHeading = lines[0].trim();
            String sectionBody = lines.length > 1 ? lines[1] : "";

            // 2. Create the Parent Chunk (High-level clause overview)
            String parentText = String.format("Section: %s\nPolicy: %s\nContent: %s", sectionHeading, policyType, sectionBody);
            float[] parentEmbedding = getEmbedding(parentText);

            insertChunkToDb(parentId, null, parentText, parentEmbedding, state, policyType, effectiveYear, sectionHeading, new String[]{});

            // 3. Extract Child chunks using subheaders "### " (representing disclaimers, tables, or exclusions)
            String[] subsections = section.split("\n### ");
            for (int i = 1; i < subsections.length; i++) {
                UUID childId = UUID.randomUUID();
                String subsectionContent = subsections[i];
                
                String childText = String.format("Clause detail under Section [%s]: %s", sectionHeading, subsectionContent);
                float[] childEmbedding = getEmbedding(childText);

                // Build explicit relationship mapping: cross-reference the Child disclaimer directly back to its Parent clause
                String[] relatedIds = new String[]{ parentId.toString() };

                insertChunkToDb(childId, parentId, childText, childEmbedding, state, policyType, effectiveYear, sectionHeading, relatedIds);
            }
        }
    }

    private float[] getEmbedding(String text) {
        try {
            Embedding embedding = embeddingModel.embed(text).content();
            return embedding.vector();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate OpenAI embedding: " + e.getMessage(), e);
        }
    }

    private void insertChunkToDb(UUID id, UUID parentId, String content, float[] embedding, 
                                 String state, String policyType, String year, String section, String[] relatedChunkIds) {
        
        String sql = """
            INSERT INTO policy_chunks (id, parent_id, chunk_content, embedding, state_jurisdiction, policy_type, effective_year, document_section, related_chunk_ids)
            VALUES (?, ?, ?, ?::vector, ?, ?, ?, ?, ?)
        """;

        // Convert float array to Postgres vector format: [0.123, 0.456, ...]
        String vectorString = Arrays.toString(embedding);

        jdbcTemplate.update(sql, 
                id, 
                parentId, 
                content, 
                vectorString, 
                state, 
                policyType, 
                year, 
                section, 
                relatedChunkIds
        );
    }
}
