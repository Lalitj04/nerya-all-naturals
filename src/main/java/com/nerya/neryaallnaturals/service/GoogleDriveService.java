package com.nerya.neryaallnaturals.service;

import com.google.api.client.http.FileContent;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.Permission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Optional;

/**
 * Thin wrapper around the Drive v3 API. The Drive bean is absent (see GoogleDriveConfig)
 * whenever GOOGLE_DRIVE_CREDENTIALS isn't set, so every method here fails fast with a clear
 * message rather than a NullPointerException when Drive access is actually attempted.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleDriveService {

    private final Optional<Drive> driveClient;

    private static final String FIELDS = "id, name, mimeType, webViewLink, thumbnailLink";

    public File upload(MultipartFile file, String folderId) throws IOException {
        Drive drive = requireDrive();

        java.io.File tempFile = java.io.File.createTempFile("nerya-media-", "-" + file.getOriginalFilename());
        try {
            file.transferTo(tempFile);

            File metadata = new File()
                    .setName(file.getOriginalFilename())
                    .setParents(Collections.singletonList(folderId));

            FileContent content = new FileContent(file.getContentType(), tempFile);

            File uploaded = drive.files().create(metadata, content)
                    .setFields(FIELDS)
                    .execute();
            log.info("Uploaded '{}' to Drive folder {} as file {}", file.getOriginalFilename(), folderId, uploaded.getId());
            return uploaded;
        } finally {
            Files.deleteIfExists(tempFile.toPath());
        }
    }

    public void makePublicReader(String fileId) throws IOException {
        Drive drive = requireDrive();
        Permission permission = new Permission().setType("anyone").setRole("reader");
        drive.permissions().create(fileId, permission).execute();
    }

    public void delete(String fileId) throws IOException {
        Drive drive = requireDrive();
        drive.files().delete(fileId).execute();
    }

    public File getMetadata(String fileId) throws IOException {
        Drive drive = requireDrive();
        return drive.files().get(fileId).setFields(FIELDS).execute();
    }

    /**
     * Canonical URL the UI renders directly in an &lt;img&gt; tag.
     */
    public static String publicUrlFor(String fileId) {
        return "https://drive.google.com/uc?export=view&id=" + fileId;
    }

    private Drive requireDrive() {
        return driveClient.orElseThrow(() -> new IllegalStateException(
                "Google Drive is not configured; set GOOGLE_DRIVE_CREDENTIALS to enable media uploads"));
    }
}
