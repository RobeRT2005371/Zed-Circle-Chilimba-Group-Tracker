package com.chilimba;

import com.chilimba.exceptions.ChilimbaException;
import com.chilimba.models.CashContribution;
import com.chilimba.models.Contribution;
import com.chilimba.models.Member;
import com.chilimba.models.MobileMoneyContribution;
import com.chilimba.models.Payout;
import com.chilimba.security.PasswordUtil;
import com.chilimba.service.ChilimbaGroup;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

public class Main {
    public static void main(String[] args) {
        String adminPassword = System.getenv("CHILIMBA_ADMIN_PASSWORD");
        if (adminPassword == null || adminPassword.isBlank()) {
            throw new IllegalStateException("Set CHILIMBA_ADMIN_PASSWORD before starting the application.");
        }
        ChilimbaGroup group = new ChilimbaGroup();

        Javalin app = Javalin.create(config -> config.staticFiles.add("/public"));

        app.before("/api/*", ctx -> {
            if (!ctx.path().startsWith("/api/auth/") && ctx.sessionAttribute("role") == null) {
                ctx.status(401).json(Map.of("error", "Sign in to access this resource."));
                ctx.skipRemainingHandlers();
            }
        });

        app.post("/api/auth/admin/login", ctx -> {
            JsonObject json = parseBody(ctx.body());
            String password = requiredString(json, "password");
            if (!MessageDigest.isEqual(adminPassword.getBytes(StandardCharsets.UTF_8),
                    password.getBytes(StandardCharsets.UTF_8))) {
                ctx.status(401).json(Map.of("error", "Invalid admin password."));
                return;
            }
            startSession(ctx, "ADMIN", null);
            ctx.json(Map.of("role", "ADMIN"));
        });

        app.post("/api/auth/member/setup", ctx -> {
            JsonObject json = parseBody(ctx.body());
            String memberNumber = requiredString(json, "memberNumber");
            String phoneNumber = requiredString(json, "phoneNumber");
            String password = requiredPassword(json);
            Member member = group.getMember(memberNumber);
            if (member.passwordHash() != null) {
                ctx.status(409).json(Map.of("error", "A password is already set for this member."));
                return;
            }
            if (!member.getPhoneNumber().equals(phoneNumber.trim())) {
                ctx.status(401).json(Map.of("error", "Member number and registered phone do not match."));
                return;
            }
            group.setMemberPassword(memberNumber, PasswordUtil.hash(password));
            startSession(ctx, "MEMBER", member.getMemberNumber());
            ctx.status(201).json(Map.of("role", "MEMBER", "memberNumber", member.getMemberNumber()));
        });

        app.post("/api/auth/member/login", ctx -> {
            JsonObject json = parseBody(ctx.body());
            String memberNumber = requiredString(json, "memberNumber");
            String password = requiredString(json, "password");
            Member member;
            try {
                member = group.getMember(memberNumber);
            } catch (ChilimbaException e) {
                ctx.status(401).json(Map.of("error", "Invalid member number or password."));
                return;
            }
            if (!PasswordUtil.verify(password, member.passwordHash())) {
                ctx.status(401).json(Map.of("error", "Invalid member number or password."));
                return;
            }
            startSession(ctx, "MEMBER", member.getMemberNumber());
            ctx.json(Map.of("role", "MEMBER", "memberNumber", member.getMemberNumber()));
        });

        app.get("/api/auth/session", ctx -> {
            String role = ctx.sessionAttribute("role");
            if (role == null) {
                ctx.json(Map.of("authenticated", false));
            } else if ("MEMBER".equals(role)) {
                ctx.json(Map.of("authenticated", true, "role", role,
                        "memberNumber", ctx.sessionAttribute("memberNumber")));
            } else {
                ctx.json(Map.of("authenticated", true, "role", role));
            }
        });

        app.post("/api/auth/logout", ctx -> {
            if (ctx.req().getSession(false) != null) {
                ctx.req().getSession(false).invalidate();
            }
            ctx.json(Map.of("authenticated", false));
        });

        app.exception(ChilimbaException.class,
                (exception, ctx) -> ctx.status(400).json(Map.of("error", exception.getMessage())));
        app.exception(JsonParseException.class,
                (exception, ctx) -> ctx.status(400).json(Map.of("error", "Request body must contain valid JSON.")));

        app.get("/api/members", ctx -> ctx.json(group.getMembers()));
        app.get("/api/members/{memberNumber}", ctx -> ctx.json(group.getMember(ctx.pathParam("memberNumber"))));

        app.post("/api/members", ctx -> {
            if (!requireAdmin(ctx)) return;
            JsonObject json = parseBody(ctx.body());
            Member member = new Member(
                    requiredString(json, "memberNumber"),
                    requiredString(json, "name"),
                    requiredString(json, "phoneNumber"));
            group.addMember(member);
            ctx.status(201).json(member);
        });

        app.put("/api/members/{memberNumber}", ctx -> {
            if (!requireAdmin(ctx)) return;
            JsonObject json = parseBody(ctx.body());
            String memberNumber = ctx.pathParam("memberNumber");
            Member updatedMember = new Member(
                    memberNumber,
                    requiredString(json, "name"),
                    requiredString(json, "phoneNumber"));
            ctx.json(group.updateMember(memberNumber, updatedMember));
        });

        app.delete("/api/members/{memberNumber}", ctx -> {
            if (!requireAdmin(ctx)) return;
            group.deleteMember(ctx.pathParam("memberNumber"));
            ctx.status(204);
        });

        app.get("/api/contributions", ctx -> ctx.json(group.getContributions()));
        app.get("/api/contributions/{id}", ctx -> ctx.json(group.getContributionById(ctx.pathParam("id"))));

        app.post("/api/contributions", ctx -> {
            JsonObject json = parseBody(ctx.body());
            Contribution contribution = buildContribution(json);
            if ("MEMBER".equals(ctx.sessionAttribute("role"))
                    && !contribution.getMemberNumber().equalsIgnoreCase(ctx.sessionAttribute("memberNumber"))) {
                ctx.status(403).json(Map.of("error", "Members can only record their own contributions."));
                return;
            }
            group.addContribution(contribution);
            ctx.status(201).json(contribution);
        });

        app.put("/api/contributions/{id}", ctx -> {
            if (!requireAdmin(ctx)) return;
            JsonObject json = parseBody(ctx.body());
            String contributionId = ctx.pathParam("id");
            Contribution contribution = buildContribution(json);
            contribution.setId(contributionId);
            ctx.json(group.updateContribution(contributionId, contribution));
        });

        app.delete("/api/contributions/{id}", ctx -> {
            if (!requireAdmin(ctx)) return;
            group.deleteContribution(ctx.pathParam("id"));
            ctx.status(204);
        });

        app.get("/api/payouts", ctx -> ctx.json(group.getPayouts()));

        app.get("/api/summary", ctx -> {
            Map<String, Object> summary = new HashMap<>();
            summary.put("totalPot", group.getTotalPot());
            summary.put("nextRecipient", group.getNextRecipient());
            summary.put("memberCount", group.getMembers().size());
            summary.put("payouts", group.getPayouts());
            ctx.json(summary);
        });

        app.post("/api/payouts", ctx -> {
            if (!requireAdmin(ctx)) return;
            JsonObject json = parseBody(ctx.body());
            Payout payout = new Payout(
                    requiredString(json, "memberNumber"),
                    requiredAmount(json, "amount"),
                    requiredString(json, "date"));
            group.addPayout(payout);
            ctx.status(201).json(payout);
        });

        app.start(7000);
    }

    private static boolean requireAdmin(Context ctx) {
        if (!"ADMIN".equals(ctx.sessionAttribute("role"))) {
            ctx.status(403).json(Map.of("error", "Admin access is required."));
            return false;
        }
        return true;
    }

    private static void startSession(Context ctx, String role, String memberNumber) {
        ctx.req().getSession(true);
        ctx.req().changeSessionId();
        ctx.sessionAttribute("role", role);
        if (memberNumber != null) {
            ctx.sessionAttribute("memberNumber", memberNumber);
        }
    }

    private static String requiredPassword(JsonObject json) {
        String password = requiredString(json, "password");
        if (password.length() < 10) {
            throw new ChilimbaException("Password must be at least 10 characters long.");
        }
        return password;
    }

    private static Contribution buildContribution(JsonObject json) {
        String memberNumber = requiredString(json, "memberNumber");
        double amount = requiredAmount(json, "amount");
        String date = requiredString(json, "date");
        String type = json.has("type") ? requiredString(json, "type") : "CASH";

        if ("MOBILE".equalsIgnoreCase(type)) {
            return new MobileMoneyContribution(
                    memberNumber,
                    amount,
                    date,
                    requiredString(json, "transactionRef"));
        }
        if ("CASH".equalsIgnoreCase(type)) {
            return new CashContribution(memberNumber, amount, date);
        }
        throw new ChilimbaException("Payment type must be CASH or MOBILE.");
    }

    private static JsonObject parseBody(String body) {
        JsonElement element = JsonParser.parseString(body);
        if (!element.isJsonObject()) {
            throw new ChilimbaException("Request body must be a JSON object.");
        }
        return element.getAsJsonObject();
    }

    private static String requiredString(JsonObject json, String field) {
        if (!json.has(field) || json.get(field).isJsonNull()
                || !json.get(field).isJsonPrimitive()
                || !json.getAsJsonPrimitive(field).isString()) {
            throw new ChilimbaException(field + " is required and must be a string.");
        }
        return json.get(field).getAsString();
    }

    private static double requiredAmount(JsonObject json, String field) {
        if (!json.has(field) || !json.get(field).isJsonPrimitive()) {
            throw new ChilimbaException(field + " is required and must be a number.");
        }
        JsonPrimitive value = json.getAsJsonPrimitive(field);
        if (!value.isNumber()) {
            throw new ChilimbaException(field + " is required and must be a number.");
        }
        try {
            double amount = value.getAsDouble();
            if (!Double.isFinite(amount) || amount <= 0) {
                throw new ChilimbaException(field + " must be greater than zero.");
            }
            return amount;
        } catch (NumberFormatException exception) {
            throw new ChilimbaException(field + " must be a valid number.");
        }
    }
}
