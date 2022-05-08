package com.dor.package_zipper.models;

import com.dor.package_zipper.exceptions.IllegalImageNameException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

import java.util.Arrays;

@Builder
@Data
@AllArgsConstructor
public class ImageDetails {
    @Builder.Default
    private String tag = "latest";
    private String repository;
    @NonNull
    private String imageName;
    @Builder.Default
    private String registryUrl = "registry-1.docker.io";

    public ImageDetails(String fullImageName) throws IllegalImageNameException {
        String[] imageParts = fullImageName.split("/");
//        if (imageParts.length < 2) {
//            throw new IllegalImageNameException("The image name should have the format of: [registry/][repository/]image[:tag|@digest]");
//        }
        String[] splitByAtSign = imageParts[imageParts.length - 1].split("@");
        String[] splitByColon = imageParts[imageParts.length - 1].split(":");
        if (splitByAtSign.length == 2) {
            imageName = splitByAtSign[0];
            tag = splitByAtSign[1];
        } else if (splitByColon.length == 2) {
            imageName = splitByColon[0];
            tag = splitByColon[1];
        } else {
            imageName = imageParts[imageParts.length - 1];
        }
        String partialRepositoryName;
        // Docker client doesn't seem to consider the first element as a potential registry unless there is a '.' or ':'
        if (imageParts.length > 1 && (imageParts[0].contains(".") || imageParts[0].contains(":"))) {
            registryUrl = imageParts[0];
            partialRepositoryName = String.join("/", Arrays.copyOfRange(imageParts, 1, imageParts.length - 1));;
        } else if (Arrays.copyOfRange(imageParts, 0, imageParts.length - 1).length != 0) {
            partialRepositoryName = String.join("/", Arrays.copyOfRange(imageParts, 0, imageParts.length - 1));
        } else {
            partialRepositoryName = "library";
        }
        repository = String.format("%s/%s", partialRepositoryName, imageName);
    }
}
