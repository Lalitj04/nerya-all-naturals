package com.nerya.neryaallnaturals.service;

import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.Permission;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GoogleDriveServiceTest {

    @Test
    void publicUrlForBuildsTheDirectViewLink() {
        assertThat(GoogleDriveService.publicUrlFor("abc123"))
                .isEqualTo("https://drive.google.com/uc?export=view&id=abc123");
    }

    @Test
    void everyOperationFailsFastWhenDriveIsNotConfigured() {
        GoogleDriveService service = new GoogleDriveService(Optional.empty());
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", new byte[]{1, 2, 3});

        assertThatThrownBy(() -> service.upload(file, "folder-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GOOGLE_DRIVE_CREDENTIALS");

        assertThatThrownBy(() -> service.makePublicReader("abc123"))
                .isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() -> service.delete("abc123"))
                .isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() -> service.getMetadata("abc123"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void uploadReturnsTheDriveFileWhenDriveIsConfigured() throws Exception {
        Drive drive = mock(Drive.class);
        Drive.Files files = mock(Drive.Files.class);
        Drive.Files.Create create = mock(Drive.Files.Create.class);
        File created = new File().setId("file-123").setName("photo.png").setMimeType("image/png");

        when(drive.files()).thenReturn(files);
        when(files.create(any(File.class), any())).thenReturn(create);
        when(create.setFields(anyString())).thenReturn(create);
        when(create.execute()).thenReturn(created);

        GoogleDriveService service = new GoogleDriveService(Optional.of(drive));
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", new byte[]{1, 2, 3});

        File result = service.upload(file, "folder-1");

        assertThat(result.getId()).isEqualTo("file-123");
        assertThat(result.getName()).isEqualTo("photo.png");
    }

    @Test
    void makePublicReaderGrantsAnyoneReaderAccess() throws Exception {
        Drive drive = mock(Drive.class);
        Drive.Permissions permissions = mock(Drive.Permissions.class);
        Drive.Permissions.Create create = mock(Drive.Permissions.Create.class);

        when(drive.permissions()).thenReturn(permissions);
        when(permissions.create(anyString(), any(Permission.class))).thenReturn(create);
        when(create.execute()).thenReturn(new Permission());

        GoogleDriveService service = new GoogleDriveService(Optional.of(drive));
        service.makePublicReader("file-123");

        org.mockito.ArgumentCaptor<Permission> captor = org.mockito.ArgumentCaptor.forClass(Permission.class);
        org.mockito.Mockito.verify(permissions).create(anyString(), captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("anyone");
        assertThat(captor.getValue().getRole()).isEqualTo("reader");
    }
}
