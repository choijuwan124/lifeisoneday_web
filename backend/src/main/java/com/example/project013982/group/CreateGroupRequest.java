package com.example.project013982.group;

/** Request body for creating a private group diary. The owner is resolved from the session. */
public record CreateGroupRequest(String name, String description) {
}
