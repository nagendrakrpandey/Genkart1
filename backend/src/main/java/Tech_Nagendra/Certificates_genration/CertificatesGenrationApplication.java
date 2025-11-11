package Tech_Nagendra.Certificates_genration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;
@SpringBootApplication
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
@EnableScheduling
public class CertificatesGenrationApplication extends SpringBootServletInitializer {
    public static void main(String[] args) {
        try {
            Tech_Nagendra.Certificates_genration.Config.JasperFontConfig.loadFontJarsFromResources();
            System.out.println(" Fonts initialized before Spring startup.");
        } catch (Exception e) {
            System.err.println(" Font loading failed: " + e.getMessage());
            e.printStackTrace();
        }
        SpringApplication.run(CertificatesGenrationApplication.class, args);
    }
    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(CertificatesGenrationApplication.class);
    }
}

