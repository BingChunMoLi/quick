package ${basePackage}.model;

import java.util.List;

/** Shared pagination response. */
public record PageResult<T>(List<T> records, long total, long page, long size) { }
