package com.aquarius.database.dto.records;


import com.aquarius.database.dto.enums.Connectiontype;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ConnectionsRecord(OffsetDateTime time, Connectiontype connection, String playerName, UUID playerUuid) { }
