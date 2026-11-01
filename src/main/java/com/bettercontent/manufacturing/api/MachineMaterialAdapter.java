package com.bettercontent.manufacturing.api;

/** Custom owners implement this contract on their own native state holders. */
public interface MachineMaterialAdapter<T> {
    String id();
    MachineBuild readBuild(T host);
    /** Reject a replacement unless native contents, stored resources and geometry remain valid. */
    void writeBuild(T host, MachineBuild build);
    EngineeringEvaluator.Evaluation evaluate(T host);
    void invalidate(T host);
    default String explanation(T host) { return evaluate(host).blockedReason(); }
}
