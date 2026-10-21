package com.cbms.service;

import com.cbms.dto.CustomerDocumentResponse;
import com.cbms.entity.CustomerDocument;
import com.cbms.repository.CustomerDocumentRepository;
import com.cbms.repository.CustomerRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.List;
import java.util.UUID;

@Service
public class CustomerDocumentService {

    private final CustomerDocumentRepository documentRepository;
    private final CustomerRepository customerRepository;
    private final Path storageRoot;

    public CustomerDocumentService(
            CustomerDocumentRepository documentRepository,
            CustomerRepository customerRepository,
            @Value("${app.document.storage-path}") String storagePath
    ) {
        this.documentRepository = documentRepository;
        this.customerRepository = customerRepository;
        this.storageRoot = Paths.get(storagePath)
                .toAbsolutePath()
                .normalize();
    }

    public CustomerDocumentResponse upload(
            Long custId,
            MultipartFile file,
            Authentication authentication
    ) throws IOException {

        if (!customerRepository.existsById(custId)) {
            throw new IllegalArgumentException(
                    "Customer not found: " + custId
            );
        }

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(
                    "Uploaded file cannot be empty"
            );
        }

        if (file.getSize() > 5L * 1024 * 1024) {
            throw new IllegalArgumentException(
                    "Maximum file size is 5 MB"
            );
        }

        String originalName = file.getOriginalFilename();

        if (originalName == null || originalName.isBlank()) {
            originalName = "document";
        }

        originalName = originalName.replace("\\", "/");
        originalName = originalName.substring(
                originalName.lastIndexOf('/') + 1
        );

        if (originalName.isBlank()
                || originalName.length() > 255) {
            throw new IllegalArgumentException(
                    "Invalid original filename"
            );
        }

        byte[] signature = new byte[8];
        int bytesRead;

        try (InputStream input = file.getInputStream()) {
            bytesRead = input.readNBytes(
                    signature, 0, signature.length
            );
        }

        boolean pdf = bytesRead >= 5
                && signature[0] == '%'
                && signature[1] == 'P'
                && signature[2] == 'D'
                && signature[3] == 'F'
                && signature[4] == '-';

        boolean png = bytesRead == 8
                && (signature[0] & 0xFF) == 0x89
                && signature[1] == 'P'
                && signature[2] == 'N'
                && signature[3] == 'G'
                && (signature[4] & 0xFF) == 0x0D
                && (signature[5] & 0xFF) == 0x0A
                && (signature[6] & 0xFF) == 0x1A
                && (signature[7] & 0xFF) == 0x0A;

        boolean jpeg = bytesRead >= 3
                && (signature[0] & 0xFF) == 0xFF
                && (signature[1] & 0xFF) == 0xD8
                && (signature[2] & 0xFF) == 0xFF;

        if (!pdf && !png && !jpeg) {
            throw new IllegalArgumentException(
                    "Only PDF, PNG and JPEG files are supported"
            );
        }

        String contentType = pdf
                ? MediaType.APPLICATION_PDF_VALUE
                : png ? MediaType.IMAGE_PNG_VALUE
                : MediaType.IMAGE_JPEG_VALUE;

        String extension = pdf ? ".pdf" : png ? ".png" : ".jpg";

        Files.createDirectories(storageRoot);

        String storageKey = UUID.randomUUID() + extension;
        Path destination = storageRoot.resolve(storageKey).normalize();

        if (!destination.getParent().equals(storageRoot)) {
            throw new IllegalArgumentException(
                    "Invalid storage destination"
            );
        }

        try {
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, destination);
            }

            CustomerDocument document = new CustomerDocument();
            document.setCustId(custId);
            document.setFileNm(originalName);
            document.setFileTyp(contentType);
            document.setFileSize(file.getSize());
            document.setStorageKey(storageKey);
            document.setUploadedBy(authentication.getName());

            return toResponse(documentRepository.save(document));

        } catch (IOException | RuntimeException ex) {
            Files.deleteIfExists(destination);
            throw ex;
        }
    }

    // Internal compensation only. Not exposed as a document DELETE API.
    public void removeUploadedDocument(Long custId, Long docId) throws IOException {
        CustomerDocument document = findDocument(custId, docId);
        Path path = resolveDocumentPath(document);
        documentRepository.delete(document);
        documentRepository.flush();
        Files.deleteIfExists(path);
    }

    public List<CustomerDocumentResponse> list(Long custId) {

        if (!customerRepository.existsById(custId)) {
            throw new IllegalArgumentException(
                    "Customer not found: " + custId
            );
        }

        return documentRepository
                .findByCustIdOrderByUploadedDtDesc(custId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public CustomerDocument findDocument(
            Long custId,
            Long docId
    ) {
        return documentRepository
                .findByDocIdAndCustId(docId, custId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Document not found for customer"
                        )
                );
    }

    public Path resolveDocumentPath(CustomerDocument document) {

        Path path = storageRoot
                .resolve(document.getStorageKey())
                .normalize();

        if (!path.getParent().equals(storageRoot)) {
            throw new IllegalArgumentException(
                    "Invalid document path"
            );
        }

        return path;
    }

    private CustomerDocumentResponse toResponse(
            CustomerDocument document
    ) {
        return new CustomerDocumentResponse(
                document.getDocId(),
                document.getCustId(),
                document.getFileNm(),
                document.getFileTyp(),
                document.getFileSize(),
                document.getUploadedDt()
        );
    }
}
