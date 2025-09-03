package com.example.shade.model;

/**
 * Date-9/3/2025
 * By Sardor Tokhirov
 * Time-6:49 AM (GMT+5)
 */
public enum Language {
    RU("ru"), UZ("uz");
    private final String code;

    Language(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
