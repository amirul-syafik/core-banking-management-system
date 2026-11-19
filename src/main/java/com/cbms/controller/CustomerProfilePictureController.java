package com.cbms.controller;

import com.cbms.entity.CustomerDocument;
import com.cbms.service.CustomerProfilePictureService;
import com.cbms.service.CustomerDocumentService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@RestController
@RequestMapping("/api/customers/{custId}/profile-picture")
public class CustomerProfilePictureController {
    private final CustomerProfilePictureService pictureService;
    private final CustomerDocumentService documentService;

    public CustomerProfilePictureController(CustomerProfilePictureService pictureService,
                                            CustomerDocumentService documentService) {
        this.pictureService = pictureService;
        this.documentService = documentService;
    }

    @GetMapping
    public ResponseEntity<FileSystemResource> get(@PathVariable Long custId) throws IOException {
        CustomerDocument document = pictureService.get(custId);
        Path path = documentService.resolveDocumentPath(document);
        if (!Files.isRegularFile(path) || Files.isSymbolicLink(path)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer profile picture not found");
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(document.getFileTyp()))
                .contentLength(Files.size(path))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(new FileSystemResource(path));
    }

    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> replace(@PathVariable Long custId,
                                         @RequestParam("file") MultipartFile file,
                                         Authentication authentication) throws IOException {
        pictureService.replace(custId, file, authentication);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> remove(@PathVariable Long custId) {
        pictureService.remove(custId);
        return ResponseEntity.noContent().build();
    }
}
