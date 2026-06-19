package dev.turtywurty.gamedashboard.data;

import java.text.Normalizer;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public final class IGDBGameMatcher {
    private static final int MAX_QUERIES = 12;
    private static final double MATCH_THRESHOLD = 78.0;
    private static final double AMBIGUITY_MARGIN = 4.0;
    private static final Set<String> STOP_WORDS = Set.of("the", "a", "an", "of", "for", "to", "in", "on");
    private static final Set<String> EDITION_WORDS = Set.of(
            "edition", "deluxe", "standard", "ultimate", "complete", "collection", "definitive",
            "remastered", "remaster", "goty", "anniversary", "gold", "bundle"
    );
    private static final List<List<String>> EDITION_PHRASES = List.of(
            List.of("deluxe", "edition"),
            List.of("standard", "edition"),
            List.of("ultimate", "edition"),
            List.of("complete", "edition"),
            List.of("definitive", "edition"),
            List.of("game", "of", "the", "year", "edition"),
            List.of("game", "of", "the", "year"),
            List.of("goty", "edition"),
            List.of("goty"),
            List.of("remastered"),
            List.of("remaster"),
            List.of("bundle")
    );
    private static final Map<String, String> ROMAN_NUMERALS = Map.ofEntries(
            Map.entry("i", "1"),
            Map.entry("ii", "2"),
            Map.entry("iii", "3"),
            Map.entry("iv", "4"),
            Map.entry("v", "5"),
            Map.entry("vi", "6"),
            Map.entry("vii", "7"),
            Map.entry("viii", "8"),
            Map.entry("ix", "9"),
            Map.entry("x", "10")
    );
    private static final Pattern SYMBOLS = Pattern.compile("[\\u2122\\u00AE\\u00A9]");
    private static final Pattern SEPARATORS = Pattern.compile("[:\\-\\u2013\\u2014.,'\"_/\\\\()\\[\\]{}]+");
    private static final Pattern NON_TOKEN = Pattern.compile("[^a-z0-9& ]+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private IGDBGameMatcher() {
    }

    public static List<String> generateQueries(String rawTitle) {
        TitleParts title = normalizeTitle(rawTitle);
        LinkedHashSet<String> queries = new LinkedHashSet<>();
        addQuery(queries, rawTitle);
        addQuery(queries, title.normalized());
        addStructuredQueryVariants(queries, title.tokens());

        List<String> tokens = title.tokens();
        List<TokenWindow> windows = new ArrayList<>();
        for (int size = Math.min(6, tokens.size()); size >= 2; size--) {
            for (int start = 0; start <= tokens.size() - size; start++) {
                List<String> windowTokens = tokens.subList(start, start + size);
                windows.add(new TokenWindow(String.join(" ", windowTokens), size, start, containsNumber(windowTokens)));
            }
        }

        windows.sort((left, right) -> {
            int numberCompare = Boolean.compare(right.containsNumber(), left.containsNumber());
            if (numberCompare != 0)
                return numberCompare;

            int sizeCompare = Integer.compare(right.size(), left.size());
            if (sizeCompare != 0)
                return sizeCompare;

            return Integer.compare(right.start(), left.start());
        });

        for (TokenWindow window : windows) {
            addQuery(queries, window.query());
            if (queries.size() >= MAX_QUERIES)
                break;
        }

        if (queries.size() < MAX_QUERIES) {
            for (String token : tokens) {
                if (containsDigit(token)) {
                    addQuery(queries, token);
                    if (queries.size() >= MAX_QUERIES)
                        break;
                }
            }
        }

        return queries.stream().limit(MAX_QUERIES).toList();
    }

    private static void addStructuredQueryVariants(LinkedHashSet<String> queries, List<String> tokens) {
        if (tokens.size() < 4)
            return;

        List<String> ampersandTokens = tokens.stream()
                .map(token -> token.equals("and") ? "&" : token)
                .toList();
        addQuery(queries, String.join(" ", ampersandTokens));

        for (int split = Math.max(2, tokens.size() - 3); split < tokens.size(); split++) {
            List<String> prefix = ampersandTokens.subList(0, split);
            List<String> suffix = ampersandTokens.subList(split, tokens.size());
            addQuery(queries, String.join(" ", prefix) + ": " + String.join(" ", suffix));
            if (suffix.size() >= 2) {
                addQuery(queries, String.join(" ", prefix)
                        + ": "
                        + suffix.getFirst()
                        + " - "
                        + String.join(" ", suffix.subList(1, suffix.size())));
            }

            if (queries.size() >= MAX_QUERIES)
                return;
        }
    }

    public static MatchResult findBestMatch(String localTitle, List<APIConnector.IGDBGameCandidate> candidates) {
        return findBestMatch(localTitle, candidates, null, null);
    }

    public static MatchResult findBestMatch(
            String localTitle,
            List<APIConnector.IGDBGameCandidate> candidates,
            String platform,
            Integer releaseYear
    ) {
        List<ScoredCandidate> scoredCandidates = candidates.stream()
                .filter(Objects::nonNull)
                .map(candidate -> score(localTitle, candidate, platform, releaseYear))
                .sorted(Comparator.comparing(ScoredCandidate::score)
                        .reversed()
                        .thenComparing(candidate -> candidate.candidate().parentGame() != null
                                || candidate.candidate().versionParent() != null))
                .toList();

        if (scoredCandidates.isEmpty()) {
            return new MatchResult(null, false, List.of(), "No IGDB candidates were returned.");
        }

        ScoredCandidate best = scoredCandidates.getFirst();
        ScoredCandidate second = scoredCandidates.stream()
                .skip(1)
                .filter(candidate -> !isEquivalentRelease(best.candidate(), candidate.candidate()))
                .findFirst()
                .orElse(null);
        if (best.score() < MATCH_THRESHOLD) {
            return new MatchResult(null, false, scoredCandidates,
                    "Best score " + format(best.score()) + " is below threshold " + format(MATCH_THRESHOLD) + ".");
        }

        if (second != null && best.score() - second.score() < AMBIGUITY_MARGIN) {
            return new MatchResult(null, true, scoredCandidates,
                    "Best score " + format(best.score()) + " is too close to second score " + format(second.score()) + ".");
        }

        return new MatchResult(best, false, scoredCandidates,
                "Selected '" + best.candidate().name() + "' with score " + format(best.score()) + ".");
    }

    private static boolean isEquivalentRelease(
            APIConnector.IGDBGameCandidate first,
            APIConnector.IGDBGameCandidate second
    ) {
        TitleParts firstTitle = normalizeTitle(first.name());
        TitleParts secondTitle = normalizeTitle(second.name());
        Integer firstYear = first.releaseYear();
        return firstYear != null
                && Objects.equals(firstYear, second.releaseYear())
                && firstTitle.normalizedNoAnd().equals(secondTitle.normalizedNoAnd())
                && firstTitle.hadEditionNoise() == secondTitle.hadEditionNoise();
    }

    private static ScoredCandidate score(
            String localTitle,
            APIConnector.IGDBGameCandidate candidate,
            String platform,
            Integer releaseYear
    ) {
        TitleParts local = normalizeTitle(localTitle);
        List<String> candidateTitles = new ArrayList<>();
        candidateTitles.add(candidate.name());
        candidateTitles.add(candidate.slug());
        candidateTitles.addAll(candidate.alternativeNames());

        double bestTextScore = 0;
        String bestTitle = "";
        for (String candidateTitle : candidateTitles) {
            if (candidateTitle == null || candidateTitle.isBlank())
                continue;

            TitleParts candidateParts = normalizeTitle(candidateTitle);
            double textScore = textScore(local, candidateParts);
            if (textScore > bestTextScore) {
                bestTextScore = textScore;
                bestTitle = candidateTitle;
            }
        }

        double score = bestTextScore;
        List<String> reasons = new ArrayList<>();
        reasons.add("text=" + format(bestTextScore) + " via '" + bestTitle + "'");

        TitleParts candidateName = normalizeTitle(candidate.name());
        boolean exactNormalizedMatch = isExactNormalizedMatch(local, candidateName);
        if (exactNormalizedMatch) {
            score += 14;
            reasons.add("exact normalized title");
        }

        Set<String> importantLocalTokens = importantTokens(local.tokensNoAnd());
        Set<String> candidateTokens = new HashSet<>(candidateName.tokensNoAnd());
        if (!importantLocalTokens.isEmpty() && candidateTokens.containsAll(importantLocalTokens)) {
            score += 8;
            reasons.add("all important tokens");
        }

        Set<String> localNumbers = numericTokens(local.tokens());
        Set<String> candidateNumbers = numericTokens(candidateName.tokens());
        if (!localNumbers.isEmpty()) {
            if (candidateNumbers.containsAll(localNumbers)) {
                score += 9;
                reasons.add("matching numbers");
            } else {
                score -= 18;
                reasons.add("missing number");
            }
        }

        if (platform != null && !platform.isBlank() && candidate.hasPlatform(platform)) {
            score += 5;
            reasons.add("platform match");
        }

        boolean releaseYearMismatch = false;
        if (releaseYear != null && candidate.releaseYear() != null) {
            if (Objects.equals(releaseYear, candidate.releaseYear())) {
                score += 4;
                reasons.add("release year match");
            } else {
                releaseYearMismatch = true;
                reasons.add("release year mismatch");
            }
        }

        double overlap = tokenOverlap(local.tokensNoAnd(), candidateName.tokensNoAnd());
        if (overlap < 0.45) {
            score -= 18;
            reasons.add("low token overlap");
        }

        if (local.tokensNoAnd().size() <= 2 && importantLocalTokens.size() <= 1) {
            score -= 8;
            reasons.add("short ambiguous title");
        }

        boolean localLooksLikeEdition = local.hadEditionNoise();
        boolean candidateLooksLikeEdition = candidate.looksLikeExpansionOrEdition() || candidateName.hadEditionNoise();
        boolean exactTitleNamesExpansion = exactNormalizedMatch && !candidateName.hadEditionNoise();
        if (!exactTitleNamesExpansion && !localLooksLikeEdition && candidateLooksLikeEdition) {
            score = Math.min(score - 20, MATCH_THRESHOLD - 2);
            reasons.add("candidate expansion/edition");
        }

        double normalizedScore = Math.clamp(score, 0, 100);
        if (releaseYearMismatch)
            normalizedScore = Math.max(0, normalizedScore - 5);
        return new ScoredCandidate(candidate, normalizedScore, reasons);
    }

    private static double textScore(TitleParts local, TitleParts candidate) {
        double tokenScore = tokenSetRatio(local.tokensNoAnd(), candidate.tokensNoAnd());
        double jaroScore = jaroWinkler(local.normalizedNoAnd(), candidate.normalizedNoAnd()) * 100;
        double levenshteinScore = levenshteinRatio(local.normalizedNoAnd(), candidate.normalizedNoAnd()) * 100;
        return tokenScore * 0.50 + jaroScore * 0.30 + levenshteinScore * 0.20;
    }

    private static boolean isExactNormalizedMatch(TitleParts local, TitleParts candidate) {
        return local.normalized().equals(candidate.normalized())
                || local.normalizedNoAnd().equals(candidate.normalizedNoAnd());
    }

    public static TitleParts normalizeTitle(String rawTitle) {
        if (rawTitle == null)
            rawTitle = "";

        String normalized = SYMBOLS.matcher(rawTitle).replaceAll("");
        normalized = Normalizer.normalize(normalized, Normalizer.Form.NFKD);
        normalized = normalized.replaceAll("(?i)\\((?:tm|r|c)\\)", "");
        normalized = normalized.toLowerCase(Locale.ROOT).replace("&", " and ");
        normalized = SEPARATORS.matcher(normalized).replaceAll(" ");
        normalized = NON_TOKEN.matcher(normalized).replaceAll(" ");
        normalized = WHITESPACE.matcher(normalized).replaceAll(" ").trim();

        List<String> rawTokens = normalized.isBlank()
                ? List.of()
                : new ArrayList<>(List.of(normalized.split(" ")));
        rawTokens = normalizeRomanNumerals(rawTokens);
        List<String> tokens = removeEditionNoise(rawTokens);
        boolean hadEditionNoise = !tokens.equals(rawTokens);
        List<String> tokensNoAnd = tokens.stream()
                .filter(token -> !token.equals("and"))
                .toList();

        return new TitleParts(
                String.join(" ", tokens),
                String.join(" ", tokensNoAnd),
                List.copyOf(tokens),
                List.copyOf(tokensNoAnd),
                hadEditionNoise
        );
    }

    private static List<String> normalizeRomanNumerals(List<String> tokens) {
        return tokens.stream()
                .map(token -> ROMAN_NUMERALS.getOrDefault(token, token))
                .toList();
    }

    private static List<String> removeEditionNoise(List<String> tokens) {
        List<String> current = new ArrayList<>(tokens);
        for (List<String> phrase : EDITION_PHRASES) {
            current = removePhraseIfSafe(current, phrase);
        }

        return current;
    }

    private static List<String> removePhraseIfSafe(List<String> tokens, List<String> phrase) {
        if (tokens.size() <= phrase.size())
            return tokens;

        for (int index = 0; index <= tokens.size() - phrase.size(); index++) {
            if (!tokens.subList(index, index + phrase.size()).equals(phrase))
                continue;

            List<String> remaining = new ArrayList<>(tokens);
            remaining.subList(index, index + phrase.size()).clear();
            if (remaining.size() >= 2
                    || remaining.stream().anyMatch(IGDBGameMatcher::containsDigit)
                    || remaining.stream().anyMatch(token -> token.length() >= 4 && !EDITION_WORDS.contains(token)))
                return remaining;
        }

        return tokens;
    }

    private static Set<String> importantTokens(List<String> tokens) {
        Set<String> important = new HashSet<>();
        for (String token : tokens) {
            if (token.length() >= 3 && !STOP_WORDS.contains(token) || containsDigit(token))
                important.add(token);
        }

        return important;
    }

    private static Set<String> numericTokens(List<String> tokens) {
        Set<String> numbers = new HashSet<>();
        for (String token : tokens) {
            if (containsDigit(token))
                numbers.add(token);
        }

        return numbers;
    }

    private static double tokenSetRatio(List<String> leftTokens, List<String> rightTokens) {
        Set<String> left = new LinkedHashSet<>(leftTokens);
        Set<String> right = new LinkedHashSet<>(rightTokens);
        if (left.isEmpty() && right.isEmpty())
            return 100;
        if (left.isEmpty() || right.isEmpty())
            return 0;

        Set<String> intersection = new HashSet<>(left);
        intersection.retainAll(right);
        double containment = Math.max(
                (double) intersection.size() / left.size(),
                (double) intersection.size() / right.size()
        );
        double dice = (2.0 * intersection.size()) / (left.size() + right.size());
        return (containment * 0.65 + dice * 0.35) * 100;
    }

    private static double tokenOverlap(List<String> leftTokens, List<String> rightTokens) {
        Set<String> left = importantTokens(leftTokens);
        Set<String> right = importantTokens(rightTokens);
        if (left.isEmpty())
            return 0;

        Set<String> intersection = new HashSet<>(left);
        intersection.retainAll(right);
        return (double) intersection.size() / left.size();
    }

    private static double levenshteinRatio(String left, String right) {
        if (left.equals(right))
            return 1;
        if (left.isEmpty() || right.isEmpty())
            return 0;

        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int index = 0; index <= right.length(); index++) {
            previous[index] = index;
        }

        for (int leftIndex = 1; leftIndex <= left.length(); leftIndex++) {
            current[0] = leftIndex;
            for (int rightIndex = 1; rightIndex <= right.length(); rightIndex++) {
                int cost = left.charAt(leftIndex - 1) == right.charAt(rightIndex - 1) ? 0 : 1;
                current[rightIndex] = Math.min(
                        Math.min(current[rightIndex - 1] + 1, previous[rightIndex] + 1),
                        previous[rightIndex - 1] + cost
                );
            }

            int[] swap = previous;
            previous = current;
            current = swap;
        }

        return 1.0 - (double) previous[right.length()] / Math.max(left.length(), right.length());
    }

    private static double jaroWinkler(String left, String right) {
        if (left.equals(right))
            return 1;
        if (left.isEmpty() || right.isEmpty())
            return 0;

        int matchDistance = Math.max(left.length(), right.length()) / 2 - 1;
        boolean[] leftMatches = new boolean[left.length()];
        boolean[] rightMatches = new boolean[right.length()];
        int matches = 0;
        for (int leftIndex = 0; leftIndex < left.length(); leftIndex++) {
            int start = Math.max(0, leftIndex - matchDistance);
            int end = Math.min(leftIndex + matchDistance + 1, right.length());
            for (int rightIndex = start; rightIndex < end; rightIndex++) {
                if (rightMatches[rightIndex] || left.charAt(leftIndex) != right.charAt(rightIndex))
                    continue;

                leftMatches[leftIndex] = true;
                rightMatches[rightIndex] = true;
                matches++;
                break;
            }
        }

        if (matches == 0)
            return 0;

        double transpositions = 0;
        int rightIndex = 0;
        for (int leftIndex = 0; leftIndex < left.length(); leftIndex++) {
            if (!leftMatches[leftIndex])
                continue;

            while (!rightMatches[rightIndex]) {
                rightIndex++;
            }

            if (left.charAt(leftIndex) != right.charAt(rightIndex))
                transpositions++;
            rightIndex++;
        }

        double jaro = ((double) matches / left.length()
                + (double) matches / right.length()
                + (matches - transpositions / 2.0) / matches) / 3.0;
        int prefix = 0;
        int maxPrefix = Math.min(4, Math.min(left.length(), right.length()));
        while (prefix < maxPrefix && left.charAt(prefix) == right.charAt(prefix)) {
            prefix++;
        }

        return jaro + prefix * 0.1 * (1 - jaro);
    }

    private static boolean containsNumber(List<String> tokens) {
        return tokens.stream().anyMatch(IGDBGameMatcher::containsDigit);
    }

    private static boolean containsDigit(String value) {
        return value != null && value.chars().anyMatch(Character::isDigit);
    }

    private static void addQuery(Set<String> queries, String query) {
        if (query == null)
            return;

        String normalized = WHITESPACE.matcher(query).replaceAll(" ").trim();
        if (!normalized.isBlank())
            queries.add(normalized);
    }

    private static String format(double score) {
        return String.format(Locale.ROOT, "%.1f", score);
    }

    private record TokenWindow(String query, int size, int start, boolean containsNumber) {
    }

    public record TitleParts(
            String normalized,
            String normalizedNoAnd,
            List<String> tokens,
            List<String> tokensNoAnd,
            boolean hadEditionNoise
    ) {
    }

    public record MatchResult(
            ScoredCandidate winner,
            boolean ambiguous,
            List<ScoredCandidate> candidates,
            String reason
    ) {
        public APIConnector.GameResult gameResult() {
            return this.winner == null ? null : this.winner.candidate().toGameResult();
        }
    }

    public record ScoredCandidate(
            APIConnector.IGDBGameCandidate candidate,
            double score,
            List<String> reasons
    ) {
    }

    public static Integer releaseYear(Long firstReleaseDate) {
        if (firstReleaseDate == null)
            return null;

        return Instant.ofEpochSecond(firstReleaseDate).atZone(ZoneOffset.UTC).getYear();
    }
}
