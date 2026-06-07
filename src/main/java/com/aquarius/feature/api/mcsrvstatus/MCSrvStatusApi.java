package com.aquarius.feature.api.mcsrvstatus;

import com.aquarius.feature.api.Api;
import com.aquarius.feature.api.mcsrvstatus.model.MCSrvStatusResponse;

import java.util.Optional;

public class MCSrvStatusApi extends Api {
    public static final MCSrvStatusApi INSTANCE = new MCSrvStatusApi();

    public MCSrvStatusApi() {
        super("https://api.mcsrvstat.us/3");
    }

    public Optional<MCSrvStatusResponse> getMCSrvStatus(final String address) {
        return get("/" + address, MCSrvStatusResponse.class);
    }
}
