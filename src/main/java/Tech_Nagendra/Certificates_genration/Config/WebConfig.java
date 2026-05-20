package Tech_Nagendra.Certificates_genration.Config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.storage.base-path}")
    private String basePath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {

        // Ensure trailing slash
        String root = "file:" + (basePath.endsWith("/") ? basePath : basePath + "/");

        // ===============================
        // Templates & images
        // URL  : /templates/{templateName}/{file}
        // Disk : {basePath}/{templateName}/{file}
        // ===============================
        registry.addResourceHandler("/templates/**")
                .addResourceLocations(root)
                .setCachePeriod(3600);

        // ===============================
        // Generated PDFs / files
        // URL  : /generated/**
        // Disk : {basePath}/generated/**
        // ===============================
        registry.addResourceHandler("/generated/**")
                .addResourceLocations(root + "generated/")
                .setCachePeriod(3600);
    }
}
