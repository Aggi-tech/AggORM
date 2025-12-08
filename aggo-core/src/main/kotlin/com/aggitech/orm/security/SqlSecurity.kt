package com.aggitech.orm.security

/**
 * Centralized SQL security utilities for preventing SQL Injection and other attacks.
 *
 * This module provides:
 * - Identifier validation (table names, column names, aliases)
 * - Value sanitization
 * - Pattern detection for SQL injection attempts
 * - Safe string escaping
 *
 * Security Principles:
 * 1. Always use parameterized queries (prepared statements)
 * 2. Validate all identifiers against allowlist patterns
 * 3. Never interpolate user input directly into SQL
 * 4. Escape special characters in identifiers
 * 5. Limit identifier lengths to prevent buffer overflow attacks
 */
object SqlSecurity {

    // Maximum lengths to prevent buffer overflow attacks
    private const val MAX_IDENTIFIER_LENGTH = 128
    private const val MAX_ALIAS_LENGTH = 64
    private const val MAX_VALUE_LENGTH = 65535

    // Valid identifier pattern: alphanumeric, underscore, starting with letter or underscore
    // Also allows quoted identifiers
    private val VALID_IDENTIFIER_PATTERN = Regex("^[a-zA-Z_][a-zA-Z0-9_]*$")

    // Dangerous SQL patterns that should never appear in identifiers
    private val DANGEROUS_PATTERNS = listOf(
        Regex("--"),                          // SQL comment
        Regex("/\\*"),                        // Block comment start
        Regex("\\*/"),                        // Block comment end
        Regex(";"),                           // Statement terminator
        Regex("'"),                           // Single quote (unescaped)
        Regex("\"\""),                        // Double escaped quote
        Regex("\\\\"),                        // Backslash
        Regex("(?i)\\bUNION\\b"),            // UNION keyword
        Regex("(?i)\\bSELECT\\b"),           // SELECT keyword (in identifiers)
        Regex("(?i)\\bINSERT\\b"),           // INSERT keyword
        Regex("(?i)\\bUPDATE\\b"),           // UPDATE keyword
        Regex("(?i)\\bDELETE\\b"),           // DELETE keyword
        Regex("(?i)\\bDROP\\b"),             // DROP keyword
        Regex("(?i)\\bTRUNCATE\\b"),         // TRUNCATE keyword
        Regex("(?i)\\bEXEC\\b"),             // EXEC keyword
        Regex("(?i)\\bEXECUTE\\b"),          // EXECUTE keyword
        Regex("(?i)\\bXP_\\w+"),             // SQL Server extended procedures
        Regex("(?i)\\bSP_\\w+"),             // SQL Server stored procedures
        Regex("(?i)\\bINFORMATION_SCHEMA\\b"), // Schema information
        Regex("(?i)\\bSYS\\."),              // System tables
        Regex("(?i)\\bPG_\\w+"),             // PostgreSQL system tables
        Regex("(?i)\\bSLEEP\\s*\\("),        // Time-based attacks
        Regex("(?i)\\bBENCHMARK\\s*\\("),    // MySQL benchmark
        Regex("(?i)\\bWAITFOR\\b"),          // SQL Server delay
        Regex("(?i)\\bLOAD_FILE\\s*\\("),    // File reading
        Regex("(?i)\\bINTO\\s+OUTFILE\\b"),  // File writing
        Regex("(?i)\\bINTO\\s+DUMPFILE\\b"), // File writing
        Regex("0x[0-9a-fA-F]+"),             // Hex encoding (potential bypass)
        Regex("(?i)CHAR\\s*\\("),            // CHAR function (bypass)
        Regex("(?i)CONCAT\\s*\\("),          // CONCAT function (bypass)
        Regex("(?i)\\|\\|"),                 // String concatenation
    )

    // Reserved SQL keywords that shouldn't be used as identifiers without quoting
    private val RESERVED_KEYWORDS = setOf(
        "SELECT", "INSERT", "UPDATE", "DELETE", "DROP", "CREATE", "ALTER", "TRUNCATE",
        "TABLE", "INDEX", "VIEW", "DATABASE", "SCHEMA", "FROM", "WHERE", "AND", "OR",
        "NOT", "NULL", "TRUE", "FALSE", "IN", "LIKE", "BETWEEN", "IS", "AS", "ON",
        "JOIN", "LEFT", "RIGHT", "INNER", "OUTER", "FULL", "CROSS", "NATURAL",
        "ORDER", "BY", "ASC", "DESC", "LIMIT", "OFFSET", "GROUP", "HAVING",
        "UNION", "INTERSECT", "EXCEPT", "ALL", "DISTINCT", "INTO", "VALUES",
        "SET", "CASE", "WHEN", "THEN", "ELSE", "END", "EXISTS", "ANY", "SOME",
        "PRIMARY", "KEY", "FOREIGN", "REFERENCES", "CONSTRAINT", "UNIQUE",
        "CHECK", "DEFAULT", "CASCADE", "RESTRICT", "USER", "PASSWORD", "GRANT", "REVOKE"
    )

    /**
     * Exception thrown when a SQL injection attempt is detected.
     */
    class SqlInjectionException(message: String) : SecurityException(message)

    /**
     * Exception thrown when an identifier is invalid.
     */
    class InvalidIdentifierException(message: String) : IllegalArgumentException(message)

    /**
     * Validates and sanitizes a SQL identifier (table name, column name).
     *
     * @param identifier The identifier to validate
     * @param type Description of the identifier type for error messages
     * @return The validated identifier
     * @throws InvalidIdentifierException if the identifier is invalid
     * @throws SqlInjectionException if SQL injection is detected
     */
    fun validateIdentifier(identifier: String, type: String = "identifier"): String {
        // Check for null or empty
        if (identifier.isBlank()) {
            throw InvalidIdentifierException("$type cannot be blank")
        }

        // Check length
        if (identifier.length > MAX_IDENTIFIER_LENGTH) {
            throw InvalidIdentifierException(
                "$type exceeds maximum length of $MAX_IDENTIFIER_LENGTH characters"
            )
        }

        // Check for dangerous patterns
        for (pattern in DANGEROUS_PATTERNS) {
            if (pattern.containsMatchIn(identifier)) {
                throw SqlInjectionException(
                    "Potential SQL injection detected in $type: '$identifier' matches dangerous pattern"
                )
            }
        }

        // Validate against allowed pattern
        if (!VALID_IDENTIFIER_PATTERN.matches(identifier)) {
            throw InvalidIdentifierException(
                "$type '$identifier' contains invalid characters. " +
                "Only alphanumeric characters and underscores are allowed, " +
                "and it must start with a letter or underscore."
            )
        }

        return identifier
    }

    /**
     * Validates an alias (which may have slightly different rules than identifiers).
     *
     * @param alias The alias to validate
     * @return The validated alias
     * @throws InvalidIdentifierException if the alias is invalid
     * @throws SqlInjectionException if SQL injection is detected
     */
    fun validateAlias(alias: String): String {
        if (alias.isBlank()) {
            throw InvalidIdentifierException("Alias cannot be blank")
        }

        if (alias.length > MAX_ALIAS_LENGTH) {
            throw InvalidIdentifierException(
                "Alias exceeds maximum length of $MAX_ALIAS_LENGTH characters"
            )
        }

        // Check for dangerous patterns
        for (pattern in DANGEROUS_PATTERNS) {
            if (pattern.containsMatchIn(alias)) {
                throw SqlInjectionException(
                    "Potential SQL injection detected in alias: '$alias'"
                )
            }
        }

        // Aliases can be more permissive but still need validation
        if (!VALID_IDENTIFIER_PATTERN.matches(alias)) {
            throw InvalidIdentifierException(
                "Alias '$alias' contains invalid characters"
            )
        }

        return alias
    }

    /**
     * Safely quotes an identifier for use in SQL.
     * Uses double quotes (SQL standard) and escapes any embedded quotes.
     *
     * @param identifier The identifier to quote
     * @return The safely quoted identifier
     */
    fun quoteIdentifier(identifier: String): String {
        val validated = validateIdentifier(identifier)
        // Escape any double quotes by doubling them
        val escaped = validated.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    /**
     * Validates a value before it's used as a parameter.
     * Note: Values should always be passed as prepared statement parameters,
     * but this provides an additional layer of validation.
     *
     * @param value The value to validate
     * @return The validated value
     * @throws SqlInjectionException if SQL injection is detected
     */
    fun validateValue(value: Any?): Any? {
        if (value == null) return null

        when (value) {
            is String -> {
                if (value.length > MAX_VALUE_LENGTH) {
                    throw InvalidIdentifierException(
                        "Value exceeds maximum length of $MAX_VALUE_LENGTH characters"
                    )
                }

                // Check for obvious injection attempts in string values
                // Note: Prepared statements handle this, but we add defense in depth
                val suspiciousPatterns = listOf(
                    Regex("(?i)';\\s*DROP\\b"),
                    Regex("(?i)';\\s*DELETE\\b"),
                    Regex("(?i)';\\s*UPDATE\\b"),
                    Regex("(?i)';\\s*INSERT\\b"),
                    Regex("(?i)'\\s*OR\\s+'1'\\s*=\\s*'1"),
                    Regex("(?i)'\\s*OR\\s+1\\s*=\\s*1"),
                    Regex("(?i)--\\s*$"),
                )

                for (pattern in suspiciousPatterns) {
                    if (pattern.containsMatchIn(value)) {
                        throw SqlInjectionException(
                            "Potential SQL injection detected in value"
                        )
                    }
                }
            }
            is Number, is Boolean, is java.util.UUID, is java.time.temporal.Temporal -> {
                // These types are safe
            }
            is Collection<*> -> {
                // Validate each element in collections
                value.forEach { validateValue(it) }
            }
            is Array<*> -> {
                value.forEach { validateValue(it) }
            }
        }

        return value
    }

    /**
     * Validates that a LIKE pattern doesn't contain injection attempts.
     *
     * @param pattern The LIKE pattern to validate
     * @return The validated pattern
     */
    fun validateLikePattern(pattern: String): String {
        if (pattern.length > MAX_VALUE_LENGTH) {
            throw InvalidIdentifierException("LIKE pattern too long")
        }

        // Check for dangerous patterns (excluding % and _ which are valid LIKE wildcards)
        val dangerousInLike = listOf(
            Regex("--"),
            Regex("/\\*"),
            Regex(";"),
            Regex("(?i)\\bUNION\\b"),
            Regex("(?i)\\bSELECT\\b"),
        )

        for (regex in dangerousInLike) {
            if (regex.containsMatchIn(pattern)) {
                throw SqlInjectionException(
                    "Potential SQL injection detected in LIKE pattern"
                )
            }
        }

        return pattern
    }

    /**
     * Validates a raw SQL expression.
     * Raw SQL should be avoided, but when necessary, this provides validation.
     *
     * @param sql The raw SQL to validate
     * @return The validated SQL
     * @throws SqlInjectionException if dangerous patterns are detected
     */
    fun validateRawSql(sql: String): String {
        if (sql.isBlank()) {
            throw InvalidIdentifierException("Raw SQL cannot be blank")
        }

        // Check for dangerous patterns in raw SQL
        val dangerousInRaw = listOf(
            Regex("(?i)\\bDROP\\b"),
            Regex("(?i)\\bTRUNCATE\\b"),
            Regex("(?i)\\bDELETE\\s+FROM\\b"),
            Regex("(?i)\\bUPDATE\\s+\\w+\\s+SET\\b"),
            Regex("(?i)\\bINSERT\\s+INTO\\b"),
            Regex("(?i)\\bCREATE\\b"),
            Regex("(?i)\\bALTER\\b"),
            Regex("(?i)\\bGRANT\\b"),
            Regex("(?i)\\bREVOKE\\b"),
            Regex("(?i)\\bEXEC\\b"),
            Regex("(?i)\\bEXECUTE\\b"),
        )

        for (pattern in dangerousInRaw) {
            if (pattern.containsMatchIn(sql)) {
                throw SqlInjectionException(
                    "Dangerous SQL statement detected in raw expression: ${pattern.pattern}"
                )
            }
        }

        return sql
    }

    /**
     * Checks if an identifier is a reserved SQL keyword.
     *
     * @param identifier The identifier to check
     * @return true if it's a reserved keyword
     */
    fun isReservedKeyword(identifier: String): Boolean {
        return identifier.uppercase() in RESERVED_KEYWORDS
    }

    /**
     * Sanitizes a string for safe logging (removes potential sensitive data patterns).
     *
     * @param input The string to sanitize
     * @return The sanitized string safe for logging
     */
    fun sanitizeForLogging(input: String): String {
        return input
            .replace(Regex("password\\s*=\\s*'[^']*'", RegexOption.IGNORE_CASE), "password='***'")
            .replace(Regex("pwd\\s*=\\s*'[^']*'", RegexOption.IGNORE_CASE), "pwd='***'")
            .replace(Regex("secret\\s*=\\s*'[^']*'", RegexOption.IGNORE_CASE), "secret='***'")
            .replace(Regex("token\\s*=\\s*'[^']*'", RegexOption.IGNORE_CASE), "token='***'")
            .replace(Regex("api_key\\s*=\\s*'[^']*'", RegexOption.IGNORE_CASE), "api_key='***'")
    }
}

/**
 * Extension function to safely quote an identifier.
 */
fun String.asSqlIdentifier(): String = SqlSecurity.quoteIdentifier(this)

/**
 * Extension function to validate an identifier.
 */
fun String.validateAsSqlIdentifier(type: String = "identifier"): String =
    SqlSecurity.validateIdentifier(this, type)
