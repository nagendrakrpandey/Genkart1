package Tech_Nagendra.Certificates_genration.Config;

import Tech_Nagendra.Certificates_genration.Entity.UserProfile;
import Tech_Nagendra.Certificates_genration.Repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;

@Configuration
@RequiredArgsConstructor
public class DummyUserInitializer {

    private final ProfileRepository profileRepository;
    private final PasswordEncoder passwordEncoder;

    @Bean
    public CommandLineRunner createDefaultAdmin() {
        return args -> {

            if (profileRepository.count() == 0) {
                UserProfile admin = new UserProfile();
                admin.setName("Nagendra");
                admin.setUsername("Nagendra");
                admin.setEmail("nagendra@admin.com");
                admin.setPassword(passwordEncoder.encode("123456"));
                admin.setRole("ADMIN");
                admin.setCreatedAt(LocalDateTime.now());
                admin.setModifiedAt(LocalDateTime.now());

                profileRepository.save(admin);
                System.out.println("✔ Default admin created: Nagendra (password=123456)");
            }
        };
    }
}
