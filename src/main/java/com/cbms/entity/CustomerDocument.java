package com.cbms.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "customer_document")
public class CustomerDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "doc_id")
    private Long docId;

    @Column(name = "cust_id", nullable = false)
    private Long custId;

    @Column(name = "file_nm", nullable = false)
    private String fileNm;

    @Column(name = "file_typ", nullable = false)
    private String fileTyp;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Column(name = "uploaded_by", nullable = false)
    private String uploadedBy;

    @Column(name = "uploaded_dt", nullable = false)
    private OffsetDateTime uploadedDt;

    @PrePersist
    public void prePersist() {
        if (uploadedDt == null) {
            uploadedDt = OffsetDateTime.now();
        }
    }

    public Long getDocId() { return docId; }
    public void setDocId(Long docId) { this.docId = docId; }

    public Long getCustId() { return custId; }
    public void setCustId(Long custId) { this.custId = custId; }

    public String getFileNm() { return fileNm; }
    public void setFileNm(String fileNm) { this.fileNm = fileNm; }

    public String getFileTyp() { return fileTyp; }
    public void setFileTyp(String fileTyp) { this.fileTyp = fileTyp; }

    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }

    public String getStorageKey() { return storageKey; }
    public void setStorageKey(String storageKey) {
        this.storageKey = storageKey;
    }

    public String getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(String uploadedBy) {
        this.uploadedBy = uploadedBy;
    }

    public OffsetDateTime getUploadedDt() { return uploadedDt; }
    public void setUploadedDt(OffsetDateTime uploadedDt) {
        this.uploadedDt = uploadedDt;
    }
}
