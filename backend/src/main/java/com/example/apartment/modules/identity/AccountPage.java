package com.example.apartment.modules.identity;

import java.util.List;

public record AccountPage(List<AccountView> items, long total, int page, int size) {}
