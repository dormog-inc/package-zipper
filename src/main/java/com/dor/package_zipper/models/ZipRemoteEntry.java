package com.dor.package_zipper.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@Getter
@Setter
@Data
@Builder
public class ZipRemoteEntry {
    private String ZipPath;
    private String Url;
}
