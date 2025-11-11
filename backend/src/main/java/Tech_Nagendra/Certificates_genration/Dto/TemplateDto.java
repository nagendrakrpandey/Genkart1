package Tech_Nagendra.Certificates_genration.Dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TemplateDto {
    private Long id;
    private String templateName;
    private Integer imageType;
    private String jrxmlPath;
    private String templateFolder;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;
    private List<String> imagePaths;
}
