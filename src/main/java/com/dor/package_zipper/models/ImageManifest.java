package com.dor.package_zipper.models;

import lombok.Data;

import java.util.List;

@Data
public class ImageManifest {
    private int schemaVersion;
    private String mediaType;
    private ImageConfig config;
    private List<ImageLayer> layers;
}




