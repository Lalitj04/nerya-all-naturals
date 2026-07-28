package com.nerya.neryaallnaturals.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

/**
 * Thin wrapper around the Cloudinary uploader. The Cloudinary bean is absent (see
 * {@link com.nerya.neryaallnaturals.config.CloudinaryConfig}) whenever CLOUDINARY_URL isn't set,
 * so every method here fails fast with a clear message rather than a NullPointerException when a
 * media operation is actually attempted.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CloudinaryService {

    private final Optional<Cloudinary> cloudinaryClient;

    /** Upload an image into {@code folder}; returns its Cloudinary public id and secure URL. */
    @SuppressWarnings("unchecked")
    public UploadResult upload(MultipartFile file, String folder) throws IOException {
        Cloudinary cloudinary = requireClient();
        Map<String, Object> result = cloudinary.uploader().upload(
                file.getBytes(),
                ObjectUtils.asMap(
                        "folder", folder,
                        "resource_type", "image"));
        String publicId = (String) result.get("public_id");
        String url = (String) result.get("secure_url");
        log.info("Uploaded '{}' to Cloudinary folder {} as {}", file.getOriginalFilename(), folder, publicId);
        return new UploadResult(publicId, url);
    }

    /** Permanently delete an asset by its Cloudinary public id. */
    public void delete(String publicId) throws IOException {
        requireClient().uploader().destroy(publicId, ObjectUtils.emptyMap());
        log.info("Deleted Cloudinary asset {}", publicId);
    }

    private Cloudinary requireClient() {
        return cloudinaryClient.orElseThrow(() -> new IllegalStateException(
                "Cloudinary is not configured; set CLOUDINARY_URL to enable media uploads"));
    }

    /** Result of a successful upload. */
    public record UploadResult(String publicId, String url) {
    }
}
