package com.github.dormog.packagezipper.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@AllArgsConstructor
@Data
@Builder
public class ZipStreamerBody {
    private List<ZipRemoteEntry> files;
}
