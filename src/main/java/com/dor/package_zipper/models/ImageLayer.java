package com.dor.package_zipper.models;

import lombok.Data;

@Data
public class ImageLayer {
    private String mediaType;
    private int size;
    private String digest;
}