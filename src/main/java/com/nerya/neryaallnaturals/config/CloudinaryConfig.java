package com.nerya.neryaallnaturals.config;

import com.cloudinary.Cloudinary;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds the Cloudinary client from the connection URL. Deliberately returns null (registering
 * no bean) when no URL is configured, so local dev and any environment without Cloudinary access
 * still boots — {@code CloudinaryService} treats the client as optional and fails only when an
 * upload/delete is actually invoked.
 */
@Configuration
@Slf4j
public class CloudinaryConfig {

    @Bean
    public Cloudinary cloudinary(CloudinaryProperties properties) {
        String url = properties.getUrl();
        if (url == null || url.isBlank()) {
            log.info("CLOUDINARY_URL not set; media uploads are disabled");
            return null;
        }
        Cloudinary cloudinary = new Cloudinary(url);
        cloudinary.config.secure = true; // always emit https:// asset URLs
        return cloudinary;
    }
}
