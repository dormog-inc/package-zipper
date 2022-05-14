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

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ZipRemoteEntry other = (ZipRemoteEntry) obj;
        if (Url == null) {
            if (other.Url != null)
                return false;
        } else if (!Url.equals(other.Url))
            return false;
        if (ZipPath == null) {
            if (other.ZipPath != null)
                return false;
        } else if (!ZipPath.equals(other.ZipPath))
            return false;
        return true;
    }
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((Url == null) ? 0 : Url.hashCode());
        result = prime * result + ((ZipPath == null) ? 0 : ZipPath.hashCode());
        return result;
    }

    
}
