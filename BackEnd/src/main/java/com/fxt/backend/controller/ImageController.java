package com.fxt.backend.controller;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/api/images")
@CrossOrigin(origins = "*", maxAge = 3600)
public class ImageController {
    
    private final String basePath = "../downloads/images/";
    
    @GetMapping("/{folder}/{filename}")
    public ResponseEntity<Resource> getImage(
            @PathVariable String folder,
            @PathVariable String filename) {
        try {
            Path filePath = Paths.get(basePath, folder, filename);
            Resource resource = new UrlResource(filePath.toUri());
            
            if (resource.exists() && resource.isReadable()) {
                String contentType = "image/jpeg";
                if (filename.endsWith(".png")) contentType = "image/png";
                else if (filename.endsWith(".webp")) contentType = "image/webp";
                else if (filename.endsWith(".gif")) contentType = "image/gif";
                
                return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                    .body(resource);
            }
            
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}