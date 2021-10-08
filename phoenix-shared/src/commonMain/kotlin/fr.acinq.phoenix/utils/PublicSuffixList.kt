package fr.acinq.phoenix.utils

/* Implements "Public Suffix List" spec:
 * https://publicsuffix.org/list/
 */
class PublicSuffixList(
    list: String
) {
    /**
     * Represents a single "rule".
     * That is, a singe (parsed) line from Public Suffix List.
     */
    class Rule(
        val labels: List<String>,
        val isExceptionRule: Boolean
    ) {
        companion object {
            private val whitespace = "\\s+".toRegex()

            /**
             * Attempts to parse a rule from the given line.
             * If the line is only whitespace or a comment, then returns null.
             */
            fun parse(line: String): Rule? {
                // Definitions:
                // - Each line is only read up to the first whitespace;
                //   entire lines can also be commented using //.
                // - Each line which is not entirely whitespace or begins with a comment contains a rule.
                // - A rule may begin with a "!" (exclamation mark).
                //   If it does, it is labelled as an "exception rule"
                //   and then treated as if the exclamation mark is not present.
                // - A domain or rule can be split into a list of labels using the separator "." (dot).
                //   The separator is not part of any label.
                //   Empty labels are not permitted, meaning that leading and trailing dots are ignored.

                var suffix = line.split(regex = whitespace, limit = 1).firstOrNull() ?: ""
                if (suffix.isBlank() || suffix.startsWith("//")) {
                    return null
                }

                var isExceptionRule = false
                if (suffix.startsWith('!')) {
                    isExceptionRule = true
                    suffix = suffix.substring(1)
                }

                val labels = suffix.split('.').filter {
                    it.isNotBlank()
                }.map {
                    it.lowercase()
                }
                if (labels.isEmpty()) {
                    return null
                }
                if (isExceptionRule && labels.size == 1) { // implicitly illegal
                    return null
                }

                return Rule(labels, isExceptionRule)
            }
        }

        fun matches(domain: List<String>): Boolean {
            // A domain is said to match a rule if and only if all the following conditions are met:
            // - When the domain and rule are split into corresponding labels,
            //   that the domain contains as many or more labels than the rule.
            // - Beginning with the right-most labels of both the domain and the rule,
            //   and continuing for all labels in the rule, one finds that for every pair,
            //   either they are identical, or that the label from the rule is "*".

            val dSize = domain.size
            val lSize = labels.size // known to be non-empty

            if (dSize < lSize) {
                return false
            }

            var dIdx = dSize
            var lIdx = lSize

            while (lIdx > 0) {
                val lLabel = labels[lIdx - 1]
                if (lLabel != "*") {
                    if (lLabel != domain[dIdx - 1]) {
                        return false
                    }
                }

                dIdx -= 1
                lIdx -= 1
            }

            return true
        }

        val label: String get() {
            val label = labels.joinToString(".")
            return if (isExceptionRule) {
                "!${label}"
            } else {
                label
            }
        }
    }

    data class Match(
        val matchingRules: List<Rule>,
        val prevailingRule: Rule,
        val domainComponents: List<String>
    ) {
        fun eTld(plus: Int = 0): String? {

            val eTldCount = if (prevailingRule.isExceptionRule) {
                prevailingRule.labels.size - 1
            } else {
                prevailingRule.labels.size
            }
            val desiredCount = eTldCount + plus

            return if (domainComponents.size < desiredCount) {
                null
            } else {
                domainComponents.subList(
                    fromIndex = domainComponents.size - desiredCount, // inclusive
                    toIndex = domainComponents.size // exclusive
                ).joinToString(separator = ".").lowercase()
            }
        }
    }

    private val rules = mutableListOf<Rule>()

    init {
        for (line in list.lines()) {
            Rule.parse(line)?.let {
                rules.add(it)
            }
        }
    }

    fun match(domain: String): Match {

        val whitespace = "\\s+".toRegex()
        val domainComponents = domain
            .trimStart()
            .split(whitespace, limit = 1).first() // split always returns non-empty list
            .split('.')
            .filter { it.isNotEmpty() }

        val domainLabels = domainComponents.map { it.lowercase() }

        // Algorithm:
        // 1. Match domain against all rules and take note of the matching ones.
        // 2. If no rules match, the prevailing rule is "*".
        // 3. If more than one rule matches, the prevailing rule is the one which is an exception rule.
        // 4. If there is no matching exception rule, the prevailing rule is the one with the most labels.
        // 5. If the prevailing rule is an exception rule, modify it by removing the leftmost label.
        // 6. The public suffix is the set of labels from the domain which match the labels
        //    of the prevailing rule, using the matching algorithm above.
        // 7. The registered or registrable domain is the public suffix plus one additional label.

        val matchingRules = mutableListOf<Rule>()
        if (domainLabels.isNotEmpty()) {
            for (rule in rules) {
                if (rule.matches(domainLabels)) {
                    matchingRules.add(rule)
                }
            }
        }

        val prevailingRule = when {
            matchingRules.isEmpty() -> {
                Rule(labels = listOf("*"), isExceptionRule = false)
            }
            matchingRules.size == 1 -> {
                matchingRules.first()
            }
            else -> {
                matchingRules.firstOrNull { it.isExceptionRule } ?: kotlin.run {
                    val longestSize = matchingRules.maxOf { it.labels.size }
                    matchingRules.first { it.labels.size == longestSize }
                }
            }
        }

        return Match(matchingRules, prevailingRule, domainComponents)
    }

    fun eTld(domain: String): String? {
        return match(domain).eTld(plus = 0)
    }

    fun eTldPlusOne(domain: String): String? {
        return match(domain).eTld(plus = 1)
    }
}