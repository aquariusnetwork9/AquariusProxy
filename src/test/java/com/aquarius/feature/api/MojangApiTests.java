package com.aquarius.feature.api;

import com.aquarius.feature.api.minetools.MinetoolsApi;
import com.aquarius.feature.api.mojang.MojangApi;
import com.aquarius.feature.api.sessionserver.SessionServerApi;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class MojangApiTests {

    final MojangApi api = new MojangApi();
    final MinetoolsApi minetoolsApi = new MinetoolsApi();
    final SessionServerApi sessionServerApi = new SessionServerApi();

//    @Test
    public void getMojangProfileTest() {
        var response = api.getProfile("rfresh2");
        assertTrue(response.isPresent());
        assertTrue(response.get().name().equals("rfresh2"));
        var uuid = response.get().uuid();
    }

//    @Test
    public void getMinetoolsProfileTest() {
        var response = minetoolsApi.getProfileFromUsername("rfresh2");
        assertTrue(response.isPresent());
        assertTrue(response.get().name().equals("rfresh2"));
        var uuid = response.get().uuid();
    }

//    @Test
    public void getSessionServerProfileTest() {
        var response = sessionServerApi.getProfile(UUID.fromString("572e683c-888a-4a0d-bc10-5d9cfa76d892"));
        assertTrue(response.isPresent());
        assertTrue(response.get().name().equals("rfresh2"));
        var uuid = response.get().uuid();
    }
}
