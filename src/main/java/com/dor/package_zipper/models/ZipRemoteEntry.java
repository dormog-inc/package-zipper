package com.dor.package_zipper.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@AllArgsConstructor
@Data
@Builder
public class ZipRemoteEntry {
    @JsonProperty("ZipPath")
    private String zipPath;
    @JsonProperty("Url")
    private String url;

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ZipRemoteEntry other = (ZipRemoteEntry) obj;
        if (url == null) {
            if (other.url != null)
                return false;
        } else if (!url.equals(other.url))
            return false;
        if (zipPath == null) {
            if (other.zipPath != null)
                return false;
        } else if (!zipPath.equals(other.zipPath))
            return false;
        return true;
    }
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((url == null) ? 0 : url.hashCode());
        result = prime * result + ((zipPath == null) ? 0 : zipPath.hashCode());
        return result;
    }

    
}
