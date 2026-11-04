package com.chilimba.models;

import com.chilimba.exceptions.ChilimbaException;

import java.util.UUID;

public abstract class Contribution {
    private String id;
    private String memberNumber;
    private double amount;
    private String date;

    public Contribution(String memberNumber, double amount, String date) {
        setId("CONTR-" + UUID.randomUUID().toString().substring(0, 8));
        setMemberNumber(memberNumber);
        setAmount(amount);
        setDate(date);
    }

    public abstract double getFee();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new ChilimbaException("Contribution id cannot be empty.");
        }
        this.id = id.trim();
    }

    public String getMemberNumber() {
        return memberNumber;
    }

    public void setMemberNumber(String memberNumber) {
        if (memberNumber == null || memberNumber.trim().isEmpty()) {
            throw new ChilimbaException("Contribution must be linked to a valid member number.");
        }
        this.memberNumber = memberNumber.trim();
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        if (!Double.isFinite(amount) || amount <= 0) {
            throw new ChilimbaException("Contribution amount must be greater than zero.");
        }
        this.amount = amount;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        if (date == null || date.trim().isEmpty()) {
            throw new ChilimbaException("Date cannot be empty.");
        }
        this.date = date.trim();
    }
}