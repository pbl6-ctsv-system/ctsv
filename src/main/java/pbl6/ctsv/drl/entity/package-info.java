/**
 * Internal DRL persistence, never a cross-module API or HTTP representation.
 * SQL identifiers are quoted and preserved by
 * {@link pbl6.ctsv.drl.config.DrlPhysicalNamingStrategy}: Boot's
 * default transforms even quoted names, while Vietnamese collation distinguishes
 * some mixed-case digraphs from their lower-case spelling. Non-DRL identifiers
 * retain Boot's default naming behavior. Foreign keys remain scalar CTSV IDs.
 * {@link pbl6.ctsv.drl.config.DrlConfiguration} registers the naming strategy and
 * conditionally exposes the repository in Boot's shared persistence unit. The
 * repository condition requires EntityManagerFactory and JdbcTemplate bean definitions;
 * default tests can keep JPA disabled. Boot SQL IT verifies actual bean availability.
 */
package pbl6.ctsv.drl.entity;
