package com.swasthai.controller;

import com.swasthai.model.User;
import com.swasthai.model.dto.ChatRequest;
import com.swasthai.model.dto.ChatResponse;
import com.swasthai.model.dto.UserProfileDto;
import com.swasthai.repository.UserRepository;
import com.swasthai.service.AiChatService;
import com.swasthai.service.PdfReportService;
import com.swasthai.service.RagService;
import com.swasthai.session.ChatState;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class ApiController {

    private final AiChatService aiChatService;
    private final RagService ragService;
    private final PdfReportService pdfReportService;
    private final UserRepository userRepository;
    private final ChatState chatState;

    public ApiController(AiChatService aiChatService, RagService ragService, 
                         PdfReportService pdfReportService, UserRepository userRepository, ChatState chatState) {
        this.aiChatService = aiChatService;
        this.ragService = ragService;
        this.pdfReportService = pdfReportService;
        this.userRepository = userRepository;
        this.chatState = chatState;
    }

    private User getAuthenticatedUser(Authentication authentication) {
        String username = authentication.getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));
    }

    @GetMapping("/get_user_info")
    public ResponseEntity<UserProfileDto> getUserInfo(Authentication authentication) {
        User user = getAuthenticatedUser(authentication);
        return ResponseEntity.ok(new UserProfileDto(user.getAge(), user.getAllergies(), user.getMedicalHistory()));
    }

    @PostMapping("/update_user_info")
    public ResponseEntity<String> updateUserInfo(@RequestBody UserProfileDto profile, Authentication authentication) {
        User user = getAuthenticatedUser(authentication);
        user.setAge(profile.age());
        user.setAllergies(profile.allergies());
        user.setMedicalHistory(profile.medicalHistory());
        userRepository.save(user);
        return ResponseEntity.ok("{\"message\": \"Profile updated successfully.\"}");
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request, Authentication authentication) {
        User user = getAuthenticatedUser(authentication);
        String response = aiChatService.handleChat(request, chatState, user);
        return ResponseEntity.ok(new ChatResponse(response));
    }

    @PostMapping("/analyze_prescription")
    public ResponseEntity<ChatResponse> analyzePrescription(@RequestParam("file") MultipartFile file, Authentication authentication) {
        User user = getAuthenticatedUser(authentication);
        String response = ragService.analyzePrescription(file, user);
        return ResponseEntity.ok(new ChatResponse(response));
    }

    @GetMapping("/download_prediction_pdf")
    public ResponseEntity<byte[]> downloadPredictionPdf() {
        String content = chatState.getLastPredictionResult();
        if (content == null) {
            return ResponseEntity.notFound().build();
        }
        byte[] pdfBytes = pdfReportService.createPdfReport("SwasthAI - Disease Prediction Report", content);
        return createPdfResponse(pdfBytes);
    }

    @GetMapping("/download_analysis_pdf")
    public ResponseEntity<byte[]> downloadAnalysisPdf() {
        String content = chatState.getLastAnalysisResult();
        if (content == null) {
            return ResponseEntity.notFound().build();
        }
        byte[] pdfBytes = pdfReportService.createPdfReport("SwasthAI - Prescription Analysis Report", content);
        return createPdfResponse(pdfBytes);
    }

    private ResponseEntity<byte[]> createPdfResponse(byte[] pdfBytes) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "SwasthAI_Report.pdf");
        return ResponseEntity.ok().headers(headers).body(pdfBytes);
    }
}