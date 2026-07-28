package com.nerya.neryaallnaturals.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "cloudinary")
@Getter
@Setter
public class CloudinaryProperties {

    /**
     * The single Cloudinary connection string from the dashboard, of the form
     * {@code cloudinary://<api_key>:<api_secret>@<cloud_name>}. Empty leaves media uploads
     * disabled (the app still boots).
     */
    private String url;

    /** Base folder that all uploads are nested under; the media category is appended to it. */
    private String folder = "nerya";
}
