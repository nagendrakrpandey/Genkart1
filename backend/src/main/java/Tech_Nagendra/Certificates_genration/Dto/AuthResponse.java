package Tech_Nagendra.Certificates_genration.Dto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
    @AllArgsConstructor
    @NoArgsConstructor
@Builder
    public class AuthResponse {
    private boolean success;
    private int status;
    private String message;
    private String username;
    private String email;
    private String token;

}