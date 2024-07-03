package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.domain.entity.MatchResult;
import ir.ramtung.tinyme.domain.entity.Result;

import java.util.ArrayList;

public class RequestHandlingResult {
    public Result executionResult = null;
    public ArrayList<MatchResult> ActivationResults = new ArrayList<>();
    public ArrayList<MatchResult> OpenningResults = new ArrayList<>();
}
