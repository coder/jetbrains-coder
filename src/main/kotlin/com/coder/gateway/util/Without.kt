package com.coder.gateway.util

/**
 * Run block with provided arguments after checking they are all non-null.  This
 * is to enforce non-null values and should be used to signify developer error.
 */
fun <A, Z> withoutNull(
    a: A?,
    block: (a: A) -> Z,
): Z {
    if (a == null) {
        throw Exception("Unexpected null value")
    }
    return block(a)
}

/**
 * Run block with provided arguments after checking they are all non-null.  This
 * is to enforce non-null values and should be used to signify developer error.
 */
fun <A, B, Z> withoutNull(
    a: A?,
    b: B?,
    block: (a: A, b: B) -> Z,
): Z {
    if (a == null || b == null) {
        throw Exception("Unexpected null value")
    }
    return block(a, b)
}

/**
 * Run block with provided arguments after checking they are all non-null.  This
 * is to enforce non-null values and should be used to signify developer error.
 */
fun <A, B, C, D, Z> withoutNull(
    a: A?,
    b: B?,
    c: C?,
    d: D?,
    block: (a: A, b: B, c: C, d: D) -> Z,
): Z {
    if (a == null || b == null || c == null || d == null) {
        throw Exception("Unexpected null value")
    }
    return block(a, b, c, d)
}
