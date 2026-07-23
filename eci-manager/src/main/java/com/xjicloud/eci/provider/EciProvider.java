package com.xjicloud.eci.provider;

public interface EciProvider {

    String create(CreateSpec spec);

    CloudInstanceView describe(String cloudInstanceId);

    void delete(String cloudInstanceId);
}
