package com.example.project013982.user;

/** A stored account. Never return this object (including its password hash) from an API. */
public record User(Long id, String username, String passwordHash, String nickname) {
}
