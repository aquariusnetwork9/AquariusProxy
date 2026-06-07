package com.aquarius.feature.api.mojang;

import com.aquarius.feature.api.Api;
import com.aquarius.feature.api.mojang.model.MojangProfileResponse;

import java.util.Optional;

public class MojangApi extends Api {
    public static final MojangApi INSTANCE = new MojangApi();

    public MojangApi() {
        super("https://api.mojang.com");
    }

    public Optional<MojangProfileResponse> getProfile(final String username) {
        return get("/users/profiles/minecraft/" + username, MojangProfileResponse.class);
    }
}
