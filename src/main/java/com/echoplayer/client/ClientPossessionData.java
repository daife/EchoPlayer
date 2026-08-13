package com.echoplayer.client;

import java.util.UUID;

public class ClientPossessionData {
    public static UUID possessedUUID = null;
    public static int shellEntityId = -1;

    public static void beginPossession(UUID echoUUID, int shellId) {
        ClientPossessionData.reset();
        possessedUUID = echoUUID;
        shellEntityId = shellId;
    }

    public static void reset() {
        possessedUUID = null;
        shellEntityId = -1;
    }
}
