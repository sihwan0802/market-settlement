package com.sihwan.market.member;

public record Member(long id, String email, String passwordHash, String name, String role) {}
