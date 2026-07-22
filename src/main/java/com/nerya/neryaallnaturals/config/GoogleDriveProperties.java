package com.nerya.neryaallnaturals.config;

import com.nerya.neryaallnaturals.entity.MediaAsset.MediaCategory;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "media.google")
@Getter
@Setter
public class GoogleDriveProperties {

    /**
     * Path to the service-account JSON key, or empty to leave Drive integration disabled.
     */
    private String credentialsPath;

    /**
     * Drive folder ID to upload into, keyed by media category.
     */
    private Map<MediaCategory, String> folders = new EnumMap<>(MediaCategory.class);
}
