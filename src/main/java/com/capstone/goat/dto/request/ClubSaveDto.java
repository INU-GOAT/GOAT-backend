package com.capstone.goat.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ClubSaveDto {

    @Schema(description = "클럽명",example = "인천의태양")
    @NotBlank
    private String name;

    @Schema(description = "스포츠",example = "축구")
    @NotBlank
    private String sport;
}
