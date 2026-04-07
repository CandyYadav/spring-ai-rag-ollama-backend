package com.example.rag.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final GoogleGenAiChatModel googleGenAiChatModel;
    private final VectorStore vectorStore;
    private final ConversationMemoryService memoryService;

    String systemPrompt = """
                You are a precise question-answering assistant.
                
                Rules:
                - Answer ONLY what is asked.
                - Use ONLY the provided context.
                - Do NOT include extra details.
                - If the answer is not in the context, say:
                  "The answer is not available in the provided documents."
                - Keep the answer concise and specific.
                """;

    public String ask(String message) {
        log.info("Received user query: {}", message);

        String docType = classifyQuestion(message);
        log.info("Classified question into docType: {}", docType);

        SearchRequest searchRequest = SearchRequest.builder()
                .query(message)
                .topK(5)
                .filterExpression("doc_type == '" + docType + "'")
                .build();

//        This performs: Embedding(query) -> Vector similarity search -> Return closest chunks
        List<Document> docs = vectorStore.similaritySearch(searchRequest);
        log.info("Retrieved {} relevant document chunks from vector store", docs.size());

        docs.forEach(d -> log.debug("Retrieved chunk: {}", d.getText()));

//        It automatically injects retrieved context into the prompt.
        QuestionAnswerAdvisor advisor = QuestionAnswerAdvisor
                .builder(vectorStore)
                .searchRequest(searchRequest)
                .build();

        log.debug("QuestionAnswerAdvisor created with search request");

        return ChatClient.builder(googleGenAiChatModel)
                .build()
                .prompt()
                .system(systemPrompt)
                .advisors(advisor)
                .user(message)
                .call()
                .content();
    }

    private String classifyQuestion(String question) {
        log.debug("Classifying question: {}", question);

        question = question.toLowerCase();

        // 🔹 Trip Planning Policy
        if (question.contains("travel")
                || question.contains("trip")
                || question.contains("booking")
                || question.contains("hotel")
                || question.contains("accommodation")
                || question.contains("allowance")
                || question.contains("reimbursement")
                || question.contains("transport")
                || question.contains("uber")
                || question.contains("cab")) {

            log.debug("Question classified as trip_policy");
            return "trip_planning_policy";
        }

//        // 🔹 Company Policy
//        if (question.contains("policy")
//                || question.contains("leave")
//                || question.contains("wfh")
//                || question.contains("insurance")
//                || question.contains("laptop")) {
//
//            log.debug("Question classified as company_policy");
//            return "company_policy";
//        }

        log.debug("Question classified as company_info");
        return "company_info";
    }

    public Flux<String> askStream(String message) {

        SearchRequest searchRequest = SearchRequest.builder()
                .query(message)
                .topK(5)
                .build();

        QuestionAnswerAdvisor advisor =
                QuestionAnswerAdvisor.builder(vectorStore)
                        .searchRequest(searchRequest)
                        .build();

        return ChatClient.builder(googleGenAiChatModel)
                .build()
                .prompt()
                .system(systemPrompt)
                .advisors(advisor)
                .user(message)
                .stream()
                .content();
    }

    // ✅ Streaming tokens with session memory
    public Flux<String> askStreamTokens(String message, String sessionId) {
        // Add user message to memory
        memoryService.addMessage(sessionId, "user", message);

        SearchRequest searchRequest = SearchRequest.builder()
                .query(message)
                .topK(5)
                .build();

        QuestionAnswerAdvisor advisor =
                QuestionAnswerAdvisor.builder(vectorStore)
                        .searchRequest(searchRequest)
                        .build();

        // Stream content chunks (tokens are part of each chunk)
        return ChatClient.builder(googleGenAiChatModel)
                .build()
                .prompt()
                .system(systemPrompt + "\nConversation so far:\n" + String.join("\n", memoryService.getMemory(sessionId)))
                .advisors(advisor)
                .user(message)
                .stream()
                .content()
                .doOnNext(chunk -> memoryService.addMessage(sessionId, "assistant", chunk));
    }

    // ✅ Hybrid search (vector + simple keyword)
    private List<Document> hybridSearch(String message, String docType) {
        SearchRequest vectorSearch = SearchRequest.builder()
                .query(message)
                .topK(5)
                .filterExpression("doc_type == '" + docType + "'")
                .build();

        List<Document> vectorResults = vectorStore.similaritySearch(vectorSearch);

        List<Document> keywordResults = vectorResults.stream()
                .filter(doc -> doc.getText().toLowerCase().contains(message.toLowerCase()))
                .toList();

        return keywordResults.isEmpty() ? vectorResults : keywordResults;
    }

    // ✅ LLM-based classification
    private String classifyQuestionWithLLM(String question) {
        String prompt = """
            Classify the following question into one of the categories: 
            1) trip_planning_policy
            2) company_policy
            3) company_info
            Question: %s
            Only return the category.
            """.formatted(question);

        return ChatClient.builder(googleGenAiChatModel)
                .build()
                .prompt()
                .system("You are a classifier assistant.")
                .user(prompt)
                .call()
                .content();
    }
}
