package com.dor.package_zipper.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@AllArgsConstructor
@Data
@Builder
@EqualsAndHashCode
public class ZipRemoteEntry {
    private String ZipPath;
    private String Url;
}
