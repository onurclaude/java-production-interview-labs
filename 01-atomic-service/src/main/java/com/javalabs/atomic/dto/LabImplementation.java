package com.javalabs.atomic.dto;

public enum LabImplementation {
    PLAIN_INT,
    ATOMIC_CHECK_THEN_ACT,
    CAS,
    LEAK_BAD,
    LEAK_GOOD
}
