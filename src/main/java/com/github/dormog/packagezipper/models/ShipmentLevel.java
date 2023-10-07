package com.github.dormog.packagezipper.models;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public enum ShipmentLevel {
    EXACTLY("exactly"),
    SINGLE("single"),
    HEAVY("heavy");

    public final String level;
}
