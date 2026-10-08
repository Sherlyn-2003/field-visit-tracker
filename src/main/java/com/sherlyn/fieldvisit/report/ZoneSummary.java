package com.sherlyn.fieldvisit.report;

/**
 * Aggregated schedule health for one zone.
 *
 * @param zone            zone name
 * @param totalLocations  number of locations in the zone
 * @param overdue         count with status OVERDUE
 * @param neverVisited    count with status NEVER_VISITED
 * @param dueSoon         count with status DUE_SOON
 * @param onTrack         count with status ON_TRACK
 * @param coveragePercent percentage of locations that are not overdue / never visited
 */
public record ZoneSummary(String zone,
                          long totalLocations,
                          long overdue,
                          long neverVisited,
                          long dueSoon,
                          long onTrack,
                          double coveragePercent) {
}
