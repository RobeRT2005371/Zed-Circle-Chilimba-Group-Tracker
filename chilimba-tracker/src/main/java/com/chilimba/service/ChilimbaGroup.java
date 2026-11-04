package com.chilimba.service;

import com.chilimba.exceptions.ChilimbaException;
import com.chilimba.models.*;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class ChilimbaGroup {
    private List<Member> members = new ArrayList<>();
    private List<Contribution> contributions = new ArrayList<>();
    private List<Payout> payouts = new ArrayList<>();
    private static final String DATA_FILE = "chilimba_data.json";

    public ChilimbaGroup() {
        loadData();
    }

    public synchronized void addMember(Member member) {
        for (Member m : members) {
            if (m.getMemberNumber().equalsIgnoreCase(member.getMemberNumber())) {
                throw new ChilimbaException("Member number " + member.getMemberNumber() + " already exists.");
            }
        }
        members.add(member);
        try {
            saveData();
        } catch (IllegalStateException e) {
            members.remove(member);
            throw e;
        }
    }

    public synchronized void addContribution(Contribution contribution) {
        boolean memberExists = members.stream()
                .anyMatch(m -> m.getMemberNumber().equalsIgnoreCase(contribution.getMemberNumber()));
        if (!memberExists) {
            throw new ChilimbaException("Member " + contribution.getMemberNumber() + " not found in group.");
        }
        contributions.add(contribution);
        try {
            saveData();
        } catch (IllegalStateException e) {
            contributions.remove(contribution);
            throw e;
        }
    }

    public synchronized void addPayout(Payout payout) {
        boolean memberExists = members.stream()
                .anyMatch(m -> m.getMemberNumber().equalsIgnoreCase(payout.getMemberNumber()));
        if (!memberExists) {
            throw new ChilimbaException("Recipient member " + payout.getMemberNumber() + " not found.");
        }
        if (payout.getAmount() > getTotalPot()) {
            throw new ChilimbaException("Payout amount cannot exceed the available group pot.");
        }
        payouts.add(payout);
        try {
            saveData();
        } catch (IllegalStateException e) {
            payouts.remove(payout);
            throw e;
        }
    }

    public synchronized List<Member> getMembers() {
        return List.copyOf(members);
    }

    public synchronized Member getMember(String memberNumber) {
        return members.stream()
                .filter(member -> member.getMemberNumber().equalsIgnoreCase(memberNumber))
                .findFirst()
                .orElseThrow(() -> new ChilimbaException("Member " + memberNumber + " not found."));
    }

    public synchronized Member updateMember(String memberNumber, Member updatedMember) {
        int index = findMemberIndex(memberNumber);
        if (updatedMember == null) {
            throw new ChilimbaException("Member details cannot be null.");
        }
        if (!updatedMember.getMemberNumber().equalsIgnoreCase(memberNumber)) {
            throw new ChilimbaException("Member number cannot be changed through this endpoint.");
        }
        updatedMember.setPasswordHash(members.get(index).passwordHash());
        members.set(index, updatedMember);
        saveData();
        return updatedMember;
    }

    public synchronized void setMemberPassword(String memberNumber, String passwordHash) {
        Member member = getMember(memberNumber);
        String previousPasswordHash = member.passwordHash();
        member.setPasswordHash(passwordHash);
        try {
            saveData();
        } catch (IllegalStateException e) {
            member.setPasswordHash(previousPasswordHash);
            throw e;
        }
    }

    public synchronized void deleteMember(String memberNumber) {
        int index = findMemberIndex(memberNumber);
        members.remove(index);
        contributions.removeIf(contribution -> contribution.getMemberNumber().equalsIgnoreCase(memberNumber));
        payouts.removeIf(payout -> payout.getMemberNumber().equalsIgnoreCase(memberNumber));
        saveData();
    }

    public synchronized List<Contribution> getContributions() {
        return List.copyOf(contributions);
    }

    public synchronized Contribution getContributionById(String contributionId) {
        return contributions.stream()
                .filter(contribution -> contribution.getId().equalsIgnoreCase(contributionId))
                .findFirst()
                .orElseThrow(() -> new ChilimbaException("Contribution " + contributionId + " not found."));
    }

    public synchronized Contribution updateContribution(String contributionId, Contribution updatedContribution) {
        int index = findContributionIndex(contributionId);
        if (updatedContribution == null) {
            throw new ChilimbaException("Contribution details cannot be null.");
        }
        updatedContribution.setId(contributionId);
        contributions.set(index, updatedContribution);
        saveData();
        return updatedContribution;
    }

    public synchronized void deleteContribution(String contributionId) {
        int index = findContributionIndex(contributionId);
        contributions.remove(index);
        saveData();
    }

    public synchronized List<Payout> getPayouts() {
        return List.copyOf(payouts);
    }

    public synchronized double getTotalPot() {
        double contributionsTotal = contributions.stream()
                .mapToDouble(c -> c.getAmount() - c.getFee())
                .sum();
        double payoutsTotal = payouts.stream().mapToDouble(Payout::getAmount).sum();
        return contributionsTotal - payoutsTotal;
    }

    public synchronized Member getNextRecipient() {
        if (members.isEmpty())
            return null;
        int index = payouts.size() % members.size();
        return members.get(index);
    }

    private int findMemberIndex(String memberNumber) {
        for (int index = 0; index < members.size(); index++) {
            if (members.get(index).getMemberNumber().equalsIgnoreCase(memberNumber)) {
                return index;
            }
        }
        throw new ChilimbaException("Member " + memberNumber + " not found.");
    }

    private int findContributionIndex(String contributionId) {
        for (int index = 0; index < contributions.size(); index++) {
            if (contributions.get(index).getId().equalsIgnoreCase(contributionId)) {
                return index;
            }
        }
        throw new ChilimbaException("Contribution " + contributionId + " not found.");
    }

    // JSON File Persistence so data survives restart
    private void saveData() {
        try (Writer writer = new FileWriter(DATA_FILE)) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            JsonObject root = new JsonObject();
            root.add("members", gson.toJsonTree(members));

            JsonObject passwordHashes = new JsonObject();
            for (Member member : members) {
                if (member.passwordHash() != null) {
                    passwordHashes.addProperty(member.getMemberNumber(), member.passwordHash());
                }
            }
            root.add("memberPasswordHashes", passwordHashes);

            JsonArray contribsArray = new JsonArray();
            for (Contribution c : contributions) {
                JsonObject obj = gson.toJsonTree(c).getAsJsonObject();
                obj.addProperty("type", c instanceof MobileMoneyContribution ? "MOBILE" : "CASH");
                contribsArray.add(obj);
            }
            root.add("contributions", contribsArray);
            root.add("payouts", gson.toJsonTree(payouts));

            gson.toJson(root, writer);
        } catch (IOException e) {
            throw new IllegalStateException("Could not save Chilimba group data.", e);
        }
    }

    private void loadData() {
        File file = new File(DATA_FILE);
        if (!file.exists())
            return;

        try (Reader reader = new FileReader(file)) {
            Gson gson = new Gson();
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

            if (root.has("members")) {
                Type memberListType = new TypeToken<ArrayList<Member>>() {
                }.getType();
                members = gson.fromJson(root.get("members"), memberListType);
            }

            if (root.has("memberPasswordHashes") && root.get("memberPasswordHashes").isJsonObject()) {
                JsonObject passwordHashes = root.getAsJsonObject("memberPasswordHashes");
                for (Member member : members) {
                    if (passwordHashes.has(member.getMemberNumber())) {
                        member.setPasswordHash(passwordHashes.get(member.getMemberNumber()).getAsString());
                    }
                }
            }

            if (root.has("contributions")) {
                JsonArray arr = root.getAsJsonArray("contributions");
                contributions.clear();
                for (JsonElement el : arr) {
                    JsonObject obj = el.getAsJsonObject();
                    String type = obj.has("type") ? obj.get("type").getAsString() : "CASH";
                    String memberNumber = obj.get("memberNumber").getAsString();
                    double amount = obj.get("amount").getAsDouble();
                    String date = obj.get("date").getAsString();

                    Contribution contribution;
                    if ("MOBILE".equals(type)) {
                        String ref = obj.has("transactionRef") ? obj.get("transactionRef").getAsString()
                                : "REF-UNKNOWN";
                        contribution = new MobileMoneyContribution(memberNumber, amount, date, ref);
                    } else {
                        contribution = new CashContribution(memberNumber, amount, date);
                    }

                    if (obj.has("id") && !obj.get("id").isJsonNull()) {
                        contribution.setId(obj.get("id").getAsString());
                    }
                    contributions.add(contribution);
                }
            }

            if (root.has("payouts")) {
                Type payoutListType = new TypeToken<ArrayList<Payout>>() {
                }.getType();
                payouts = gson.fromJson(root.get("payouts"), payoutListType);
            }
        } catch (IOException | JsonParseException | IllegalStateException e) {
            throw new IllegalStateException("Could not load Chilimba group data.", e);
        }
    }
}