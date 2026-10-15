package com.cbms.controller;

import com.cbms.dto.CustomerDocumentResponse;
import com.cbms.entity.CustomerDocument;
import com.cbms.service.CustomerDocumentService;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/api/customers/{custId}/documents")
public class CustomerDocumentController {

    private final CustomerDocumentService documentService;

    public CustomerDocumentController(
            CustomerDocumentService documentService
    ) {
        this.documentService = documentService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CustomerDocumentResponse> upload(
            @PathVariable Long custId,
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) throws IOException {

        CustomerDocumentResponse response =
                documentService.upload(
                        custId,
                        file,
                        authentication
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping
    public List<CustomerDocumentResponse> list(
            @PathVariable Long custId
    ) {
        return documentService.list(custId);
    }

    @GetMapping("/{docId}/download")
    public ResponseEntity<Resource> download(
            @PathVariable Long custId,
            @PathVariable Long docId
    ) throws IOException {

        CustomerDocument document =
                documentService.findDocument(custId, docId);

        Path path = documentService.resolveDocumentPath(document);

        if (!Files.isRegularFile(path)
                || Files.isSymbolicLink(path)) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(path);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        document.getFileTyp()
                ))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(document.getFileNm())
                                .build()
                                .toString()
                )
                .header(
                        HttpHeaders.CACHE_CONTROL,
                        "no-store"
                )
                .body(resource);
    }
}
