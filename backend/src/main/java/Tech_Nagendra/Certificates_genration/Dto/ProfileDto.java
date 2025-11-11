package Tech_Nagendra.Certificates_genration.Dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProfileDto {
    private Long id;
    private String name;
    private String username;
    private String email;
    private String role;
    private String password;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;
    private Long createdBy;
    private Long modifiedBy;


    public ProfileDto(Long id, String name, String username, String email, String role,
                      LocalDateTime createdAt, LocalDateTime modifiedAt,
                      Long createdBy, Long modifiedBy) {
        this.id = id;
        this.name = name;
        this.username = username;
        this.email = email;
        this.role = role;
        this.createdAt = createdAt;
        this.modifiedAt = modifiedAt;
        this.createdBy = createdBy;
        this.modifiedBy = modifiedBy;
    }
}
