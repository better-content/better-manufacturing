package com.bettercontent.manufacturing.api;

import java.util.*;

/** Pure, explicit accounting plan. Native callers validate/consume slots only after this succeeds. */
public record TransferPlan(List<Move> moves, List<QualificationMove> qualificationMoves, Set<String> issuedQualifications) {
    public record Move(int slot, String sourceRole, String destinationRole) {
        public Move { TransferPlan.slot(slot); Identifiers.role(sourceRole); Identifiers.role(destinationRole); }
    }
    public record QualificationMove(int slot, String qualification) {
        public QualificationMove { TransferPlan.slot(slot); Identifiers.id(qualification); }
    }
    public record Source(Map<String, ComponentProvenance> components, Set<String> qualifications) {
        public Source {
            components = Map.copyOf(components); qualifications = Set.copyOf(qualifications);
            if (components.isEmpty() || components.size() > MachineBuild.MAX_ROLES || qualifications.size() > MachineBuild.MAX_QUALIFICATIONS)
                throw new IllegalArgumentException("Invalid source size");
            components.keySet().forEach(Identifiers::role); qualifications.forEach(Identifiers::id);
        }
        public static Source part(ComponentProvenance part) { return new Source(Map.of("part", part), Set.of()); }
        public static Source build(MachineBuild build) { return new Source(build.components(), build.qualifications()); }
    }
    public TransferPlan {
        moves = List.copyOf(moves); qualificationMoves = List.copyOf(qualificationMoves); issuedQualifications = Set.copyOf(issuedQualifications);
        if (moves.isEmpty() || moves.size() > MachineBuild.MAX_ROLES || qualificationMoves.size() > MachineBuild.MAX_QUALIFICATIONS
                || issuedQualifications.size() > MachineBuild.MAX_QUALIFICATIONS) throw new IllegalArgumentException("Invalid transfer size");
        issuedQualifications.forEach(Identifiers::id);
        var sources = new HashSet<String>(); var destinations = new HashSet<String>();
        for (Move move : moves) {
            if (!sources.add(move.slot() + ":" + move.sourceRole()) || !destinations.add(move.destinationRole()))
                throw new IllegalArgumentException("A component cannot be copied or overwritten");
        }
        if (new HashSet<>(qualificationMoves).size() != qualificationMoves.size()) throw new IllegalArgumentException("Duplicate qualification transfer");
        for (QualificationMove move : qualificationMoves)
            if (moves.stream().noneMatch(component -> component.slot() == move.slot())) throw new IllegalArgumentException("Qualification has no component source");
    }
    public MachineBuild apply(String chassis, String function, Map<Integer, Source> sources, int outputCount) {
        if (outputCount < 1 || outputCount > 64) throw new IllegalArgumentException("Invalid output count");
        var expectedSlots = new HashSet<Integer>(); moves.forEach(move -> expectedSlots.add(move.slot()));
        if (!sources.keySet().equals(expectedSlots)) throw new IllegalArgumentException("Transfer source slots mismatch");
        var result = new LinkedHashMap<String, ComponentProvenance>();
        var qualifications = new HashSet<>(issuedQualifications);
        for (var entry : sources.entrySet()) {
            var expectedRoles = new HashSet<String>();
            moves.stream().filter(move -> move.slot() == entry.getKey()).forEach(move -> expectedRoles.add(move.sourceRole()));
            if (!entry.getValue().components().keySet().equals(expectedRoles)) throw new IllegalArgumentException("Unaccounted source components");
            var expectedQualifications = new HashSet<String>();
            qualificationMoves.stream().filter(move -> move.slot() == entry.getKey()).forEach(move -> expectedQualifications.add(move.qualification()));
            if (!entry.getValue().qualifications().equals(expectedQualifications)) throw new IllegalArgumentException("Unaccounted source qualifications");
            if (outputCount != 1 && !expectedQualifications.isEmpty()) throw new IllegalArgumentException("Cannot fan out inherited access qualifications");
            qualifications.addAll(expectedQualifications);
        }
        for (Move move : moves) result.put(move.destinationRole(), sources.get(move.slot()).components().get(move.sourceRole()).divide(outputCount));
        return new MachineBuild(MachineBuild.VERSION, chassis, function, result, qualifications);
    }
    private static void slot(int value) { if (value < 0 || value > 255) throw new IllegalArgumentException("Invalid native source slot"); }
}
