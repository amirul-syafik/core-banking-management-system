package com.cbms.service;

import com.cbms.dto.CustomerDocumentResponse;
import com.cbms.entity.Customer;
import com.cbms.entity.CustomerDocument;
import com.cbms.repository.CustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

@Service
public class CustomerProfilePictureService {
    private static final Logger log = LoggerFactory.getLogger(CustomerProfilePictureService.class);
    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final CustomerRepository customerRepository;
    private final CustomerDocumentService documentService;

    public CustomerProfilePictureService(CustomerRepository customerRepository,
                                         CustomerDocumentService documentService) {
        this.customerRepository = customerRepository;
        this.documentService = documentService;
    }

    @Transactional
    public void replace(Long custId, MultipartFile file, Authentication authentication) throws IOException {
        Customer customer = customer(custId);
        CustomerProfilePictureValidator.validate(file);
        Long previousDocumentId = customer.getProfilePictureDocId();
        CustomerDocumentResponse uploaded = documentService.upload(custId, file, authentication);
        CustomerDocument uploadedDocument = documentService.findDocument(custId, uploaded.docId());
        Path uploadedPath = documentService.resolveDocumentPath(uploadedDocument);
        removeNewFileIfTransactionRollsBack(uploadedPath);
        try {
            customer.setProfilePictureDocId(uploaded.docId());
            customerRepository.saveAndFlush(customer);
        } catch (RuntimeException error) {
            try {
                documentService.removeUploadedDocument(custId, uploaded.docId());
            } catch (IOException | RuntimeException cleanupError) {
                error.addSuppressed(cleanupError);
            }
            throw error;
        }
        if (previousDocumentId != null) {
            removeAfterCommit(custId, previousDocumentId);
        }
    }

    @Transactional(readOnly = true)
    public CustomerDocument get(Long custId) {
        Customer customer = customer(custId);
        Long documentId = customer.getProfilePictureDocId();
        if (documentId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer profile picture not found");
        }
        CustomerDocument document = documentService.findDocument(custId, documentId);
        if (!IMAGE_TYPES.contains(document.getFileTyp())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer profile picture not found");
        }
        return document;
    }

    @Transactional
    public void remove(Long custId) {
        Customer customer = customer(custId);
        Long documentId = customer.getProfilePictureDocId();
        if (documentId == null) return;
        customer.setProfilePictureDocId(null);
        customerRepository.saveAndFlush(customer);
        removeAfterCommit(custId, documentId);
    }

    private Customer customer(Long custId) {
        return customerRepository.findById(custId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));
    }

    private void removeAfterCommit(Long custId, Long docId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    documentService.removeUploadedDocument(custId, docId);
                } catch (IOException | RuntimeException error) {
                    log.error("Unable to remove superseded customer profile picture; customerId={} documentId={}",
                            custId, docId, error);
                }
            }
        });
    }

    private void removeNewFileIfTransactionRollsBack(Path path) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_ROLLED_BACK) return;
                try {
                    Files.deleteIfExists(path);
                } catch (IOException error) {
                    log.error("Unable to remove stored customer profile picture after transaction rollback", error);
                }
            }
        });
    }
}
