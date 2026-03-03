package com.swasthai.service;

import com.swasthai.model.User;
import com.swasthai.session.ChatState;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RagService {

    private final ChatClient.Builder chatClientBuilder;
    private final EmbeddingModel embeddingModel;
    private final ChatState chatState;

    public RagService(ChatClient.Builder chatClientBuilder,
                      EmbeddingModel embeddingModel,
                      ChatState chatState) {
        this.chatClientBuilder = chatClientBuilder;
        this.embeddingModel = embeddingModel;
        this.chatState = chatState;
    }

    public String analyzePrescription(MultipartFile file, User user) {

        try {
           
            VectorStore vectorStore = SimpleVectorStore.builder(this.embeddingModel).build();

           
            Resource resource = file.getResource();
            PdfDocumentReaderConfig config = PdfDocumentReaderConfig.builder().build();
            PagePdfDocumentReader reader = new PagePdfDocumentReader(resource, config);
            List<Document> docs = reader.read();

            TokenTextSplitter splitter = new TokenTextSplitter();
            List<Document> splitDocs = splitter.apply(docs);

            
            vectorStore.add(splitDocs);

            
            SearchRequest request = SearchRequest.builder()
                    .query("Extract all medical information: medicines, dosages, instructions, and doctor's notes.")
                    .topK(5)
                    .build();

            List<Document> contextDocs = vectorStore.similaritySearch(request);

           
            String context = contextDocs.stream()
                    .map(d -> d.isText() ? d.getText() : "") 
                    .collect(Collectors.joining("\n---\n"));

            String userDetails = String.format(
                    "Age: %s, Allergies: %s, Medical History: %s",
                    user.getAge(), user.getAllergies(), user.getMedicalHistory()
            );

            String template = """
                    SYSTEM PERSONA: You are SwasthAI, a prescription analysis expert.
                    You must provide your analysis based *only* on the context provided.
                    
                    USER DETAILS:
                    {userDetails}

                    PRESCRIPTION CONTEXT:
                    ---
                    {context}
                    ---

                    TASK:
                    Analyze the prescription context for the given user.
                    Provide your analysis using these exact markdown headings:

                    **Overall Safety Assessment:**
                    **Dosage Check:**
                    **Allergy & Interaction Check:**
                    **Guidance:**
                    """;

            Map<String, Object> promptVariables = Map.of(
                    "userDetails", userDetails,
                    "context", context
            );

            
            ChatClient chatClient = chatClientBuilder.build();

            String result = chatClient
                    .prompt()
                    .system(s -> s.text(template).params(promptVariables)) // <-- use params(map)
                    .call()
                    .content();

           
            chatState.setLastAnalysisResult(result);
            chatState.setLastPredictionResult(null);

            return result;

        } catch (Exception e) {
            e.printStackTrace();
            return "Error analyzing prescription: " + e.getMessage();
        }
    }
}