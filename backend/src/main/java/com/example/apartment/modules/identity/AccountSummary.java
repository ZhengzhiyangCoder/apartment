package com.example.apartment.modules.identity;

/** The limited account facts housing may display. No credentials or second role fact. */
public record AccountSummary(long id, String username, boolean enabled) {}
