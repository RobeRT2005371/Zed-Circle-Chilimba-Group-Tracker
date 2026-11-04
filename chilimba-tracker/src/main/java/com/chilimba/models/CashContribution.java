package com.chilimba.models;

public final class CashContribution extends Contribution {
    public CashContribution(String memberNumber, double amount, String date) {
        super(memberNumber, amount, date);
    }

    @Override
    public double getFee() {
        return 0;
    }
}
