package com.github.dormog.packagezipper.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@AllArgsConstructor
@Data
@Builder
public class ZipRemoteEntry {
    private String zipPath;
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
