package cn.kynarain.legacyidaliases.api;

/**
 * Per registry statistics of the last alias injection.
 *
 * @param entries    mappings in the table
 * @param aliases    alias identifiers registered in the registry
 * @param unresolved mappings dropped because the current registry does not know the target
 */
public record LegacyRegistryStats(int entries, int aliases, int unresolved) {
}
