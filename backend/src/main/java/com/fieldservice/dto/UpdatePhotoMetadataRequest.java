package com.fieldservice.dto;

import com.fieldservice.entity.PhotoCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePhotoMetadataRequest {
    private String caption;
    private PhotoCategory category;
}
