package jyk.bcar.domain

/** 다루지 않는 상사. 규칙 하나가 시트 한 줄 — `/.../`로 감싸면 정규식, 아니면 상사명에 포함되는지만 본다 */
class ExcludedAgencies(
    rules: List<String>,
) {
    private val patterns = rules
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()
        .associateWith(::toRegex)

    operator fun contains(car: Car): Boolean = patterns.values.any { it.containsMatchIn(car.agency) }

    /** 어느 차에도 걸리지 않은 규칙. 오타로 필터가 조용히 빠지는 걸 드러낸다 */
    fun unmatched(cars: List<Car>): Set<String> = patterns.filterValues { r -> cars.none { r.containsMatchIn(it.agency) } }.keys

    private fun toRegex(rule: String): Regex {
        val regex = rule.length > 2 && rule.startsWith("/") && rule.endsWith("/")
        return if (regex) Regex(rule.substring(1, rule.length - 1)) else Regex(Regex.escape(rule))
    }
}
