package com.swasthai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swasthai.model.User;
import com.swasthai.model.dto.ChatRequest;
import com.swasthai.session.ChatState;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class AiChatService {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final ChatState chatState;

    public AiChatService(ChatClient.Builder chatClientBuilder, ObjectMapper objectMapper, ChatState chatState) {
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
        this.chatState = chatState;
    }

    public String handleChat(ChatRequest request, ChatState chatState, User user) {
        String userInput = request.message();
        String userProfile = getUserProfileString(user);

        try {
            if (request.isPredictionStart()) {
                chatState.resetChat();
                chatState.setStage("PREDICTION_STARTED");
                chatState.getSymptomData().put("initial", userInput);
            }

            String aiResponse = "";
            String stage = chatState.getStage();

            if ("PREDICTION_STARTED".equals(stage)) {
                String promptString = String.format(
                        "SYSTEM: A patient reported symptoms: \"%s\". Generate EXACTLY 3 follow-up diagnostic questions in JSON array format only, like [\"question1\",\"question2\",\"question3\"]. No other text.",
                        chatState.getSymptomData().get("initial")
                );

                String responseText = chatClient.prompt()
                        .user(promptString)
                        .call()
                        .content();

                responseText = responseText.replaceAll("(?s).*(\\[.*\\]).*", "$1");

                List<String> questions = objectMapper.readValue(responseText, new TypeReference<List<String>>() {});
                chatState.setFollowUpQuestions(questions);
                chatState.setStage("AWAITING_ANSWER_1");
                aiResponse = questions.get(0);

            } else if ("AWAITING_ANSWER_1".equals(stage)) {
                chatState.getSymptomData().put("answer1", userInput);
                chatState.setStage("AWAITING_ANSWER_2");
                aiResponse = chatState.getFollowUpQuestions().get(1);

            } else if ("AWAITING_ANSWER_2".equals(stage)) {
                chatState.getSymptomData().put("answer2", userInput);
                chatState.setStage("AWAITING_ANSWER_3");
                aiResponse = chatState.getFollowUpQuestions().get(2);

            } else if ("AWAITING_ANSWER_3".equals(stage)) {
                chatState.getSymptomData().put("answer3", userInput);

                String fullContext = String.format(
                        "Initial Symptoms: %s. Answers: 1. %s, 2. %s, 3. %s",
                        chatState.getSymptomData().get("initial"),
                        chatState.getSymptomData().get("answer1"),
                        chatState.getSymptomData().get("answer2"),
                        chatState.getSymptomData().get("answer3")
                );

                String systemPrompt = """
                    SYSTEM ROLE: You are SwasthAI — a virtual doctor with clinical expertise.
                    You NEVER refuse or say "I cannot recommend treatment". You MUST always give a useful medical opinion.

                    OUTPUT FORMAT (exact headings in this order):

                    **Disclaimer:** (1 short line — you are an AI, final diagnosis must be done by doctor.)

                    **Probable Disease / Condition:** (Give 1–2 clear most possible diseases.)

                    **Medicines & Dosage Based on Age:**
                    - Age <5 → No medication allowed; recommend doctor only.
                    - Age 5–12 → Child dosage (low mg)
                    - Age 13–18 → Teen dosage
                    - Age 19–60 → Adult dosage
                    - Age >60 → Senior dosage (reduced mg)
                    Use only safe OTC medicines (e.g., Paracetamol, ORS, Cough Syrup).  
                    Format: Medicine — dosage in mg, frequency per day, number of days.

                    **When to Contact a Doctor Immediately:**
                    List warning signs.  
                    If condition is severe add: 📞 Emergency Helpline: 108 (India) / 112 (General Emergency)

                    **Lifestyle & Home Remedies:**
                    Give 6–8 useful points.

                    **Food Recommendations:**
                    - Foods TO CONSUME (5–7 items)
                    - Foods TO AVOID (5–7 items)
                    NEVER give JSON.
                    Never respond with disclaimers like "I cannot provide diagnosis".
                    """;

                String userPrompt = String.format("Patient Profile: %s. Symptom Details: %s", userProfile, fullContext);

                aiResponse = chatClient.prompt()
                        .system(systemPrompt)
                        .user(userPrompt)
                        .call()
                        .content();

                chatState.setLastPredictionResult(aiResponse);
                chatState.resetChat();

            } else {
                
                String generalPrompt = String.format(
                        """
                        You are SwasthAI — behave like a real doctor.
                        Never reply with 'I cannot diagnose' or 'consult a doctor only'.
                        Always give meaningful medical guidance.

                        Patient Profile: %s
                        User Question: "%s"

                        Your answer must include:
                        - Direct clear medical explanation
                        - Possible cause
                        - Safe OTC medication with age-based dosage
                        - Diet & lifestyle tips
                        - When to consult a doctor only if necessary
                        """,
                        userProfile, userInput
                );

                aiResponse = chatClient.prompt()
                        .user(generalPrompt)
                        .call()
                        .content();
            }

            return aiResponse;

        } catch (Exception e) {
            e.printStackTrace();
            chatState.resetChat();
            return "⚠ Something went wrong processing the request. Please try again.";
        }
    }

    public String getUserProfileString(User user) {
        return String.format(
                "Age: %s, Allergies: %s, Medical History: %s",
                user.getAge() != null ? user.getAge() : "Not provided",
                user.getAllergies() != null ? user.getAllergies() : "Not provided",
                user.getMedicalHistory() != null ? user.getMedicalHistory() : "Not provided"
        );
    }
}
