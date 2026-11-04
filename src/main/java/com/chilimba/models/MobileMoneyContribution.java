package com.chilimba.models;

import com.chilimba.exceptions.ChilimbaException;

public class MobileMoneyContribution extends Contribution {
    private String transactionRef;

    public MobileMoneyContribution(String memberNumber, double amount, String date, String transactionRef) {
        super(memberNumber, amount, date);
        setTransactionRef(transactionRef);
    }

    public String getTransactionRef() {
        return transactionRef;
    }

    public void setTransactionRef(String transactionRef) {
        if (transactionRef == null || transactionRef.trim().isEmpty()) {
            throw new ChilimbaException("Mobile money transaction reference is required.");
        }
        this.transactionRef = transactionRef.trim();
    }

    @Override
    public double getFee() {
        // Example fee calculation: 1% mobile money transaction charge
        return getAmount() * 0.01;
    }
}