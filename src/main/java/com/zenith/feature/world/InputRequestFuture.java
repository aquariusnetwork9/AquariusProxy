package com.zenith.feature.world;

import com.zenith.util.RequestFuture;
import lombok.Getter;
import lombok.Setter;

public class InputRequestFuture extends RequestFuture {
    @Getter @Setter
    private volatile ClickResult clickResult = ClickResult.None.INSTANCE;

    public static final InputRequestFuture rejected = wrap(immediateFuture(false));

    public static InputRequestFuture wrap(RequestFuture future) {
        var inputRequestFuture = new InputRequestFuture();
        inputRequestFuture.setAccepted(future.isAccepted());
        inputRequestFuture.setCompleted(future.isCompleted());
        return inputRequestFuture;
    }

}
