package com.swasthai.session;

import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@SessionScope
@Getter
@Setter
public class ChatState implements Serializable {
    
    private String stage = "GENERAL";
    private Map<String, Object> symptomData = new HashMap<>();
    private List<String> followUpQuestions = new ArrayList<>();
    
    private String lastPredictionResult;
    private String lastAnalysisResult;

    public void resetChat() {
        this.stage = "GENERAL";
        this.symptomData.clear();
        this.followUpQuestions.clear();
    }
}