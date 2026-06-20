package com.example.insurancerag;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class RetrievalQueryService {

    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingModel embeddingModel;
    private final ChatLanguageModel chatModel;

    public RetrievalQueryService(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel, ChatLanguageModel chatModel) {
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingModel = embeddingModel;
        this.chatModel = chatModel;
    }

    public String executeRAGQuery(String queryText, String state, String policyType, String year) {
        // 1. Vectorize the adjuster's query text using local nomic model
        Embedding queryEmbedding = embeddingModel.embed(queryText).content();
        String queryVectorStr = Arrays.toString(queryEmbedding.vector());

        // 2. Perform Hybrid Search: Query pgvector using metadata filters and ordering by distance
        String sql = """
            SELECT id, parent_id, chunk_content, state_jurisdiction, policy_type, document_section,
                   (embedding <=> ?::vector) AS distance
            FROM policy_chunks
            WHERE state_jurisdiction = ?
              AND policy_type = ?
              AND effective_year = ?
            ORDER BY distance ASC
            LIMIT 5
        """;

        List<Map<String, Object>> matchedChunks = jdbcTemplate.queryForList(
                sql, queryVectorStr, state, policyType, year
        );

        if (matchedChunks.isEmpty()) {
            return "I cannot answer this query because no matching policy data is active for this state, year, and policy type.";
        }

        // 3. Compile the matched chunks into a clean, annotated Context block
        StringBuilder contextBuilder = new StringBuilder();
        for (int i = 0; i < matchedChunks.size(); i++) {
            Map<String, Object> chunk = matchedChunks.get(i);
            contextBuilder.append(String.format("[%d] (Section: %s, State: %s)\nContent: %s\n\n",
                    i + 1,
                    chunk.get("document_section"),
                    chunk.get("state_jurisdiction"),
                    chunk.get("chunk_content")
            ));
        }

        // 4. Construct a strict grounding prompt
        String systemPrompt = """
            You are an expert insurance claim validation assistant. Your task is to provide accurate, grounded answers
            based solely on the policy document fragments provided in the Context below.
            
            Strict Operational Rules:
            1. If the context does not contain the answer, state: "I cannot answer this based on the active policy."
            2. Do not make assumptions or extrapolate details.
            3. Group your response into two clear sections:
               - Coverage Assessment
               - Associated Limitations and Disclaimers
            4. You must cite your source using the bracket index format, e.g., [1], for every claim you make.
            
            Context:
            """ + contextBuilder.toString();

        // 5. Generate and return the final answer via local LLM
        return chatModel.generate(systemPrompt + "\nUser Query: " + queryText);
    }
}
