package com.fakerevive.support;

import com.fakerevive.FakeRevivePlugin;
import com.fakerevive.disguise.FakeIdentity;
import com.fakerevive.disguise.MojangIdentityFetcher;

import java.util.Optional;
import java.util.Set;

public class StubIdentityFakeRevivePlugin extends FakeRevivePlugin {
    private final MojangIdentityFetcher identityFetcher;

    public StubIdentityFakeRevivePlugin() {
        this(new MojangIdentityFetcher() {
            @Override
            public Optional<FakeIdentity> fetchNewIdentity(Set<String> excludedNames, int maxAttempts) {
                return Optional.empty();
            }
        });
    }

    public StubIdentityFakeRevivePlugin(MojangIdentityFetcher identityFetcher) {
        this.identityFetcher = identityFetcher;
    }

    @Override
    protected MojangIdentityFetcher createIdentityFetcher() {
        return identityFetcher;
    }
}
