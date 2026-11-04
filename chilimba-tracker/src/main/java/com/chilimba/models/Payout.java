package com.chilimba.models;

import com.chilimba.exceptions.ChilimbaException;

public class Payout {
    private String memberNumber;
    private double amount;
    private String date;

    public Payout(String memberNumber, double amount, String date) {
        setMemberNumber(memberNumber);
        setAmount(amount);
        setDate(date);
    }

    public String getMemberNumber() {
        return memberNumber;
    }

    public void setMemberNumber(String memberNumber) {
        if (memberNumber == null || memberNumber.trim().isEmpty()) {
            throw new ChilimbaException("Payout recipient member number is required.");
        }
        this.memberNumber = memberNumber;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        if (!Double.isFinite(amount) || amount <= 0) {
            throw new ChilimbaException("Payout amount must be greater than zero.");
        }
        this.amount = amount;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        if (date == null || date.trim().isEmpty()) {
            throw new ChilimbaException("Payout date is required.");
        }
        this.date = date.trim();
    }
}