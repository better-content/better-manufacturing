package com.bettercontent.manufacturing.recipe;

import com.bettercontent.manufacturing.api.Identifiers;
import com.bettercontent.manufacturing.api.MachineBuild;
import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.GsonHelper;
import java.util.HashSet;
import java.util.Set;

/** Bounded recipe-issued identities; never copied from arbitrary ingredient metadata. */
final class RecipeQualifications {
    private RecipeQualifications() {}
    static Set<String> fromJson(JsonObject json) {
        if (!json.has("qualifications")) return Set.of();
        var array = GsonHelper.getAsJsonArray(json, "qualifications");
        size(array.size());
        var result = new HashSet<String>();
        for (var element : array) add(result, GsonHelper.convertToString(element, "qualification"));
        return Set.copyOf(result);
    }
    static Set<String> read(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt(); size(count);
        var result = new HashSet<String>();
        for (int i = 0; i < count; i++) add(result, buffer.readUtf(192));
        return Set.copyOf(result);
    }
    static void write(FriendlyByteBuf buffer, Set<String> values) {
        size(values.size()); buffer.writeVarInt(values.size());
        values.stream().sorted().forEach(id -> buffer.writeUtf(Identifiers.id(id), 192));
    }
    static java.util.List<com.bettercontent.manufacturing.api.TransferPlan.QualificationMove> movesFromJson(JsonObject json) {
        if (!json.has("qualification_transfers")) return java.util.List.of();
        var array = GsonHelper.getAsJsonArray(json, "qualification_transfers"); size(array.size());
        var result = new java.util.ArrayList<com.bettercontent.manufacturing.api.TransferPlan.QualificationMove>();
        for (var element : array) {
            var move = GsonHelper.convertToJsonObject(element, "qualification transfer");
            result.add(new com.bettercontent.manufacturing.api.TransferPlan.QualificationMove(GsonHelper.getAsInt(move, "slot"), GsonHelper.getAsString(move, "qualification")));
        }
        return java.util.List.copyOf(result);
    }
    static java.util.List<com.bettercontent.manufacturing.api.TransferPlan.QualificationMove> readMoves(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt(); size(count);
        var result = new java.util.ArrayList<com.bettercontent.manufacturing.api.TransferPlan.QualificationMove>();
        for (int i = 0; i < count; i++) result.add(new com.bettercontent.manufacturing.api.TransferPlan.QualificationMove(buffer.readVarInt(), buffer.readUtf(192)));
        return java.util.List.copyOf(result);
    }
    static void writeMoves(FriendlyByteBuf buffer, java.util.List<com.bettercontent.manufacturing.api.TransferPlan.QualificationMove> moves) {
        size(moves.size()); buffer.writeVarInt(moves.size());
        for (var move : moves) { buffer.writeVarInt(move.slot()); buffer.writeUtf(move.qualification(), 192); }
    }
    private static void size(int value) {
        if (value < 0 || value > MachineBuild.MAX_QUALIFICATIONS) throw new IllegalArgumentException("Invalid qualification count");
    }
    private static void add(Set<String> result, String value) {
        if (!result.add(Identifiers.id(value))) throw new IllegalArgumentException("Duplicate qualification");
    }
}
