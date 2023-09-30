package com.dor.package_zipper.models;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public enum ShipmentLevel {
//    HEAVY("heavy"),
//    JAR_BASED("jar_based"),
    EXACTLY("exactly"),
    SINGLE("single");

    public final String level;
}
