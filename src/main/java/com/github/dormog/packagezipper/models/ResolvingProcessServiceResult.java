package com.github.dormog.packagezipper.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@RequiredArgsConstructor
public class ResolvingProcessServiceResult {
    private final List<ZipRemoteEntry> zipRemoteEntries;
    private List<String> exceptionMessages;
}
