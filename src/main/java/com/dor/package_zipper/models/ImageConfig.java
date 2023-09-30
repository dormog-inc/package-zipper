package com.dor.package_zipper.models;

import lombok.Data;

@Data
public class ImageConfig {
    private String mediaType;
    private int size;
    private String digest;
}