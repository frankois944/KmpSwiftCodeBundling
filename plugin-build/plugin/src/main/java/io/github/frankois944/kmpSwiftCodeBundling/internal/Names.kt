package io.github.frankois944.kmpSwiftCodeBundling.internal

internal fun lowerCamelCaseName(vararg nameParts: String?): String =
    nameParts
        .filterNot { it.isNullOrEmpty() }
        .mapIndexed { index, part ->
            if (index == 0) part!!.replaceFirstChar { it.lowercase() } else part!!.replaceFirstChar { it.uppercase() }
        }.joinToString("")

/** Appends underscores until the identifier no longer collides with an already used one. */
internal fun String.collisionFreeIdentifier(usedIdentifiers: Set<String>): String {
    var candidate = this

    while (candidate in usedIdentifiers) {
        candidate += "_"
    }

    return candidate
}
