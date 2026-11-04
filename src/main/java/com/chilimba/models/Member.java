package com.chilimba.models;

import com.chilimba.exceptions.ChilimbaException;

public class Member {
    private String memberNumber;
    private String name;
    private String phoneNumber;
    private transient String passwordHash;

    public Member(String memberNumber, String name, String phoneNumber) {
        setMemberNumber(memberNumber);
        setName(name);
        setPhoneNumber(phoneNumber);
    }

    public String getMemberNumber() {
        return memberNumber;
    }

    public void setMemberNumber(String memberNumber) {
        if (memberNumber == null || memberNumber.trim().isEmpty()) {
            throw new ChilimbaException("Member number cannot be empty.");
        }
        this.memberNumber = memberNumber.trim();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new ChilimbaException("Member name cannot be empty.");
        }
        this.name = name.trim();
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void setPhoneNumber(String phoneNumber) {
        if (phoneNumber == null) {
            throw new ChilimbaException("Invalid phone number format.");
        }
        String normalizedPhoneNumber = phoneNumber.trim();
        if (!normalizedPhoneNumber.matches("^\\+?[0-9]{10,15}$")) {
            throw new ChilimbaException("Invalid phone number format.");
        }
        this.phoneNumber = normalizedPhoneNumber;
    }
}