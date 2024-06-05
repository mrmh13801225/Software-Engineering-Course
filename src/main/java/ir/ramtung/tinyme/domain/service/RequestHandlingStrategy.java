package ir.ramtung.tinyme.domain.service;

public interface RequestHandlingStrategy<T extends Request> {
    List<MatchResult> handleRequest(T request);
}
