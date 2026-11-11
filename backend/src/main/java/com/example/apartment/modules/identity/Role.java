package com.example.apartment.modules.identity;

public enum Role {
    SYSTEM_ADMIN("系统管理员"), DORM_MANAGER("宿舍管理员"), STUDENT("学生"), MAINTAINER("维修人员");
    private final String label;
    Role(String label) { this.label = label; }
    public String label() { return label; }
}
