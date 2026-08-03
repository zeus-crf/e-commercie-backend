package com.ecommercie.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
public class StorageWebConfig implements WebMvcConfigurer {

    @Value("${storage.local.dir:uploads}")
    private String dir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String local = Paths.get(dir).toAbsolutePath().normalize().toUri().toString();
        if (!local.endsWith("/")) local += "/";
        registry.addResourceHandler("/files/**").addResourceLocations(local);
    }
}
