package com.qs.ai.admian.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Copywriting generation request.
 */
@Data
public class CopywritingGenerateRequest {

    @Schema(description = "Copywriting type", example = "product", allowableValues = {
            "product", "social", "poster", "email", "article"
    })
    @NotBlank(message = "copyType must not be blank")
    @Pattern(regexp = "^(product|social|poster|email|article)$",
            message = "copyType must be product, social, poster, email or article")
    private String copyType;

    @Schema(description = "Keywords for copywriting", example = "[\"AI客服\", \"降本增效\", \"7x24小时\"]")
    @NotEmpty(message = "keywords must not be empty")
    @Size(max = 10, message = "keywords size must not exceed 10")
    private List<@NotBlank(message = "keyword must not be blank") String> keywords;

    @Schema(description = "Target audience", example = "enterprise customer service teams")
    @Size(max = 100, message = "targetAudience length must not exceed 100")
    private String targetAudience;

    @Schema(description = "Tone", example = "professional", allowableValues = {
            "professional", "friendly", "energetic", "premium"
    })
    @Pattern(regexp = "^(professional|friendly|energetic|premium)?$",
            message = "tone must be professional, friendly, energetic or premium")
    private String tone = "professional";

    @Schema(description = "Expected length", example = "medium", allowableValues = {"short", "medium", "long"})
    @Pattern(regexp = "^(short|medium|long)?$", message = "length must be short, medium or long")
    private String length = "medium";

    @Schema(description = "Model name", example = "qwen-turbo", defaultValue = "qwen-turbo")
    private String model = "qwen-turbo";

    @Schema(description = "Sampling temperature", example = "0.7", defaultValue = "0.7")
    @DecimalMin(value = "0.0", message = "temperature must be greater than or equal to 0")
    @DecimalMax(value = "2.0", message = "temperature must be less than or equal to 2")
    private Double temperature = 0.7D;
}
