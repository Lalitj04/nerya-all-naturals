package com.nerya.neryaallnaturals.config;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.util.Collections;

/**
 * Builds the Drive client from the service-account credentials. Deliberately returns null
 * (registering no bean) when no credentials path is configured, so local dev and any
 * environment without Drive access still boots — GoogleDriveService treats the client as
 * optional and fails only when a Drive operation is actually invoked.
 */
@Configuration
@Slf4j
public class GoogleDriveConfig {

    @Bean
    public Drive driveClient(GoogleDriveProperties properties) throws Exception {
        String credentialsPath = properties.getCredentialsPath();
        if (credentialsPath == null || credentialsPath.isBlank()) {
            log.info("GOOGLE_DRIVE_CREDENTIALS not set; Google Drive media uploads are disabled");
            return null;
        }

        GoogleCredentials credentials;
        try (FileInputStream in = new FileInputStream(credentialsPath)) {
            credentials = GoogleCredentials.fromStream(in)
                    .createScoped(Collections.singleton(DriveScopes.DRIVE));
        }

        return new Drive.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials))
                .setApplicationName("nerya-all-naturals")
                .build();
    }
}
