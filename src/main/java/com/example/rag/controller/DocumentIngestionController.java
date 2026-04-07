package com.example.rag.controller;

import com.example.rag.service.DocumentIngestionService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class DocumentIngestionController {

    private final DocumentIngestionService documentIngestionService;

    public DocumentIngestionController(DocumentIngestionService documentIngestionService) {
        this.documentIngestionService = documentIngestionService;
    }

    @PostMapping("/document/inject")
    public String injectDocument() {
        documentIngestionService.ingestDocuments();
        return "Document injected successfully!";
    }

    @PostMapping("/document/upload")
    @CrossOrigin(origins = "*")
    public String uploadDocument(@RequestParam("file") MultipartFile file,
                                 @RequestParam("docType") String docType) throws Exception {
        documentIngestionService.ingestMultipartFile(file, docType);
        return "Document uploaded successfully!";
    }
}