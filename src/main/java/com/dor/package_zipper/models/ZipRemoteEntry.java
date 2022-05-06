package com.dor.package_zipper.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@AllArgsConstructor
@Data
@Builder
public class ZipRemoteEntry {
    private String ZipPath;
    private String Url;
}
