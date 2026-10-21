package com.cbms.controller;

import com.cbms.dto.CustomerIdentificationRequest;
import com.cbms.dto.CustomerIdentificationResponse;
import com.cbms.service.CustomerIdentificationService;

import jakarta.validation.Valid;
import tools.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import com.cbms.service.CustomerDocumentService;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customers/{custId}/identifications")
public class CustomerIdentificationController {

    private final CustomerIdentificationService identificationService;
    private final CustomerDocumentService documentService;
    private final ObjectMapper objectMapper;

    public CustomerIdentificationController(
            CustomerIdentificationService identificationService,
            CustomerDocumentService documentService,
            ObjectMapper objectMapper
    ) {
        this.identificationService = identificationService;
        this.documentService = documentService;
        this.objectMapper = objectMapper;
    }

    // Registration is deliberately add-only. The file and identification are
    // submitted together; docId is never accepted from the client.
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerIdentificationResponse create(
            @PathVariable Long custId,
            @RequestPart("details") String details,
            @RequestPart("file") MultipartFile file,
            Authentication authentication
    ) throws IOException {
        CustomerIdentificationRequest input = objectMapper.readValue(details, CustomerIdentificationRequest.class);
        return identificationService.registerWithDocument(custId, input, file, authentication, documentService);
    }

    @GetMapping
    public List<CustomerIdentificationResponse> list(
            @PathVariable Long custId
    ) {
        return identificationService.list(custId);
    }

    @GetMapping("/{idnId}")
    public CustomerIdentificationResponse get(
            @PathVariable Long custId,
            @PathVariable Long idnId
    ) {
        return identificationService.get(custId, idnId);
    }

}
